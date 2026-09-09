package gg.lightpractice.gui.menu;

import gg.lightpractice.gameevent.EventManager;
import gg.lightpractice.gameevent.EventState;
import gg.lightpractice.gameevent.GameEvent;
import gg.lightpractice.gameevent.GameEventProvider;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.service.PluginCore;
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
 * Event screen: join the running event or host a new one.
 *
 * <p>While an event is live the menu shows its state and the join or leave action, otherwise it lists the
 * configured event definitions a player is allowed to host.</p>
 */
public final class EventMenu extends PagedMenu {

    public EventMenu(PluginCore core) {
        super(core, title(core), 4, null, "&7No events are configured.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.events", "&8Events");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final EventManager events = core.optional(EventManager.class);
        if (events == null || viewer == null) {
            return entries;
        }
        GameEvent active = events.active();
        if (active != null) {
            entries.add(activeButton(events, active, viewer));
            return entries;
        }
        for (final EventManager.EventDefinition definition : events.definitions()) {
            if (definition == null || !definition.enabled()) {
                continue;
            }
            GameEventProvider provider = events.provider(definition.typeId());
            if (provider == null) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            for (String line : provider.description()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7Type: &f" + provider.id()));
            lore.add(Text.color("&7Kit: &f" + (definition.kitId().isEmpty() ? events.defaultKit()
                    : definition.kitId())));
            if (!definition.arenaName().isEmpty()) {
                lore.add(Text.color("&7Arena: &f" + definition.arenaName()));
            }
            lore.add(Text.color("&7Players: &f" + definition.minPlayers() + " &7- &f"
                    + definition.maxPlayers()));
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aLeft click &7to host with the configured kit"));
            lore.add(Text.color("&eRight click &7to host and pick a kit"));
            ItemStack icon = Items.item(provider.icon() == null ? Material.CAKE : provider.icon(), 1,
                    (short) 0, definition.name(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    EventManager manager = core.optional(EventManager.class);
                    if (manager == null) {
                        return;
                    }
                    if (!player.hasPermission("lightpractice.event.host")) {
                        core.messages().send(player, "event.no-permission");
                        return;
                    }
                    if (type == ClickType.RIGHT) {
                        open(player, new EventKitMenu(core, definition.id()));
                        return;
                    }
                    close(player);
                    manager.host(player, definition.id(), null, null);
                }
            }));
        }
        return entries;
    }

    private Button activeButton(final EventManager events, final GameEvent active, final Player viewer) {
        boolean entered = active.isParticipant(viewer.getUniqueId());
        List<String> lore = new ArrayList<String>();
        lore.add(Text.color("&7Type: &f" + active.typeId()));
        lore.add(Text.color("&7Host: &f" + active.hostName()));
        lore.add(Text.color("&7Kit: &f" + active.kitId()));
        lore.add(Text.color("&7State: &f" + active.state().configKey()));
        lore.add(Text.color("&7Players: &f" + active.size() + "&7/&f" + active.maxPlayers()));
        if (active.state() == EventState.GATHERING) {
            lore.add(Text.color("&7Starts in: &f" + Text.duration(active.remainingMillis())));
        }
        lore.add(Text.color("&7&m                        "));
        if (entered) {
            lore.add(Text.color("&cClick &7to leave the event"));
        } else if (active.state().joinable()) {
            lore.add(Text.color("&aClick &7to join the event"));
        } else {
            lore.add(Text.color("&7Entries are closed"));
        }
        ItemStack icon = Items.item(Material.CAKE, 1, (short) 0, active.name(), lore);
        return Button.of(icon, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                EventManager manager = core.optional(EventManager.class);
                if (manager == null) {
                    return;
                }
                if (active.isParticipant(player.getUniqueId())) {
                    manager.leave(player);
                } else {
                    manager.join(player);
                }
                refresh(player);
            }
        });
    }

    @Override
    protected void decorate(Map<Integer, Button> map, final Player viewer) {
        final EventManager events = core.optional(EventManager.class);
        int lastRow = (rows() - 1) * 9;
        GameEvent active = events == null ? null : events.active();
        ItemStack info = active == null
                ? Items.item(Material.BOOK, 1, (short) 0, Text.color("&eEvents"),
                        lore("&7No event is running", "&7Host one from this menu",
                                "&7Types: &f" + (events == null ? 0 : events.providers().size())))
                : Items.item(Material.BOOK, 1, (short) 0, Text.color("&eRunning Event"),
                        lore("&7Name: " + active.name(),
                                "&7State: &f" + active.state().configKey(),
                                "&7Players: &f" + active.size()));
        map.put(Integer.valueOf(lastRow + 3), Button.display(info));
        if (active != null && viewer != null && viewer.hasPermission("lightpractice.event.admin")) {
            ItemStack stop = Items.item(Material.BARRIER, 1, (short) 0, Text.color("&cStop Event"),
                    lore("&7Cancel the running event"));
            map.put(Integer.valueOf(lastRow + 6), Button.of(stop, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    EventManager manager = core.optional(EventManager.class);
                    if (manager != null) {
                        manager.stop(player, "stopped from the event menu");
                    }
                    refresh(player);
                }
            }));
        }
    }
}
