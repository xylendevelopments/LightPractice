package gg.lightpractice.arena;

import gg.lightpractice.kit.Kit;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A place where matches happen.
 *
 * <p>Arenas are configuration driven: named spawn points ({@code red}, {@code blue}, plus any number of
 * extra spawns for FFA), optional bounds for out of arena rules, an optional schematic used for resets
 * and a kit whitelist. Reservation is tracked per match identifier so a standalone arena can serve the
 * configured number of concurrent matches while a standard arena stays exclusive.</p>
 */
public final class Arena {

    public static final String SPAWN_RED = "red";
    public static final String SPAWN_BLUE = "blue";

    private final String name;
    private final ArenaType type;
    private final Map<String, Location> spawns = new LinkedHashMap<String, Location>();
    private final Set<String> kitWhitelist = new LinkedHashSet<String>();
    private final Set<String> activeMatches = new LinkedHashSet<String>();
    private String displayName;
    private String worldName;
    private Location spectatorSpawn;
    private ArenaBounds bounds;
    private String schematic;
    private boolean enabled;
    private int maxConcurrent = 1;
    private ArenaState state = ArenaState.IDLE;
    private long lastUsed;

    public Arena(String name, ArenaType type) {
        this.name = name;
        this.type = type == null ? ArenaType.STANDARD : type;
        this.enabled = true;
        this.lastUsed = 0L;
    }

    public String name() {
        return name;
    }

    public ArenaType type() {
        return type;
    }

    public String displayName() {
        return displayName == null || displayName.isEmpty() ? name : displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName;
    }

    public World world() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public boolean isWorldLoaded() {
        return world() != null;
    }

    public Location spectatorSpawn() {
        if (spectatorSpawn != null) {
            return spectatorSpawn.clone();
        }
        Location center = center();
        return center == null ? null : center.add(0.0D, 6.0D, 0.0D);
    }

    public void spectatorSpawn(Location location) {
        this.spectatorSpawn = Locations.isValid(location) ? location.clone() : null;
    }

    public ArenaBounds bounds() {
        return bounds;
    }

    public void bounds(ArenaBounds bounds) {
        this.bounds = bounds;
    }

    public boolean hasBounds() {
        return bounds != null;
    }

    public String schematic() {
        return schematic;
    }

    public void schematic(String schematic) {
        this.schematic = schematic == null || schematic.trim().isEmpty() ? null : schematic.trim();
    }

    public boolean hasSchematic() {
        return schematic != null;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.state = ArenaState.DISABLED;
        } else if (this.state == ArenaState.DISABLED) {
            this.state = activeMatches.isEmpty() ? ArenaState.IDLE : ArenaState.IN_USE;
        }
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public void maxConcurrent(int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public ArenaState state() {
        return state;
    }

    /** True when the arena may host another match right now. */
    public boolean isAvailable() {
        if (!enabled || state == ArenaState.DISABLED || state == ArenaState.RESETTING) {
            return false;
        }
        if (!isWorldLoaded()) {
            return false;
        }
        return activeMatches.size() < maxConcurrent;
    }

    public boolean isResetting() {
        return state == ArenaState.RESETTING;
    }

    /** Marks the arena as being rebuilt; no match may be started while this is true. */
    public void markResetting(boolean resetting) {
        if (resetting) {
            this.state = ArenaState.RESETTING;
        } else {
            this.state = activeMatches.isEmpty() ? (enabled ? ArenaState.IDLE : ArenaState.DISABLED) : ArenaState.IN_USE;
        }
    }

    /** Reserves a slot for a match. Returns false when the arena is full or unusable. */
    public boolean reserve(String matchId) {
        if (matchId == null) {
            return false;
        }
        if (activeMatches.contains(matchId)) {
            return true;
        }
        if (!isAvailable()) {
            Debug.log(DebugCategory.ARENA, "Arena {} cannot be reserved (state={}, active={}/{})",
                    name, state, activeMatches.size(), maxConcurrent);
            return false;
        }
        activeMatches.add(matchId);
        state = ArenaState.IN_USE;
        lastUsed = System.currentTimeMillis();
        Debug.log(DebugCategory.ARENA, "Arena {} reserved for match {} ({} active)", name, matchId, activeMatches.size());
        return true;
    }

    /** Releases a reservation. Safe to call for unknown or already released identifiers. */
    public void release(String matchId) {
        if (matchId == null) {
            return;
        }
        if (activeMatches.remove(matchId)) {
            Debug.log(DebugCategory.ARENA, "Arena {} released match {}", name, matchId);
        }
        if (activeMatches.isEmpty() && state == ArenaState.IN_USE) {
            state = enabled ? ArenaState.IDLE : ArenaState.DISABLED;
        }
    }

    public void releaseAll() {
        activeMatches.clear();
        if (state == ArenaState.IN_USE) {
            state = enabled ? ArenaState.IDLE : ArenaState.DISABLED;
        }
    }

    public int activeCount() {
        return activeMatches.size();
    }

    public Set<String> activeMatches() {
        return Collections.unmodifiableSet(activeMatches);
    }

    public long lastUsed() {
        return lastUsed;
    }

    public void touch() {
        this.lastUsed = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ spawns

    public Location spawn(String key) {
        if (key == null) {
            return null;
        }
        Location location = spawns.get(key.toLowerCase(java.util.Locale.ROOT));
        return location == null ? null : location.clone();
    }

    public void setSpawn(String key, Location location) {
        if (key == null || !Locations.isValid(location)) {
            return;
        }
        spawns.put(key.toLowerCase(java.util.Locale.ROOT).trim(), location.clone());
        if (worldName == null) {
            worldName = location.getWorld().getName();
        }
    }

    public boolean removeSpawn(String key) {
        return key != null && spawns.remove(key.toLowerCase(java.util.Locale.ROOT)) != null;
    }

    public Set<String> spawnNames() {
        return Collections.unmodifiableSet(spawns.keySet());
    }

    public List<Location> spawnList() {
        List<Location> result = new ArrayList<Location>();
        for (Location location : spawns.values()) {
            if (Locations.isValid(location)) {
                result.add(location.clone());
            }
        }
        return result;
    }

    public boolean hasSpawn(String key) {
        return spawn(key) != null;
    }

    public boolean hasMinimumSpawns() {
        int usable = 0;
        for (Location location : spawns.values()) {
            if (Locations.isValid(location)) {
                usable++;
            }
        }
        return usable >= 2;
    }

    /** Spawn used by a team index, falling back to any configured spawn. */
    public Location spawnFor(int index) {
        if (index <= 0) {
            Location red = spawn(SPAWN_RED);
            if (red != null) {
                return red;
            }
        }
        if (index == 1) {
            Location blue = spawn(SPAWN_BLUE);
            if (blue != null) {
                return blue;
            }
        }
        List<Location> all = spawnList();
        if (all.isEmpty()) {
            return null;
        }
        return all.get(Math.abs(index) % all.size()).clone();
    }

    public Location center() {
        if (bounds != null) {
            Location center = bounds.center();
            if (center != null) {
                return center;
            }
        }
        List<Location> all = spawnList();
        if (all.isEmpty()) {
            return null;
        }
        if (all.size() == 1) {
            return all.get(0).clone();
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        World world = all.get(0).getWorld();
        for (Location location : all) {
            x += location.getX();
            y += location.getY();
            z += location.getZ();
        }
        return new Location(world, x / all.size(), y / all.size(), z / all.size());
    }

    public boolean contains(Location location) {
        return bounds != null && bounds.contains(location);
    }

    // -------------------------------------------------------------------- kits

    public Set<String> kitWhitelist() {
        return Collections.unmodifiableSet(kitWhitelist);
    }

    public void allowKit(String kitId) {
        if (kitId != null && !kitId.trim().isEmpty()) {
            kitWhitelist.add(kitId.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public boolean removeKit(String kitId) {
        return kitId != null && kitWhitelist.remove(kitId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void clearKits() {
        kitWhitelist.clear();
    }

    /** An empty whitelist means the arena accepts every kit. */
    public boolean acceptsKit(Kit kit) {
        if (kit == null) {
            return false;
        }
        return kitWhitelist.isEmpty() || kitWhitelist.contains(kit.id().toLowerCase(java.util.Locale.ROOT));
    }

    // -------------------------------------------------------------- persistence

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("type", type.name());
        section.set("display-name", displayName);
        section.set("world", worldName);
        section.set("enabled", enabled);
        section.set("max-concurrent", maxConcurrent);
        section.set("schematic", schematic);
        section.set("last-used", lastUsed);
        section.set("kits", new ArrayList<String>(kitWhitelist));
        if (spectatorSpawn != null) {
            Locations.write(section, "spectator", spectatorSpawn);
        }
        if (bounds != null) {
            bounds.write(section, "bounds");
        }
        ConfigurationSection spawnSection = section.createSection("spawns");
        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            Locations.write(spawnSection, entry.getKey(), entry.getValue());
        }
    }

    public static Arena read(String name, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Arena arena = new Arena(name, ArenaType.parse(section.getString("type")));
        arena.displayName(section.getString("display-name"));
        arena.worldName(section.getString("world"));
        arena.enabled(section.getBoolean("enabled", true));
        arena.maxConcurrent(section.getInt("max-concurrent", 1));
        arena.schematic(section.getString("schematic"));
        arena.lastUsed = section.getLong("last-used", 0L);
        for (String kit : section.getStringList("kits")) {
            arena.allowKit(kit);
        }
        arena.spectatorSpawn(Locations.read(section, "spectator"));
        arena.bounds(ArenaBounds.read(section, "bounds"));
        ConfigurationSection spawnSection = section.getConfigurationSection("spawns");
        if (spawnSection != null) {
            for (String key : spawnSection.getKeys(false)) {
                Location location = Locations.read(spawnSection, key);
                if (location != null) {
                    arena.spawns.put(key.toLowerCase(java.util.Locale.ROOT), location);
                    if (arena.worldName == null && location.getWorld() != null) {
                        arena.worldName = location.getWorld().getName();
                    }
                } else {
                    Debug.log(DebugCategory.ARENA, "Spawn '{}' of arena {} points at an unloaded world", key, name);
                }
            }
        }
        if (!arena.enabled) {
            arena.state = ArenaState.DISABLED;
        }
        return arena;
    }

    /** Problems that would stop this arena from hosting matches, shown by {@code /arena info}. */
    public List<String> problems() {
        List<String> problems = new ArrayList<String>();
        if (!enabled) {
            problems.add("disabled");
        }
        if (worldName == null || !isWorldLoaded()) {
            problems.add("world '" + worldName + "' is not loaded");
        }
        if (spawns.size() < 2) {
            problems.add("needs at least two spawn points (" + spawns.size() + " configured)");
        }
        if (schematic == null) {
            problems.add("no schematic, resets are unavailable");
        }
        if (bounds == null) {
            problems.add("no bounds, out of arena rules are unavailable");
        }
        return problems;
    }

    @Override
    public String toString() {
        return "Arena{" + name + ", type=" + type + ", state=" + state + ", spawns=" + spawns.size() + '}';
    }
}
