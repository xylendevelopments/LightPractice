package gg.lightpractice.kit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Drops every child of a configuration section so a rewrite never leaves removed kits behind.
 */
final class YamlClear {

    private YamlClear() {
    }

    static void clear(YamlConfiguration configuration, String path) {
        if (configuration == null || path == null) {
            return;
        }
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            section.set(key, null);
        }
    }
}
