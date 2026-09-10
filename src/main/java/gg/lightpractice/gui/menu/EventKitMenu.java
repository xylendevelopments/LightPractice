package gg.lightpractice.gui.menu;

import gg.lightpractice.gameevent.EventManager;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit chooser for hosting an event.
 *
 * <p>Only kits that fit the event type are listed: a provider validates the session before it is hosted,
 * so a kit without respawns cannot be picked for king of the hill.</p>
 */
public final class EventKitMenu extends PagedMenu {

    private final String typeId;

    public EventKitMenu(PluginCore core, String typeId) {
        super(core, title(core), 4, new EventMenu(core), "&7No kits are available.");
        this.typeId = typeId;
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.event-kit", "&8Event Kit");
    }

    public String typeId() {
        return typeId;
    }

    @Override
    public List<Button> entries(Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        KitManager kits = core.optional(KitManager.class);
        if (kits == null || viewer == null) {
            return entries;
        }
        for (final Kit kit : kits.enabled()) {
            if (kit == null || !kit.hasPermission(viewer)) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            for (String line : kit.description()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aClick &7to host with this kit"));
            ItemStack icon = Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    EventManager events = core.optional(EventManager.class);
                    if (events == null) {
                        return;
                    }
                    if (!player.hasPermission("lightpractice.event.host")) {
                        core.messages().send(player, "event.no-permission");
                        return;
                    }
                    close(player);
                    events.host(player, typeId, kit.id(), null);
                }
            }));
        }
        return entries;
    }
}
