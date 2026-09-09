package gg.lightpractice.model;

import java.util.Locale;

/** Cosmetic categories, each one independently purchasable and equipable. */
public enum CosmeticType {

    KILL_EFFECT,
    KILL_MESSAGE,
    TRAIL,
    PROJECTILE_EFFECT,
    VICTORY_EFFECT;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String messageKey() {
        return "cosmetics.types." + configKey();
    }

    public static CosmeticType parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("KILLEFFECT".equals(key)) {
            return KILL_EFFECT;
        }
        if ("KILLMESSAGE".equals(key) || "MESSAGE".equals(key)) {
            return KILL_MESSAGE;
        }
        if ("PROJECTILE".equals(key)) {
            return PROJECTILE_EFFECT;
        }
        if ("VICTORY".equals(key)) {
            return VICTORY_EFFECT;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
