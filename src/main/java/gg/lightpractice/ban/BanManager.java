package gg.lightpractice.ban;

import gg.lightpractice.api.BanService;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.repository.BanRepository;
import gg.lightpractice.model.BanEntry;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Practice bans kept in MongoDB and cached in memory.
 *
 * <p>The cache answers the check that runs on every queue join, duel request and party invite, so those
 * paths never touch the database. Bans are loaded when the service starts and whenever the database
 * reconnects, and an expiry task drops finished bans without a restart.</p>
 */
public final class BanManager implements BanService, LightService {

    private final PluginCore core;
    private final BanRepository repository;
    private final Map<UUID, BanEntry> cache = new ConcurrentHashMap<UUID, BanEntry>();
    private BukkitTask expiryTask;
    private int cacheLimit = 1000;
    private boolean kickOnBan = true;

    public BanManager(PluginCore core, BanRepository repository) {
        this.core = core;
        this.repository = repository;
    }

    @Override
    public String name() {
        return "bans";
    }

    @Override
    public int startupOrder() {
        return 48;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
        refresh();
        expiryTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                expire();
            }
        }, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(expiryTask);
        expiryTask = null;
        cache.clear();
    }

    @Override
    public void onReload() {
        readConfiguration();
        refresh();
    }

    private void readConfiguration() {
        ConfigFile file = core.configs().database();
        this.cacheLimit = Math.max(50, file.getInt("bans.cache-limit", 1000));
        ConfigFile config = core.configs().config();
        this.kickOnBan = config.getBoolean("bans.kick-on-ban", true);
    }

    // ------------------------------------------------------------- BanService

    @Override
    public boolean isBanned(UUID uuid) {
        BanEntry entry = ban(uuid);
        return entry != null && entry.active();
    }

    @Override
    public BanEntry ban(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        BanEntry entry = cache.get(uuid);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            cache.remove(uuid);
            return null;
        }
        return entry;
    }

    @Override
    public boolean ban(final UUID uuid, String name, String reason, UUID issuer, String issuerName,
                       long durationMillis) {
        if (uuid == null) {
            return false;
        }
        BanEntry entry = durationMillis > 0L
                ? BanEntry.temporary(uuid, name, reason, issuer, issuerName, durationMillis)
                : BanEntry.permanent(uuid, name, reason, issuer, issuerName);
        cache.put(uuid, entry);
        DatabaseService database = core.database();
        if (database != null && database.isConnected() && repository != null) {
            database.onMain(repository.save(entry), new Consumer<Boolean>() {
                @Override
                public void accept(Boolean saved) {
                    Debug.log(DebugCategory.DATABASE, "Stored the ban of {}: {}", uuid, saved);
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable error) {
                    Debug.error(DebugCategory.DATABASE, "Could not store the ban of " + uuid, error);
                }
            });
        } else {
            Debug.warn(DebugCategory.DATABASE, "Ban of " + uuid + " is cached only, the database is down");
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && kickOnBan) {
            player.kickPlayer(kickMessage(entry));
        }
        Debug.log(DebugCategory.PLAYER, "{} was banned by {} ({}): {}", name, issuerName,
                entry.permanent() ? "permanent" : Text.duration(entry.remainingMillis()), reason);
        return true;
    }

    @Override
    public boolean unban(final UUID uuid) {
        if (uuid == null) {
            return false;
        }
        cache.remove(uuid);
        DatabaseService database = core.database();
        if (database != null && database.isConnected() && repository != null) {
            database.onMain(repository.remove(uuid), new Consumer<Boolean>() {
                @Override
                public void accept(Boolean done) {
                    Debug.log(DebugCategory.DATABASE, "Removed the ban of {}: {}", uuid, done);
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable error) {
                    Debug.error(DebugCategory.DATABASE, "Could not remove the ban of " + uuid, error);
                }
            });
        }
        Debug.log(DebugCategory.PLAYER, "{} was unbanned", uuid);
        // the cache no longer holds the ban; the database removal is confirmed asynchronously
        return true;
    }

    @Override
    public Collection<BanEntry> bans() {
        List<BanEntry> entries = new ArrayList<BanEntry>(cache.values());
        Collections.sort(entries, new Comparator<BanEntry>() {
            @Override
            public int compare(BanEntry left, BanEntry right) {
                return Long.compare(right.issuedAt(), left.issuedAt());
            }
        });
        return Collections.unmodifiableList(entries);
    }

    @Override
    public void refresh() {
        DatabaseService database = core.database();
        if (database == null || !database.isConnected() || repository == null) {
            Debug.log(DebugCategory.DATABASE, "Ban cache not refreshed, the database is not connected");
            return;
        }
        database.onMain(repository.all(cacheLimit), new Consumer<List<BanEntry>>() {
            @Override
            public void accept(List<BanEntry> entries) {
                cache.clear();
                int active = 0;
                for (BanEntry entry : entries == null ? new ArrayList<BanEntry>() : entries) {
                    if (entry == null || entry.uuid() == null) {
                        continue;
                    }
                    if (entry.expired()) {
                        continue;
                    }
                    cache.put(entry.uuid(), entry);
                    active++;
                }
                Debug.log(DebugCategory.DATABASE, "Cached {} active ban(s)", active);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not load the ban list", error);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Kicks a joining player when a ban is active, returning true when the player was kicked. */
    public boolean checkJoin(Player player) {
        if (player == null) {
            return false;
        }
        BanEntry entry = ban(player.getUniqueId());
        if (entry == null || !entry.active()) {
            return false;
        }
        player.kickPlayer(kickMessage(entry));
        Debug.log(DebugCategory.PLAYER, "{} tried to join while banned", player.getName());
        return true;
    }

    /** Kick screen of a ban, using the configured message and the remaining time. */
    public String kickMessage(BanEntry entry) {
        if (entry == null) {
            return Text.color("&cYou are banned.");
        }
        String key = entry.permanent() ? "ban.kick-permanent" : "ban.kick-temporary";
        String message = core.messages().raw(key,
                "{reason}", entry.reason() == null ? "none" : entry.reason(),
                "{issuer}", entry.issuerName() == null ? "console" : entry.issuerName(),
                "{time}", Text.duration(entry.remainingMillis()),
                "{expires}", String.valueOf(entry.expiresAt()));
        return Text.color(message);
    }

    /** Drops expired bans locally and in the database, run by one central task. */
    private void expire() {
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, BanEntry> entry : cache.entrySet()) {
            BanEntry value = entry.getValue();
            if (value == null || value.expired()) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            cache.remove(uuid);
        }
        if (!expired.isEmpty()) {
            Debug.log(DebugCategory.DATABASE, "{} ban(s) expired", expired.size());
        }
        DatabaseService database = core.database();
        if (database != null && database.isConnected() && repository != null) {
            database.onMain(repository.purgeExpired(), new Consumer<Integer>() {
                @Override
                public void accept(Integer removed) {
                    if (removed != null && removed > 0) {
                        Debug.log(DebugCategory.DATABASE, "Purged {} expired ban document(s)", removed);
                    }
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable error) {
                    Debug.error(DebugCategory.DATABASE, "Could not purge expired bans", error);
                }
            });
        }
    }

    public int cachedCount() {
        return cache.size();
    }

    /** Formatted remaining time of a ban, used by menus and placeholders. */
    public String remaining(BanEntry entry) {
        if (entry == null) {
            return "";
        }
        return entry.permanent() ? "permanent" : Text.duration(entry.remainingMillis());
    }
}
