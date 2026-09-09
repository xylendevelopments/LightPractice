package gg.lightpractice.gui;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of every menu.
 *
 * <p>A menu builds its buttons for one viewer, renders them into an inventory owned by a
 * {@link MenuHolder} and dispatches clicks back to those buttons. Nothing is cached between viewers, so a
 * menu can show live data such as queue sizes or a party list without a second implementation.</p>
 */
public abstract class Menu {

    protected final PluginCore core;
    private final String title;
    private final int size;
    private final Map<Integer, Button> buttons = new LinkedHashMap<Integer, Button>();
    private int page;

    protected Menu(PluginCore core, String title, int size) {
        this.core = core;
        this.title = Text.trimToFit(Text.color(title == null ? "Menu" : title), 32);
        this.size = normalize(size);
    }

    private static int normalize(int size) {
        int rows = Math.max(1, Math.min(6, (size + 8) / 9));
        return rows * 9;
    }

    /** Builds the buttons of this menu for one viewer, keyed by slot. */
    public abstract Map<Integer, Button> buttons(Player viewer);

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public int rows() {
        return size / 9;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    public PluginCore core() {
        return core;
    }

    public Button button(int slot) {
        return buttons.get(Integer.valueOf(slot));
    }

    public Map<Integer, Button> buttons() {
        return buttons;
    }

    /** True when the contents change on their own and should be re-rendered every second. */
    public boolean liveData() {
        return false;
    }

    /** Called before the inventory of this menu is created, once per opening. */
    public void onOpen(Player viewer) {
        // menus without side effects keep the default
    }

    /** Called when the inventory of this menu is closed. */
    public void onClose(Player viewer) {
        // menus without side effects keep the default
    }

    /** Creates a fresh inventory filled with the buttons of this menu. */
    public Inventory inventory(Player viewer) {
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);
        rebuild(viewer);
        for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
            Integer slot = entry.getKey();
            Button button = entry.getValue();
            if (slot == null || button == null || slot.intValue() < 0 || slot.intValue() >= size) {
                continue;
            }
            inventory.setItem(slot.intValue(), button.item());
        }
        return inventory;
    }

    /** Re-reads the buttons of this menu without touching the inventory. */
    protected void rebuild(Player viewer) {
        buttons.clear();
        Map<Integer, Button> built = buttons(viewer);
        if (built != null) {
            for (Map.Entry<Integer, Button> entry : built.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    buttons.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /** Updates an open inventory in place, only touching slots whose item really changed. */
    public void refresh(Player viewer, Inventory inventory) {
        if (viewer == null || inventory == null) {
            return;
        }
        rebuild(viewer);
        int slots = Math.min(size, inventory.getSize());
        for (int slot = 0; slot < slots; slot++) {
            Button button = buttons.get(Integer.valueOf(slot));
            ItemStack wanted = button == null ? null : button.item();
            ItemStack current = inventory.getItem(slot);
            if (same(current, wanted)) {
                continue;
            }
            inventory.setItem(slot, wanted);
        }
    }

    /** Dispatches a click on one of the slots of this menu. */
    public boolean click(Player viewer, int slot, ClickType type) {
        Button button = buttons.get(Integer.valueOf(slot));
        if (button == null) {
            return false;
        }
        button.click(viewer, type);
        return true;
    }

    /** Compares two items the way a viewer would see them. */
    public static boolean same(ItemStack left, ItemStack right) {
        boolean leftEmpty = left == null || left.getType() == Material.AIR;
        boolean rightEmpty = right == null || right.getType() == Material.AIR;
        if (leftEmpty || rightEmpty) {
            return leftEmpty && rightEmpty;
        }
        if (left.getType() != right.getType() || left.getDurability() != right.getDurability()
                || left.getAmount() != right.getAmount()) {
            return false;
        }
        ItemMeta leftMeta = left.hasItemMeta() ? left.getItemMeta() : null;
        ItemMeta rightMeta = right.hasItemMeta() ? right.getItemMeta() : null;
        String leftName = leftMeta != null && leftMeta.hasDisplayName() ? leftMeta.getDisplayName() : "";
        String rightName = rightMeta != null && rightMeta.hasDisplayName() ? rightMeta.getDisplayName() : "";
        if (!leftName.equals(rightName)) {
            return false;
        }
        List<String> leftLore = leftMeta != null && leftMeta.hasLore() ? leftMeta.getLore() : null;
        List<String> rightLore = rightMeta != null && rightMeta.hasLore() ? rightMeta.getLore() : null;
        if (leftLore == null && rightLore == null) {
            return true;
        }
        if (leftLore == null || rightLore == null) {
            return false;
        }
        return leftLore.equals(rightLore);
    }

    // ------------------------------------------------------------------- helpers

    protected ConfigFile gui() {
        return core.configs().gui();
    }

    /** Title from {@code gui.yml} with the placeholders of a menu applied. */
    protected String configuredTitle(String key, String fallback) {
        return gui().getString("titles." + key, fallback);
    }

    protected int configuredSlot(String key, int fallback) {
        return Math.max(0, Math.min(size - 1, gui().getInt("slots." + key, fallback)));
    }

    protected Material configuredMaterial(String key, Material fallback) {
        return Items.material(gui().getString("materials." + key, fallback.name()), fallback);
    }

    /** Opens another menu for a viewer through the GUI manager. */
    protected void open(Player viewer, Menu menu) {
        GuiManager manager = core.optional(GuiManager.class);
        if (manager != null && viewer != null && menu != null) {
            manager.open(viewer, menu);
        }
    }

    /** Refreshes this menu for a viewer after an action changed what it shows. */
    protected void refresh(Player viewer) {
        GuiManager manager = core.optional(GuiManager.class);
        if (manager != null) {
            manager.refresh(viewer);
        }
    }

    protected void close(Player viewer) {
        GuiManager manager = core.optional(GuiManager.class);
        if (manager != null) {
            manager.close(viewer);
        }
    }

    /** Fills a row of slots with a filler item. */
    protected void fillRow(Map<Integer, Button> map, int row, ItemStack filler) {
        if (filler == null || filler.getType() == Material.AIR) {
            return;
        }
        int from = Math.max(0, Math.min(rows() - 1, row)) * 9;
        for (int slot = from; slot < from + 9 && slot < size; slot++) {
            map.put(Integer.valueOf(slot), Button.display(filler));
        }
    }

    protected void fillBorder(Map<Integer, Button> map, ItemStack filler) {
        if (filler == null || filler.getType() == Material.AIR) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            if (row == 0 || row == rows() - 1 || column == 0 || column == 8) {
                map.put(Integer.valueOf(slot), Button.display(filler));
            }
        }
    }

    protected void fillEmpty(Map<Integer, Button> map, ItemStack filler) {
        if (filler == null || filler.getType() == Material.AIR) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (!map.containsKey(Integer.valueOf(slot))) {
                map.put(Integer.valueOf(slot), Button.display(filler));
            }
        }
    }

    /** Close button placed in the bottom row. */
    protected Button closeButton() {
        ItemStack item = Items.item(Material.INK_SACK, 1, (short) 1, Text.color("&cClose"),
                new ArrayList<String>());
        return Button.of(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                close(player);
            }
        });
    }

    /** Back button returning to a parent menu. */
    protected Button backButton(final Menu parent) {
        List<String> lore = new ArrayList<String>();
        lore.add(Text.color("&7Back to " + (parent == null ? "the menu" : Text.strip(parent.title()))));
        ItemStack item = Items.item(Material.ARROW, 1, (short) 0, Text.color("&cBack"), lore);
        return Button.of(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                if (parent != null) {
                    open(player, parent);
                } else {
                    close(player);
                }
            }
        });
    }

    protected List<String> lore(String... lines) {
        List<String> lore = new ArrayList<String>();
        if (lines != null) {
            for (String line : lines) {
                if (line != null) {
                    lore.add(Text.color(line));
                }
            }
        }
        return lore;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + Text.strip(title) + ", size=" + size + ", page=" + page + '}';
    }
}
