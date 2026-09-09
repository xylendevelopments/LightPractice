package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.HistoryManager;
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
 * Recent matches of one player.
 *
 * <p>The profile keeps the newest entries in memory, older pages are fetched from MongoDB asynchronously
 * and land back in this menu when they arrive, so browsing history never blocks the server thread.</p>
 */
public final class HistoryMenu extends PagedMenu {

    private final UUID target;
    private final List<MatchHistoryEntry> loaded = new ArrayList<MatchHistoryEntry>();
    private int loadedPage = -1;
    private boolean fetching;

    public HistoryMenu(PluginCore core, UUID target, int page) {
        super(core, title(core), 5, null, "&7No matches recorded yet.");
        this.target = target;
        this.loadedPage = page;
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.history", "&8Match History");
    }

    public UUID target() {
        return target;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        for (final MatchHistoryEntry entry : rows()) {
            if (entry == null) {
                continue;
            }
            MatchOutcome outcome = entry.outcome();
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Result: " + colorOf(outcome) + nameOf(outcome)));
            lore.add(Text.color("&7Kit: &f" + entry.kitName()));
            lore.add(Text.color("&7Arena: &f" + entry.arenaName()));
            lore.add(Text.color("&7Type: &f" + entry.matchType() + (entry.ranked() ? " &6(ranked)" : "")));
            lore.add(Text.color("&7Duration: &f" + Text.duration(entry.durationMillis())));
            lore.add(Text.color("&7Kills: &a" + entry.kills() + " &7Deaths: &c" + entry.deaths()));
            if (entry.ranked()) {
                lore.add(Text.color("&7Elo: &f" + entry.eloBefore() + " &7-> &f" + entry.eloAfter()
                        + " &7(" + (entry.eloDelta() >= 0 ? "&a+" : "&c") + entry.eloDelta() + "&7)"));
            }
            lore.add(Text.color("&7Opponents: &f" + Text.join(entry.opponents(), ", ")));
            lore.add(Text.color("&7" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new java.util.Date(entry.timestamp()))));
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aClick &7to see this match in chat"));
            ItemStack icon = Items.item(materialOf(outcome), 1, (short) 0,
                    Text.color(colorOf(outcome) + nameOf(outcome) + " &7- &f" + entry.kitName()), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    core.messages().sendList(player, "history.details",
                            "{kit}", entry.kitName(),
                            "{arena}", entry.arenaName(),
                            "{type}", entry.matchType(),
                            "{outcome}", nameOf(entry.outcome()),
                            "{duration}", Text.duration(entry.durationMillis()),
                            "{kills}", String.valueOf(entry.kills()),
                            "{deaths}", String.valueOf(entry.deaths()),
                            "{elo}", String.valueOf(entry.eloDelta()),
                            "{opponents}", Text.join(entry.opponents(), ", "));
                }
            }));
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, final Player viewer) {
        int lastRow = (rows() - 1) * 9;
        final HistoryManager history = core.optional(HistoryManager.class);
        ItemStack newer = Items.item(Material.ARROW, 1, (short) 0, Text.color("&6Newer Matches"),
                fetching ? lore("&7Loading...") : lore("&7Load the previous page"));
        map.put(Integer.valueOf(lastRow + 1), history == null || loadedPage <= 0
                ? Button.display(newer) : Button.of(newer, new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        load(viewer, Math.max(0, loadedPage - 1));
                    }
                }));
        ItemStack older = Items.item(Material.ARROW, 1, (short) 0, Text.color("&6Older Matches"),
                fetching ? lore("&7Loading...") : lore("&7Load the next page"));
        map.put(Integer.valueOf(lastRow + 7), history == null ? Button.display(older) : Button.of(older,
                new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        load(viewer, loadedPage + 1);
                    }
                }));
        Profile profile = profile();
        ItemStack info = Items.item(Material.BOOK, 1, (short) 0, Text.color("&eHistory"),
                lore("&7Page: &f" + (loadedPage + 1),
                        "&7Entries loaded: &f" + rows().size(),
                        "&7Total matches: &f" + (profile == null ? 0 : profile.statistics().gamesPlayed())));
        map.put(Integer.valueOf(lastRow + 3), Button.display(info));
    }

    /** Loads one page from the database and re-renders the menu when it arrives. */
    private void load(Player viewer, final int page) {
        final HistoryManager history = core.optional(HistoryManager.class);
        if (history == null || target == null || fetching) {
            return;
        }
        fetching = true;
        history.load(target, page, new Consumer<List<MatchHistoryEntry>>() {
            @Override
            public void accept(List<MatchHistoryEntry> entries) {
                fetching = false;
                loaded.clear();
                if (entries != null) {
                    loaded.addAll(entries);
                }
                loadedPage = page;
                if (viewer != null && viewer.isOnline()) {
                    refresh(viewer);
                }
            }
        });
    }

    private List<MatchHistoryEntry> rows() {
        if (!loaded.isEmpty()) {
            return loaded;
        }
        Profile profile = profile();
        return profile == null ? new ArrayList<MatchHistoryEntry>() : profile.history();
    }

    private Profile profile() {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || target == null ? null : profiles.getProfile(target);
    }

    private Material materialOf(MatchOutcome outcome) {
        if (outcome == null) {
            return Material.PAPER;
        }
        switch (outcome) {
            case WIN:
                return Material.GOLD_SWORD;
            case LOSS:
                return Material.BONE;
            default:
                return Material.PAPER;
        }
    }

    private String colorOf(MatchOutcome outcome) {
        if (outcome == null) {
            return "&7";
        }
        switch (outcome) {
            case WIN:
                return "&a";
            case LOSS:
                return "&c";
            default:
                return "&e";
        }
    }

    private String nameOf(MatchOutcome outcome) {
        return outcome == null ? "Unknown" : Text.capitalize(outcome.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Opens the history of a player, used by the stats command. */
    public static Menu of(PluginCore core, UUID uuid, int page) {
        return new HistoryMenu(core, uuid, page);
    }
}
