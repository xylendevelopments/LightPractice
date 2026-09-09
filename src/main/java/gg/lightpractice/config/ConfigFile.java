package gg.lightpractice.config;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Tasks;
import gg.lightpractice.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A single YAML configuration file living inside {@code plugins/LightPractice}.
 *
 * <p>Files that ship with the jar are copied out on first run, everything else starts empty and is
 * filled in by the owning manager (arenas and kits are generated from the running server so they can
 * never disagree with the configuration). Writes are serialised per file and can be pushed to the
 * async scheduler so saving never blocks the main thread.</p>
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String name;
    private final File file;
    private final boolean bundled;
    private YamlConfiguration configuration;

    public ConfigFile(Plugin plugin, String name, boolean bundled) {
        this.plugin = plugin;
        this.name = name;
        this.bundled = bundled;
        this.file = new File(plugin.getDataFolder(), name + ".yml");
        load();
    }

    public synchronized void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            Debug.log(DebugCategory.DATABASE, "Could not create plugin data folder {}", plugin.getDataFolder());
        }
        if (!file.exists()) {
            if (bundled) {
                try {
                    plugin.saveResource(name + ".yml", false);
                } catch (IllegalArgumentException exception) {
                    Debug.error(DebugCategory.DATABASE,
                            "Bundled configuration " + name + ".yml is missing from the jar", exception);
                }
            }
            if (!file.exists()) {
                try {
                    if (!file.createNewFile()) {
                        Debug.log(DebugCategory.DATABASE, "Could not create {}", file.getName());
                    }
                } catch (IOException exception) {
                    Debug.error(DebugCategory.DATABASE, "Could not create " + file.getName(), exception);
                }
            }
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void reload() {
        load();
    }

    public synchronized void save() {
        if (configuration == null) {
            return;
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            Debug.error(DebugCategory.DATABASE, "Could not save " + file.getName(), exception);
        }
    }

    /** Saves without blocking the caller, used after every arena or kit change. */
    public void saveAsync(Tasks tasks) {
        if (tasks == null) {
            save();
            return;
        }
        final ConfigFile self = this;
        tasks.async(new Runnable() {
            @Override
            public void run() {
                self.save();
            }
        });
    }

    public YamlConfiguration config() {
        return configuration;
    }

    public File file() {
        return file;
    }

    public String name() {
        return name;
    }

    public boolean contains(String path) {
        return configuration != null && configuration.contains(path);
    }

    public String getString(String path, String fallback) {
        if (configuration == null || !configuration.contains(path)) {
            return fallback;
        }
        String value = configuration.getString(path);
        return value == null ? fallback : value;
    }

    public String getColorString(String path, String fallback) {
        return Text.color(getString(path, fallback));
    }

    public List<String> getStringList(String path) {
        if (configuration == null) {
            return new ArrayList<String>();
        }
        List<String> values = configuration.getStringList(path);
        return values == null ? new ArrayList<String>() : values;
    }

    public List<String> getColorList(String path) {
        return Text.color(getStringList(path));
    }

    public int getInt(String path, int fallback) {
        return configuration == null || !configuration.contains(path) ? fallback : configuration.getInt(path, fallback);
    }

    public long getLong(String path, long fallback) {
        return configuration == null || !configuration.contains(path) ? fallback : configuration.getLong(path, fallback);
    }

    public double getDouble(String path, double fallback) {
        return configuration == null || !configuration.contains(path) ? fallback : configuration.getDouble(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return configuration == null || !configuration.contains(path) ? fallback : configuration.getBoolean(path, fallback);
    }

    public ConfigurationSection section(String path) {
        return configuration == null ? null : configuration.getConfigurationSection(path);
    }

    public ConfigurationSection getOrCreateSection(String path) {
        if (configuration == null) {
            return null;
        }
        ConfigurationSection section = configuration.getConfigurationSection(path);
        return section != null ? section : configuration.createSection(path);
    }

    public Set<String> keys(String path) {
        ConfigurationSection section = section(path);
        return section == null ? Collections.<String>emptySet() : section.getKeys(false);
    }

    public Set<String> keys() {
        return configuration == null ? Collections.<String>emptySet() : configuration.getKeys(false);
    }

    public void set(String path, Object value) {
        if (configuration == null) {
            return;
        }
        configuration.set(path, value);
    }

    public void remove(String path) {
        if (configuration == null) {
            return;
        }
        configuration.set(path, null);
    }
}
