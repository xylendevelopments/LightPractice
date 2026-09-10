package gg.lightpractice.lobby;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.player.PlayerState;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Locations;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The hub: lobby spawn, lobby items and the return path used after every match.
 *
 * <p>Every system that has to send a player home calls the same {@link #sendToLobby(Player)}, including
 * the match engine which looks this service up when a match ends, so the behaviour never diverges.</p>
 */
public final class LobbyManager implements LightService {

    private final PluginCore core;
    private final List<LobbyItem> items = new ArrayList<LobbyItem>();
    private boolean giveItems = true;
    private boolean healOnReturn = true;
    private String joinSound = "";
    private boolean clearOnEnter = true;

    public LobbyManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "lobby";
    }

    @Override
    public int startupOrder() {
        return 42;
    }

    @Override
    public void onLoad() {
        reload();
    }

    @Override
    public void onEnable() {
        reload();
        if (!hasSpawn()) {
            Debug.warn(DebugCategory.CONFIG, "No lobby spawn configured, use /setlobby to define one");
        }
    }

    @Override
    public void onDisable() {
        items.clear();
    }

    @Override
    public void onReload() {
        reload();
    }

    public void reload() {
        ConfigFile config = core.configs().config();
        this.giveItems = config.getBoolean("lobby.give-items", true);
        this.healOnReturn = config.getBoolean("lobby.heal-on-return", true);
        this.joinSound = config.getString("lobby.join-sound", "");
        this.clearOnEnter = config.getBoolean("lobby.clear-inventory", true);
        items.clear();
        ConfigFile gui = core.configs().gui();
        ConfigurationSection root = gui.section("lobby.items");
        if (root == null || root.getKeys(false).isEmpty()) {
            installDefaults(gui);
            root = gui.section("lobby.items");
        }
        if (root != null) {
            for (String key : root.getKeys(false)) {
                LobbyItem item = LobbyItem.read(key, root.getConfigurationSection(key));
                if (item != null && item.enabled()) {
                    items.add(item);
                }
            }
        }
        Collections.sort(items, new Comparator<LobbyItem>() {
            @Override
            public int compare(LobbyItem left, LobbyItem right) {
                return Integer.compare(left.slot(), right.slot());
            }
        });
        Debug.log(DebugCategory.PLAYER, "Lobby ready: {} item(s), spawn {}", items.size(),
                hasSpawn() ? Locations.format(spawn()) : "unset");
    }

    private void installDefaults(ConfigFile gui) {
        write(gui, "kits", 0, "CHEST", "&bKit Selector", "&7Choose or edit your kits");
        write(gui, "queues", 1, "IRON_SWORD", "&aQueues", "&7Join a ranked or unranked queue");
        write(gui, "party", 3, "NAME_TAG", "&dParty", "&7Create or manage your party");
        write(gui, "leaderboards", 4, "SIGN", "&6Leaderboards", "&7See the top players");
        write(gui, "stats", 5, "BOOK", "&eYour Stats", "&7View your statistics");
        write(gui, "daily", 7, "GOLD_INGOT", "&6Daily Reward", "&7Claim your daily reward");
        write(gui, "cosmetics", 8, "ENDER_CHEST", "&bCosmetics", "&7Buy and equip cosmetics");
        gui.save();
        Debug.log(DebugCategory.CONFIG, "Installed the default lobby items into gui.yml");
    }

    private void write(ConfigFile gui, String action, int slot, String material, String name, String lore) {
        ConfigurationSection section = gui.getOrCreateSection("lobby.items." + action);
        section.set("slot", slot);
        section.set("material", material);
        section.set("name", name);
        section.set("lore", Collections.singletonList(lore));
        section.set("enabled", true);
    }

    // --------------------------------------------------------------------- spawn

    public Location spawn() {
        Location configured = Locations.read(core.configs().config().config(), "lobby.spawn");
        if (configured != null && configured.getWorld() != null) {
            return configured;
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world == null ? null : world.getSpawnLocation();
    }

    public boolean hasSpawn() {
        return Locations.read(core.configs().config().config(), "lobby.spawn") != null;
    }

    /** Stores a new lobby spawn, used by {@code /setlobby}. */
    public boolean setSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        ConfigFile config = core.configs().config();
        Locations.write(config.getOrCreateSection("lobby"), "spawn", location);
        config.save();
        Debug.log(DebugCategory.PLAYER, "Lobby spawn set to {}", Locations.format(location));
        return true;
    }

    // -------------------------------------------------------------------- return

    /** Sends a player back to the lobby: state, position, health and lobby items. */
    public boolean sendToLobby(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null) {
            if (session.state() != PlayerState.LOBBY) {
                players.forceState(session, PlayerState.LOBBY);
            }
            session.matchId(null);
            session.queueId(null);
            session.spectatedMatchId(null);
            session.spectatedTarget(null);
            session.editingKit(null);
            session.botId(null);
            session.clearCombat();
            session.resetCombo();
        }
        gg.lightpractice.combat.CombatManager combat =
                core.optional(gg.lightpractice.combat.CombatManager.class);
        if (combat != null) {
            combat.clear(player.getUniqueId());
        }
        Location spawn = spawn();
        if (spawn != null && spawn.getWorld() != null) {
            player.teleport(spawn);
        }
        try {
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setFallDistance(0.0F);
            player.setFireTicks(0);
            if (healOnReturn) {
                player.setHealth(Math.max(1.0D, player.getMaxHealth()));
                player.setFoodLevel(20);
                player.setSaturation(5.0F);
                player.setExhaustion(0.0F);
                for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                    player.removePotionEffect(effect.getType());
                }
            }
            player.setWalkSpeed(0.2F);
            player.setFlySpeed(0.1F);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.PLAYER, "Could not reset " + player.getName() + " in the lobby", throwable);
        }
        if (clearOnEnter) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
        }
        giveItems(player);
        player.updateInventory();
        if (joinSound != null && !joinSound.isEmpty()) {
            Visuals.sound(player, joinSound, 1.0F, 1.0F);
        }
        return true;
    }

    /** Welcome handling after a profile finished loading. */
    public void handleJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        sendToLobby(player);
        core.messages().send(player, "lobby.welcome",
                "{player}", player.getName(),
                "{version}", core.version());
    }

    /** Hands out the configured lobby items, skipping slots a player may not see. */
    public void giveItems(Player player) {
        if (player == null || !giveItems || !player.isOnline()) {
            return;
        }
        for (LobbyItem item : items) {
            if (!item.visibleTo(player)) {
                continue;
            }
            player.getInventory().setItem(item.slot(), item.item());
        }
    }

    public void clearItems(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (LobbyItem item : items) {
            ItemStack held = player.getInventory().getItem(item.slot());
            if (held != null && held.getType() == item.material() && item.displayName().equals(nameOf(held))) {
                player.getInventory().setItem(item.slot(), null);
            }
        }
    }

    private String nameOf(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        return item.getItemMeta().getDisplayName();
    }

    /** Action of a clicked lobby item, {@code null} when the item is not one of ours. */
    public String actionOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        String name = nameOf(item);
        for (LobbyItem candidate : items) {
            if (candidate.material() == item.getType() && candidate.displayName().equals(name)) {
                return candidate.action();
            }
        }
        return null;
    }

    /** Action of a lobby item clicked in a specific slot, used by the inventory listener. */
    public String actionAt(int slot) {
        for (LobbyItem item : items) {
            if (item.slot() == slot) {
                return item.action();
            }
        }
        return null;
    }

    public List<LobbyItem> items() {
        return Collections.unmodifiableList(items);
    }

    public boolean giveItems() {
        return giveItems;
    }

    /** Whether a player is standing in the lobby world, used by protection listeners. */
    public boolean isLobbyWorld(World world) {
        if (world == null) {
            return false;
        }
        Location spawn = spawn();
        return spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(world);
    }

    /** Lobby location formatted for placeholders and messages. */
    public String describe() {
        Location spawn = spawn();
        return spawn == null ? "no spawn" : Text.color("&7Lobby: &f" + Locations.format(spawn));
    }

    /** Items of the lobby that use a material, used by tab completion of the item command. */
    public List<String> actions() {
        List<String> actions = new ArrayList<String>();
        for (LobbyItem item : items) {
            actions.add(item.action());
        }
        return actions;
    }

    /** Convenience for tests and the GUI layer: builds an item without handing it out. */
    public ItemStack itemFor(String action) {
        if (action == null) {
            return null;
        }
        String key = action.trim().toLowerCase(java.util.Locale.ROOT);
        for (LobbyItem item : items) {
            if (item.action().equals(key)) {
                return item.item();
            }
        }
        return Items.item(Material.PAPER, 1, (short) 0, Text.color("&7" + key), new ArrayList<String>());
    }
}
