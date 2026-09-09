package gg.lightpractice.listener;

import gg.lightpractice.ban.BanManager;
import gg.lightpractice.bot.BotManager;
import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.gameevent.EventManager;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.party.PartyManager;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.queue.QueueManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.spectator.SpectatorManager;
import gg.lightpractice.model.BanEntry;
import gg.lightpractice.tournament.Tournament;
import gg.lightpractice.tournament.TournamentManager;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Visuals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Connections: who may enter, what happens when they arrive and what has to be cleaned up when they go.
 *
 * <p>Joining loads the profile asynchronously and only sends the player to the lobby once it is there, so
 * a slow database shows a loading message instead of a broken scoreboard. Leaving runs the whole cleanup
 * chain in one place: queues, parties, events, tournaments, bots, spectators, cosmetics, combat tags and
 * finally the profile save.</p>
 */
public final class PlayerConnectionListener implements Listener {

    private final PluginCore core;

    public PlayerConnectionListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event == null || event.getUniqueId() == null) {
            return;
        }
        BanManager bans = core.optional(BanManager.class);
        if (bans == null || !bans.isBanned(event.getUniqueId())) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                bans.kickMessage(bans.ban(event.getUniqueId())));
        Debug.log(DebugCategory.PLAYER, "{} was refused at the login screen: {}", event.getName(), message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        BanManager bans = core.optional(BanManager.class);
        if (bans == null) {
            return;
        }
        BanEntry entry = bans.ban(event.getPlayer().getUniqueId());
        if (entry != null && entry.active()) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, bans.kickMessage(entry));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        final Player player = event.getPlayer();
        event.setJoinMessage(null);
        PlayerManager players = core.optional(PlayerManager.class);
        final LightPlayer session = players == null ? null : players.register(player);
        if (session != null) {
            session.name(player.getName());
        }
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles == null) {
            Debug.warn(DebugCategory.PROFILE, "No profile manager registered, {} plays without a profile",
                    player.getName());
            sendToLobby(player);
            return;
        }
        final String joinSound = core.configs().config().getString("lobby.join-sound", "");
        profiles.requestProfile(player, new Consumer<Profile>() {
            @Override
            public void accept(Profile profile) {
                PlayerManager manager = core.optional(PlayerManager.class);
                LightPlayer current = manager == null ? null : manager.get(player);
                if (current != null) {
                    current.profile(profile);
                }
                if (!player.isOnline()) {
                    return;
                }
                Match match = reconnectMatch(player);
                if (match != null) {
                    return;
                }
                sendToLobby(player);
                if (joinSound != null && !joinSound.isEmpty()) {
                    Visuals.sound(player, joinSound, 1.0F, 1.0F);
                }
                core.messages().send(player, "player.welcome",
                        "{player}", player.getName(),
                        "{version}", core.version());
                Debug.log(DebugCategory.PROFILE, "Profile of {} is ready, sent to the lobby", player.getName());
            }
        });
    }

    /** Puts a returning player back into the match they disconnected from, when it is still live. */
    private Match reconnectMatch(Player player) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return null;
        }
        Match match = matches.getMatch(player.getUniqueId());
        if (match == null || match.isFinished() || !match.isParticipant(player.getUniqueId())) {
            return null;
        }
        if (match.reconnect(player)) {
            Debug.log(DebugCategory.MATCH, "{} reconnected into match {}", player.getName(),
                    match.identifier());
            return match;
        }
        return null;
    }

    private void sendToLobby(Player player) {
        LobbyManager lobby = core.optional(LobbyManager.class);
        if (lobby != null) {
            lobby.handleJoin(player);
            return;
        }
        player.teleport(player.getWorld() == null ? player.getLocation()
                : player.getWorld().getSpawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        // a kicked player is not a combat logger, the tag is dropped so the quit handler stays quiet
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null) {
            combat.clearTag(event.getPlayer().getUniqueId());
        }
        Debug.log(DebugCategory.PLAYER, "{} was kicked: {}", event.getPlayer().getName(), event.getReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        event.setQuitMessage(null);
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        try {
            handleMatch(player, session);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.MATCH, "Could not clean up the match of " + player.getName(), throwable);
        }
        QueueManager queues = core.optional(QueueManager.class);
        if (queues != null) {
            queues.removeAll(uuid);
        }
        PartyManager parties = core.optional(PartyManager.class);
        if (parties != null) {
            parties.handleQuit(uuid);
        }
        EventManager events = core.optional(EventManager.class);
        if (events != null) {
            events.handleQuit(uuid);
        }
        TournamentManager tournaments = core.optional(TournamentManager.class);
        if (tournaments != null && tournaments.isEntered(uuid)) {
            Tournament tournament = tournaments.active();
            if (tournament != null && tournament.state().joinable()) {
                tournament.leave(uuid);
                core.messages().broadcast("tournament.exit", "{player}", player.getName(),
                        "{size}", String.valueOf(tournament.size()));
            }
        }
        BotManager bots = core.optional(BotManager.class);
        if (bots != null) {
            bots.handleQuit(player);
        }
        SpectatorManager spectators = core.optional(SpectatorManager.class);
        if (spectators != null && spectators.isSpectating(uuid)) {
            spectators.leave(player);
        }
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics != null) {
            cosmetics.clearPlayer(uuid);
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null) {
            combat.clearTag(uuid);
        }
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles != null) {
            Profile profile = profiles.getProfile(uuid);
            if (profile != null) {
                profile.lastSeen(System.currentTimeMillis());
                profiles.save(profile);
            }
            profiles.unload(uuid);
        }
        if (players != null) {
            players.unregister(player);
        }
        Debug.log(DebugCategory.PLAYER, "{} left the server", player.getName());
    }

    /** Combat logging, spectating and reconnect grace of a leaving player. */
    private void handleMatch(Player player, LightPlayer session) {
        MatchManager matches = core.optional(MatchManager.class);
        UUID uuid = player.getUniqueId();
        if (matches == null) {
            return;
        }
        if (matches.isSpectating(uuid)) {
            matches.stopSpectating(uuid);
        }
        Match match = matches.getMatch(uuid);
        if (match == null || match.isFinished() || !match.isParticipant(uuid)) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        boolean tagged = combat != null && combat.isTagged(uuid);
        if (tagged && combat.punishCombatLog()) {
            Debug.log(DebugCategory.COMBAT, "{} combat logged out of match {}", player.getName(),
                    match.identifier());
            matches.forfeit(uuid, EndCause.FORFEIT);
            core.messages().broadcast("match.combat-log", "{player}", player.getName(),
                    "{kit}", match.kit() == null ? "unknown" : match.kit().displayName());
            return;
        }
        if (tagged) {
            Debug.log(DebugCategory.COMBAT, "{} left match {} while tagged, the grace period runs",
                    player.getName(), match.identifier());
        }
        // the match keeps its disconnect grace and forfeits on its own when nobody comes back
    }
}
