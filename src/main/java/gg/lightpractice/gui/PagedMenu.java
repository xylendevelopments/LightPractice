package gg.lightpractice.gui;

import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu whose entries are spread over pages.
 *
 * <p>Subclasses only produce a list of buttons; this class slices the list into the content rows, adds the
 * navigation row and keeps the current page between refreshes.</p>
 */
public abstract class PagedMenu extends Menu {

    private final int[] contentSlots;
    private final Menu parent;
    private final String emptyText;

    protected PagedMenu(PluginCore core, String title, int rows, Menu parent) {
        this(core, title, rows, parent, "&7Nothing to show.");
    }

    protected PagedMenu(PluginCore core, String title, int rows, Menu parent, String emptyText) {
        super(core, title, Math.max(2, Math.min(6, rows)) * 9);
        this.parent = parent;
        this.emptyText = Text.color(emptyText == null ? "&7Nothing to show." : emptyText);
        int contentRows = Math.max(1, Math.min(6, rows) - 1);
        this.contentSlots = new int[contentRows * 9];
        for (int index = 0; index < contentSlots.length; index++) {
            contentSlots[index] = index;
        }
    }

    /** Entries of this menu, one button per entry. */
    public abstract List<Button> entries(Player viewer);

    /** Chance to decorate the navigation row, called after the entries were placed. */
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        // most menus need no decoration
    }

    public Menu parent() {
        return parent;
    }

    public int perPage() {
        return contentSlots.length;
    }

    public int contentSlot(int index) {
        return index < 0 || index >= contentSlots.length ? -1 : contentSlots[index];
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        List<Button> entries = entries(viewer);
        if (entries == null) {
            entries = new ArrayList<Button>();
        }
        int perPage = perPage();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        if (page() >= pages) {
            page(pages - 1);
        }
        int from = page() * perPage;
        if (entries.isEmpty()) {
            map.put(Integer.valueOf(contentSlot(perPage / 2)),
                    Button.display(Items.item(Material.PAPER, 1, (short) 0, emptyText, new ArrayList<String>())));
        }
        for (int index = 0; index < perPage; index++) {
            int position = from + index;
            if (position >= entries.size()) {
                break;
            }
            Button button = entries.get(position);
            if (button != null) {
                map.put(Integer.valueOf(contentSlot(index)), button);
            }
        }
        decorate(map, viewer);
        int lastRow = (rows() - 1) * 9;
        if (pages > 1) {
            map.put(Integer.valueOf(lastRow), Button.of(navigationItem("&6Previous Page",
                    "&7Go to page " + page() + " of " + pages), new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    if (page() > 0) {
                        page(page() - 1);
                        refresh(player);
                    }
                }
            }));
            map.put(Integer.valueOf(lastRow + 8), Button.of(navigationItem("&6Next Page",
                    "&7Page " + (page() + 2) + " of " + pages), new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    if (page() + 1 < pages) {
                        page(page() + 1);
                        refresh(player);
                    }
                }
            }));
        }
        map.put(Integer.valueOf(lastRow + 4), Button.display(Items.item(Material.BOOK, 1, (short) 0,
                Text.color("&ePage " + (page() + 1) + "&7/&f" + pages),
                lore("&7Entries: &f" + entries.size()))));
        if (!map.containsKey(Integer.valueOf(lastRow + 5))) {
            map.put(Integer.valueOf(lastRow + 5), parent == null ? closeButton() : backButton(parent));
        }
        return map;
    }

    private ItemStack navigationItem(String name, String lore) {
        return Items.item(Material.ARROW, 1, (short) 0, Text.color(name), lore(lore));
    }
}
