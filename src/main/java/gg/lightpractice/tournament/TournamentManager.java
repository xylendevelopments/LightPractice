package gg.lightpractice.tournament;

import gg.lightpractice.api.TournamentService;
import gg.lightpractice.api.event.LightPracticeTournamentEndEvent;
import gg.lightpractice.api.event.LightPracticeTournamentStartEvent;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.repository.TournamentRepository;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.model.TournamentRecord;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Single slot tournament host.
 *
 * <p>A tournament gathers entries, builds a bracket and then plays round after round through the normal
 * match engine: every pairing becomes a {@link MatchType#TOURNAMENT} match, and the winners of a round
 * are re-paired until one team is left. One central task drives the countdown, the launching of pending
 * pairings and the round transitions, so a tournament never needs its own schedulers.</p>
 */
public final class TournamentManager implements TournamentService, LightService {

    private final PluginCore core;
    private final TournamentRepository repository;
    private final ConcurrentHashMap<UUID, String> entered = new ConcurrentHashMap<UUID, String>();
    private volatile Tournament active;
    private volatile Tournament completed;
    private BukkitTask tickTask;
    private long lastAnnouncement;

    private boolean enabled = true;
    private int joinSeconds = 60;
    private int startCountdown = 10;
    private int minPlayers = 4;
    private int maxPlayers = 64;
    private int maxTeamSize = 4;
    private String defaultKit = "nodebuff";
    private String arenaName = "";
    private int announceInterval = 30;
    private long winCoins = 500L;
    private long winExperience = 250L;
    private int matchCountdown = 5;
    private int maxLaunchAttempts = 12;
    private int launchesPerTick = 4;
    private boolean allowLeaveWhileRunning;
    private boolean saveRecords = true;

    public TournamentManager(PluginCore core, TournamentRepository repository) {
        this.core = core;
        this.repository = repository;
    }

    @Override
    public String name() {
        return "tournaments";
    }

    @Override
    public int startupOrder() {
        return 66;
    }

    @Override
    public void onLoad() {
        reload();
    }

    @Override
    public void onEnable() {
        reload();
        tickTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
        cleanupUnfinished();
    }

    @Override
    public void onDisable() {
        Tournament tournament = active;
        if (tournament != null) {
            stop(null, "the plugin was disabled");
        }
        core.tasks().cancel(tickTask);
        tickTask = null;
        entered.clear();
        completed = null;
    }

    @Override
    public void onReload() {
        reload();
    }

    public void reload() {
        ConfigFile file = core.configs().tournaments();
        file.reload();
        this.enabled = file.getBoolean("settings.enabled", true);
        this.joinSeconds = Math.max(5, file.getInt("settings.join-seconds", 60));
        this.startCountdown = Math.max(0, file.getInt("settings.start-countdown-seconds", 10));
        this.minPlayers = Math.max(2, file.getInt("settings.min-players", 4));
        this.maxPlayers = Math.max(minPlayers, file.getInt("settings.max-players", 64));
        this.maxTeamSize = Math.max(1, file.getInt("settings.max-team-size", 4));
        this.defaultKit = file.getString("settings.default-kit", "nodebuff");
        this.arenaName = file.getString("settings.arena", "");
        this.announceInterval = Math.max(5, file.getInt("settings.announce-interval-seconds", 30));
        this.winCoins = Math.max(0L, file.getLong("rewards.win-coins", 500L));
        this.winExperience = Math.max(0L, file.getLong("rewards.win-experience", 250L));
        this.matchCountdown = Math.max(0, file.getInt("settings.match-countdown-seconds", 5));
        this.maxLaunchAttempts = Math.max(1, file.getInt("settings.max-launch-attempts", 12));
        this.launchesPerTick = Math.max(1, file.getInt("settings.max-matches-per-tick", 4));
        this.allowLeaveWhileRunning = file.getBoolean("settings.allow-leave-while-running", false);
        this.saveRecords = file.getBoolean("settings.save-records", true);
        Debug.log(DebugCategory.TOURNAMENT, "Tournaments {}: kit {}, {}-{} players, {}s gathering",
                enabled ? "enabled" : "disabled", defaultKit, minPlayers, maxPlayers, joinSeconds);
    }

    // ---------------------------------------------------------- TournamentService

    @Override
    public Tournament active() {
        return active;
    }

    /** Tournament that finished or was cancelled last, kept for menus and placeholders. */
    public Tournament completed() {
        return completed;
    }

    @Override
    public boolean isRunning() {
        Tournament tournament = active;
        return tournament != null && tournament.state() == TournamentState.RUNNING;
    }

    /** True while a tournament accepts entries. */
    public boolean isGathering() {
        Tournament tournament = active;
        return tournament != null && tournament.state() == TournamentState.GATHERING;
    }

    @Override
    public boolean create(Player host, String kitId, int size) {
        if (!enabled) {
            core.messages().send(host, "tournament.disabled");
            return false;
        }
        if (active != null) {
            core.messages().send(host, "tournament.already-running",
                    "{kit}", kitOf(active).displayName(), "{host}", active.hostName());
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(kitId == null || kitId.isEmpty() ? defaultKit : kitId);
        if (kit == null) {
            core.messages().send(host, "tournament.kit-missing", "{kit}", String.valueOf(kitId));
            return false;
        }
        if (!kit.enabled()) {
            core.messages().send(host, "tournament.kit-disabled", "{kit}", kit.displayName());
            return false;
        }
        int teamSize = Math.max(1, Math.min(maxTeamSize, size));
        PlayerManager players = core.optional(PlayerManager.class);
        if (host != null && players != null && !players.isIdle(host.getUniqueId())) {
            core.messages().send(host, "tournament.busy");
            return false;
        }
        if (minPlayers > maxPlayers) {
            core.messages().send(host, "tournament.misconfigured");
            Debug.warn(DebugCategory.TOURNAMENT, "tournaments.yml asks for {} minimum but {} maximum players",
                    minPlayers, maxPlayers);
            return false;
        }
        String id = "t-" + Long.toString(System.currentTimeMillis(), 36);
        Tournament tournament = new Tournament(id, kit.id(), arenaName,
                host == null ? null : host.getUniqueId(), host == null ? "console" : host.getName(),
                teamSize, minPlayers, maxPlayers);
        tournament.startAt(System.currentTimeMillis() + joinSeconds * 1000L);
        this.active = tournament;
        this.lastAnnouncement = System.currentTimeMillis();
        core.messages().broadcast("tournament.created",
                "{kit}", kit.displayName(),
                "{host}", tournament.hostName(),
                "{team-size}", String.valueOf(teamSize),
                "{seconds}", String.valueOf(joinSeconds),
                "{min}", String.valueOf(minPlayers),
                "{max}", String.valueOf(maxPlayers));
        tournament.log("created by " + tournament.hostName() + " with kit " + kit.id());
        save(tournament);
        Debug.log(DebugCategory.TOURNAMENT, "Tournament {} created by {} (kit {}, {}v{})", id,
                tournament.hostName(), kit.id(), teamSize, teamSize);
        return true;
    }

    @Override
    public boolean join(Player player) {
        Tournament tournament = active;
        if (player == null) {
            return false;
        }
        if (tournament == null) {
            core.messages().send(player, "tournament.none");
            return false;
        }
        if (!tournament.state().joinable()) {
            core.messages().send(player, "tournament.not-joinable", "{state}", tournament.state().configKey());
            return false;
        }
        if (tournament.isParticipant(player.getUniqueId())) {
            core.messages().send(player, "tournament.already-joined");
            return false;
        }
        if (tournament.isFull()) {
            core.messages().send(player, "tournament.full", "{max}", String.valueOf(tournament.maxPlayers()));
            return false;
        }
        gg.lightpractice.api.BanService bans = core.optional(gg.lightpractice.api.BanService.class);
        if (bans != null && bans.isBanned(player.getUniqueId())) {
            core.messages().send(player, "tournament.banned");
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null && (session.inMatch() || session.inQueue() || session.inTournament())) {
            core.messages().send(player, "tournament.busy");
            return false;
        }
        if (!tournament.join(player.getUniqueId())) {
            core.messages().send(player, "tournament.join-failed");
            return false;
        }
        entered.put(player.getUniqueId(), tournament.id());
        if (session != null) {
            session.tournamentId(tournament.id());
        }
        core.messages().send(player, "tournament.joined",
                "{kit}", kitOf(tournament).displayName(),
                "{size}", String.valueOf(tournament.size()),
                "{min}", String.valueOf(tournament.minPlayers()),
                "{max}", String.valueOf(tournament.maxPlayers()),
                "{seconds}", String.valueOf(Math.max(0L, tournament.remainingMillis() / 1000L)));
        announce(tournament, "tournament.entry",
                "{player}", player.getName(),
                "{size}", String.valueOf(tournament.size()),
                "{min}", String.valueOf(tournament.minPlayers()),
                "{max}", String.valueOf(tournament.maxPlayers()));
        Debug.log(DebugCategory.TOURNAMENT, "{} entered tournament {} ({} player(s))", player.getName(),
                tournament.id(), tournament.size());
        return true;
    }

    @Override
    public boolean leave(Player player) {
        Tournament tournament = active;
        if (player == null || tournament == null || !tournament.isParticipant(player.getUniqueId())) {
            core.messages().send(player, "tournament.not-entered");
            return false;
        }
        if (tournament.state().left() && !allowLeaveWhileRunning) {
            core.messages().send(player, "tournament.cannot-leave");
            return false;
        }
        Bracket bracket = tournament.bracketOf(player.getUniqueId());
        if (bracket != null && !bracket.finished() && bracket.matchId() != null) {
            MatchManager matches = core.optional(MatchManager.class);
            Match match = matches == null ? null : matches.get(bracket.matchId());
            if (match != null && match.isLive()) {
                matches.forfeit(player.getUniqueId(), EndCause.SURRENDER);
            }
        }
        tournament.leave(player.getUniqueId());
        entered.remove(player.getUniqueId());
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null) {
            session.tournamentId(null);
        }
        core.messages().send(player, "tournament.left");
        announce(tournament, "tournament.exit", "{player}", player.getName(),
                "{size}", String.valueOf(tournament.size()));
        return true;
    }

    @Override
    public boolean start(Player starter) {
        Tournament tournament = active;
        if (tournament == null) {
            core.messages().send(starter, "tournament.none");
            return false;
        }
        if (tournament.state() != TournamentState.GATHERING) {
            core.messages().send(starter, "tournament.already-started",
                    "{state}", tournament.state().configKey());
            return false;
        }
        if (!tournament.hasEnoughPlayers()) {
            core.messages().send(starter, "tournament.not-enough-players",
                "{size}", String.valueOf(tournament.size()),
                "{min}", String.valueOf(tournament.minPlayers()));
            return false;
        }
        tournament.state(TournamentState.STARTING);
        tournament.countdownSeconds(startCountdown);
        core.messages().send(starter, "tournament.starting-now");
        core.messages().broadcast("tournament.starting",
                "{kit}", kitOf(tournament).displayName(),
                "{size}", String.valueOf(tournament.size()),
                "{seconds}", String.valueOf(startCountdown));
        save(tournament);
        return true;
    }

    @Override
    public boolean stop(Player stopper, String reason) {
        Tournament tournament = active;
        if (tournament == null) {
            core.messages().send(stopper, "tournament.none");
            return false;
        }
        String cause = reason == null || reason.trim().isEmpty() ? "stopped by an administrator" : reason.trim();
        tournament.endReason(cause);
        tournament.state(TournamentState.CANCELLED);
        abortMatches(tournament);
        releaseEntries(tournament);
        core.messages().broadcast("tournament.cancelled",
                "{kit}", kitOf(tournament).displayName(),
                "{reason}", cause,
                "{size}", String.valueOf(tournament.size()));
        if (stopper != null) {
            core.messages().send(stopper, "tournament.stopped", "{reason}", cause);
        }
        tournament.log("cancelled: " + cause);
        save(tournament);
        Bukkit.getPluginManager().callEvent(new LightPracticeTournamentEndEvent(tournament, null));
        this.completed = tournament;
        this.active = null;
        Debug.log(DebugCategory.TOURNAMENT, "Tournament {} cancelled: {}", tournament.id(), cause);
        return true;
    }

    @Override
    public void broadcast(Tournament tournament) {
        if (tournament == null) {
            return;
        }
        for (Player player : receivers(tournament)) {
            core.messages().send(player, "tournament.info",
                    "{kit}", kitOf(tournament).displayName(),
                    "{host}", tournament.hostName(),
                    "{state}", tournament.state().configKey(),
                    "{size}", String.valueOf(tournament.size()),
                    "{min}", String.valueOf(tournament.minPlayers()),
                    "{max}", String.valueOf(tournament.maxPlayers()),
                    "{round}", String.valueOf(tournament.round()),
                    "{seconds}", String.valueOf(Math.max(0L, tournament.remainingMillis() / 1000L)));
            core.messages().sendList(player, "tournament.bracket",
                    "{round}", String.valueOf(tournament.round()),
                    "{brackets}", String.valueOf(tournament.brackets().size()));
        }
    }

    // ----------------------------------------------------------------- scheduling

    /** Central tournament task, runs once a second while the plugin is enabled. */
    private void tick() {
        Tournament tournament = active;
        if (tournament == null) {
            return;
        }
        try {
            switch (tournament.state()) {
                case GATHERING:
                    tickGathering(tournament);
                    break;
                case STARTING:
                    tickStarting(tournament);
                    break;
                case RUNNING:
                    launchPending(tournament);
                    checkRound(tournament);
                    break;
                default:
                    this.active = null;
                    break;
            }
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.TOURNAMENT, "The tournament task of " + tournament.id() + " failed", throwable);
        }
    }

    private void tickGathering(Tournament tournament) {
        long now = System.currentTimeMillis();
        if (now - lastAnnouncement >= announceInterval * 1000L) {
            lastAnnouncement = now;
            core.messages().broadcast("tournament.reminder",
                    "{kit}", kitOf(tournament).displayName(),
                    "{size}", String.valueOf(tournament.size()),
                    "{min}", String.valueOf(tournament.minPlayers()),
                    "{seconds}", String.valueOf(Math.max(0L, tournament.remainingMillis() / 1000L)));
        }
        if (tournament.remainingMillis() > 0L) {
            return;
        }
        if (!tournament.hasEnoughPlayers()) {
            stop(null, "not enough players joined");
            return;
        }
        tournament.state(TournamentState.STARTING);
        tournament.countdownSeconds(startCountdown);
        core.messages().broadcast("tournament.starting",
                "{kit}", kitOf(tournament).displayName(),
                "{size}", String.valueOf(tournament.size()),
                "{seconds}", String.valueOf(startCountdown));
        save(tournament);
    }

    private void tickStarting(Tournament tournament) {
        int seconds = tournament.countdownSeconds();
        if (seconds > 0) {
            if (seconds <= 5 || seconds % 5 == 0) {
                announce(tournament, "tournament.countdown", "{seconds}", String.valueOf(seconds));
            }
            tournament.countdownSeconds(seconds - 1);
            return;
        }
        beginRounds(tournament);
    }

    private void beginRounds(Tournament tournament) {
        dropOffline(tournament);
        if (!tournament.hasEnoughPlayers()) {
            stop(null, "not enough players remained online");
            return;
        }
        Bukkit.getPluginManager().callEvent(new LightPracticeTournamentStartEvent(tournament));
        int brackets = tournament.begin();
        tournament.log("round 1 started with " + brackets + " pairing(s)");
        core.messages().broadcast("tournament.round",
                "{round}", String.valueOf(tournament.round()),
                "{brackets}", String.valueOf(brackets),
                "{players}", String.valueOf(tournament.size()),
                "{kit}", kitOf(tournament).displayName());
        save(tournament);
        launchPending(tournament);
    }

    /** Drops entries of players who disconnected while gathering. */
    private void dropOffline(Tournament tournament) {
        List<UUID> offline = new ArrayList<UUID>();
        for (UUID uuid : tournament.participantsList()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                offline.add(uuid);
            }
        }
        for (UUID uuid : offline) {
            tournament.leave(uuid);
            entered.remove(uuid);
            Debug.log(DebugCategory.TOURNAMENT, "Removed the offline entry {} from tournament {}", uuid,
                    tournament.id());
        }
        if (!offline.isEmpty()) {
            announce(tournament, "tournament.offline-removed", "{count}", String.valueOf(offline.size()),
                    "{size}", String.valueOf(tournament.size()));
        }
    }

    // -------------------------------------------------------------------- bracket

    /** Launches pairings of the current round that have no match yet. */
    private void launchPending(Tournament tournament) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            Debug.warn(DebugCategory.TOURNAMENT, "No match manager registered, tournament {} cannot play",
                    tournament.id());
            return;
        }
        Kit kit = kitOf(tournament);
        Arena arena = resolveArena(tournament, kit);
        int launched = 0;
        for (Bracket bracket : tournament.pendingBrackets()) {
            if (launched >= launchesPerTick) {
                return;
            }
            if (bracket.matchId() != null && matches.get(bracket.matchId()) != null) {
                continue;
            }
            List<UUID> home = onlineOnly(bracket.home());
            List<UUID> away = onlineOnly(bracket.away());
            if (home.isEmpty() && away.isEmpty()) {
                bracket.finish(Collections.<UUID>emptyList());
                continue;
            }
            if (home.isEmpty() || away.isEmpty()) {
                List<UUID> walkover = home.isEmpty() ? away : home;
                bracket.finish(walkover);
                tournament.log("round " + tournament.round() + ": walkover for " + namesOf(walkover));
                announce(tournament, "tournament.walkover", "{players}", namesOf(walkover),
                        "{round}", String.valueOf(tournament.round()));
                continue;
            }
            MatchRequest request = new MatchRequest(kit, MatchType.TOURNAMENT, false).source("tournament");
            request.countdownSeconds(matchCountdown);
            request.resetArena(true);
            if (arena != null) {
                request.arena(arena);
            }
            request.addTeam("home", namesOf(home), home).spawnKey(Arena.SPAWN_RED);
            request.addTeam("away", namesOf(away), away).spawnKey(Arena.SPAWN_BLUE);
            Match match = matches.startMatch(request);
            if (match != null) {
                bracket.matchId(match.identifier());
                bracket.launched(true);
                launched++;
                Debug.log(DebugCategory.TOURNAMENT, "Tournament {} round {} pairing {} started as match {}",
                        tournament.id(), tournament.round(), bracket.index(), match.identifier());
                continue;
            }
            bracket.attempted();
            if (bracket.attempts() >= maxLaunchAttempts) {
                bracket.finish(home);
                tournament.log("round " + tournament.round() + ": pairing " + bracket.index()
                        + " could not start, " + namesOf(home) + " advances");
                Debug.warn(DebugCategory.TOURNAMENT, "Tournament {} pairing {} gave up after {} attempts",
                        tournament.id(), bracket.index(), bracket.attempts());
                announce(tournament, "tournament.bracket-failed", "{players}", namesOf(home),
                        "{round}", String.valueOf(tournament.round()));
            }
        }
    }

    private Arena resolveArena(Tournament tournament, Kit kit) {
        ArenaManager arenas = core.optional(ArenaManager.class);
        if (arenas == null) {
            return null;
        }
        String name = tournament.arenaId();
        if (name != null && !name.isEmpty()) {
            Arena configured = arenas.get(name);
            if (configured != null && configured.enabled()) {
                return configured;
            }
            Debug.log(DebugCategory.ARENA, "Tournament arena '{}' is unknown, picking a free one", name);
        }
        return arenas.findAvailable(kit);
    }

    /** Called by the match engine when a match ends so the bracket can advance. */
    public void handleMatchEnd(Match match) {
        Tournament tournament = active;
        if (tournament == null || match == null || tournament.state() != TournamentState.RUNNING) {
            return;
        }
        Bracket bracket = tournament.bracketOfMatch(match.identifier());
        if (bracket == null || bracket.finished()) {
            return;
        }
        MatchTeam winningTeam = match.winningTeam();
        List<UUID> winners = new ArrayList<UUID>();
        if (winningTeam != null) {
            for (MatchPlayer member : winningTeam.members()) {
                winners.add(member.uuid());
            }
        }
        bracket.finish(onlineOnly(winners));
        tournament.log("round " + tournament.round() + ": " + namesOf(bracket.survivors()) + " advanced");
        announce(tournament, "tournament.bracket-won",
                "{players}", namesOf(bracket.survivors()),
                "{round}", String.valueOf(tournament.round()));
        save(tournament);
        checkRound(tournament);
    }

    /** Advances the tournament when every pairing of the round is decided. */
    private void checkRound(Tournament tournament) {
        if (tournament.state() != TournamentState.RUNNING || !tournament.allBracketsFinished()) {
            return;
        }
        List<UUID> survivors = onlineOnly(tournament.survivors());
        if (survivors.size() <= tournament.teamSize()) {
            finish(tournament, survivors);
            return;
        }
        if (survivors.isEmpty()) {
            stop(null, "every participant disconnected");
            return;
        }
        int brackets = tournament.buildRound(survivors);
        tournament.log("round " + tournament.round() + " started with " + brackets + " pairing(s)");
        core.messages().broadcast("tournament.round",
                "{round}", String.valueOf(tournament.round()),
                "{brackets}", String.valueOf(brackets),
                "{players}", String.valueOf(survivors.size()),
                "{kit}", kitOf(tournament).displayName());
        save(tournament);
        launchPending(tournament);
    }

    private void finish(Tournament tournament, List<UUID> winners) {
        tournament.state(TournamentState.FINISHED);
        UUID winner = winners == null || winners.isEmpty() ? null : winners.get(0);
        tournament.winner(winner);
        tournament.log("finished after " + tournament.round() + " round(s)");
        reward(tournament, winners);
        core.messages().broadcast("tournament.winner",
                "{winner}", namesOf(winners),
                "{kit}", kitOf(tournament).displayName(),
                "{rounds}", String.valueOf(tournament.round()),
                "{players}", String.valueOf(tournament.size()));
        Player winnerPlayer = winner == null ? null : Bukkit.getPlayer(winner);
        if (winnerPlayer != null) {
            core.messages().title(winnerPlayer, "tournament.winner-title", "tournament.winner-subtitle",
                    "{kit}", kitOf(tournament).displayName(),
                    "{rounds}", String.valueOf(tournament.round()));
        }
        save(tournament);
        Bukkit.getPluginManager().callEvent(new LightPracticeTournamentEndEvent(tournament, winner));
        releaseEntries(tournament);
        this.completed = tournament;
        this.active = null;
        Debug.log(DebugCategory.TOURNAMENT, "Tournament {} finished, winner {}", tournament.id(), namesOf(winners));
    }

    /** Pays the configured tournament reward to every member of the winning side. */
    private void reward(Tournament tournament, List<UUID> winners) {
        if (winners == null || winners.isEmpty() || (winCoins <= 0L && winExperience <= 0L)) {
            return;
        }
        ProfileManager profiles = core.optional(ProfileManager.class);
        for (UUID uuid : winners) {
            Profile profile = profiles == null ? null : profiles.getProfile(uuid);
            if (profile == null) {
                continue;
            }
            if (winCoins > 0L) {
                profile.addCoins(winCoins);
            }
            if (winExperience > 0L) {
                profile.addExperience(winExperience);
            }
            profiles.save(profile);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                core.messages().send(player, "tournament.reward",
                        "{coins}", String.valueOf(winCoins),
                        "{experience}", String.valueOf(winExperience),
                        "{kit}", kitOf(tournament).displayName());
            }
        }
    }

    // ------------------------------------------------------------------- helpers

    /** Ends matches that are still running because the tournament was stopped. */
    private void abortMatches(Tournament tournament) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return;
        }
        for (Bracket bracket : tournament.brackets()) {
            if (bracket.matchId() == null || bracket.finished()) {
                continue;
            }
            Match match = matches.get(bracket.matchId());
            if (match != null && !match.isFinished()) {
                matches.end(match, null, EndCause.ABANDONED);
            }
        }
    }

    private void releaseEntries(Tournament tournament) {
        PlayerManager players = core.optional(PlayerManager.class);
        for (UUID uuid : tournament.participantsList()) {
            entered.remove(uuid);
            LightPlayer session = players == null ? null : players.get(uuid);
            if (session != null) {
                session.tournamentId(null);
            }
        }
        List<UUID> stale = new ArrayList<UUID>();
        for (java.util.Map.Entry<UUID, String> entry : entered.entrySet()) {
            if (tournament.id().equals(entry.getValue())) {
                stale.add(entry.getKey());
            }
        }
        for (UUID uuid : stale) {
            entered.remove(uuid);
        }
    }

    /** Announces a message to everybody entered in the tournament plus its host. */
    private void announce(Tournament tournament, String key, String... replacements) {
        core.messages().broadcastTo(receivers(tournament), key, replacements);
    }

    private List<Player> receivers(Tournament tournament) {
        List<Player> receivers = new ArrayList<Player>();
        if (tournament == null) {
            return receivers;
        }
        for (UUID uuid : tournament.participantsList()) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !receivers.contains(player)) {
                receivers.add(player);
            }
        }
        Player host = tournament.host() == null ? null : Bukkit.getPlayer(tournament.host());
        if (host != null && host.isOnline() && !receivers.contains(host)) {
            receivers.add(host);
        }
        return receivers;
    }

    private List<UUID> onlineOnly(Collection<UUID> uuids) {
        List<UUID> online = new ArrayList<UUID>();
        if (uuids == null) {
            return online;
        }
        for (UUID uuid : uuids) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(uuid);
            }
        }
        return online;
    }

    /** Comma separated, coloured names of a side, used in announcements. */
    public String namesOf(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "nobody";
        }
        List<String> names = new ArrayList<String>();
        for (UUID uuid : uuids) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
                continue;
            }
            Profile profile = profile(uuid);
            names.add(profile == null || profile.name() == null ? "unknown" : profile.name());
        }
        return Text.color("&a" + Text.join(names, "&7, &a"));
    }

    private Profile profile(UUID uuid) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || uuid == null ? null : profiles.getProfile(uuid);
    }

    public Kit kitOf(Tournament tournament) {
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null || tournament == null ? null : kits.get(tournament.kitId());
        if (kit != null) {
            return kit;
        }
        Kit fallback = kits == null ? null : kits.get(defaultKit);
        return fallback == null ? new Kit("unknown") : fallback;
    }

    private void save(final Tournament tournament) {
        if (!saveRecords || tournament == null || repository == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            return;
        }
        final TournamentRecord record = tournament.record();
        database.onMain(repository.save(record), new Consumer<Boolean>() {
            @Override
            public void accept(Boolean saved) {
                Debug.log(DebugCategory.DATABASE, "Tournament {} stored: {}", record.id(), saved);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not store tournament " + record.id(), error);
            }
        });
    }

    /**
     * Marks tournament documents left unfinished by a crash or a restart as cancelled.
     *
     * <p>Without this a restart would leave brackets in the database that look like they are still
     * running, and every report about past tournaments would be wrong.</p>
     */
    private void cleanupUnfinished() {
        if (!saveRecords || repository == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            return;
        }
        database.onMain(repository.loadUnfinished(), new Consumer<List<TournamentRecord>>() {
            @Override
            public void accept(List<TournamentRecord> records) {
                if (records == null || records.isEmpty()) {
                    return;
                }
                int fixed = 0;
                for (TournamentRecord record : records) {
                    if (record == null || record.finished()) {
                        continue;
                    }
                    TournamentRecord cancelled = new TournamentRecord(record.id(), record.kitId(),
                            record.host(), record.hostName(), TournamentState.CANCELLED.name(),
                            record.startedAt(), System.currentTimeMillis(), record.size(),
                            record.participants(), null);
                    repository.save(cancelled);
                    fixed++;
                }
                if (fixed > 0) {
                    Debug.log(DebugCategory.DATABASE, "Marked {} unfinished tournament record(s) cancelled",
                            fixed);
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not clean up unfinished tournaments", error);
            }
        });
    }

    // --------------------------------------------------------------- introspection

    public boolean enabled() {
        return enabled;
    }

    /** True when a player is entered in the active tournament. */
    public boolean isEntered(UUID uuid) {
        Tournament tournament = active;
        return uuid != null && tournament != null && tournament.isParticipant(uuid);
    }

    public int enteredCount() {
        Tournament tournament = active;
        return tournament == null ? 0 : tournament.size();
    }

    public String describe(Tournament tournament) {
        if (tournament == null) {
            return "no tournament";
        }
        return tournament.state().configKey() + " " + tournament.size() + "/" + tournament.maxPlayers()
                + " round " + tournament.round();
    }

    public int joinSeconds() {
        return joinSeconds;
    }

    /** Kits a tournament can be hosted with, used by the tournament menu. */
    public List<Kit> kitsForHosting() {
        KitManager kits = core.optional(KitManager.class);
        List<Kit> result = new ArrayList<Kit>();
        if (kits == null) {
            return result;
        }
        for (Kit kit : kits.enabled()) {
            if (kit != null) {
                result.add(kit);
            }
        }
        return result;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int maxTeamSize() {
        return maxTeamSize;
    }

    public String defaultKit() {
        return defaultKit;
    }

    /** Placeholder value used by menus: how long entries stay open. */
    public String remaining(Tournament tournament) {
        if (tournament == null) {
            return "";
        }
        return Text.duration(tournament.remainingMillis());
    }

    /** Convenience overload used by the GUI layer. */
    public boolean stop(String reason) {
        return stop(null, reason);
    }

}
