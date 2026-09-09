package gg.lightpractice.gui;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every open menu.
 *
 * <p>Menus are recognised through their {@link MenuHolder}, clicks are always cancelled so nothing can be
 * taken out of a menu, and one shared task re-renders the menus that show live data instead of giving each
 * of them a timer.</p>
 */
public final class GuiManager implements LightService {

    private final PluginCore core;
    private final Map<UUID, Menu> open = new ConcurrentHashMap<UUID, Menu>();
    private final Map<UUID, Inventory> inventories = new ConcurrentHashMap<UUID, Inventory>();
    private BukkitTask refreshTask;
    private boolean clickSounds = true;
    private String clickSound = "CLICK";
    private int refreshTicks = 20;

    public GuiManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "gui";
    }

    @Override
    public int startupOrder() {
        return 76;
    }

    @Override
    public void onLoad() {
        reload();
    }

    @Override
    public void onEnable() {
        reload();
        refreshTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                refreshLive();
            }
        }, refreshTicks, refreshTicks);
    }

    @Override
    public void onDisable() {
        closeAll();
        core.tasks().cancel(refreshTask);
        refreshTask = null;
        open.clear();
        inventories.clear();
    }

    @Override
    public void onReload() {
        reload();
    }

    public void reload() {
        ConfigFile file = core.configs().gui();
        file.reload();
        this.clickSounds = file.getBoolean("settings.click-sounds", true);
        this.clickSound = file.getString("settings.click-sound", "CLICK");
        this.refreshTicks = Math.max(5, Math.min(100, file.getInt("settings.refresh-ticks", 20)));
        Debug.log(DebugCategory.GUI, "GUI ready: click sounds {} ({}), refresh every {} ticks",
                clickSounds, clickSound, refreshTicks);
    }

    // ------------------------------------------------------------------- opening

    /** Opens a menu for a player, replacing whatever menu was open before. */
    public boolean open(Player player, Menu menu) {
        if (player == null || menu == null || !player.isOnline()) {
            return false;
        }
        try {
            menu.onOpen(player);
            Inventory inventory = menu.inventory(player);
            open.put(player.getUniqueId(), menu);
            inventories.put(player.getUniqueId(), inventory);
            player.openInventory(inventory);
            Debug.log(DebugCategory.GUI, "Opened {} for {}", menu, player.getName());
            return true;
        } catch (Throwable throwable) {
            open.remove(player.getUniqueId());
            inventories.remove(player.getUniqueId());
            Debug.error(DebugCategory.GUI, "Could not open " + menu + " for " + player.getName(), throwable);
            core.messages().send(player, "gui.error", "{menu}", Text.strip(menu.title()));
            return false;
        }
    }

    public void close(Player player) {
        if (player == null) {
            return;
        }
        Menu menu = open.remove(player.getUniqueId());
        inventories.remove(player.getUniqueId());
        if (menu != null) {
            try {
                menu.onClose(player);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.GUI, "The close hook of " + menu + " failed", throwable);
            }
        }
        if (player.isOnline()) {
            player.closeInventory();
        }
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<UUID>(open.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                close(player);
            } else {
                open.remove(uuid);
                inventories.remove(uuid);
            }
        }
    }

    public Menu menuOf(Player player) {
        return player == null ? null : open.get(player.getUniqueId());
    }

    public Inventory inventoryOf(Player player) {
        return player == null ? null : inventories.get(player.getUniqueId());
    }

    public boolean hasOpen(Player player) {
        return menuOf(player) != null;
    }

    public int openCount() {
        return open.size();
    }

    /** Re-renders the menu a player has open, used after an action changed what it shows. */
    public boolean refresh(Player player) {
        if (player == null) {
            return false;
        }
        Menu menu = open.get(player.getUniqueId());
        Inventory inventory = inventories.get(player.getUniqueId());
        if (menu == null || inventory == null) {
            return false;
        }
        try {
            menu.refresh(player, inventory);
            return true;
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.GUI, "Could not refresh " + menu, throwable);
            return false;
        }
    }

    private void refreshLive() {
        if (open.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Menu> entry : open.entrySet()) {
            Menu menu = entry.getValue();
            if (menu == null || !menu.liveData()) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                open.remove(entry.getKey());
                inventories.remove(entry.getKey());
                continue;
            }
            refresh(player);
        }
    }

    // -------------------------------------------------------------- interaction

    /** True when an inventory belongs to one of our menus. */
    public boolean isMenu(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder;
    }

    public Menu menuOf(Inventory inventory) {
        if (!isMenu(inventory)) {
            return null;
        }
        return ((MenuHolder) inventory.getHolder()).menu();
    }

    /**
     * Handles a click inside a menu.
     *
     * <p>Every click is cancelled, including clicks in the player's own inventory while a menu is open, so
     * a shift click can never move a menu item into a survival inventory.</p>
     *
     * @return true when the event belonged to one of our menus
     */
    public boolean handleClick(InventoryClickEvent event) {
        if (event == null) {
            return false;
        }
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        Menu menu = menuOf(top);
        if (menu == null) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return true;
        }
        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return true;
        }
        Button button = menu.button(rawSlot);
        if (button == null) {
            return true;
        }
        if (clickSounds && clickSound != null && !clickSound.isEmpty()) {
            Visuals.sound(player, clickSound, 0.5F, 1.0F);
        }
        try {
            button.click(player, event.getClick());
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.GUI, "A click in " + menu + " failed", throwable);
            core.messages().send(player, "gui.error", "{menu}", Text.strip(menu.title()));
        }
        return true;
    }

    /** Handles a drag inside a menu: drags that touch menu slots are cancelled. */
    public boolean handleDrag(InventoryDragEvent event) {
        if (event == null || event.getView() == null) {
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        if (menuOf(top) == null) {
            return false;
        }
        int size = top.getSize();
        for (Integer slot : event.getRawSlots()) {
            if (slot != null && slot.intValue() < size) {
                event.setCancelled(true);
                return true;
            }
        }
        return true;
    }

    /** Handles an inventory close: the menu is unregistered and its close hook runs. */
    public boolean handleClose(InventoryCloseEvent event) {
        if (event == null || event.getView() == null) {
            return false;
        }
        Menu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return false;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return true;
        }
        Player player = (Player) event.getPlayer();
        open.remove(player.getUniqueId());
        inventories.remove(player.getUniqueId());
        try {
            menu.onClose(player);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.GUI, "The close hook of " + menu + " failed", throwable);
        }
        Debug.log(DebugCategory.GUI, "Closed {} for {}", menu, player.getName());
        return true;
    }

    /** Menus a player has open, used by the debug command. */
    public List<String> describeOpen() {
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<UUID, Menu> entry : open.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            lines.add((player == null ? entry.getKey().toString() : player.getName()) + ": " + entry.getValue());
        }
        if (lines.isEmpty()) {
            lines.add(Text.color("&7No menus are open."));
        }
        return lines;
    }

    public boolean clickSounds() {
        return clickSounds;
    }

    public String clickSound() {
        return clickSound;
    }
}
