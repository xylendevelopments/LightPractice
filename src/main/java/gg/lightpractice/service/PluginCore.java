package gg.lightpractice.service;

import gg.lightpractice.config.ConfigManager;
import gg.lightpractice.config.Messages;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.util.Tasks;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Infrastructure every manager needs: the plugin instance, the scheduler, configuration, messages,
 * the database service and the registry itself. Passing this single object keeps constructor
 * injection readable without turning any manager into a static singleton.
 */
public final class PluginCore {

    private final JavaPlugin plugin;
    private final Tasks tasks;
    private final ConfigManager configs;
    private final Messages messages;
    private final DatabaseService database;
    private final ServiceRegistry services;

    public PluginCore(JavaPlugin plugin, Tasks tasks, ConfigManager configs, DatabaseService database,
                      ServiceRegistry services) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.configs = configs;
        this.messages = configs.messages();
        this.database = database;
        this.services = services;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Tasks tasks() {
        return tasks;
    }

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public DatabaseService database() {
        return database;
    }

    public ServiceRegistry services() {
        return services;
    }

    public String version() {
        return plugin.getDescription().getVersion();
    }

    public File dataFolder() {
        return plugin.getDataFolder();
    }

    public File schematicFolder() {
        File folder = new File(plugin.getDataFolder(), "schematics");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the schematics folder: " + folder.getAbsolutePath());
        }
        return folder;
    }

    public <T> T service(Class<T> type) {
        return services.get(type);
    }

    public <T> T optional(Class<T> type) {
        return services.find(type);
    }
}
