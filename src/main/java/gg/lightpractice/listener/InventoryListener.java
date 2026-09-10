package gg.lightpractice.listener;

import gg.lightpractice.gui.GuiManager;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.spectator.SpectatorManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.UUID;

/**
 * Inventory protection and the bridge into the GUI system.
 *
 * <p>Every click and drag first goes to the {@link GuiManager}; when it is not a menu the click is still
 * cancelled for spectators and for anyone whose loadout is locked down, so items can never be pulled out of
 * a match or a kit editor.</p>
 */
public final class InventoryListener implements Listener {

    private final PluginCore core;

    public InventoryListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (event == null) {
            return;
        }
        GuiManager gui = core.optional(GuiManager.class);
        if (gui != null && gui.handleClick(event)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (locked(player)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                player.setItemOnCursor(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event == null) {
            return;
        }
        GuiManager gui = core.optional(GuiManager.class);
        if (gui != null && gui.handleDrag(event)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (locked((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event == null) {
            return;
        }
        GuiManager gui = core.optional(GuiManager.class);
        if (gui != null) {
            gui.handleClose(event);
        }
    }

    /** Hoppers may not steal items out of a player while a match runs. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event == null || event.getDestination() == null) {
            return;
        }
        if (event.getDestination().getType() == InventoryType.PLAYER
                || event.getInitiator().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether this player may touch their inventory at all right now.
     *
     * <p>Spectators get a read only view and a player whose session is locked (being restored, being
     * teleported into a match) must not be able to move items around.</p>
     */
    private boolean locked(Player player) {
        UUID uuid = player.getUniqueId();
        SpectatorManager spectators = core.optional(SpectatorManager.class);
        if (spectators != null && spectators.isSpectating(uuid)) {
            return true;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        return session != null && session.spectating();
    }
}
