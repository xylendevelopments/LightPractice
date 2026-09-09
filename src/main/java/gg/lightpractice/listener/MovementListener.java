package gg.lightpractice.listener;

import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.spectator.SpectatorManager;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Movement: cosmetic trails, arena borders and the void.
 *
 * <p>The event fires for every head turn, so the handler returns immediately unless the player changed
 * block. Trails are throttled inside the cosmetic manager and border warnings at most every two seconds,
 * which keeps the cost of a busy server at one cheap check per real step.</p>
 */
public final class MovementListener implements Listener {

    private static final long WARNING_INTERVAL_MILLIS = 2000L;

    private final PluginCore core;
    private final Map<UUID, Long> borderWarnings = new ConcurrentHashMap<UUID, Long>();

    public MovementListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || sameBlock(from, to)) {
            return;
        }
        Player player = event.getPlayer();
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics != null) {
            cosmetics.playTrail(player);
        }
        UUID uuid = player.getUniqueId();
        SpectatorManager spectators = core.optional(SpectatorManager.class);
        if (spectators != null && spectators.isSpectating(uuid)) {
            if (to.getBlockY() <= 0) {
                event.setCancelled(true);
            }
            return;
        }
        MatchManager matches = core.optional(MatchManager.class);
        Match match = matches == null ? null : matches.getMatch(uuid);
        if (match == null) {
            if (to.getBlockY() <= 0) {
                LobbyManager lobby = core.optional(LobbyManager.class);
                Location spawn = lobby == null || !lobby.hasSpawn() ? null : lobby.spawn();
                if (spawn != null) {
                    player.teleport(spawn);
                    player.setFallDistance(0.0F);
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }
        if (!match.isRunning()) {
            return;
        }
        MatchPlayer participant = match.participant(player);
        if (participant == null || participant.eliminated()) {
            return;
        }
        KitRuleSet rules = match.rules();
        if (rules != null && rules.instantLoss(match, player, null, to)) {
            Debug.log(DebugCategory.MATCH, "{} left the field of match {}", player.getName(),
                    match.identifier());
            match.eliminate(player, EndCause.DISQUALIFIED);
            return;
        }
        if (!match.canModify(to)) {
            event.setCancelled(true);
            warnBorder(player, match);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event != null && event.getPlayer() != null) {
            forget(event.getPlayer().getUniqueId());
        }
    }

    private void warnBorder(Player player, Match match) {
        long now = System.currentTimeMillis();
        prune(now);
        Long last = borderWarnings.get(player.getUniqueId());
        if (last != null && now - last.longValue() < WARNING_INTERVAL_MILLIS) {
            return;
        }
        borderWarnings.put(player.getUniqueId(), Long.valueOf(now));
        core.messages().send(player, "match.border");
    }

    /** Keeps the warning map small without a task of its own. */
    private void prune(long now) {
        if (borderWarnings.size() <= 64) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> iterator = borderWarnings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Long stamp = entry.getValue();
            if (stamp == null || now - stamp.longValue() > WARNING_INTERVAL_MILLIS * 30L) {
                iterator.remove();
            }
        }
    }

    /** Forget a player, called when they leave so no stale entry survives. */
    public void forget(UUID uuid) {
        if (uuid != null) {
            borderWarnings.remove(uuid);
        }
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
