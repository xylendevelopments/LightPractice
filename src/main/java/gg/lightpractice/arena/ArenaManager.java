package gg.lightpractice.arena;

import gg.lightpractice.api.ArenaService;
import gg.lightpractice.api.event.LightPracticeArenaResetEvent;
import gg.lightpractice.arena.schematic.SchematicManager;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Arena registry, reservations and resets.
 *
 * <p>Arenas are keyed by lower case name so lookups from commands are forgiving, while the configured
 * name is preserved for display. Reservations are tracked per match identifier: a standard arena holds
 * one match, a standalone arena can hold as many as {@code max-concurrent} allows, which is how
 * duplicate arenas are supported without cloning configuration.</p>
 */
public final class ArenaManager implements ArenaService, LightService {

    private final PluginCore core;
    private final SchematicManager schematics;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private ArenaEvacuator evacuator;
    private String defaultWorld;

    public ArenaManager(PluginCore core, SchematicManager schematics) {
        this.core = core;
        this.schematics = schematics;
        this.defaultWorld = core.configs().config().getString("arena.default-world", "");
    }

    @Override
    public String name() {
        return "ArenaManager";
    }

    @Override
    public void onEnable() {
        load();
        int usable = 0;
        for (Arena arena : arenas.values()) {
            if (arena.problems().isEmpty()) {
                usable++;
            }
        }
        core.plugin().getLogger().info("Loaded " + arenas.size() + " arena(s), " + usable + " ready for matches.");
    }

    @Override
    public void onDisable() {
        for (Arena arena : arenas.values()) {
            arena.releaseAll();
        }
        saveAll();
    }

    @Override
    public void onReload() {
        this.defaultWorld = core.configs().config().getString("arena.default-world", defaultWorld);
        Map<String, java.util.Set<String>> reservations = new LinkedHashMap<String, java.util.Set<String>>();
        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            reservations.put(entry.getKey(), entry.getValue().activeMatches());
        }
        load();
        for (Map.Entry<String, java.util.Set<String>> entry : reservations.entrySet()) {
            Arena arena = arenas.get(entry.getKey());
            if (arena != null) {
                for (String matchId : entry.getValue()) {
                    arena.reserve(matchId);
                }
            }
        }
        Debug.log(DebugCategory.ARENA, "Reloaded {} arena definition(s), reservations preserved", arenas.size());
    }

    public void setEvacuator(ArenaEvacuator evacuator) {
        this.evacuator = evacuator;
    }

    public SchematicManager schematics() {
        return schematics;
    }

    // ------------------------------------------------------------------ loading

    private void load() {
        arenas.clear();
        ConfigFile file = core.configs().arenas();
        ConfigurationSection root = file.section("arenas");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Arena arena = Arena.read(key, section);
            if (arena == null) {
                Debug.warn(DebugCategory.ARENA, "Arena entry '" + key + "' could not be read");
                continue;
            }
            arenas.put(key.toLowerCase(Locale.ROOT), arena);
            List<String> problems = arena.problems();
            if (!problems.isEmpty()) {
                Debug.log(DebugCategory.ARENA, "Arena {} is not match ready: {}", arena.name(), problems);
            }
        }
    }

    @Override
    public boolean save(Arena arena) {
        if (arena == null) {
            return false;
        }
        ConfigFile file = core.configs().arenas();
        ConfigurationSection root = file.getOrCreateSection("arenas");
        if (root == null) {
            return false;
        }
        ConfigurationSection section = root.getConfigurationSection(arena.name());
        if (section == null) {
            section = root.createSection(arena.name());
        }
        arena.write(section);
        file.saveAsync(core.tasks());
        Debug.log(DebugCategory.ARENA, "Saved arena {}", arena.name());
        return true;
    }

    @Override
    public void saveAll() {
        ConfigFile file = core.configs().arenas();
        ConfigurationSection root = file.getOrCreateSection("arenas");
        if (root == null) {
            return;
        }
        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            ConfigurationSection section = root.getConfigurationSection(entry.getValue().name());
            if (section == null) {
                section = root.createSection(entry.getValue().name());
            }
            entry.getValue().write(section);
        }
        file.saveAsync(core.tasks());
    }

    // ------------------------------------------------------------------ lookups

    @Override
    public Arena get(String name) {
        if (name == null) {
            return null;
        }
        return arenas.get(name.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<Arena> arenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    @Override
    public List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Arena arena : arenas.values()) {
            names.add(arena.name());
        }
        Collections.sort(names);
        return names;
    }

    @Override
    public int activeCount() {
        int total = 0;
        for (Arena arena : arenas.values()) {
            total += arena.activeCount();
        }
        return total;
    }

    /** Arena whose bounds contain the location, used by world protection listeners. */
    public Arena arenaAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (Arena arena : arenas.values()) {
            if (arena.contains(location)) {
                return arena;
            }
        }
        return null;
    }

    public List<Arena> arenasInWorld(World world) {
        List<Arena> result = new ArrayList<Arena>();
        if (world == null) {
            return result;
        }
        for (Arena arena : arenas.values()) {
            if (world.getName().equals(arena.worldName())) {
                result.add(arena);
            }
        }
        return result;
    }

    /** Arenas that can host matches right now, used by admin diagnostics. */
    public List<Arena> available() {
        List<Arena> result = new ArrayList<Arena>();
        for (Arena arena : arenas.values()) {
            if (arena.isAvailable() && arena.hasMinimumSpawns()) {
                result.add(arena);
            }
        }
        return result;
    }

    @Override
    public Arena findAvailable(Kit kit) {
        if (kit == null) {
            return null;
        }
        List<Arena> candidates = new ArrayList<Arena>();
        for (Arena arena : arenas.values()) {
            if (arena.isAvailable() && arena.acceptsKit(kit) && arena.hasMinimumSpawns()) {
                candidates.add(arena);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // Least recently used first so a busy server spreads matches across every arena it has.
        Collections.sort(candidates, new Comparator<Arena>() {
            @Override
            public int compare(Arena left, Arena right) {
                return Long.compare(left.lastUsed(), right.lastUsed());
            }
        });
        Arena chosen = candidates.get(0);
        Debug.log(DebugCategory.ARENA, "Selected arena {} for kit {} ({} candidates)", chosen.name(), kit.id(),
                candidates.size());
        return chosen;
    }

    // -------------------------------------------------------------- reservations

    @Override
    public boolean reserve(Arena arena, String matchId) {
        return arena != null && arena.reserve(matchId);
    }

    @Override
    public void release(Arena arena, String matchId) {
        if (arena != null) {
            arena.release(matchId);
        }
    }

    @Override
    public boolean isAvailable(Arena arena) {
        return arena != null && arena.isAvailable() && arena.hasMinimumSpawns();
    }

    /** Releases every reservation, used when matches are torn down during shutdown. */
    public void releaseAll() {
        for (Arena arena : arenas.values()) {
            arena.releaseAll();
        }
    }

    // ------------------------------------------------------------------ editing

    @Override
    public Arena create(String name, ArenaType type) {
        return create(name, type, defaultWorld());
    }

    public Arena create(String name, ArenaType type, World world) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmed = name.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(key)) {
            return null;
        }
        if (!trimmed.matches("[A-Za-z0-9_\\- ]{2,32}")) {
            Debug.log(DebugCategory.ARENA, "Rejected arena name '{}': only letters, numbers, _ - and space", trimmed);
            return null;
        }
        Arena arena = new Arena(trimmed, type == null ? ArenaType.STANDARD : type);
        arena.maxConcurrent(arena.type().standalone()
                ? core.configs().config().getInt("arena.standalone-max-concurrent", 1) : 1);
        if (world != null) {
            arena.worldName(world.getName());
        } else if (defaultWorld != null && !defaultWorld.isEmpty()) {
            arena.worldName(defaultWorld);
        }
        arenas.put(key, arena);
        save(arena);
        Debug.log(DebugCategory.ARENA, "Created arena {} ({})", arena.name(), arena.type());
        return arena;
    }

    private World defaultWorld() {
        if (defaultWorld != null && !defaultWorld.isEmpty()) {
            World world = Bukkit.getWorld(defaultWorld);
            if (world != null) {
                return world;
            }
        }
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    @Override
    public boolean delete(String name) {
        Arena arena = get(name);
        if (arena == null) {
            return false;
        }
        if (arena.activeCount() > 0) {
            Debug.log(DebugCategory.ARENA, "Refusing to delete arena {} while {} match(es) use it",
                    arena.name(), arena.activeCount());
            return false;
        }
        arenas.remove(arena.name().toLowerCase(Locale.ROOT));
        ConfigFile file = core.configs().arenas();
        ConfigurationSection root = file.section("arenas");
        if (root != null) {
            root.set(arena.name(), null);
            file.saveAsync(core.tasks());
        }
        Debug.log(DebugCategory.ARENA, "Deleted arena {}", arena.name());
        return true;
    }

    // ------------------------------------------------------------------- resets

    @Override
    public void reset(final Arena arena, final Consumer<Boolean> callback) {
        if (arena == null) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        LightPracticeArenaResetEvent event = new LightPracticeArenaResetEvent(arena);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.ARENA, "Reset of arena {} cancelled by a listener", arena.name());
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        int evacuated = evacuate(arena);
        if (evacuated > 0) {
            Debug.log(DebugCategory.ARENA, "Moved {} player(s) out of arena {} before resetting", evacuated,
                    arena.name());
        }
        schematics.paste(arena, callback);
    }

    @Override
    public void resetAll(final Consumer<Integer> callback) {
        final List<Arena> targets = new ArrayList<Arena>();
        for (Arena arena : arenas.values()) {
            if (arena.hasSchematic() && arena.hasBounds() && arena.activeCount() == 0) {
                targets.add(arena);
            }
        }
        if (targets.isEmpty()) {
            if (callback != null) {
                callback.accept(0);
            }
            return;
        }
        final int[] done = new int[]{0};
        resetSequentially(targets, 0, done, callback);
    }

    private void resetSequentially(final List<Arena> targets, final int index, final int[] done,
                                   final Consumer<Integer> callback) {
        if (index >= targets.size()) {
            saveAll();
            if (callback != null) {
                callback.accept(done[0]);
            }
            return;
        }
        final Arena arena = targets.get(index);
        reset(arena, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (Boolean.TRUE.equals(success)) {
                    done[0]++;
                }
                resetSequentially(targets, index + 1, done, callback);
            }
        });
    }

    /**
     * Moves every player inside the arena bounds somewhere safe. Returns how many players were moved so
     * the caller can report it.
     */
    public int evacuate(Arena arena) {
        if (arena == null || !arena.hasBounds() || arena.world() == null) {
            return 0;
        }
        int moved = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arena.contains(player.getLocation())) {
                continue;
            }
            if (evacuator != null) {
                evacuator.evacuate(player);
            } else {
                World world = arena.world();
                if (world != null) {
                    player.teleport(world.getSpawnLocation());
                }
            }
            moved++;
        }
        return moved;
    }

    /** True when the arena can be reset right now (has schematic, bounds, no live matches). */
    public boolean canReset(Arena arena) {
        return arena != null && arena.hasSchematic() && arena.hasBounds() && arena.activeCount() == 0
                && !schematics.isResetting(arena);
    }
}
