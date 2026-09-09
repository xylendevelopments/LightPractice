package gg.lightpractice.match;

import gg.lightpractice.api.event.LightPracticeMatchDeathEvent;
import gg.lightpractice.api.event.LightPracticeMatchEndEvent;
import gg.lightpractice.api.event.LightPracticeMatchStartEvent;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.bot.BotManager;
import gg.lightpractice.arena.ArenaBounds;
import gg.lightpractice.config.Messages;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.player.InventorySnapshot;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.player.PlayerState;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Tasks;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single running match.
 *
 * <p>The match owns the participants, the teams, the arena reservation and the rule set of its kit. It
 * answers the questions listeners ask ("may this block be broken", "is this damage allowed", "does this
 * death eliminate") and drives the lifecycle: prepare, countdown, fight, end, cleanup. Everything a kit
 * does differently comes from {@link KitRuleSet}, so this class holds no kit specific branches.</p>
 */
public final class Match {

    private final UUID id = UUID.randomUUID();
    private final PluginCore core;
    private final MatchManager manager;
    private final Kit kit;
    private final MatchType type;
    private final boolean ranked;
    private final Arena arena;
    private final String source;
    private final List<MatchTeam> teams = new ArrayList<MatchTeam>();
    private final List<MatchPlayer> participants = new ArrayList<MatchPlayer>();
    private final Map<UUID, MatchPlayer> byUuid = new ConcurrentHashMap<UUID, MatchPlayer>();
    private final Set<UUID> spectators = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> internalDamage = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, Long> offlineSince = new ConcurrentHashMap<UUID, Long>();
    private final List<String> problems = new ArrayList<String>();
    private final long createdAt = System.currentTimeMillis();
    private final String countdownSound;
    private final String startSound;
    private final String endSound;
    private final long disconnectGraceMillis;
    private final int respawnDelayTicks;
    private volatile MatchState state = MatchState.PREPARING;
    private volatile long startMillis;
    private volatile long endMillis;
    private volatile MatchTeam winningTeam;
    private volatile EndCause endCause;
    private int remainingCountdown;

    public Match(PluginCore core, MatchManager manager, MatchRequest request, Arena arena) {
        this.core = core;
        this.manager = manager;
        this.kit = request == null ? null : request.kit();
        this.type = request == null ? MatchType.SOLO : request.type();
        this.ranked = request != null && request.ranked();
        this.source = request == null ? "command" : request.source();
        this.arena = arena;
        this.countdownSound = core.configs().config().getString("match.countdown-sound", "NOTE_PLING");
        this.startSound = core.configs().config().getString("match.start-sound", "LEVEL_UP");
        this.endSound = core.configs().config().getString("match.end-sound", "ENDERDRAGON_GROWL");
        this.disconnectGraceMillis = Math.max(0L,
                core.configs().config().getLong("match.disconnect-grace-seconds", 30L) * 1000L);
        this.respawnDelayTicks = Math.max(0, core.configs().config().getInt("match.respawn-delay-ticks", 40));
        this.remainingCountdown = request == null || request.countdownSeconds() < 0
                ? core.configs().config().getInt("match.countdown-seconds", 3)
                : request.countdownSeconds();
    }

    // ------------------------------------------------------------------ identity

    public UUID id() {
        return id;
    }

    /** String form of the id, stored on player sessions and in match documents. */
    public String identifier() {
        return id.toString();
    }

    public Kit kit() {
        return kit;
    }

    public MatchType type() {
        return type;
    }

    public boolean ranked() {
        return ranked;
    }

    public Arena arena() {
        return arena;
    }

    public String source() {
        return source;
    }

    public MatchState state() {
        return state;
    }

    public boolean isLive() {
        return state.isLive();
    }

    public boolean isRunning() {
        return state == MatchState.IN_PROGRESS;
    }

    public boolean isFinished() {
        return state.isFinished();
    }

    public EndCause endCause() {
        return endCause;
    }

    public MatchTeam winningTeam() {
        return winningTeam;
    }

    public KitRuleSet rules() {
        return kit == null ? null : kit.ruleSet();
    }

    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    // -------------------------------------------------------------------- people

    public List<MatchTeam> teams() {
        return Collections.unmodifiableList(teams);
    }

    public Collection<MatchPlayer> participants() {
        return Collections.unmodifiableCollection(participants);
    }

    public MatchPlayer participant(UUID uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public MatchPlayer participant(Player player) {
        return player == null ? null : byUuid.get(player.getUniqueId());
    }

    public MatchTeam teamOf(Player player) {
        MatchPlayer participant = participant(player);
        return participant == null ? null : participant.team();
    }

    public MatchTeam teamOf(UUID uuid) {
        MatchPlayer participant = participant(uuid);
        return participant == null ? null : participant.team();
    }

    /** Online participants, including eliminated ones that are spectating their own match. */
    public List<Player> onlinePlayers() {
        List<Player> players = new ArrayList<Player>();
        for (MatchPlayer participant : participants) {
            Player player = participant.player();
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** Online participants that are still fighting. */
    public List<Player> alivePlayers() {
        List<Player> players = new ArrayList<Player>();
        for (MatchPlayer participant : participants) {
            if (participant.eliminated()) {
                continue;
            }
            Player player = participant.player();
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public int aliveCount() {
        int count = 0;
        for (MatchPlayer participant : participants) {
            if (!participant.eliminated()) {
                count++;
            }
        }
        return count;
    }

    public List<MatchTeam> aliveTeams() {
        List<MatchTeam> alive = new ArrayList<MatchTeam>();
        for (MatchTeam team : teams) {
            if (!team.eliminated() && team.aliveCount() > 0) {
                alive.add(team);
            }
        }
        return alive;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid != null && byUuid.containsKey(uuid);
    }

    // --------------------------------------------------------------- spectators

    public Set<UUID> spectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public boolean addSpectator(UUID uuid) {
        return uuid != null && !isParticipant(uuid) && spectators.add(uuid);
    }

    public boolean removeSpectator(UUID uuid) {
        return uuid != null && spectators.remove(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return uuid != null && spectators.contains(uuid);
    }

    /** Names of everyone watching, stored with the match document. */
    public List<String> spectatorNames() {
        List<String> names = new ArrayList<String>();
        for (UUID uuid : spectators) {
            Player player = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
            names.add(player == null ? String.valueOf(uuid) : player.getName());
        }
        return names;
    }

    // --------------------------------------------------------------------- time

    public long createdAt() {
        return createdAt;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    /** Duration of the fight itself, ignoring preparation, in milliseconds. */
    public long durationMillis() {
        if (startMillis <= 0L) {
            return 0L;
        }
        long until = endMillis > 0L ? endMillis : System.currentTimeMillis();
        return Math.max(0L, until - startMillis);
    }

    public long elapsedSeconds() {
        return durationMillis() / 1000L;
    }

    public int remainingCountdown() {
        return remainingCountdown;
    }

    // ----------------------------------------------------------------- lifecycle

    /**
     * Builds the teams and participants of the request and prepares every online player.
     *
     * @return false when the match cannot run, with reasons in {@link #problems()}
     */
    public boolean prepare(MatchRequest request) {
        if (request == null) {
            problems.add("no request");
            return false;
        }
        List<String> invalid = request.problems();
        if (!invalid.isEmpty()) {
            problems.addAll(invalid);
            return false;
        }
        if (type.everyoneAlone()) {
            request.splitPerPlayer();
        }
        PlayerManager players = core.optional(PlayerManager.class);
        KitManager kits = core.optional(KitManager.class);
        int index = 0;
        for (MatchRequest.TeamSpec spec : request.teams()) {
            Location spawn = resolveSpawn(spec, index);
            MatchTeam team = new MatchTeam(spec.name(), spec.displayName(), spec.color(), spawn);
            teams.add(team);
            for (UUID uuid : spec.members()) {
                Player player = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
                String name = player == null ? displayNameOf(uuid) : player.getName();
                MatchPlayer participant = new MatchPlayer(uuid, name);
                team.add(participant);
                participants.add(participant);
                byUuid.put(uuid, participant);
                if (!preparePlayer(participant, team, players, kits)) {
                    problems.add(name + " could not be prepared");
                }
            }
            index++;
        }
        if (arena != null) {
            arena.touch();
        }
        if (!problems.isEmpty()) {
            Debug.log(DebugCategory.MATCH, "Match {} could not be prepared: {}", identifier(), problems);
            return false;
        }
        refreshVisibility();
        Debug.log(DebugCategory.MATCH, "Match {} prepared: {} {} vs {} participant(s) in {}", identifier(),
                kit == null ? "?" : kit.id(), type, participants.size(),
                arena == null ? "no arena" : arena.name());
        return true;
    }

    private Location resolveSpawn(MatchRequest.TeamSpec spec, int index) {
        if (arena == null) {
            return null;
        }
        if (spec.spawnKey() != null && arena.hasSpawn(spec.spawnKey())) {
            return arena.spawn(spec.spawnKey());
        }
        if (arena.hasSpawn(Arena.SPAWN_RED) && index == 0) {
            return arena.spawn(Arena.SPAWN_RED);
        }
        if (arena.hasSpawn(Arena.SPAWN_BLUE) && index == 1) {
            return arena.spawn(Arena.SPAWN_BLUE);
        }
        return arena.spawnFor(index);
    }

    /**
     * Name of a participant that has no online player, which is a practice bot or a stale entry.
     */
    private String displayNameOf(UUID uuid) {
        BotManager bots = core.optional(BotManager.class);
        if (bots != null) {
            String name = bots.nameOf(uuid);
            if (name != null) {
                return name;
            }
        }
        return "unknown";
    }

    /** Moves one player into the match: state, snapshot, kit, spawn and rules. */
    private boolean preparePlayer(MatchPlayer participant, MatchTeam team, PlayerManager players, KitManager kits) {
        Player player = participant.player();
        if (player == null) {
            BotManager bots = core.optional(BotManager.class);
            if (bots != null && bots.isBot(participant.uuid())) {
                // a bot has no inventory, state or snapshot, it only needs a position in the arena
                bots.place(participant.uuid(), team.spawn());
                return true;
            }
            problems.add(participant.name() + " is offline");
            return false;
        }
        LightPlayer session = players == null ? null : players.get(player);
        if (session == null) {
            session = players == null ? null : players.register(player);
        }
        if (session != null) {
            if (!players.setState(session, PlayerState.MATCH)) {
                return false;
            }
            session.matchId(identifier());
            session.snapshot(InventorySnapshot.capture(player));
            session.clearCombat();
            session.resetCombo();
        }
        if (kits != null && kit != null) {
            kits.applyKit(player, kit, true);
        } else if (kit != null) {
            kit.apply(player, null);
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector());
        for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        Location spawn = team.spawn();
        if (spawn != null && spawn.getWorld() != null) {
            player.teleport(spawn.clone().add(0.5D, 0.0D, 0.5D));
            participant.respawnPoint(spawn.clone().add(0.5D, 0.0D, 0.5D));
        } else {
            problems.add(team.name() + " has no usable spawn");
            return false;
        }
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onPlayerEnter(this, player);
        }
        return true;
    }

    /** Starts the countdown; a countdown of zero begins the fight immediately. */
    public void beginCountdown() {
        if (state != MatchState.PREPARING || !state.canTransitionTo(MatchState.STARTING)) {
            return;
        }
        if (remainingCountdown <= 0) {
            start();
            return;
        }
        state = MatchState.STARTING;
        broadcast("match.starting", "{seconds}", String.valueOf(remainingCountdown));
    }

    /** Called once per second by the central match task. */
    public void tick() {
        if (state == MatchState.STARTING) {
            if (remainingCountdown > 0) {
                broadcast("match.countdown", "{seconds}", String.valueOf(remainingCountdown));
                sound(countdownSound, 1.0F, 1.0F);
                remainingCountdown--;
                if (remainingCountdown <= 0) {
                    start();
                }
            } else {
                start();
            }
            return;
        }
        if (state != MatchState.IN_PROGRESS) {
            return;
        }
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onTick(this);
        }
        trackOfflineParticipants();
        if (state == MatchState.IN_PROGRESS) {
            checkWinCondition();
        }
    }

    /** Makes the fight live: records the start, informs rules and fires the API event. */
    public void start() {
        if (state.isFinished()) {
            return;
        }
        if (!state.canTransitionTo(MatchState.IN_PROGRESS)) {
            return;
        }
        state = MatchState.IN_PROGRESS;
        startMillis = System.currentTimeMillis();
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onMatchStart(this);
        }
        core.plugin().getServer().getPluginManager().callEvent(new LightPracticeMatchStartEvent(this));
        broadcast("match.started", "{kit}", kit == null ? "unknown" : kit.displayName(),
                "{arena}", arena == null ? "none" : arena.name());
        sound(startSound, 1.0F, 1.0F);
        Debug.log(DebugCategory.MATCH, "Match {} started ({}, {})", identifier(),
                kit == null ? "no kit" : kit.id(), type);
    }

    // ------------------------------------------------------------------- combat

    /**
     * Handles a death inside the match.
     *
     * @return what the death listener has to apply: instant respawn or elimination
     */
    public DeathDecision handleDeath(final Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        MatchPlayer participant = participant(victim);
        if (participant == null || state.isFinished()) {
            return DeathDecision.ignore();
        }
        participant.addDeath(1);
        participant.resetCombo();
        if (killer != null) {
            MatchPlayer attacker = participant(killer);
            if (attacker != null) {
                attacker.addKill(1);
                attacker.registerHit(comboWindowMillis());
            }
        }
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onDeath(this, victim);
        }
        MatchTeam team = participant.team();
        boolean respawn = !state.isFinished() && team != null && team.respawnEnabled()
                && ruleSet != null && ruleSet.canRespawn(this, victim);
        Location respawnAt = respawn ? resolveRespawnLocation(victim) : null;
        respawn = respawn && respawnAt != null;
        participant.respawnPoint(respawnAt);
        core.plugin().getServer().getPluginManager().callEvent(
                new LightPracticeMatchDeathEvent(this, participant.uuid(),
                        killer == null ? null : killer.getUniqueId()));
        if (!respawn) {
            // elimination runs one tick later so the death event and its drops are processed first
            core.tasks().syncLater(new Runnable() {
                @Override
                public void run() {
                    eliminate(victim, EndCause.KNOCKOUT);
                }
            }, 1L);
        }
        boolean keepItems = ruleSet == null || !ruleSet.allowItemDrops();
        int delay = respawn ? Math.max(1, respawnDelayTicks) : 0;
        return new DeathDecision(respawn, delay, keepItems, respawnAt);
    }

    /** Brings a participant back after a death, used from the respawn listener. */
    public void respawn(Player player) {
        MatchPlayer participant = participant(player);
        if (participant == null || state.isFinished() || player == null) {
            return;
        }
        participant.addRespawn();
        participant.eliminated(false);
        KitManager kits = core.optional(KitManager.class);
        if (kits != null && kit != null) {
            kits.applyKit(player, kit, true);
        }
        Location point = participant.respawnPoint();
        if (point == null) {
            point = resolveRespawnLocation(player);
        }
        if (point != null) {
            player.teleport(point);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector());
        player.setAllowFlight(false);
        player.setFlying(false);
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onPlayerRespawn(this, player);
            giveRespawnItems(player, ruleSet);
        }
        for (Player other : alivePlayers()) {
            other.showPlayer(player);
            player.showPlayer(other);
        }
        Debug.log(DebugCategory.MATCH, "{} respawned in match {} ({} left)", player.getName(), identifier(),
                ruleSet == null ? "?" : String.valueOf(ruleSet.lives()));
    }

    private Location resolveRespawnLocation(Player player) {
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            Location fromRules = ruleSet.respawnLocation(this, player);
            if (fromRules != null) {
                return fromRules;
            }
        }
        MatchTeam team = teamOf(player);
        if (team != null && team.spawn() != null) {
            return team.spawn().clone().add(0.5D, 0.0D, 0.5D);
        }
        if (arena != null) {
            Location spawn = arena.spectatorSpawn();
            return spawn == null ? arena.center() : spawn;
        }
        return null;
    }

    /** Hands out the extra items a rule set defines for a respawn, dropping whatever does not fit. */
    private void giveRespawnItems(Player player, KitRuleSet ruleSet) {
        ItemStack[] extra = ruleSet.respawnItems(this, player);
        if (extra == null || extra.length == 0) {
            return;
        }
        for (ItemStack item : extra) {
            if (item == null) {
                continue;
            }
            java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack left : overflow.values()) {
                if (left != null && player.getWorld() != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }
    }

    /**
     * Removes a participant from the fight and puts them into spectator mode of their own match.
     */
    public void eliminate(Player player, EndCause cause) {
        eliminate(player == null ? null : player.getUniqueId(), cause);
    }

    /**
     * Removes a participant by uuid.
     *
     * <p>Practice bots have no {@link Player}, so their elimination runs through this path: the bot system
     * is told to despawn the body and the win condition is checked as usual.</p>
     */
    public void eliminate(UUID uuid, EndCause cause) {
        MatchPlayer participant = participant(uuid);
        Player player = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
        if (participant == null || participant.eliminated() || state.isFinished()) {
            return;
        }
        participant.eliminated(true);
        if (participant.outcome() == null) {
            participant.outcome(MatchOutcome.LOSS);
        }
        MatchTeam team = participant.team();
        if (team != null && team.aliveCount() == 0) {
            team.eliminated(true);
        }
        KitRuleSet ruleSet = rules();
        if (ruleSet != null && player != null) {
            ruleSet.onPlayerEliminated(this, player);
        }
        if (player != null) {
            moveToSpectatorMode(player);
            broadcast("match.eliminated", "{player}", participant.name(),
                    "{team}", team == null ? "" : team.coloredName());
        } else {
            BotManager bots = core.optional(BotManager.class);
            if (bots != null && bots.isBot(uuid)) {
                bots.despawn(uuid);
            }
            broadcast("match.eliminated", "{player}", participant.name(),
                    "{team}", team == null ? "" : team.coloredName());
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null || player == null ? null : players.get(player);
        if (session != null) {
            players.setState(session, PlayerState.SPECTATING);
            session.spectatedMatchId(identifier());
        }
        Debug.log(DebugCategory.MATCH, "{} was eliminated from match {} ({})", participant.name(), identifier(),
                cause == null ? "knockout" : cause);
        checkWinCondition();
    }

    /** Turns an eliminated participant into a spectator of the remaining fight. */
    private void moveToSpectatorMode(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.MATCH, "Spectator gamemode unavailable: {}", throwable.getMessage());
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        player.setHealth(Math.min(player.getMaxHealth(), kit == null ? 20.0D : Math.max(1.0D, kit.health())));
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.updateInventory();
        for (Player other : alivePlayers()) {
            other.hidePlayer(player);
        }
        Location spawn = arena == null ? null : arena.spectatorSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    /**
     * Ends a match when only one side is left.
     *
     * @return true when the match ended as a result of this check
     */
    public boolean checkWinCondition() {
        if (state.isFinished()) {
            return false;
        }
        List<MatchTeam> alive = aliveTeams();
        if (alive.size() > 1) {
            return false;
        }
        end(alive.isEmpty() ? null : alive.get(0), EndCause.KNOCKOUT);
        return true;
    }

    /** Ends the match because a point based rule reached its target. */
    public void endByScore(Player winner, String reason) {
        if (state.isFinished()) {
            return;
        }
        MatchTeam team = teamOf(winner);
        if (team != null) {
            team.addScore(1);
        }
        broadcast("match.points", "{player}", winner == null ? "unknown" : winner.getName(),
                "{reason}", reason == null ? "" : reason);
        end(team, EndCause.POINTS);
    }

    /** Called by the time limit rule once the configured duration passed. */
    public void timeUp() {
        if (state.isFinished()) {
            return;
        }
        MatchTeam best = null;
        double bestHealth = -1.0D;
        int tied = 0;
        for (MatchTeam team : aliveTeams()) {
            double health = teamHealth(team);
            if (health < 0.0D) {
                continue;
            }
            if (health > bestHealth + 0.001D) {
                bestHealth = health;
                best = team;
                tied = 1;
            } else if (Math.abs(health - bestHealth) <= 0.001D) {
                tied++;
            }
        }
        broadcast("match.time-limit");
        end(tied > 1 ? null : best, EndCause.TIMEOUT);
    }

    private double teamHealth(MatchTeam team) {
        double health = 0.0D;
        int alive = 0;
        for (MatchPlayer member : team.alive()) {
            Player player = member.player();
            if (player != null) {
                health += player.getHealth();
                alive++;
            }
        }
        return alive == 0 ? -1.0D : health;
    }

    /** Warns participants that the time limit is close. */
    public void notifyTimeWarning(long secondsLeft) {
        broadcast("match.time-warning", "{seconds}", String.valueOf(Math.max(0L, secondsLeft)),
                "{time}", Text.duration(Math.max(0L, secondsLeft) * 1000L));
    }

    /**
     * Ends a match.
     *
     * <p>Sets outcomes, fires the API event and hands over to {@link MatchManager} for statistics,
     * history, rewards, arena release and player restoration. Calling it twice is ignored.</p>
     */
    public void end(MatchTeam winner, EndCause cause) {
        if (state.isFinished()) {
            return;
        }
        if (!state.canTransitionTo(MatchState.ENDING)) {
            Debug.log(DebugCategory.MATCH, "Refused to end match {} in state {}", identifier(), state);
            return;
        }
        state = MatchState.ENDING;
        endMillis = System.currentTimeMillis();
        winningTeam = winner;
        endCause = cause == null ? EndCause.ABANDONED : cause;
        assignOutcomes();
        KitRuleSet ruleSet = rules();
        if (ruleSet != null) {
            ruleSet.onMatchEnd(this);
        }
        core.plugin().getServer().getPluginManager().callEvent(
                new LightPracticeMatchEndEvent(this, winner, endCause));
        announceResult();
        sound(endSound, 1.0F, 1.0F);
        Debug.log(DebugCategory.MATCH, "Match {} ended after {}s: {} ({})", identifier(), elapsedSeconds(),
                winner == null ? "draw" : winner.name(), endCause);
        if (manager != null) {
            manager.onMatchEnded(this);
        }
    }

    private void assignOutcomes() {
        boolean draw = winningTeam == null;
        for (MatchPlayer participant : participants) {
            if (participant.outcome() == MatchOutcome.FORFEIT) {
                continue;
            }
            if (draw) {
                participant.outcome(MatchOutcome.DRAW);
                continue;
            }
            boolean won = winningTeam.equals(participant.team());
            participant.outcome(won ? MatchOutcome.WIN : MatchOutcome.LOSS);
        }
    }

    private void announceResult() {
        if (winningTeam == null) {
            broadcast("match.draw", "{kit}", kit == null ? "unknown" : kit.displayName());
            return;
        }
        List<String> winners = new ArrayList<String>();
        for (MatchPlayer member : winningTeam.members()) {
            winners.add(member.name());
        }
        broadcast("match.result", "{winner}", winningTeam.coloredName(),
                "{players}", Text.join(winners, ", "),
                "{kit}", kit == null ? "unknown" : kit.displayName(),
                "{duration}", Text.duration(durationMillis()),
                "{cause}", endCause == null ? "unknown" : endCause.name().toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------ forfeits

    /** A participant disconnected or left; the remaining side wins unless everyone is gone. */
    public void forfeit(UUID uuid, EndCause cause) {
        if (state.isFinished()) {
            return;
        }
        MatchPlayer participant = participant(uuid);
        if (participant == null) {
            removeSpectator(uuid);
            return;
        }
        participant.eliminated(true);
        participant.outcome(MatchOutcome.FORFEIT);
        MatchTeam team = participant.team();
        if (team != null && team.aliveCount() == 0) {
            team.eliminated(true);
        }
        offlineSince.remove(uuid);
        broadcast("match.forfeit", "{player}", participant.name());
        end(remainingTeam(), cause == null ? EndCause.FORFEIT : cause);
    }

    /** A participant gave up through a command. */
    public void surrender(UUID uuid) {
        MatchPlayer participant = participant(uuid);
        if (participant == null || state.isFinished()) {
            return;
        }
        broadcast("match.surrender", "{player}", participant.name());
        forfeit(uuid, EndCause.SURRENDER);
    }

    private MatchTeam remainingTeam() {
        MatchTeam result = null;
        for (MatchTeam team : teams) {
            if (team.eliminated() || team.aliveCount() == 0) {
                continue;
            }
            if (result != null) {
                return null;
            }
            result = team;
        }
        return result;
    }

    private void trackOfflineParticipants() {
        long now = System.currentTimeMillis();
        for (MatchPlayer participant : participants) {
            if (participant.eliminated()) {
                offlineSince.remove(participant.uuid());
                continue;
            }
            if (participant.isOnline()) {
                offlineSince.remove(participant.uuid());
                continue;
            }
            Long since = offlineSince.get(participant.uuid());
            if (since == null) {
                offlineSince.put(participant.uuid(), Long.valueOf(now));
                continue;
            }
            if (now - since.longValue() >= disconnectGraceMillis) {
                Debug.log(DebugCategory.MATCH, "{} did not reconnect within {}s, match {} forfeits",
                        participant.name(), disconnectGraceMillis / 1000L, identifier());
                forfeit(participant.uuid(), EndCause.FORFEIT);
                return;
            }
        }
    }

    /** Puts a disconnected participant back into the fight after a reconnect. */
    public boolean reconnect(Player player) {
        MatchPlayer participant = participant(player);
        if (participant == null || player == null) {
            return false;
        }
        if (state.isFinished()) {
            return false;
        }
        offlineSince.remove(participant.uuid());
        if (participant.eliminated()) {
            moveToSpectatorMode(player);
            return true;
        }
        KitManager kits = core.optional(KitManager.class);
        if (kits != null && kit != null) {
            kits.applyKit(player, kit, true);
        }
        Location point = participant.respawnPoint();
        if (point != null) {
            player.teleport(point);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        for (Player other : alivePlayers()) {
            other.showPlayer(player);
            player.showPlayer(other);
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null) {
            players.forceState(session, PlayerState.MATCH);
            session.matchId(identifier());
            if (session.snapshot() == null) {
                session.snapshot(InventorySnapshot.capture(player));
            }
        }
        broadcast("match.reconnected", "{player}", player.getName());
        Debug.log(DebugCategory.MATCH, "{} reconnected into match {}", player.getName(), identifier());
        return true;
    }

    // ------------------------------------------------------------------ messages

    public void broadcast(String key, String... replacements) {
        Messages messages = core.messages();
        if (messages == null || key == null) {
            return;
        }
        messages.broadcastTo(onlinePlayers(), key, replacements);
    }

    public void send(Player player, String key, String... replacements) {
        Messages messages = core.messages();
        if (messages == null || player == null || key == null) {
            return;
        }
        messages.send(player, key, replacements);
    }

    private void sound(String name, float volume, float pitch) {
        for (Player player : onlinePlayers()) {
            Visuals.sound(player, name, volume, pitch);
        }
    }

    // ---------------------------------------------------------------- protection

    /** Whether a location belongs to this match, used to keep builds and explosions inside. */
    public boolean canModify(Location location) {
        if (location == null) {
            return false;
        }
        if (arena == null) {
            return true;
        }
        ArenaBounds bounds = arena.bounds();
        if (bounds != null && bounds.valid()) {
            return bounds.contains(location);
        }
        return arena.contains(location);
    }

    public boolean canBuild(Player player, Block block) {
        if (player == null || block == null || state != MatchState.IN_PROGRESS) {
            return false;
        }
        MatchPlayer participant = participant(player);
        if (participant == null || participant.eliminated()) {
            return false;
        }
        if (!canModify(block.getLocation())) {
            return false;
        }
        KitRuleSet ruleSet = rules();
        return ruleSet != null && ruleSet.canPlace(this, player, block, player.getItemInHand());
    }

    public boolean canBreak(Player player, Block block) {
        if (player == null || block == null || state != MatchState.IN_PROGRESS) {
            return false;
        }
        MatchPlayer participant = participant(player);
        if (participant == null || participant.eliminated()) {
            return false;
        }
        if (!canModify(block.getLocation())) {
            return false;
        }
        KitRuleSet ruleSet = rules();
        return ruleSet != null && ruleSet.canBreak(this, player, block);
    }

    /** Marks damage applied by the plugin itself, such as pearl damage, so rules do not cancel it. */
    public void allowNextDamage(Player player) {
        if (player != null) {
            internalDamage.add(player.getUniqueId());
        }
    }

    /** Consumes an internal damage marker, returning true when the hit came from the plugin. */
    public boolean consumeInternalDamage(Player player) {
        return player != null && internalDamage.remove(player.getUniqueId());
    }

    /** Longest gap between hits that still counts as a combo, from combat.yml. */
    public long comboWindowMillis() {
        return Math.max(0L, core.configs().combat().getLong("combo.window-millis", 2000L));
    }

    // ---------------------------------------------------------------- visibility

    /**
     * Applies match visibility.
     *
     * <p>Participants only see each other, spectators see the fight and each other, and everyone outside
     * the match sees neither, so an arena never leaks into the lobby.</p>
     */
    public void refreshVisibility() {
        List<Player> inside = onlinePlayers();
        List<Player> watching = new ArrayList<Player>();
        for (UUID uuid : spectators) {
            Player spectator = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
            if (spectator != null && spectator.isOnline()) {
                watching.add(spectator);
            }
        }
        for (Player player : inside) {
            applyVisibility(player, inside, Collections.<Player>emptyList());
        }
        for (Player spectator : watching) {
            applyVisibility(spectator, inside, watching);
        }
    }

    private void applyVisibility(Player viewer, List<Player> visible, List<Player> alsoVisible) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (other == null || !other.isOnline() || other.equals(viewer)) {
                continue;
            }
            boolean shown = visible.contains(other) || alsoVisible.contains(other);
            if (shown) {
                viewer.showPlayer(other);
            } else {
                viewer.hidePlayer(other);
                other.hidePlayer(viewer);
            }
        }
    }

    // ------------------------------------------------------------------- cleanup

    /**
     * Restores every participant: inventory snapshot, vitals, gamemode and player state.
     *
     * <p>Called once by {@link MatchManager} after the match ended; the lobby teleport is handled there
     * so this class stays independent of the lobby system.</p>
     */
    public void restorePlayers() {
        PlayerManager players = core.optional(PlayerManager.class);
        Collection<? extends Player> online = org.bukkit.Bukkit.getOnlinePlayers();
        for (MatchPlayer participant : participants) {
            Player player = participant.player();
            LightPlayer session = players == null ? null : players.get(participant.uuid());
            if (session != null) {
                InventorySnapshot snapshot = session.snapshot();
                if (snapshot != null && player != null) {
                    snapshot.restore(player, false);
                }
                session.clearSnapshot();
                session.matchId(null);
                session.spectatedMatchId(null);
                session.clearCombat();
                session.resetCombo();
                players.forceState(session, player == null ? PlayerState.OFFLINE : PlayerState.LOBBY);
            }
            if (player == null) {
                continue;
            }
            try {
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(5.0F);
                player.setExhaustion(0.0F);
                player.setFireTicks(0);
                player.setFallDistance(0.0F);
                player.setVelocity(new Vector());
                player.setWalkSpeed(0.2F);
                player.setFlySpeed(0.1F);
                for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                    player.removePotionEffect(effect.getType());
                }
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.MATCH, "Could not fully restore " + player.getName(), throwable);
            }
            for (Player other : online) {
                if (other != null && other.isOnline()) {
                    player.showPlayer(other);
                    other.showPlayer(player);
                }
            }
        }
        for (UUID uuid : new ArrayList<UUID>(spectators)) {
            Player spectator = uuid == null ? null : org.bukkit.Bukkit.getPlayer(uuid);
            if (spectator != null) {
                for (Player other : online) {
                    if (other != null && other.isOnline()) {
                        spectator.showPlayer(other);
                        other.showPlayer(spectator);
                    }
                }
            }
        }
        byUuid.clear();
        participants.clear();
        teams.clear();
        spectators.clear();
        internalDamage.clear();
        offlineSince.clear();
        state = MatchState.ENDED;
    }

    /** Marks the match as ended without restoring players, used when shutdown already cleaned up. */
    public void markEnded() {
        if (state != MatchState.ENDED) {
            state = MatchState.ENDED;
        }
    }

    public Tasks tasks() {
        return core.tasks();
    }

    public PluginCore core() {
        return core;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Match)) {
            return false;
        }
        return id.equals(((Match) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Match{" + identifier() + ", kit=" + (kit == null ? "none" : kit.id()) + ", type=" + type
                + ", state=" + state + ", players=" + participants.size() + '}';
    }

    /**
     * What a death listener has to do after {@link #handleDeath(Player, Player, EntityDamageEvent.DamageCause)}.
     */
    public static final class DeathDecision {

        private final boolean respawn;
        private final int delayTicks;
        private final boolean keepInventory;
        private final Location respawnLocation;

        public DeathDecision(boolean respawn, int delayTicks, boolean keepInventory, Location respawnLocation) {
            this.respawn = respawn;
            this.delayTicks = Math.max(0, delayTicks);
            this.keepInventory = keepInventory;
            this.respawnLocation = respawnLocation;
        }

        static DeathDecision ignore() {
            return new DeathDecision(false, 0, true, null);
        }

        public boolean respawn() {
            return respawn;
        }

        public int delayTicks() {
            return delayTicks;
        }

        public boolean keepInventory() {
            return keepInventory;
        }

        public Location respawnLocation() {
            return respawnLocation;
        }

        @Override
        public String toString() {
            return "DeathDecision{respawn=" + respawn + ", delay=" + delayTicks + ", keep=" + keepInventory + '}';
        }
    }

}
