package gg.lightpractice.gui.menu;

import gg.lightpractice.duel.DuelManager;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kit choice for a duel request.
 *
 * <p>Picking a kit sends the request through {@link DuelManager}, which re-validates both players when the
 * request is accepted, so a menu can never force a match that is no longer legal.</p>
 */
public final class DuelKitMenu extends PagedMenu {

    private final UUID target;
    private final String targetName;
    private final boolean ranked;

    public DuelKitMenu(PluginCore core, UUID target, String targetName, boolean ranked) {
        super(core, buildTitle(core, targetName, ranked), 5, new DuelPlayerMenu(core),
                "&7No kits can be duelled.");
        this.target = target;
        this.targetName = targetName == null ? "player" : targetName;
        this.ranked = ranked;
    }

    private static String buildTitle(PluginCore core, String name, boolean ranked) {
        String base = core.configs().gui().getString("titles.duel-kit", "&8Duel");
        return base + " &7- &f" + name + (ranked ? " &6(ranked)" : "");
    }

    public UUID target() {
        return target;
    }

    public boolean ranked() {
        return ranked;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        KitManager kits = core.optional(KitManager.class);
        if (kits == null || viewer == null || target == null) {
            return entries;
        }
        for (final Kit kit : kits.duellable()) {
            if (kit == null || !kit.hasPermission(viewer)) {
                continue;
            }
            if (ranked && !kit.ranked()) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            for (String line : kit.description()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aClick &7to challenge &f" + targetName));
            ItemStack icon = Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    DuelManager duels = core.optional(DuelManager.class);
                    Player online = Bukkit.getPlayer(target);
                    if (duels == null || online == null) {
                        core.messages().send(player, "duel.target-offline", "{player}", targetName);
                        return;
                    }
                    close(player);
                    duels.send(player, online, kit.id(), null, ranked);
                }
            }));
        }
        return entries;
    }
}
