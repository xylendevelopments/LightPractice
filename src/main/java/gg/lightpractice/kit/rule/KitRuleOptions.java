package gg.lightpractice.kit.rule;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Options of one rule inside one kit.
 *
 * <p>Configuration accepts either a boolean ({@code no-fall-damage: true}) or a nested section
 * ({@code build: {allowed: [WOOL], only-in-bounds: true}}), and this class presents both shapes
 * through the same accessors so rules never parse raw YAML.</p>
 */
public final class KitRuleOptions {

    private static final KitRuleOptions DISABLED = new KitRuleOptions(false, Collections.<String, Object>emptyMap());

    private final boolean enabled;
    private final Map<String, Object> values;

    private KitRuleOptions(boolean enabled, Map<String, Object> values) {
        this.enabled = enabled;
        this.values = values;
    }

    public static KitRuleOptions disabled() {
        return DISABLED;
    }

    @SuppressWarnings("unchecked")
    public static KitRuleOptions of(Object raw) {
        if (raw == null) {
            return DISABLED;
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? new KitRuleOptions(true, Collections.<String, Object>emptyMap()) : DISABLED;
        }
        if (raw instanceof org.bukkit.configuration.ConfigurationSection || raw instanceof Map) {
            Map<String, Object> values = gg.lightpractice.util.ConfigMaps.toMap(raw);
            Object enabled = values.get("enabled");
            boolean active = !(enabled instanceof Boolean) || (Boolean) enabled;
            return new KitRuleOptions(active, values);
        }
        if (raw instanceof Number || raw instanceof String) {
            Map<String, Object> values = new java.util.HashMap<String, Object>();
            values.put("value", raw);
            return new KitRuleOptions(true, values);
        }
        return DISABLED;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public int integer(String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public double decimal(String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<String> list(String key) {
        Object value = values.get(key);
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object entry : (List<Object>) value) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
        } else if (value instanceof String) {
            for (String entry : ((String) value).split(",")) {
                if (!entry.trim().isEmpty()) {
                    result.add(entry.trim());
                }
            }
        }
        return result;
    }

    /** Materials of a configured list, ignoring names that do not exist on 1.8.9. */
    public Set<Material> materials(String key) {
        Set<Material> materials = new LinkedHashSet<Material>();
        for (String entry : list(key)) {
            Material material = Items.material(entry, null);
            if (material == null) {
                Debug.log(DebugCategory.KIT, "Rule option '{}' lists unknown material {}", key, entry);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    /** Materials of a configured list, falling back to the supplied defaults when absent. */
    public Set<Material> materials(String key, java.util.Collection<String> fallback) {
        if (!has(key)) {
            Set<Material> materials = new LinkedHashSet<Material>();
            if (fallback != null) {
                for (String entry : fallback) {
                    Material material = Items.material(entry, null);
                    if (material != null) {
                        materials.add(material);
                    }
                }
            }
            return materials;
        }
        return materials(key);
    }

    public Material material(String key, Material fallback) {
        Object value = values.get(key);
        if (value instanceof String) {
            Material material = Items.material((String) value, null);
            if (material != null) {
                return material;
            }
            Debug.log(DebugCategory.KIT, "Rule option '{}' lists unknown material {}", key, value);
        }
        return fallback;
    }

    public Set<String> lowerCaseList(String key) {
        Set<String> values = new LinkedHashSet<String>();
        for (String entry : list(key)) {
            values.add(entry.toLowerCase(Locale.ROOT));
        }
        return values;
    }

    public Map<String, Object> raw() {
        return values;
    }
}
