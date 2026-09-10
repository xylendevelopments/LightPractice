package gg.lightpractice.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Bukkit configuration sections into plain nested maps.
 *
 * <p>Kit rules, cosmetic actions and reward tables are read as generic option trees, so a section has
 * to be turned into {@code Map<String, Object>} values that survive being passed around without a
 * configuration dependency.</p>
 */
public final class ConfigMaps {

    private ConfigMaps() {
    }

    @SuppressWarnings("unchecked")
    public static Object toPlain(Object value) {
        if (value instanceof ConfigurationSection) {
            return toMap((ConfigurationSection) value);
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), toPlain(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object entry : (List<Object>) value) {
                result.add(toPlain(entry));
            }
            return result;
        }
        return value;
    }

    public static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, toPlain(section.get(key)));
        }
        return result;
    }

    public static Map<String, Object> toMap(Object value) {
        Object plain = toPlain(value);
        if (plain instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) plain;
            return map;
        }
        return new LinkedHashMap<String, Object>();
    }

    /** True when the value represents a nested section rather than a scalar. */
    public static boolean isSection(Object value) {
        return value instanceof ConfigurationSection || value instanceof Map;
    }
}
