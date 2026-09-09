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
 * Queue browser.
 *
 * <p>One button per queue with its live waiting count. A click joins, a right click leaves whatever queue
 * the player is in, and the position of the viewer is shown in the entry they are standing in.</p>
 */
public final class QueueMenu extends PagedMenu {

    public QueueMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7No queues are configured.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.queues", "&8Queues");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        QueueManager queues = core.optional(QueueManager.class);
        if (queues == null || viewer == null) {
            return entries;
        }
        KitManager kits = core.optional(KitManager.class);
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        Profile profile = profile(viewer);
        for (final Queue queue : queues.sorted()) {
            if (queue == null || !queue.enabled() || !queue.hasPermission(viewer)) {
                continue;
            }
            Kit kit = kits == null ? null : kits.get(queue.kitId());
            List<String> lore = new ArrayList<String>();
            for (String line : queue.lore()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7Waiting: &f" + queue.size()));
            lore.add(Text.color("&7In match: &f" + queue.startedMatches()));
            if (profile != null && statistics != null) {
                lore.add(Text.color("&7Your elo: &f" + statistics.elo(profile.uuid(), queue.kitId())));
            }
            if (queue.contains(viewer.getUniqueId())) {
                lore.add(Text.color("&aYou are in this queue (#" + queue.position(viewer.getUniqueId()) + ")"));
                lore.add(Text.color("&7Estimated wait: &f"
                        + Text.duration(queues.estimatedWait(viewer.getUniqueId()))));
                lore.add(Text.color("&cRight click &7to leave"));
            } else {
                lore.add(Text.color("&7&m                        "));
                lore.add(Text.color("&aLeft click &7to join " + (queue.ranked() ? "ranked" : "unranked")));
            }
            Material material = queue.icon() == null ? Material.IRON_SWORD : queue.icon();
            ItemStack icon = Items.item(material, 1, queue.iconData(),
                    Text.color((queue.ranked() ? "&6" : "&7") + queue.displayName()), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    QueueManager manager = core.optional(QueueManager.class);
                    if (manager == null) {
                        return;
                    }
                    if (type == ClickType.RIGHT && manager.isQueued(player.getUniqueId())) {
                        manager.leave(player);
                        refresh(player);
                        return;
                    }
                    if (queue.contains(player.getUniqueId())) {
                        manager.leave(player);
                        refresh(player);
                        return;
                    }
                    close(player);
                    manager.join(player, queue);
                }
            }));
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        int lastRow = (rows() - 1) * 9;
        QueueManager queues = core.optional(QueueManager.class);
        ItemStack random = Items.item(Material.COMPASS, 1, (short) 0, Text.color("&bRandom Queue"),
                lore("&7Join the queue with the shortest wait", "&7Waiting in total: &f"
                        + (queues == null ? 0 : queues.totalWaiting())));
        map.put(Integer.valueOf(lastRow + 3), Button.of(random, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                QueueManager manager = core.optional(QueueManager.class);
                if (manager == null) {
                    return;
                }
                close(player);
                manager.joinRandom(player);
            }
        }));
        Queue current = queues == null || viewer == null ? null : queues.queueOf(viewer.getUniqueId());
        ItemStack leave = Items.item(Material.INK_SACK, 1, (short) 1, Text.color("&cLeave Queue"),
                current == null ? lore("&7You are not in a queue")
                        : lore("&7You are in &f" + current.displayName()));
        map.put(Integer.valueOf(lastRow + 6), current == null ? Button.display(leave) : Button.of(leave,
                new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        QueueManager manager = core.optional(QueueManager.class);
                        if (manager != null) {
                            manager.leave(player);
                        }
                        refresh(player);
                    }
                }));
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
