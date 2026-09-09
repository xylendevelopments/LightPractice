package gg.lightpractice.config;

import gg.lightpractice.util.Tasks;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns every configuration file of the plugin and is the only place where they are loaded and
 * reloaded. Reloading re-reads data into the existing managers (see {@code LightPractice#reload}),
 * it never rebuilds listeners, tasks or registries, which is what keeps reloads safe while matches
 * are running.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final ConfigFile config;
    private final ConfigFile database;
    private final ConfigFile messages;
    private final ConfigFile arenas;
    private final ConfigFile kits;
    private final ConfigFile queues;
    private final ConfigFile matchmaking;
    private final ConfigFile parties;
    private final ConfigFile tournaments;
    private final ConfigFile events;
    private final ConfigFile bots;
    private final ConfigFile cosmetics;
    private final ConfigFile scoreboard;
    private final ConfigFile tab;
    private final ConfigFile rewards;
    private final ConfigFile gui;
    private final ConfigFile divisions;
    private final ConfigFile combat;
    private final Messages messageService;
    private final List<ConfigFile> all;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.config = new ConfigFile(plugin, "config", true);
        this.database = new ConfigFile(plugin, "database", true);
        this.messages = new ConfigFile(plugin, "messages", true);
        this.arenas = new ConfigFile(plugin, "arenas", false);
        this.kits = new ConfigFile(plugin, "kits", false);
        this.queues = new ConfigFile(plugin, "queues", true);
        this.matchmaking = new ConfigFile(plugin, "matchmaking", true);
        this.parties = new ConfigFile(plugin, "parties", true);
        this.tournaments = new ConfigFile(plugin, "tournaments", true);
        this.events = new ConfigFile(plugin, "events", true);
        this.bots = new ConfigFile(plugin, "bots", true);
        this.cosmetics = new ConfigFile(plugin, "cosmetics", true);
        this.scoreboard = new ConfigFile(plugin, "scoreboard", true);
        this.tab = new ConfigFile(plugin, "tab", true);
        this.rewards = new ConfigFile(plugin, "rewards", true);
        this.gui = new ConfigFile(plugin, "gui", true);
        this.divisions = new ConfigFile(plugin, "divisions", true);
        this.combat = new ConfigFile(plugin, "combat", true);
        this.messageService = new Messages(messages);

        List<ConfigFile> files = new ArrayList<ConfigFile>();
        Collections.addAll(files, config, database, messages, arenas, kits, queues, matchmaking, parties,
                tournaments, events, bots, cosmetics, scoreboard, tab, rewards, gui, divisions, combat);
        this.all = Collections.unmodifiableList(files);
    }

    /** Re-reads every file and refreshes the message cache. */
    public void reloadAll() {
        for (ConfigFile file : all) {
            file.reload();
        }
        messageService.reload();
    }

    /** Persists the generated files (arenas and kits) without blocking the caller. */
    public void saveGenerated(Tasks tasks) {
        arenas.saveAsync(tasks);
        kits.saveAsync(tasks);
    }

    public Plugin plugin() {
        return plugin;
    }

    public ConfigFile config() {
        return config;
    }

    public ConfigFile database() {
        return database;
    }

    public ConfigFile messageFile() {
        return messages;
    }

    public ConfigFile arenas() {
        return arenas;
    }

    public ConfigFile kits() {
        return kits;
    }

    public ConfigFile queues() {
        return queues;
    }

    public ConfigFile matchmaking() {
        return matchmaking;
    }

    public ConfigFile parties() {
        return parties;
    }

    public ConfigFile tournaments() {
        return tournaments;
    }

    public ConfigFile events() {
        return events;
    }

    public ConfigFile bots() {
        return bots;
    }

    public ConfigFile cosmetics() {
        return cosmetics;
    }

    public ConfigFile scoreboard() {
        return scoreboard;
    }

    public ConfigFile tab() {
        return tab;
    }

    public ConfigFile rewards() {
        return rewards;
    }

    public ConfigFile gui() {
        return gui;
    }

    public ConfigFile divisions() {
        return divisions;
    }

    public ConfigFile combat() {
        return combat;
    }

    public Messages messages() {
        return messageService;
    }

    public List<ConfigFile> all() {
        return all;
    }
}
