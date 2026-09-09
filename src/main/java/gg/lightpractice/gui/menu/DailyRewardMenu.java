package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.model.RewardBundle;
import gg.lightpractice.progress.RewardManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily reward claim screen.
 *
 * <p>Shows the streak, the reward of the next claim and the time until it is available again. The claim
 * itself goes through the reward system so streaks, caps and the API event all behave like a claim from
 * the command.</p>
 */
public final class DailyRewardMenu extends Menu {

    public DailyRewardMenu(PluginCore core) {
        super(core, title(core), 27);
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.daily", "&8Daily Reward");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        fillBorder(map, Items.item(Material.STAINED_GLASS_PANE, 1, (short) 4, Text.color("&6"),
                lore("&7Daily reward")));
        RewardManager rewards = core.optional(RewardManager.class);
        Profile profile = profile(viewer);
        if (rewards == null || profile == null) {
            map.put(Integer.valueOf(13), Button.display(Items.item(Material.BARRIER, 1, (short) 0,
                    Text.color("&cUnavailable"), lore("&7Your profile is still loading."))));
            map.put(Integer.valueOf(22), closeButton());
            return map;
        }
        boolean claimable = rewards.canClaimDaily(profile);
        RewardBundle bundle = rewards.dailyReward(profile);
        int streak = rewards.dailyStreak(profile);
        map.put(Integer.valueOf(11), Button.display(Items.item(Material.PAPER, 1, (short) 0,
                Text.color("&eStreak"), lore("&7Current streak: &f" + streak,
                        "&7Streak cap: &f" + rewards.dailyStreakCap(),
                        "&7Keep claiming every day to raise it"))));
        ItemStack rewardItem = Items.item(Material.GOLD_INGOT, claimable ? streak + 1 : 1, (short) 0,
                Text.color("&6Reward"), lore("&7Coins: &6" + bundle.coins(),
                        "&7Experience: &b" + bundle.experience(),
                        claimable ? "&aReady to claim" : "&7Next in: &f"
                                + Text.duration(rewards.dailyRemainingMillis(profile))));
        map.put(Integer.valueOf(13), claimable ? Button.of(rewardItem, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                RewardManager manager = core.optional(RewardManager.class);
                if (manager != null) {
                    manager.claimDaily(player);
                }
                refresh(player);
            }
        }) : Button.display(rewardItem));
        map.put(Integer.valueOf(15), Button.display(Items.item(Material.EXP_BOTTLE, 1, (short) 0,
                Text.color("&bYour Balance"), lore("&7Coins: &6" + profile.coins(),
                        "&7Experience: &b" + profile.experience(),
                        "&7Level: &a" + profile.level()))));
        map.put(Integer.valueOf(22), backButton(new MainMenu(core)));
        return map;
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
