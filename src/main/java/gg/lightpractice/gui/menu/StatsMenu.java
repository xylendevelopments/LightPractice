package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.model.Division;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.KitStatistics;
import gg.lightpractice.statistics.Statistics;
import gg.lightpractice.statistics.StatisticsManager;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistics of one player.
 *
 * <p>The navigation row summarises the global record while the pages list every kit the player has played,
 * so a viewer can compare kits without leaving the menu.</p>
 */
public final class StatsMenu extends PagedMenu {

    private final UUID target;
    private final String targetName;

    public StatsMenu(PluginCore core, UUID target, String targetName) {
        super(core, title(core, targetName), 5, null, "&7No kit statistics yet.");
        this.target = target;
        this.targetName = targetName == null ? "player" : targetName;
    }

    private static String title(PluginCore core, String name) {
        return core.configs().gui().getString("titles.stats", "&8Stats") + " &7- &f" + name;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        Profile profile = profile();
        KitManager kits = core.optional(KitManager.class);
        if (profile == null || kits == null) {
            return entries;
        }
        Statistics statistics = profile.statistics();
        for (String kitId : statistics.kitIds()) {
            Kit kit = kits.get(kitId);
            KitStatistics kitStatistics = statistics.kitOrNull(kitId);
            if (kitStatistics == null) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Wins: &a" + kitStatistics.wins() + " &7Losses: &c" + kitStatistics.losses()));
            lore.add(Text.color("&7Win rate: &f" + (int) Math.round(kitStatistics.winPercentage()) + "%"));
            lore.add(Text.color("&7Kills: &a" + kitStatistics.kills() + " &7Deaths: &c" + kitStatistics.deaths()));
            lore.add(Text.color("&7K/D: &f" + String.format(java.util.Locale.ROOT, "%.2f",
                    kitStatistics.ratio())));
            lore.add(Text.color("&7Matches: &f" + kitStatistics.gamesPlayed()));
            lore.add(Text.color("&7Elo: &f" + profile.eloOr(kitId, 0)));
            ItemStack icon = kit == null
                    ? Items.item(Material.PAPER, 1, (short) 0, Text.color("&f" + kitId), lore)
                    : Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.display(icon));
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        Profile profile = profile();
        int lastRow = (rows() - 1) * 9;
        if (profile == null) {
            map.put(Integer.valueOf(lastRow + 3), Button.display(Items.item(Material.BARRIER, 1, (short) 0,
                    Text.color("&cProfile unavailable"), lore("&7The profile is not loaded."))));
            return;
        }
        Statistics ranked = profile.rankedStatistics();
        Statistics unranked = profile.unrankedStatistics();
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        Division division = statistics == null ? null : statistics.division(profile.uuid(), null);
        map.put(Integer.valueOf(lastRow + 1), Button.display(Items.item(Material.GOLD_SWORD, 1, (short) 0,
                Text.color("&6Ranked"), lore("&7Wins: &a" + ranked.wins(),
                        "&7Losses: &c" + ranked.losses(),
                        "&7Win rate: &f" + (int) Math.round(ranked.winPercentage()) + "%",
                        "&7Best winstreak: &e" + ranked.bestWinstreak(),
                        division == null ? "&7Division: &funranked" : "&7Division: " + division.colored()))));
        map.put(Integer.valueOf(lastRow + 2), Button.display(Items.item(Material.IRON_SWORD, 1, (short) 0,
                Text.color("&7Unranked"), lore("&7Wins: &a" + unranked.wins(),
                        "&7Losses: &c" + unranked.losses(),
                        "&7Win rate: &f" + (int) Math.round(unranked.winPercentage()) + "%",
                        "&7Kills: &a" + unranked.kills(),
                        "&7Deaths: &c" + unranked.deaths()))));
        Statistics overall = profile.statistics();
        map.put(Integer.valueOf(lastRow + 3), Button.display(Items.item(Material.BOOK, 1, (short) 0,
                Text.color("&eOverall"), lore("&7Matches: &f" + overall.gamesPlayed(),
                        "&7Kills: &a" + overall.kills(),
                        "&7Deaths: &c" + overall.deaths(),
                        "&7K/D: &f" + String.format(java.util.Locale.ROOT, "%.2f", overall.ratio()),
                        "&7Time played: &f" + Text.duration(overall.timePlayedMillis())))));
        map.put(Integer.valueOf(lastRow + 6), Button.display(Items.item(Material.EXP_BOTTLE, 1, (short) 0,
                Text.color("&aProgress"), lore("&7Level: &f" + profile.level(),
                        "&7Experience: &b" + profile.experience(),
                        "&7Coins: &6" + profile.coins(),
                        "&7Current winstreak: &e" + overall.winstreak()))));
        map.put(Integer.valueOf(lastRow + 7), Button.display(Items.skull(targetName, 1,
                Text.color("&b" + targetName), lore("&7Viewing the statistics of", "&f" + targetName))));
    }

    private Profile profile() {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || target == null ? null : profiles.getProfile(target);
    }
}
