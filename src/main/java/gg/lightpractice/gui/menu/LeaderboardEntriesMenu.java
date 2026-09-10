package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.model.LeaderboardEntry;
import gg.lightpractice.model.LeaderboardType;
import gg.lightpractice.statistics.LeaderboardManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Top entries of one leaderboard.
 *
 * <p>Rows come from the leaderboard cache so opening the menu never touches MongoDB. The refresh button
 * fetches that one board asynchronously and re-renders the menu when the result arrives.</p>
 */
public final class LeaderboardEntriesMenu extends PagedMenu {

    private final LeaderboardType type;
    private final String kitId;

    public LeaderboardEntriesMenu(PluginCore core, LeaderboardType type, String kitId) {
        super(core, buildTitle(core, type, kitId), 5, new LeaderboardMenu(core), "&7No entries yet.");
        this.type = type;
        this.kitId = kitId;
    }

    private static String buildTitle(PluginCore core, LeaderboardType type, String kitId) {
        String base = core.configs().gui().getString("titles.leaderboard", "&8Leaderboard");
        String label = type == null ? "" : Text.capitalize(type.name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' '));
        return base + " &7- &f" + label + (kitId == null || kitId.isEmpty() ? "" : " &7(&f" + kitId + "&7)");
    }

    public LeaderboardType type() {
        return type;
    }

    public String kitId() {
        return kitId;
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        LeaderboardManager leaderboards = core.optional(LeaderboardManager.class);
        if (leaderboards == null || type == null) {
            return entries;
        }
        List<LeaderboardEntry> rows = leaderboards.entries(type, kitId);
        int position = 1;
        for (LeaderboardEntry entry : rows) {
            if (entry == null) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Value: &f" + entry.value()));
            if (viewer != null && viewer.getUniqueId().equals(entry.uuid())) {
                lore.add(Text.color("&aThis is you"));
            }
            Material material = rowMaterial(position);
            short data = material == Material.INK_SACK ? rowDye(position) : 0;
            ItemStack icon = Items.skull(entry.name(), 1,
                    Text.color(rowColor(position) + "#" + position + " &f" + entry.name()), lore);
            if (material == Material.INK_SACK || material == Material.GOLD_BLOCK) {
                icon = Items.item(material, 1, data, Text.color(rowColor(position) + "#" + position
                        + " &f" + entry.name()), lore);
            }
            entries.add(Button.display(icon));
            position++;
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, final Player viewer) {
        final LeaderboardManager leaderboards = core.optional(LeaderboardManager.class);
        int lastRow = (rows() - 1) * 9;
        LeaderboardEntry own = leaderboards == null || viewer == null
                ? null : leaderboards.entryOf(viewer.getUniqueId(), type, kitId);
        ItemStack ownItem = own == null
                ? Items.item(Material.PAPER, 1, (short) 0, Text.color("&eYour Position"),
                        lore("&7You are not on this board yet"))
                : Items.item(Material.PAPER, 1, (short) 0, Text.color("&eYour Position"),
                        lore("&7Position: &f#" + own.position(), "&7Value: &f" + own.value()));
        map.put(Integer.valueOf(lastRow + 3), Button.display(ownItem));
        ItemStack refresh = Items.item(Material.WATCH, 1, (short) 0, Text.color("&bRefresh"),
                lore("&7Fetch this board from the database",
                        "&7Cached: &f" + (leaderboards == null ? "never"
                                : Text.duration(Math.max(0L, System.currentTimeMillis()
                                        - leaderboards.lastRefresh())) + " ago")));
        map.put(Integer.valueOf(lastRow + 6), leaderboards == null ? Button.display(refresh)
                : Button.of(refresh, new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        leaderboards.fetch(type, kitId, 20, new Consumer<List<LeaderboardEntry>>() {
                            @Override
                            public void accept(List<LeaderboardEntry> entries) {
                                refresh(player);
                            }
                        });
                        core.messages().send(player, "leaderboard.refreshing",
                                "{type}", type == null ? "unknown" : type.name().toLowerCase(java.util.Locale.ROOT));
                    }
                }));
    }

    private Material rowMaterial(int position) {
        switch (position) {
            case 1:
                return Material.GOLD_BLOCK;
            case 2:
                return Material.IRON_BLOCK;
            case 3:
                return Material.INK_SACK;
            default:
                return Material.SKULL_ITEM;
        }
    }

    private short rowDye(int position) {
        return position == 3 ? (short) 3 : (short) 8;
    }

    private String rowColor(int position) {
        switch (position) {
            case 1:
                return "&6";
            case 2:
                return "&f";
            case 3:
                return "&c";
            default:
                return "&7";
        }
    }

    /** Menu used when the leaderboard command opens one board directly. */
    public static Menu of(PluginCore core, LeaderboardType type, String kitId) {
        return new LeaderboardEntriesMenu(core, type, kitId);
    }

    /** Position of a viewer on a board, used by placeholders. */
    public static int positionOf(PluginCore core, UUID uuid, LeaderboardType type, String kitId) {
        LeaderboardManager leaderboards = core.optional(LeaderboardManager.class);
        LeaderboardEntry entry = leaderboards == null || uuid == null
                ? null : leaderboards.entryOf(uuid, type, kitId);
        return entry == null ? 0 : entry.position();
    }
}
