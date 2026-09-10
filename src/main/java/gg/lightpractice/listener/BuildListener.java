package gg.lightpractice.listener;

import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/**
 * Building, breaking and everything that touches a block or an entity by hand.
 *
 * <p>Inside a match the decision belongs to the kit through {@link Match#canBuild} and
 * {@link Match#canBreak}, which already check the arena bounds. Outside a match only staff with the build
 * permission may change the world, so a lobby never gets decorated by accident.</p>
 */
public final class BuildListener implements Listener {

    private final PluginCore core;

    public BuildListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlockPlaced() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Match match = matchOf(player);
        if (match != null) {
            if (!match.canBuild(player, block)) {
                event.setCancelled(true);
                event.setBuild(false);
                core.messages().send(player, "match.build-denied");
                return;
            }
            KitRuleSet rules = match.rules();
            if (rules != null) {
                rules.onBlockPlaced(match, player, block);
            }
            Debug.log(DebugCategory.MATCH, "{} placed {} in match {}", player.getName(),
                    block.getType().name(), match.identifier());
            return;
        }
        if (!staff(player)) {
            event.setCancelled(true);
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Match match = matchOf(player);
        if (match != null) {
            if (!match.canBreak(player, block)) {
                event.setCancelled(true);
                core.messages().send(player, "match.break-denied");
                return;
            }
            KitRuleSet rules = match.rules();
            if (rules != null) {
                rules.onBlockBroken(match, player, block);
            }
            return;
        }
        if (!staff(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match != null) {
            if (!match.canBuild(player, event.getBlockClicked())) {
                event.setCancelled(true);
                core.messages().send(player, "match.build-denied");
            }
            return;
        }
        if (!staff(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match != null) {
            if (!match.canBreak(player, event.getBlockClicked())) {
                event.setCancelled(true);
                core.messages().send(player, "match.break-denied");
            }
            return;
        }
        if (!staff(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event == null || !(event.getRemover() instanceof Player)) {
            return;
        }
        if (!staff((Player) event.getRemover())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event == null || !(event.getEntered() instanceof Player)) {
            return;
        }
        if (!staff((Player) event.getEntered())) {
            event.setCancelled(true);
        }
    }

    /** Whether a player may change the world outside a match. */
    private boolean staff(Player player) {
        return player != null && player.hasPermission("lightpractice.admin.build");
    }

    private Match matchOf(Player player) {
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null || player == null ? null : matches.getMatch(player.getUniqueId());
    }
}
