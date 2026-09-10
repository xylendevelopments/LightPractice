package gg.lightpractice.arena;

import gg.lightpractice.util.Locations;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Axis aligned region of an arena. Used for out of bounds losses (sumo, spleef), build limits and to
 * decide which players have to be evacuated before a schematic is pasted.
 */
public final class ArenaBounds {

    private final String worldName;
    private final int minX;
    private final minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public ArenaBounds(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static ArenaBounds between(Location first, Location second) {
        if (!Locations.isValid(first) || !Locations.isValid(second) || first.getWorld() != second.getWorld()) {
            return null;
        }
        return new ArenaBounds(first.getWorld().getName(),
                first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    public static ArenaBounds around(Location center, int radius, int height) {
        if (!Locations.isValid(center)) {
            return null;
        }
        int safeRadius = Math.max(1, radius);
        int safeHeight = Math.max(1, height);
        return new ArenaBounds(center.getWorld().getName(),
                center.getBlockX() - safeRadius, center.getBlockY() - safeHeight, center.getBlockZ() - safeRadius,
                center.getBlockX() + safeRadius, center.getBlockY() + safeHeight, center.getBlockZ() + safeRadius);
    }

    public String worldName() {
        return worldName;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public int volume() {
        return width() * height() * length();
    }

    /** True when the bounds describe a usable region in a named world. */
    public boolean valid() {
        return worldName != null && !worldName.trim().isEmpty()
                && maxX >= minX && maxY >= minY && maxZ >= minZ
                && (maxX - minX) > 0 && (maxZ - minZ) > 0;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !worldName.equals(location.getWorld().getName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Location center() {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, (minX + maxX + 1) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ + 1) / 2.0D);
    }

    public Location minimum(World world) {
        return new Location(world, minX, minY, minZ);
    }

    public Location maximum(World world) {
        return new Location(world, maxX, maxY, maxZ);
    }

    public void write(ConfigurationSection section, String path) {
        if (section == null) {
            return;
        }
        section.set(path + ".world", worldName);
        section.set(path + ".min-x", minX);
        section.set(path + ".min-y", minY);
        section.set(path + ".min-z", minZ);
        section.set(path + ".max-x", maxX);
        section.set(path + ".max-y", maxY);
        section.set(path + ".max-z", maxZ);
    }

    public static ArenaBounds read(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return null;
        }
        String world = section.getString(path + ".world");
        if (world == null || world.isEmpty()) {
            return null;
        }
        return new ArenaBounds(world,
                section.getInt(path + ".min-x"), section.getInt(path + ".min-y"), section.getInt(path + ".min-z"),
                section.getInt(path + ".max-x"), section.getInt(path + ".max-y"), section.getInt(path + ".max-z"));
    }

    @Override
    public String toString() {
        return worldName + " [" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
