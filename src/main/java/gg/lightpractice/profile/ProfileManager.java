package gg.lightpractice.profile;

import gg.lightpractice.api.ProfileService;
import gg.lightpractice.api.event.LightPracticeProfileLoadEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.repository.PlayerProfileRepository;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Profile cache and persistence.
 *
 * <p>Profiles are loaded asynchronously and every callback is delivered on the main thread, so callers
 * never have to think about threading. When MongoDB is unreachable the manager refuses to hand out a
 * blank profile that could later overwrite real data: the request is queued, a retry task keeps trying
 * to reconnect and - when configured - the player is told they cannot play yet.</p>
 */
public final class ProfileManager implements ProfileService, LightService {

    private final PluginCore core;
    private final PlayerProfileRepository repository;
    private final Map<UUID, Profile> cache = new HashMap<UUID, Profile>();
    private final Map<UUID, List<Consumer<Profile>>> pending = new HashMap<UUID, List<Consumer<Profile>>>();
    private final Map<UUID, String> queuedNames = new HashMap<UUID, String>();
    private final Set<UUID> loading = new HashSet<UUID>();
    private final Set<UUID> readOnly = new HashSet<UUID>();
    private BukkitTask saveTask;
    private BukkitTask retryTask;
    private long saveIntervalTicks = 6000L;
    private boolean kickOnFailure = true;

    public ProfileManager(PluginCore core, PlayerProfileRepository repository) {
        this.core = core;
        this.repository = repository;
        reloadSettings();
    }

    private void reloadSettings() {
        ConfigFile database = core.configs().database();
        this.saveIntervalTicks = database.getLong("behaviour.save-interval-ticks", 6000L);
        this.kickOnFailure = database.getBoolean("behaviour.kick-on-failure", true);
    }

    @Override
    public String name() {
        return "ProfileManager";
    }

    @Override
    public void onEnable() {
        repository.ensureIndexes();
        if (saveTask != null) {
            core.tasks().cancel(saveTask);
        }
        if (retryTask != null) {
            core.tasks().cancel(retryTask);
        }
        saveTask = core.tasks().asyncTimer(new Runnable() {
            @Override
            public void run() {
                saveDirtyProfiles();
            }
        }, saveIntervalTicks, saveIntervalTicks);
        retryTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                retryConnection();
            }
        }, 200L, 200L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            requestProfile(player, null);
        }
        Debug.log(DebugCategory.PROFILE, "Profile manager enabled (save interval {} ticks)", saveIntervalTicks);
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(saveTask);
        core.tasks().cancel(retryTask);
        saveTask = null;
        retryTask = null;
        int saved = flushBlocking(10L);
        cache.clear();
        pending.clear();
        queuedNames.clear();
        loading.clear();
        readOnly.clear();
        core.plugin().getLogger().info("Saved " + saved + " player profile(s) during shutdown.");
    }

    @Override
    public void onReload() {
        reloadSettings();
        if (saveTask != null) {
            core.tasks().cancel(saveTask);
            saveTask = core.tasks().asyncTimer(new Runnable() {
                @Override
                public void run() {
                    saveDirtyProfiles();
                }
            }, saveIntervalTicks, saveIntervalTicks);
        }
    }

    // ------------------------------------------------------------------ lookup

    @Override
    public Profile getProfile(UUID uuid) {
        return uuid == null ? null : cache.get(uuid);
    }

    @Override
    public Profile getProfile(Player player) {
        return player == null ? null : cache.get(player.getUniqueId());
    }

    @Override
    public boolean isLoaded(UUID uuid) {
        return uuid != null && cache.containsKey(uuid);
    }

    @Override
    public Collection<Profile> cached() {
        return Collections.unmodifiableCollection(new ArrayList<Profile>(cache.values()));
    }

    @Override
    public int cachedCount() {
        return cache.size();
    }

    @Override
    public boolean isDatabaseAvailable() {
        return core.database().isConnected();
    }

    /** Profiles loaded while the database was down must never be written back. */
    public boolean isReadOnly(UUID uuid) {
        return uuid != null && readOnly.contains(uuid);
    }

    @Override
    public void requestProfile(Player player, Consumer<Profile> callback) {
        if (player == null) {
            return;
        }
        request(player.getUniqueId(), player.getName(), player, callback);
    }

    @Override
    public void requestProfile(UUID uuid, Consumer<Profile> callback) {
        request(uuid, null, null, callback);
    }

    private void request(final UUID uuid, final String fallbackName, final Player player,
                         final Consumer<Profile> callback) {
        if (uuid == null) {
            return;
        }
        Profile cached = cache.get(uuid);
        if (cached != null) {
            if (fallbackName != null) {
                cached.name(fallbackName);
            }
            if (callback != null) {
                callback.accept(cached);
            }
            return;
        }
        if (callback != null) {
            List<Consumer<Profile>> callbacks = pending.get(uuid);
            if (callbacks == null) {
                callbacks = new ArrayList<Consumer<Profile>>();
                pending.put(uuid, callbacks);
            }
            callbacks.add(callback);
        }
        if (loading.contains(uuid)) {
            return;
        }
        DatabaseService database = core.database();
        if (!database.isConnected()) {
            queuedNames.put(uuid, fallbackName == null ? "Player" : fallbackName);
            if (player != null && kickOnFailure) {
                player.kickPlayer(core.messages().raw("database.kick"));
                Debug.log(DebugCategory.PROFILE, "{} cannot be loaded because the database is offline", uuid);
            } else if (player != null) {
                core.messages().send(player, "database.unavailable");
            }
            return;
        }
        loading.add(uuid);
        final String name = fallbackName == null ? "Player" : fallbackName;
        CompletableFuture<Profile> future = repository.load(uuid);
        database.onMain(future, new Consumer<Profile>() {
            @Override
            public void accept(Profile loaded) {
                handleLoaded(uuid, name, player, loaded);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                handleFailure(uuid, name, player, error);
            }
        });
    }

    private void handleLoaded(UUID uuid, String name, Player player, Profile loaded) {
        loading.remove(uuid);
        queuedNames.remove(uuid);
        Profile profile = loaded;
        if (profile == null) {
            profile = new Profile(uuid, name);
            profile.loaded(false);
            profile.markDirty();
            Debug.log(DebugCategory.PROFILE, "Created a new profile for {}", name);
        } else if (player != null) {
            profile.name(player.getName());
        }
        cache.put(uuid, profile);
        readOnly.remove(uuid);
        Bukkit.getPluginManager().callEvent(new LightPracticeProfileLoadEvent(profile, player));
        deliver(uuid, profile);
    }

    private void handleFailure(UUID uuid, String name, Player player, Throwable error) {
        loading.remove(uuid);
        core.plugin().getLogger().log(Level.WARNING,
                "Could not load the profile of " + name + " (" + uuid + ")", error);
        readOnly.add(uuid);
        if (player != null && player.isOnline()) {
            if (kickOnFailure) {
                player.kickPlayer(core.messages().raw("database.kick"));
            } else {
                core.messages().send(player, "database.unavailable");
            }
        }
        deliver(uuid, null);
    }

    private void deliver(UUID uuid, Profile profile) {
        List<Consumer<Profile>> callbacks = pending.remove(uuid);
        if (callbacks == null) {
            return;
        }
        for (Consumer<Profile> callback : callbacks) {
            try {
                callback.accept(profile);
            } catch (Throwable throwable) {
                core.plugin().getLogger().log(Level.WARNING, "Profile callback failed for " + uuid, throwable);
            }
        }
    }

    /** Called once a reconnect succeeded so queued joins are not stuck forever. */
    private void retryConnection() {
        if (core.database().isConnected()) {
            if (!queuedNames.isEmpty()) {
                flushQueued();
            }
            return;
        }
        if (queuedNames.isEmpty() && loading.isEmpty()) {
            // Nothing is waiting, keep retrying quietly so the database is ready before players join.
            core.database().connectAsync();
            return;
        }
        Debug.log(DebugCategory.DATABASE, "Retrying the MongoDB connection, {} request(s) queued",
                queuedNames.size());
        core.database().connectAsync();
    }

    private void flushQueued() {
        Iterator<Map.Entry<UUID, String>> iterator = queuedNames.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            UUID uuid = entry.getKey();
            iterator.remove();
            Player player = Bukkit.getPlayer(uuid);
            request(uuid, entry.getValue(), player, null);
        }
    }

    // -------------------------------------------------------------------- save

    @Override
    public void save(Profile profile) {
        if (profile == null) {
            return;
        }
        if (readOnly.contains(profile.uuid())) {
            Debug.log(DebugCategory.PROFILE, "Skipping save of {} because the profile is read only",
                    profile.name());
            return;
        }
        if (!core.database().isConnected()) {
            Debug.log(DebugCategory.PROFILE, "Database offline, {} stays dirty until the next attempt",
                    profile.name());
            return;
        }
        final Profile target = profile;
        core.database().onMain(repository.save(target), new Consumer<Boolean>() {
            @Override
            public void accept(Boolean saved) {
                if (Boolean.TRUE.equals(saved)) {
                    target.markClean();
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                core.plugin().getLogger().log(Level.WARNING,
                        "Could not save the profile of " + target.name(), error);
            }
        });
    }

    /** Saves immediately regardless of the dirty flag, used before a state change that could crash. */
    public void saveNow(Profile profile) {
        if (profile == null) {
            return;
        }
        profile.markDirty();
        save(profile);
    }

    private void saveDirtyProfiles() {
        if (!core.database().isConnected()) {
            return;
        }
        List<Profile> dirty = new ArrayList<Profile>();
        for (Profile profile : cache.values()) {
            if (profile.dirty() && !readOnly.contains(profile.uuid())) {
                dirty.add(profile);
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        final int count = dirty.size();
        core.database().onMain(repository.saveAll(dirty), new Consumer<Integer>() {
            @Override
            public void accept(Integer saved) {
                Debug.log(DebugCategory.PROFILE, "Periodic save wrote {} of {} dirty profiles", saved, count);
            }
        });
    }

    @Override
    public void saveAll() {
        if (!core.database().isConnected()) {
            Debug.log(DebugCategory.PROFILE, "Database offline, skipping saveAll");
            return;
        }
        List<Profile> all = new ArrayList<Profile>(cache.values());
        if (all.isEmpty()) {
            return;
        }
        final int count = all.size();
        core.database().onMain(repository.saveAll(all), new Consumer<Integer>() {
            @Override
            public void accept(Integer saved) {
                Debug.log(DebugCategory.PROFILE, "Saved {} of {} cached profiles", saved, count);
            }
        });
    }

    /** Blocking save used while the plugin disables, returns how many profiles were written. */
    public int flushBlocking(long timeoutSeconds) {
        List<Profile> all = new ArrayList<Profile>();
        for (Profile profile : cache.values()) {
            if (!readOnly.contains(profile.uuid())) {
                profile.lastSeen(System.currentTimeMillis());
                all.add(profile);
            }
        }
        if (all.isEmpty() || !core.database().isConnected()) {
            return 0;
        }
        try {
            Integer saved = repository.saveAll(all).get(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
            return saved == null ? 0 : saved;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            core.plugin().getLogger().warning("Profile saving was interrupted during shutdown.");
        } catch (Exception exception) {
            core.plugin().getLogger().log(Level.SEVERE, "Could not save profiles during shutdown", exception);
        }
        return 0;
    }

    @Override
    public void unload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Profile profile = cache.remove(uuid);
        loading.remove(uuid);
        readOnly.remove(uuid);
        pending.remove(uuid);
        queuedNames.remove(uuid);
        if (profile != null) {
            profile.lastSeen(System.currentTimeMillis());
            save(profile);
        }
    }

    /** Applies a name change detected on join, keeping usernames in sync for history lookups. */
    public void updateName(UUID uuid, String name) {
        Profile profile = cache.get(uuid);
        if (profile != null && name != null) {
            profile.name(name);
        }
    }
}
