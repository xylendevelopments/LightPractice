package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.model.LeaderboardType;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.LeaderboardManager;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooser for every leaderboard the plugin keeps.
 *
 * <p>Global boards are listed first, then one elo board per ranked kit, because elo only means something
 * per kit. Boards that are not configured are skipped instead of showing an empty page.</p>
 */
public final class LeaderboardMenu extends PagedMenu {

    public LeaderboardMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7No leaderboards are configured.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.leaderboards", "&8Leaderboards");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final LeaderboardManager leaderboards = core.optional(LeaderboardManager.class);
        for (final LeaderboardType type : LeaderboardType.values()) {
            if (type.isKitSpecific() && type != LeaderboardType.WINS && type != LeaderboardType.ELO) {
                continue;
            }
            if (type.isKitSpecific()) {
                KitManager kits = core.optional(KitManager.class);
                if (kits == null) {
                    continue;
                }
                for (final Kit kit : kits.queueable(true)) {
                    if (kit == null) {
                        continue;
                    }
                    entries.add(board(Items.decorate(kit.icon(), Text.color("&6Elo &7- &f" + kit.displayName()),
                            list(leaderboards, type, kit.id())), type, kit.id()));
                }
                continue;
            }
            entries.add(board(Items.item(iconOf(type), 1, (short) 0,
                    Text.color("&b" + Text.capitalize(type.name().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', ' '))), list(leaderboards, type, null)), type, null));
        }
        return entries;
    }

    private List<String> list(LeaderboardManager leaderboards, LeaderboardType type, String kitId) {
        List<String> lore = new ArrayList<String>();
        if (leaderboards == null) {
            lore.add(Text.color("&7Leaderboards are unavailable."));
            return lore;
        }
        List<gg.lightpractice.model.LeaderboardEntry> rows = leaderboards.cached(type, kitId);
        if (rows.isEmpty()) {
            lore.add(Text.color("&7Nobody is on this board yet."));
        }
        int shown = 0;
        for (gg.lightpractice.model.LeaderboardEntry row : rows) {
            if (row == null || shown >= 3) {
                break;
            }
            lore.add(Text.color("&7#" + (shown + 1) + " &f" + row.name() + " &8- &f" + row.value()));
            shown++;
        }
        lore.add(Text.color("&7&m                        "));
        lore.add(Text.color("&aClick &7to open this board"));
        return lore;
    }

    private Button board(ItemStack icon, final LeaderboardType type, final String kitId) {
        return Button.of(icon, new ClickAction() {
            @Override
            public void click(Player player, ClickType type2) {
                open(player, new LeaderboardEntriesMenu(core, type, kitId));
            }
        });
    }

    private Material iconOf(LeaderboardType type) {
        switch (type) {
            case WINS:
                return Material.GOLD_SWORD;
            case LOSSES:
                return Material.IRON_SWORD;
            case KILLS:
                return Material.DIAMOND_SWORD;
            case DEATHS:
                return Material.BONE;
            case WINSTREAK:
                return Material.BLAZE_ROD;
            case BEST_WINSTREAK:
                return Material.NETHER_STAR;
            case GAMES_PLAYED:
                return Material.BOOK;
            case EXPERIENCE:
                return Material.EXP_BOTTLE;
            case COINS:
                return Material.GOLD_INGOT;
            case LEVEL:
                return Material.ENCHANTMENT_TABLE;
            default:
                return Material.DIAMOND;
        }
    }
}
