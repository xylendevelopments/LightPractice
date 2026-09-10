package gg.lightpractice.util;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visual and audio helpers for Minecraft 1.8.9.
 *
 * <p>1.8 has no {@code Particle} enum - particles are exposed through {@link Effect} and are played
 * with {@code World.Spigot#playEffect} so counts, offsets and speed stay configurable. Sounds are
 * resolved from the 1.8 {@link Sound} enum with an alias table so administrators can paste modern
 * sound names into configuration without breaking anything: an unknown name is reported through the
 * debug logger and skipped instead of throwing.</p>
 */
public final class Visuals {

    private static final Map<String, Sound> SOUND_CACHE = new ConcurrentHashMap<String, Sound>();
    private static final Map<String, Effect> EFFECT_CACHE = new ConcurrentHashMap<String, Effect>();
    private static final Map<String, String> SOUND_ALIASES;
    private static final Map<String, String> EFFECT_ALIASES;
    private static final Sound MISSING_SOUND = Sound.CLICK;
    private static volatile boolean warnedSpigotEffect;

    static {
        Map<String, String> sounds = new HashMap<String, String>();
        sounds.put("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
        sounds.put("ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
        sounds.put("UI_BUTTON_CLICK", "CLICK");
        sounds.put("BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");
        sounds.put("BLOCK_NOTE_PLING", "NOTE_PLING");
        sounds.put("ENTITY_GENERIC_EXPLODE", "EXPLODE");
        sounds.put("BLOCK_ANVIL_BREAK", "ANVIL_BREAK");
        sounds.put("BLOCK_ANVIL_USE", "ANVIL_USE");
        sounds.put("BLOCK_ANVIL_LAND", "ANVIL_LAND");
        sounds.put("BLOCK_GLASS_BREAK", "GLASS");
        sounds.put("BLOCK_CHEST_OPEN", "CHEST_OPEN");
        sounds.put("BLOCK_CHEST_CLOSE", "CHEST_CLOSE");
        sounds.put("ENTITY_PLAYER_BURP", "BURP");
        sounds.put("ENTITY_GENERIC_DRINK", "DRINK");
        sounds.put("ENTITY_GENERIC_EAT", "EAT");
        sounds.put("ENTITY_ARROW_HIT_PLAYER", "SUCCESSFUL_HIT");
        sounds.put("ENTITY_ENDERMEN_TELEPORT", "ENDERMAN_TELEPORT");
        sounds.put("ENTITY_BAT_TAKEOFF", "BAT_TAKEOFF");
        sounds.put("ENTITY_FIREWORK_BLAST", "FIREWORK_BLAST");
        sounds.put("ENTITY_BLAZE_SHOOT", "GHAST_FIREBALL");
        sounds.put("BLOCK_FIRE_EXTINGUISH", "FIZZ");
        sounds.put("ENTITY_PLAYER_HURT", "HURT_FLESH");
        sounds.put("ENTITY_PLAYER_BIG_FALL", "FALL_BIG");
        sounds.put("BLOCK_PISTON_EXTEND", "PISTON_EXTEND");
        SOUND_ALIASES = Collections.unmodifiableMap(sounds);

        Map<String, String> effects = new HashMap<String, String>();
        effects.put("FLAME", "MOBSPAWNER_FLAMES");
        effects.put("LAVA", "LAVA_POP");
        effects.put("REDSTONE", "COLOURED_DUST");
        effects.put("DUST", "COLOURED_DUST");
        effects.put("CRIT", "CRIT");
        effects.put("MAGIC_CRIT", "MAGIC_CRIT");
        effects.put("HEART", "HEART");
        effects.put("SMOKE", "SMOKE");
        effects.put("LARGE_SMOKE", "LARGE_SMOKE");
        effects.put("SPELL", "SPELL");
        effects.put("INSTANT_SPELL", "INSTANT_SPELL");
        effects.put("WITCH_MAGIC", "WITCH_MAGIC");
        effects.put("POTION_BREAK", "POTION_BREAK");
        effects.put("HAPPY_VILLAGER", "HAPPY_VILLAGER");
        effects.put("VILLAGER_THUNDERCLOUD", "VILLAGER_THUNDERCLOUD");
        effects.put("ENDER_SIGNAL", "ENDER_SIGNAL");
        effects.put("FIREWORK_SHOOT", "FIREWORK_SHOOT");
        effects.put("STEP_SOUND", "STEP_SOUND");
        effects.put("MOBSPAWNER_FLAMES", "MOBSPAWNER_FLAMES");
        EFFECT_ALIASES = Collections.unmodifiableMap(effects);
    }

    private Visuals() {
    }

    /** Resolves a sound name (1.8 or modern) or returns {@code null} when it does not exist. */
    public static Sound sound(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        Sound cached = SOUND_CACHE.get(key);
        if (cached != null) {
            return cached == MISSING_SOUND ? null : cached;
        }
        Sound resolved = lookupSound(key);
        SOUND_CACHE.put(key, resolved == null ? MISSING_SOUND : resolved);
        return resolved;
    }

    private static Sound lookupSound(String key) {
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            String alias = SOUND_ALIASES.get(key);
            if (alias != null) {
                try {
                    return Sound.valueOf(alias);
                } catch (IllegalArgumentException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    public static void sound(Location location, String name, float volume, float pitch) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Sound sound = sound(name);
        if (sound == null) {
            Debug.log(DebugCategory.COSMETIC, "Unknown sound '" + name + "' configured, effect skipped");
            return;
        }
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    public static void sound(Player player, String name, float volume, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Sound sound = sound(name);
        if (sound == null) {
            Debug.log(DebugCategory.COSMETIC, "Unknown sound '" + name + "' configured, effect skipped");
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** Resolves an {@link Effect} name, tolerating both 1.8 names and common modern aliases. */
    public static Effect effect(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        Effect cached = EFFECT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Effect resolved = null;
        try {
            resolved = Effect.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            String alias = EFFECT_ALIASES.get(key);
            if (alias != null) {
                try {
                    resolved = Effect.valueOf(alias);
                } catch (IllegalArgumentException ignoredAgain) {
                    resolved = null;
                }
            }
        }
        if (resolved != null) {
            EFFECT_CACHE.put(key, resolved);
        }
        return resolved;
    }

    /**
     * Plays a particle style effect. Effects that require block/item data (such as {@code STEP_SOUND})
     * receive {@code dataId}; everything else ignores it.
     */
    public static void effect(Location location, String name, int count, double offsetX, double offsetY,
                              double offsetZ, double speed, int radius, int dataId) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Effect effect = effect(name);
        if (effect == null) {
            Debug.log(DebugCategory.COSMETIC, "Unknown effect '" + name + "' configured, particle skipped");
            return;
        }
        World world = location.getWorld();
        try {
            world.spigot().playEffect(location, effect, dataId, 0,
                    (float) offsetX, (float) offsetY, (float) offsetZ, (float) speed,
                    Math.max(1, count), Math.max(1, radius));
        } catch (Throwable throwable) {
            // CraftBukkit (non Spigot) builds do not expose World.Spigot, fall back to the vanilla call.
            if (!warnedSpigotEffect) {
                warnedSpigotEffect = true;
                Debug.log(DebugCategory.INTEGRATION,
                        "World.Spigot#playEffect unavailable, falling back to World#playEffect");
            }
            try {
                world.playEffect(location, effect, dataId);
            } catch (Throwable ignored) {
                Debug.log(DebugCategory.COSMETIC,
                        "Effect " + effect.name() + " could not be played at " + location);
            }
        }
    }

    /** Convenience overload for single, simple effects. */
    public static void effect(Location location, String name) {
        effect(location, name, 1, 0.0D, 0.0D, 0.0D, 0.0D, 16, 0);
    }

    public static void effect(Location location, String name, int count, double speed) {
        effect(location, name, count, 0.25D, 0.25D, 0.25D, speed, 32, 0);
    }
}
