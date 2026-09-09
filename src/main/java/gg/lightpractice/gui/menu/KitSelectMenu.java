package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.queue.Queue;
import gg.lightpractice.queue.QueueManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.KitStatistics;
import gg.lightpractice.statistics.StatisticsManager;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kit picker.
 *
 * <p>A left click applies the kit in the lobby with the saved loadout, a right click opens its editor and
 * a shift click joins the unranked queue of that kit, which is what most players actually want from this
 * menu.</p>
 */
public final class KitSelectMenu extends PagedMenu {

    public KitSelectMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7No kits are available.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.kits", "&8Kits");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        KitManager kits = core.optional(KitManager.class);
        if (kits == null || viewer == null) {
            return entries;
        }
        Profile profile = profile(viewer);
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        QueueManager queues = core.optional(QueueManager.class);
        for (final Kit kit : kits.enabled()) {
            if (kit == null || !kit.hasPermission(viewer)) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            for (String line : kit.description()) {
                lore.add(Text.color(line));
            }
            if (profile != null && statistics != null) {
                KitStatistics kitStatistics = statistics.kitStatistics(profile.uuid(), kit.id(), true);
                lore.add(Text.color("&7Ranked: &a" + kitStatistics.wins() + "W &7/ &c" + kitStatistics.losses()
                        + "L &7(&f" + (int) Math.round(kitStatistics.winPercentage()) + "%&7)"));
                lore.add(Text.color("&7Elo: &f" + statistics.elo(profile.uuid(), kit.id())));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aLeft click &7to select this kit"));
            if (kit.editable()) {
                lore.add(Text.color("&eRight click &7to edit your loadout"));
            }
            Queue unranked = queues == null ? null : queues.forKit(kit.id(), false);
            if (unranked != null) {
                lore.add(Text.color("&bShift click &7to join the unranked queue (&f" + unranked.size() + "&7)"));
            }
            ItemStack icon = Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    handle(player, kit, type);
                }
            }));
        }
        return entries;
    }

    private void handle(Player player, Kit kit, ClickType type) {
        if (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT) {
            QueueManager queues = core.optional(QueueManager.class);
            Queue queue = queues == null ? null : queues.forKit(kit.id(), false);
            if (queue == null) {
                core.messages().send(player, "kit.no-queue", "{kit}", kit.displayName());
                return;
            }
            close(player);
            queues.join(player, queue);
            return;
        }
        if (type == ClickType.RIGHT) {
            if (!kit.editable()) {
                core.messages().send(player, "kit.not-editable", "{kit}", kit.displayName());
                return;
            }
            open(player, new KitEditorMenu(core, kit.id()));
            return;
        }
        KitManager kits = core.optional(KitManager.class);
        if (kits == null) {
            return;
        }
        kits.applyKit(player, kit, true);
        core.messages().send(player, "kit.selected", "{kit}", kit.displayName());
    }

    @Override
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        int lastRow = (rows() - 1) * 9;
        KitManager kits = core.optional(KitManager.class);
        ItemStack editor = Items.item(Material.ANVIL, 1, (short) 0, Text.color("&eKit Editor"),
                lore("&7Right click any kit to edit it", "&7Kits available: &f"
                        + (kits == null ? 0 : kits.editable().size())));
        map.put(Integer.valueOf(lastRow + 3), Button.display(editor));
        ItemStack random = Items.item(Material.WATCH, 1, (short) 0, Text.color("&bRandom Queue"),
                lore("&7Join the shortest queue"));
        map.put(Integer.valueOf(lastRow + 6), Button.of(random, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                QueueManager queues = core.optional(QueueManager.class);
                if (queues == null) {
                    return;
                }
                close(player);
                queues.joinRandom(player);
            }
        }));
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
