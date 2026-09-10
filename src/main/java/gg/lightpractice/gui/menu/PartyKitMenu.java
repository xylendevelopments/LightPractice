package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.party.Party;
import gg.lightpractice.party.PartyManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit chooser for party matches.
 *
 * <p>The chosen kit is used by every party match format, so the leader picks it once here instead of
 * repeating it for each fight.</p>
 */
public final class PartyKitMenu extends PagedMenu {

    public PartyKitMenu(PluginCore core) {
        super(core, title(core), 4, new PartyMenu(core), "&7No kits are available.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.party-kit", "&8Party Kit");
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        KitManager kits = core.optional(KitManager.class);
        PartyManager parties = core.optional(PartyManager.class);
        if (kits == null || parties == null || viewer == null) {
            return entries;
        }
        final Party party = parties.get(viewer);
        if (party == null || !party.isLeader(viewer.getUniqueId())) {
            return entries;
        }
        for (final Kit kit : kits.enabled()) {
            if (kit == null || !kit.hasPermission(viewer)) {
                continue;
            }
            boolean selected = kit.id().equals(party.kitId());
            List<String> lore = new ArrayList<String>();
            for (String line : kit.description()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(selected ? Text.color("&aSelected") : Text.color("&eClick &7to select this kit"));
            ItemStack icon = Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    PartyManager manager = core.optional(PartyManager.class);
                    Party current = manager == null ? null : manager.get(player);
                    if (manager != null && current != null) {
                        manager.setKit(current, player, kit.id());
                    }
                    open(player, new PartyMenu(core));
                }
            }));
        }
        return entries;
    }
}
