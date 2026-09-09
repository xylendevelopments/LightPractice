package gg.lightpractice.match;

import gg.lightpractice.api.MatchService;
import gg.lightpractice.api.SpectatorService;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.config.Messages;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.bot.BotManager;
import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.gameevent.EventManager;
import gg.lightpractice.tournament.TournamentManager;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.HistoryManager;
import gg.lightpractice.progress.RewardManager;
import gg.lightpractice.statistics.StatisticsManager;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Locations;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Owns every running match.
 *
 * <p>One central task drives all matches, so a match never schedules its own repeating task. Starting a
 * match always goes through the same path: validate the request, reserve an arena, prepare the
 * participants, run the countdown. Ending one always runs the same cleanup order: statistics, history,
 * player restoration, lobby return, spectator cleanup, arena release and reset.</p>
 */
public final class MatchManager implements MatchService, LightService {

    private final PluginCore core;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<UUID, Match>();
    private final Map<UUID, UUID> participantToMatch = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, UUID> spectatorToMatch = new ConcurrentHashMap<UUID, UUID>();
    private BukkitTask tickTask;
    private BukkitTask housekeepingTask;
    private boolean resetArenas = true;
    private boolean protectArenas = true;

    public MatchManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "matches";
    }

    @Override
    public int startupOrder() {
        return 70;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
        tickTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                tickAll();
            }
        }, 20L, 20L);
        housekeepingTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                housekeep();
            }
        }, 600L, 600L);
        Debug.log(DebugCategory.MATCH, "Match engine ready (reset arenas: {}, protection: {})",
                resetArenas, protectArenas);
    }

    @Override
    public void onDisable() {
        endAll(EndCause.SHUTDOWN);
        core.tasks().cancel(tickTask);
        core.tasks().cancel(housekeepingTask);
        tickTask = null;
        housekeepingTask = null;
        matches.clear();
        participantToMatch.clear();
        spectatorToMatch.clear();
    }

    @Override
    public void onReload() {
        readConfiguration();
    }

    private void readConfiguration() {
        this.resetArenas = core.configs().config().getBoolean("match.reset-arenas", true);
        this.protectArenas = core.configs().config().getBoolean("match.protect-arenas", true);
    }

    public boolean protectArenas() {
        return protectArenas;
    }

    public boolean resetArenas() {
        return resetArenas;
    }

    // ------------------------------------------------------------------ ticking

    private void tickAll() {
        if (matches.isEmpty()) {
            return;
        }
        for (Match match : new ArrayList<Match>(matches.values())) {
            try {
                match.tick();
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.MATCH, "Match " + match.identifier() + " failed during a tick", throwable);
            }
        }
    }

    /** Drops lookups that point at matches which are gone, keeps the maps honest. */
    private void housekeep() {
        int removed = 0;
        Iterator<Map.Entry<UUID, UUID>> participants = participantToMatch.entrySet().iterator();
        while (participants.hasNext()) {
            Map.Entry<UUID, UUID> entry = participants.next();
            Match match = matches.get(entry.getValue());
            if (match == null || match.isFinished() || !match.isParticipant(entry.getKey())) {
                participants.remove();
                removed++;
            }
        }
        Iterator<Map.Entry<UUID, UUID>> spectators = spectatorToMatch.entrySet().iterator();
        while (spectators.hasNext()) {
            Map.Entry<UUID, UUID> entry = spectators.next();
            Match match = matches.get(entry.getValue());
            if (match == null || match.isFinished() || !match.isSpectator(entry.getKey())) {
                spectators.remove();
                removed++;
            }
        }
        if (removed > 0) {
            Debug.log(DebugCategory.MATCH, "Housekeeping dropped {} stale match lookup(s)", removed);
        }
    }

    // ------------------------------------------------------------- MatchService

    @Override
    public Match get(String matchId) {
        UUID uuid = parseId(matchId);
        return uuid == null ? null : matches.get(uuid);
    }

    @Override
    public Match getMatch(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        UUID matchId = participantToMatch.get(uuid);
        if (matchId == null) {
            matchId = spectatorToMatch.get(uuid);
        }
        return matchId == null ? null : matches.get(matchId);
    }

    @Override
    public Collection<Match> matches() {
        return Collections.unmodifiableCollection(matches.values());
    }

    @Override
    public int activeCount() {
        int count = 0;
        for (Match match : matches.values()) {
            if (!match.isFinished()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isInMatch(UUID uuid) {
        return uuid != null && participantToMatch.containsKey(uuid);
    }

    public boolean isSpectating(UUID uuid) {
        return uuid != null && spectatorToMatch.containsKey(uuid);
    }

    @Override
    public MatchTeam teamOf(Match match, UUID uuid) {
        return match == null ? null : match.teamOf(uuid);
    }

    @Override
    public boolean start(MatchRequest request) {
        return startMatch(request) != null;
    }

    /**
     * Validates a request, reserves an arena and starts the match.
     *
     * @return the running match, or {@code null} with the reason logged and reported to the participants
     */
    public Match startMatch(MatchRequest request) {
        if (request == null) {
            return null;
        }
        List<String> problems = request.problems();
        if (!problems.isEmpty()) {
            Debug.log(DebugCategory.MATCH, "Refused {}: {}", request, problems);
            notifyParticipants(request, "match.invalid-request", "{reason}", String.valueOf(problems.get(0)));
            return null;
        }
        Kit kit = request.kit();
        ArenaManager arenas = core.optional(ArenaManager.class);
        Arena arena = request.arena();
        if (arena == null) {
            arena = arenas == null ? null : arenas.findAvailable(kit);
            if (arena == null) {
                Debug.log(DebugCategory.MATCH, "No arena is available for kit {}", kit.id());
                notifyParticipants(request, "match.no-arena", "{kit}", kit.displayName());
                return null;
            }
        } else if (arenas != null && !arenas.isAvailable(arena)) {
            Debug.log(DebugCategory.MATCH, "Arena {} is busy, refusing {}", arena.name(), request);
            notifyParticipants(request, "match.arena-busy", "{arena}", arena.name());
            return null;
        }
        if (arena.world() == null) {
            Debug.log(DebugCategory.ARENA, "Arena {} points at an unloaded world", arena.name());
            notifyParticipants(request, "match.arena-world-missing", "{arena}", arena.name());
            return null;
        }
        Match match = new Match(core, this, request, arena);
        if (arenas != null && !arenas.reserve(arena, match.identifier())) {
            Debug.log(DebugCategory.MATCH, "Arena {} refused the reservation of {}", arena.name(),
                    match.identifier());
            notifyParticipants(request, "match.arena-busy", "{arena}", arena.name());
            return null;
        }
        if (!match.prepare(request)) {
            match.restorePlayers();
            release(arenas, arena, match.identifier());
            Debug.log(DebugCategory.MATCH, "Could not prepare {}: {}", match.identifier(), match.problems());
            notifyParticipants(request, "match.prepare-failed", "{reason}",
                    match.problems().isEmpty() ? "unknown" : match.problems().get(0));
            return null;
        }
        matches.put(match.id(), match);
        for (MatchPlayer participant : match.participants()) {
            participantToMatch.put(participant.uuid(), match.id());
        }
        match.beginCountdown();
        Debug.log(DebugCategory.MATCH, "Started {} ({}, {}, ranked={}, arena={}, source={})",
                match.identifier(), kit.id(), match.type(), match.ranked(), arena.name(), match.source());
        return match;
    }

    private void release(ArenaManager arenas, Arena arena, String matchId) {
        if (arenas != null && arena != null) {
            arenas.release(arena, matchId);
        }
    }

    private void notifyParticipants(MatchRequest request, String key, String... replacements) {
        Messages messages = core.messages();
        if (messages == null || request == null) {
            return;
        }
        for (UUID uuid : request.participants()) {
            Player player = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                messages.send(player, key, replacements);
            }
        }
    }

    @Override
    public void end(Match match, MatchTeam winningTeam, EndCause cause) {
        if (match == null) {
            return;
        }
        match.end(winningTeam, cause);
    }

    @Override
    public void forfeit(UUID uuid, EndCause cause) {
        Match match = getMatch(uuid);
        if (match == null) {
            return;
        }
        if (match.isSpectator(uuid)) {
            stopSpectating(uuid);
            return;
        }
        match.forfeit(uuid, cause);
    }

    @Override
    public void endAll(EndCause cause) {
        List<Match> running = new ArrayList<Match>(matches.values());
        Debug.log(DebugCategory.MATCH, "Ending {} match(es) with cause {}", running.size(), cause);
        for (Match match : running) {
            try {
                if (match.isFinished()) {
                    cleanup(match);
                    continue;
                }
                match.end(null, cause);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.MATCH, "Could not end match " + match.identifier(), throwable);
            }
        }
        matches.clear();
        participantToMatch.clear();
        spectatorToMatch.clear();
    }

    /**
     * Runs the cleanup of a finished match.
     *
     * <p>Called by {@link Match#end(MatchTeam, EndCause)} exactly once per match.</p>
     */
    public void onMatchEnded(Match match) {
        if (match == null) {
            return;
        }
        List<Player> online = match.onlinePlayers();
        List<UUID> watching = new ArrayList<UUID>(match.spectators());
        try {
            applyResult(match);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.MATCH, "Could not apply the result of " + match.identifier(), throwable);
        }
        try {
            celebrate(match);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.MATCH, "Could not play the victory cosmetics of " + match.identifier(),
                    throwable);
        }
        try {
            match.restorePlayers();
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.MATCH, "Could not restore the players of " + match.identifier(), throwable);
        }
        for (Player player : online) {
            returnToLobby(player);
        }
        for (UUID uuid : watching) {
            releaseSpectator(uuid);
        }
        cleanup(match);
        try {
            handOver(match);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.MATCH, "Could not hand " + match.identifier() + " to the systems that "
                    + "build on matches", throwable);
        }
    }

    /** Plays the victory cosmetics of the winning side while everybody is still in the arena. */
    private void celebrate(Match match) {
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        MatchTeam winners = match.winningTeam();
        if (cosmetics == null || winners == null) {
            return;
        }
        List<UUID> uuids = new ArrayList<UUID>();
        for (MatchPlayer member : winners.members()) {
            uuids.add(member.uuid());
        }
        cosmetics.applyVictory(match, uuids);
    }

    /**
     * Hands a finished match to the systems that are built on top of matches.
     *
     * <p>This runs after the players are back in the lobby, because tournaments and events start their next
     * round immediately and a participant has to be idle for that to work.</p>
     */
    private void handOver(Match match) {
        TournamentManager tournaments = core.optional(TournamentManager.class);
        if (tournaments != null) {
            tournaments.handleMatchEnd(match);
        }
        EventManager events = core.optional(EventManager.class);
        if (events != null) {
            events.handleMatchEnd(match);
        }
        BotManager bots = core.optional(BotManager.class);
        if (bots != null) {
            bots.handleMatchEnd(match);
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null) {
            for (MatchPlayer participant : match.participants()) {
                combat.clearTag(participant.uuid());
            }
        }
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics != null) {
            for (MatchPlayer participant : match.participants()) {
                cosmetics.clearPlayer(participant.uuid());
            }
        }
    }

    private void applyResult(Match match) {
        if (match.type() == null || !match.type().counted()) {
            Debug.log(DebugCategory.MATCH, "Match {} of type {} is not counted", match.identifier(), match.type());
            return;
        }
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        if (statistics != null) {
            statistics.applyMatchResult(match);
        } else {
            Debug.warn(DebugCategory.MATCH, "No statistics manager registered, result of " + match.identifier()
                    + " was not applied");
        }
        HistoryManager history = core.optional(HistoryManager.class);
        if (history != null) {
            history.save(match);
        }
        RewardManager rewards = core.optional(RewardManager.class);
        if (rewards != null) {
            rewards.handleMatchEnd(match);
        }
    }

    /**
     * Sends a player back to the lobby.
     *
     * <p>The lobby system is looked up at call time, so service startup order cannot leave a finished
     * match without a way home. When no lobby is registered the configured spawn is used directly.</p>
     */
    private void returnToLobby(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        LobbyManager lobby = core.optional(LobbyManager.class);
        if (lobby != null) {
            try {
                if (lobby.sendToLobby(player)) {
                    return;
                }
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.MATCH, "The lobby could not take " + player.getName(), throwable);
            }
        }
        Location spawn = Locations.read(core.configs().config().config(), "lobby.spawn");
        if (spawn != null && spawn.getWorld() != null) {
            player.teleport(spawn);
        }
    }

    private void releaseSpectator(UUID uuid) {
        spectatorToMatch.remove(uuid);
        SpectatorService spectators = core.optional(SpectatorService.class);
        Player player = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
        if (spectators != null && player != null) {
            spectators.leave(player);
        }
    }

    private void cleanup(Match match) {
        UUID matchId = match.id();
        matches.remove(matchId);
        List<UUID> stale = new ArrayList<UUID>();
        for (Map.Entry<UUID, UUID> entry : participantToMatch.entrySet()) {
            if (matchId.equals(entry.getValue())) {
                stale.add(entry.getKey());
            }
        }
        for (UUID uuid : stale) {
            participantToMatch.remove(uuid);
        }
        stale.clear();
        for (Map.Entry<UUID, UUID> entry : spectatorToMatch.entrySet()) {
            if (matchId.equals(entry.getValue())) {
                stale.add(entry.getKey());
            }
        }
        for (UUID uuid : stale) {
            spectatorToMatch.remove(uuid);
        }
        Arena arena = match.arena();
        ArenaManager arenas = core.optional(ArenaManager.class);
        if (arena != null && arenas != null) {
            arenas.release(arena, match.identifier());
            if (resetArenas && arena.hasSchematic() && arenas.canReset(arena)) {
                arenas.reset(arena, new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean success) {
                        Debug.log(DebugCategory.ARENA, "Arena reset after a match: {}", success);
                    }
                });
            }
        }
        Debug.log(DebugCategory.MATCH, "Cleaned up match {}", match.identifier());
    }

    // ------------------------------------------------------------- spectating

    /** Registers a spectator for a match, called by the spectator system after it applied its state. */
    public boolean addSpectator(Match match, Player spectator) {
        if (match == null || spectator == null || !match.isLive()) {
            return false;
        }
        if (match.isParticipant(spectator.getUniqueId())) {
            return false;
        }
        if (!match.addSpectator(spectator.getUniqueId())) {
            return false;
        }
        spectatorToMatch.put(spectator.getUniqueId(), match.id());
        match.refreshVisibility();
        return true;
    }

    /** Removes a spectator from a match and forgets the lookup. */
    public boolean removeSpectator(Match match, Player spectator) {
        if (spectator == null) {
            return false;
        }
        spectatorToMatch.remove(spectator.getUniqueId());
        if (match == null) {
            return false;
        }
        boolean removed = match.removeSpectator(spectator.getUniqueId());
        if (removed) {
            match.refreshVisibility();
        }
        return removed;
    }

    public void stopSpectating(UUID uuid) {
        UUID matchId = spectatorToMatch.remove(uuid);
        if (matchId == null) {
            return;
        }
        Match match = matches.get(matchId);
        if (match != null) {
            match.removeSpectator(uuid);
            match.refreshVisibility();
        }
    }

    // ------------------------------------------------------------------- lookup

    /** Live matches of one kit, used by the spectate menu. */
    public List<Match> matchesOf(Kit kit) {
        List<Match> result = new ArrayList<Match>();
        if (kit == null) {
            return result;
        }
        for (Match match : matches.values()) {
            if (match.isLive() && kit.equals(match.kit())) {
                result.add(match);
            }
        }
        return result;
    }

    /** Live matches a player may spectate, honouring the request settings of the match. */
    public List<Match> spectatable() {
        List<Match> result = new ArrayList<Match>();
        for (Match match : matches.values()) {
            if (match.isLive() && match.aliveCount() > 0) {
                result.add(match);
            }
        }
        return result;
    }

    /** Live matches that use an arena, used by the arena status command. */
    public List<Match> matchesIn(Arena arena) {
        List<Match> result = new ArrayList<Match>();
        if (arena == null) {
            return result;
        }
        for (Match match : matches.values()) {
            if (arena.equals(match.arena())) {
                result.add(match);
            }
        }
        return result;
    }

    public int participantCount() {
        return participantToMatch.size();
    }

    public int spectatorCount() {
        return spectatorToMatch.size();
    }

    private static UUID parseId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

}
