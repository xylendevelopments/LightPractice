package gg.lightpractice.kit;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Configuration friendly potion effect. 1.8 resolves effect types by name, so an unknown name is
 * reported once through the debug logger and skipped instead of breaking the whole kit.
 */
public final class StoredPotionEffect {

    private final String type;
    private final int amplifier;
    private final int durationSeconds;
    private final boolean ambient;
    private final boolean particles;

    public StoredPotionEffect(String type, int amplifier, int durationSeconds, boolean ambient, boolean particles) {
        this.type = type == null ? "SPEED" : type.trim();
        this.amplifier = Math.max(0, amplifier);
        this.durationSeconds = durationSeconds;
        this.ambient = ambient;
        this.particles = particles;
    }

    public String type() {
        return type;
    }

    public int amplifier() {
        return amplifier;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public boolean ambient() {
        return ambient;
    }

    public boolean particles() {
        return particles;
    }

    public PotionEffectType resolve() {
        PotionEffectType resolved = PotionEffectType.getByName(type);
        if (resolved == null) {
            Debug.log(DebugCategory.KIT, "Unknown potion effect type '{}' in a kit definition", type);
        }
        return resolved;
    }

    /** Builds the Bukkit effect, or {@code null} when the type does not exist on this server. */
    public PotionEffect effect() {
        PotionEffectType resolved = resolve();
        if (resolved == null) {
            return null;
        }
        int ticks = durationSeconds < 0 ? Integer.MAX_VALUE : durationSeconds * 20;
        return new PotionEffect(resolved, ticks, amplifier, ambient, particles);
    }

    /** Parses the {@code TYPE:amplifier:seconds} shorthand used in kit configuration. */
    public static StoredPotionEffect parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String[] parts = value.trim().split(":");
        String type = parts[0].trim();
        int amplifier = parts.length > 1 ? parseInt(parts[1], 0) : 0;
        int duration = parts.length > 2 ? parseInt(parts[2], 60) : 60;
        boolean ambient = parts.length <= 3 || Boolean.parseBoolean(parts[3].trim());
        boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4].trim());
        return new StoredPotionEffect(type, amplifier, duration, ambient, particles);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return type + ":" + amplifier + ":" + durationSeconds + ":" + ambient + ":" + particles;
    }
}
