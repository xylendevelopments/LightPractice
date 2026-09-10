package gg.lightpractice.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Location persistence helpers. Locations are stored as a {@code world,x,y,z,yaw,pitch} string in
 * single line configuration (duels, kit editor) and as a section for arenas and the lobby so
 * administrators can edit coordinates by hand.
 */
public final class Locations {

    private Locations() {
    }

    public static boolean isValid(Location location) {
        return location != null && location.getWorld() != null;
    }

    public static String serialize(Location location) {
        if (!isValid(location)) {
            return "";
        }
        return String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.2f,%.2f",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public static Location deserialize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            Debug.log(DebugCategory.ARENA, "Stored location references unknown world '{}'", parts[0]);
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0.0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            Debug.log(DebugCategory.ARENA, "Stored location '{}' is malformed", value);
            return null;
        }
    }

    public static void write(ConfigurationSection section, String path, Location location) {
        if (section == null || !isValid(location)) {
            return;
        }
        section.set(path + ".world", location.getWorld().getName());
        section.set(path + ".x", location.getX());
        section.set(path + ".y", location.getY());
        section.set(path + ".z", location.getZ());
        section.set(path + ".yaw", location.getYaw());
        section.set(path + ".pitch", location.getPitch());
    }

    public static Location read(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return null;
        }
        String worldName = section.getString(path + ".world");
        if (worldName == null || worldName.trim().isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Debug.log(DebugCategory.ARENA, "Location '{}' references unknown world '{}'", path, worldName);
            return null;
        }
        return new Location(world,
                section.getDouble(path + ".x"),
                section.getDouble(path + ".y"),
                section.getDouble(path + ".z"),
                (float) section.getDouble(path + ".yaw"),
                (float) section.getDouble(path + ".pitch"));
    }

    /** Returns the centre of the block the location sits on, keeping yaw and pitch. */
    public static Location center(Location location) {
        if (!isValid(location)) {
            return location;
        }
        return new Location(location.getWorld(),
                location.getBlockX() + 0.5D,
                location.getY(),
                location.getBlockZ() + 0.5D,
                location.getYaw(), location.getPitch());
    }

    public static Location withDirection(Location location, float yaw, float pitch) {
        if (!isValid(location)) {
            return location;
        }
        Location copy = location.clone();
        copy.setYaw(yaw);
        copy.setPitch(pitch);
        return copy;
    }

    public static double distanceSquared(Location left, Location right) {
        if (!isValid(left) || !isValid(right) || left.getWorld() != right.getWorld()) {
            return Double.MAX_VALUE;
        }
        return left.distanceSquared(right);
    }

    public static String format(Location location) {
        if (!isValid(location)) {
            return "none";
        }
        return String.format(Locale.ROOT, "%s %.1f %.1f %.1f", location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }
}
