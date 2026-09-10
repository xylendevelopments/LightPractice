package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.model.Division;
import gg.lightpractice.progress.LevelManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.queue.QueueManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.StatisticsManager;
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
 * Hub menu every lobby item and the {@code /menu} command lead to.
 *
 * <p>It shows the profile summary of the viewer and links to the kit, queue, party, duel, cosmetics,
 * statistics, leaderboard, daily reward, event, tournament, bot, spectate and settings menus.</p>
 */
public final class MainMenu extends Menu {

    public MainMenu(PluginCore core) {
        super(core, configuredTitle(core, "main", "&8LightPractice"), 45);
    }

    private static String configuredTitle(PluginCore core, String key, String fallback) {
        return core.configs().gui().getString("titles." + key, fallback);
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        Profile profile = profile(viewer);
        fillBorder(map, Items.item(Material.STAINED_GLASS_PANE, 1, (short) 7, Text.color("&7"),
                new ArrayList<String>()));
        map.put(Integer.valueOf(4), Button.display(profileItem(viewer, profile)));
        map.put(Integer.valueOf(10), link(Material.CHEST, (short) 0, "&bKits",
                "&7Pick a kit or edit your loadout", new KitSelectMenu(core)));
        map.put(Integer.valueOf(11), link(Material.IRON_SWORD, (short) 0, "&aQueues",
                "&7Join a ranked or unranked queue\n&7Waiting: &f" + waiting(), new QueueMenu(core)));
        map.put(Integer.valueOf(12), link(Material.NAME_TAG, (short) 0, "&dParties",
                "&7Create, join or manage a party", new PartyMenu(core)));
        map.put(Integer.valueOf(13), link(Material.BOOK, (short) 0, "&eYour Stats",
                "&7Wins, losses, elo and more", new StatsMenu(core, viewer.getUniqueId(), viewer.getName())));
        map.put(Integer.valueOf(14), link(Material.SIGN, (short) 0, "&6Leaderboards",
                "&7The best players of every mode", new LeaderboardMenu(core)));
        map.put(Integer.valueOf(15), link(Material.GOLD_INGOT, (short) 0, "&6Daily Reward",
                dailyLore(profile), new DailyRewardMenu(core)));
        map.put(Integer.valueOf(16), link(Material.ENDER_CHEST, (short) 0, "&bCosmetics",
                "&7Kill effects, messages and trails", new CosmeticsMenu(core)));
        map.put(Integer.valueOf(20), link(Material.EYE_OF_ENDER, (short) 0, "&7Spectate",
                "&7Watch a live match", new SpectateMenu(core)));
        map.put(Integer.valueOf(21), link(Material.SKULL_ITEM, (short) 3, "&cBots",
                "&7Train against a practice bot", new BotMenu(core)));
        map.put(Integer.valueOf(22), link(Material.CAKE, (short) 0, "&dEvents",
                "&7Host or join a game event", new EventMenu(core)));
        map.put(Integer.valueOf(23), link(Material.GOLD_SWORD, (short) 0, "&6Tournaments",
                "&7Bracketed tournaments", new TournamentMenu(core)));
        map.put(Integer.valueOf(24), link(Material.REDSTONE_COMPARATOR, (short) 0, "&7Settings",
                "&7Toggle your preferences", new SettingsMenu(core)));
        map.put(Integer.valueOf(30), link(Material.PAPER, (short) 0, "&fMatch History",
                "&7Your last matches", new HistoryMenu(core, viewer.getUniqueId(), 0)));
        map.put(Integer.valueOf(31), profile == null ? closeButton() : Button.display(Items.item(
                Material.EXP_BOTTLE, 1, (short) 0, Text.color("&aLevel " + profile.level()), levelLore(profile))));
        map.put(Integer.valueOf(32), Button.display(Items.item(Material.GOLD_NUGGET, 1, (short) 0,
                Text.color("&6" + (profile == null ? 0 : profile.coins()) + " coins"),
                lore("&7Earn coins by playing", "&7Spend them on cosmetics"))));
        map.put(Integer.valueOf(40), closeButton());
        return map;
    }

    private Button link(Material material, short data, String name, String lore, final Menu target) {
        List<String> lines = new ArrayList<String>();
        for (String line : lore.split("\n")) {
            lines.add(Text.color(line));
        }
        ItemStack item = Items.item(material, 1, data, Text.color(name), lines);
        return Button.of(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                open(player, target);
            }
        });
    }

    private ItemStack profileItem(Player viewer, Profile profile) {
        List<String> lines = lore("&7Name: &f" + viewer.getName());
        if (profile != null) {
            StatisticsManager statistics = core.optional(StatisticsManager.class);
            Division division = statistics == null ? null : statistics.division(profile.uuid(), null);
            lines.add(Text.color("&7Level: &f" + profile.level()));
            lines.add(Text.color("&7Coins: &6" + profile.coins()));
            lines.add(Text.color("&7Experience: &b" + profile.experience()));
            lines.add(Text.color("&7Wins: &a" + profile.statistics().wins() + " &7Losses: &c"
                    + profile.statistics().losses()));
            if (division != null) {
                lines.add(Text.color("&7Division: " + division.colored()));
            }
        } else {
            lines.add(Text.color("&7Your profile is still loading."));
        }
        return Items.skull(viewer.getName(), 1, Text.color("&b" + viewer.getName()), lines);
    }

    private List<String> levelLore(Profile profile) {
        LevelManager levels = core.optional(LevelManager.class);
        List<String> lines = new ArrayList<String>();
        if (levels == null || profile == null) {
            lines.add(Text.color("&7Level progress is unavailable."));
            return lines;
        }
        int level = profile.level();
        long needed = levels.experienceForLevel(level + 1);
        long into = levels.intoLevel(profile.experience());
        lines.add(Text.color("&7Progress: &f" + into + "&7/&f" + needed));
        lines.add(Text.color("&7" + levels.bar(levels.progress(profile.experience()), 20)));
        lines.add(Text.color("&7To the next level: &f" + levels.remaining(profile)));
        return lines;
    }

    private List<String> dailyLore(Profile profile) {
        gg.lightpractice.progress.RewardManager rewards = core.optional(gg.lightpractice.progress.RewardManager.class);
        if (rewards == null || profile == null) {
            return lore("&7Claim your daily reward");
        }
        if (rewards.canClaimDaily(profile)) {
            return lore("&7Claim your daily reward", "&aReady to claim",
                    "&7Streak: &f" + rewards.dailyStreak(profile));
        }
        return lore("&7Claim your daily reward",
                "&7Next in: &f" + Text.duration(rewards.dailyRemainingMillis(profile)),
                "&7Streak: &f" + rewards.dailyStreak(profile));
    }

    private int waiting() {
        QueueManager queues = core.optional(QueueManager.class);
        return queues == null ? 0 : queues.totalWaiting();
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }

}
