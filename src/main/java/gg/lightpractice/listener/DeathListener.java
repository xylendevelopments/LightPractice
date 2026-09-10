package gg.lightpractice.listener;

import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.Match.DeathDecision;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.entity.EntityDamageEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/**
 * Deaths and respawns inside and outside matches.
 *
 * <p>The match decides what a death means through {@link Match#handleDeath}: it credits the kill, applies
 * kit rules and tells this listener whether the player comes back, after how long and where. Drops only
 * exist when the kit allows them, so a practice arena never becomes an item farm.</p>
 */
public final class DeathListener implements Listener {

    private final PluginCore core;

    public DeathListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        final Player victim = event.getEntity();
        event.setDeathMessage(null);
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        MatchManager matches = core.optional(MatchManager.class);
        Match match = matches == null ? null : matches.getMatch(victim.getUniqueId());
        if (match == null) {
            // outside a match nobody loses items: the lobby is not a battlefield
            event.getDrops().clear();
            return;
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? EntityDamageEvent.DamageCause.CUSTOM
                : last.getCause();
        Player killer = victim.getKiller();
        if (killer == null && last instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            killer = DamageListener.attackerOf(
                    ((org.bukkit.event.entity.EntityDamageByEntityEvent) last).getDamager());
        }
        DeathDecision decision = match.handleDeath(victim, killer, cause);
        if (decision.keepInventory()) {
            event.getDrops().clear();
        }
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics != null) {
            cosmetics.applyKill(match, killer == null ? null : killer.getUniqueId(),
                    victim.getUniqueId());
        }
        Debug.log(DebugCategory.MATCH, "{} died in match {} to {} ({})", victim.getName(),
                match.identifier(), killer == null ? "the environment" : killer.getName(),
                cause.name());
        if (decision.respawn()) {
            scheduleRespawn(victim, match, decision.delayTicks());
        }
    }

    /**
     * Brings a player back after the configured delay.
     *
     * <p>Death screens are only kept when the kit allows them, otherwise the respawn happens on the next
     * tick so a fast paced mode never stalls.</p>
     */
    private void scheduleRespawn(final Player victim, final Match match, int delayTicks) {
        KitRuleSet rules = match.rules();
        boolean deathScreen = rules == null || rules.allowDeathScreen();
        final int delay = deathScreen ? Math.max(1, delayTicks) : 1;
        final UUID uuid = victim.getUniqueId();
        core.tasks().syncLater(new Runnable() {
            @Override
            public void run() {
                Player player = core.plugin().getServer().getPlayer(uuid);
                if (player == null || !player.isOnline() || !player.isDead() || match.isFinished()) {
                    return;
                }
                try {
                    player.spigot().respawn();
                } catch (IllegalStateException exception) {
                    Debug.log(DebugCategory.MATCH, "{} respawned on their own before the delay ended",
                            player.getName());
                }
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(final PlayerRespawnEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        final Player player = event.getPlayer();
        MatchManager matches = core.optional(MatchManager.class);
        final Match match = matches == null ? null : matches.getMatch(player.getUniqueId());
        if (match == null) {
            LobbyManager lobby = core.optional(LobbyManager.class);
            Location spawn = lobby == null || !lobby.hasSpawn() ? null : lobby.spawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            return;
        }
        MatchPlayer participant = match.participant(player);
        Location point = participant == null ? null : participant.respawnPoint();
        KitRuleSet rules = match.rules();
        if (point == null && rules != null) {
            point = rules.respawnLocation(match, player);
        }
        if (point != null) {
            event.setRespawnLocation(point);
        }
        // the kit is applied one tick later so the respawn itself is finished first
        core.tasks().syncLater(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && match.isLive()) {
                    match.respawn(player);
                }
            }
        }, 1L);
    }
}
