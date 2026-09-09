package gg.lightpractice.gameevent;

import gg.lightpractice.api.GameEventService;
import gg.lightpractice.api.event.LightPracticeGameEventStartEvent;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.gameevent.provider.FfaEventProvider;
import gg.lightpractice.gameevent.provider.KingOfTheHillProvider;
import gg.lightpractice.gameevent.provider.SumoEventProvider;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Locations;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host for game events.
 *
 * <p>Event types are pluggable: the bundled free for all, sumo gauntlet and king of the hill providers are
 * registered on startup and anything else can be added through {@link #register(GameEventProvider)}. This
 * class owns the only event session, drives its countdown and per second tick with one shared task, and
 * exposes the helpers providers use to start matches, announce and finish.</p>
 */
public final class EventManager implements GameEventService, LightService {

    private final PluginCore core;
    private final Map<String, GameEventProvider> providers =
            new ConcurrentHashMap<String, GameEventProvider>();
    private final Map<String, EventDefinition> definitions =
            new LinkedHashMap<String, EventDefinition>();
    private volatile GameEvent active;
    private volatile GameEvent completed;
    private BukkitTask tickTask;
    private long lastAnnouncement;

    private boolean enabled = true;
    private int gatherSeconds = 60;
    private int countdownSeconds = 10;
    private int matchCountdown = 5;
    private int announceInterval = 30;
    private boolean allowSpectators = true;
    private String defaultKit = "nodebuff";
    private long winCoins = 300L;
    private long winExperience = 150L;
    private long participationCoins = 25L;

    public EventManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "events";
    }

    @Override
    public int startupOrder() {
        return 67;
    }

    @Override
    public void onLoad() {
        register(new FfaEventProvider());
        register(new SumoEventProvider());
        register(new KingOfTheHillProvider());
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
    }

    @Override
    public void onDisable() {
        GameEvent event = active;
        if (event != null) {
            stop(event, "the plugin was disabled");
        }
        core.tasks().cancel(tickTask);
        tickTask = null;
        providers.clear();
        definitions.clear();
        completed = null;
    }

    @Override
    public void onReload() {
        reload();
    }

    public void reload() {
        ConfigFile file = core.configs().events();
        file.reload();
        this.enabled = file.getBoolean("settings.enabled", true);
        this.gatherSeconds = Math.max(5, file.getInt("settings.gather-seconds", 60));
        this.countdownSeconds = Math.max(0, file.getInt("settings.countdown-seconds", 10));
        this.matchCountdown = Math.max(0, file.getInt("settings.match-countdown-seconds", 5));
        this.announceInterval = Math.max(5, file.getInt("settings.announce-interval-seconds", 30));
        this.allowSpectators = file.getBoolean("settings.allow-spectators", true);
        this.defaultKit = file.getString("settings.default-kit", "nodebuff");
        this.winCoins = Math.max(0L, file.getLong("rewards.win-coins", 300L));
        this.winExperience = Math.max(0L, file.getLong("rewards.win-experience", 150L));
        this.participationCoins = Math.max(0L, file.getLong("rewards.participation-coins", 25L));
        definitions.clear();
        ConfigurationSection root = file.section("events");
        if (root == null || root.getKeys(false).isEmpty()) {
            installDefaults(file);
            root = file.section("events");
        }
        if (root != null) {
            for (String key : root.getKeys(false)) {
                EventDefinition definition = EventDefinition.read(key, root.getConfigurationSection(key));
                if (definition != null && definition.enabled && providers.containsKey(definition.typeId)) {
                    definitions.put(definition.id, definition);
                } else if (definition != null) {
                    Debug.warn(DebugCategory.EVENT, "Event '{}' uses the unknown type '{}'", key,
                            definition.typeId);
                }
            }
        }
        Debug.log(DebugCategory.EVENT, "Events {}: {} provider(s), {} definition(s)",
                enabled ? "enabled" : "disabled", providers.size(), definitions.size());
    }

    private void installDefaults(ConfigFile file) {
        write(file, "ffa", "ffa", "&aFree For All", "nodebuff", "", 3, 16);
        write(file, "sumo-gauntlet", "sumo", "&6Sumo Gauntlet", "sumo", "", 2, 16);
        write(file, "king-of-the-hill", "koth", "&bKing Of The Hill", "nodebuff", "", 2, 24);
        file.save();
        Debug.log(DebugCategory.CONFIG, "Installed the default events into events.yml");
    }

    private void write(ConfigFile file, String id, String type, String name, String kit, String arena,
                       int minPlayers, int maxPlayers) {
        ConfigurationSection section = file.getOrCreateSection("events." + id);
        section.set("type", type);
        section.set("name", name);
        section.set("kit", kit);
        section.set("arena", arena);
        section.set("min-players", minPlayers);
        section.set("max-players", maxPlayers);
        section.set("gather-seconds", gatherSeconds);
        section.set("enabled", true);
    }

    // ---------------------------------------------------------- GameEventService

    @Override
    public void register(GameEventProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isEmpty()) {
            Debug.warn(DebugCategory.EVENT, "Ignored an event provider without an id");
            return;
        }
        String id = provider.id().trim().toLowerCase(Locale.ROOT);
        GameEventProvider previous = providers.put(id, provider);
        Debug.log(DebugCategory.EVENT, "Registered the event provider {} ({})", id,
                provider.getClass().getSimpleName());
        if (previous != null) {
            Debug.log(DebugCategory.EVENT, "Event provider {} replaced {}", id,
                    previous.getClass().getSimpleName());
        }
    }

    public GameEventProvider provider(String typeId) {
        return typeId == null ? null : providers.get(typeId.trim().toLowerCase(Locale.ROOT));
    }

    public Collection<GameEventProvider> providers() {
        return Collections.unmodifiableCollection(providers.values());
    }

    @Override
    public List<String> types() {
        List<String> types = new ArrayList<String>(definitions.keySet());
        if (types.isEmpty()) {
            types.addAll(providers.keySet());
        }
        Collections.sort(types);
        return types;
    }

    public EventDefinition definition(String id) {
        return id == null ? null : definitions.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public Collection<EventDefinition> definitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    @Override
    public GameEvent active() {
        return active;
    }

    /** Event that finished or was cancelled last, kept for menus and placeholders. */
    public GameEvent completed() {
        return completed;
    }

    @Override
    public boolean isRunning() {
        GameEvent event = active;
        return event != null && event.state() == EventState.RUNNING;
    }

    public boolean isGathering() {
        GameEvent event = active;
        return event != null && event.state() == EventState.GATHERING;
    }

    @Override
    public boolean host(Player host, String typeId, String kitId, String arenaName) {
        if (!enabled) {
            core.messages().send(host, "event.disabled");
            return false;
        }
        if (active != null) {
            core.messages().send(host, "event.already-running", "{event}", active.name());
            return false;
        }
        EventDefinition definition = resolve(typeId);
        if (definition == null) {
            core.messages().send(host, "event.unknown-type", "{type}", String.valueOf(typeId));
            return false;
        }
        GameEventProvider provider = providers.get(definition.typeId);
        if (provider == null) {
            core.messages().send(host, "event.unknown-type", "{type}", definition.typeId);
            return false;
        }
        String resolvedKit = firstNonEmpty(kitId, definition.kitId, defaultKit);
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(resolvedKit);
        if (kit == null || !kit.enabled()) {
            core.messages().send(host, "event.kit-missing", "{kit}", resolvedKit);
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        if (host != null && players != null && !players.isIdle(host.getUniqueId())) {
            core.messages().send(host, "event.busy");
            return false;
        }
        GameEvent event = new GameEvent("e-" + Long.toString(System.currentTimeMillis(), 36), provider, this,
                host == null ? null : host.getUniqueId(), host == null ? "console" : host.getName());
        event.name(definition.name);
        event.kitId(kit.id());
        event.arenaName(firstNonEmpty(arenaName, definition.arenaName, ""));
        event.minPlayers(definition.minPlayers > 0 ? definition.minPlayers
                : Math.max(2, provider.minPlayers()));
        event.maxPlayers(definition.maxPlayers > 0 ? definition.maxPlayers
                : Math.max(event.minPlayers(), provider.maxPlayers() > 0 ? provider.maxPlayers() : 32));
        event.objective(definition.objective);
        if (definition.objectiveRadius > 0.0D) {
            event.objectiveRadius(definition.objectiveRadius);
        }
        if (definition.objectiveSeconds > 0) {
            event.objectiveSeconds(definition.objectiveSeconds);
        }
        List<String> problems = new ArrayList<String>();
        provider.validate(event, problems);
        if (!problems.isEmpty()) {
            core.messages().send(host, "event.invalid", "{reason}", Text.join(problems, ", "));
            Debug.warn(DebugCategory.EVENT, "Event {} could not be hosted: {}", event.id(), problems);
            return false;
        }
        int gather = definition.gatherSeconds > 0 ? definition.gatherSeconds
                : (provider.gatherSeconds() > 0 ? provider.gatherSeconds() : gatherSeconds);
        event.startAt(System.currentTimeMillis() + gather * 1000L);
        this.active = event;
        this.lastAnnouncement = System.currentTimeMillis();
        core.messages().broadcast("event.hosted",
                "{event}", event.name(),
                "{host}", event.hostName(),
                "{kit}", kit.displayName(),
                "{seconds}", String.valueOf(gather),
                "{min}", String.valueOf(event.minPlayers()),
                "{max}", String.valueOf(event.maxPlayers()));
        event.log("hosted by " + event.hostName());
        Debug.log(DebugCategory.EVENT, "Event {} ({}) hosted by {}", event.id(), provider.id(),
                event.hostName());
        return true;
    }

    private EventDefinition resolve(String typeId) {
        if (typeId == null || typeId.trim().isEmpty()) {
            return definitions.isEmpty() ? null : definitions.values().iterator().next();
        }
        String key = typeId.trim().toLowerCase(Locale.ROOT);
        EventDefinition definition = definitions.get(key);
        if (definition != null) {
            return definition;
        }
        GameEventProvider provider = providers.get(key);
        if (provider == null) {
            return null;
        }
        // a bare provider id hosts an ad hoc event with the configured defaults
        EventDefinition adhoc = new EventDefinition(key);
        adhoc.typeId = key;
        adhoc.name = provider.displayName();
        adhoc.kitId = defaultKit;
        adhoc.minPlayers = Math.max(2, provider.minPlayers());
        adhoc.maxPlayers = provider.maxPlayers() > 0 ? provider.maxPlayers() : 32;
        adhoc.gatherSeconds = provider.gatherSeconds() > 0 ? provider.gatherSeconds() : gatherSeconds;
        return adhoc;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    public boolean join(Player player) {
        GameEvent event = active;
        if (player == null) {
            return false;
        }
        if (event == null) {
            core.messages().send(player, "event.none");
            return false;
        }
        if (!event.state().joinable()) {
            core.messages().send(player, "event.not-joinable", "{state}", event.state().configKey());
            return false;
        }
        if (event.isParticipant(player.getUniqueId())) {
            core.messages().send(player, "event.already-joined");
            return false;
        }
        if (event.isFull()) {
            core.messages().send(player, "event.full", "{max}", String.valueOf(event.maxPlayers()));
            return false;
        }
        gg.lightpractice.api.BanService bans = core.optional(gg.lightpractice.api.BanService.class);
        if (bans != null && bans.isBanned(player.getUniqueId())) {
            core.messages().send(player, "event.banned");
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null && (session.inMatch() || session.inQueue() || session.inEvent()
                || session.inTournament())) {
            core.messages().send(player, "event.busy");
            return false;
        }
        if (!event.join(player.getUniqueId())) {
            core.messages().send(player, "event.join-failed");
            return false;
        }
        if (session != null) {
            session.eventId(event.id());
        }
        core.messages().send(player, "event.joined",
                "{event}", event.name(),
                "{size}", String.valueOf(event.size()),
                "{min}", String.valueOf(event.minPlayers()),
                "{max}", String.valueOf(event.maxPlayers()),
                "{seconds}", String.valueOf(Math.max(0L, event.remainingMillis() / 1000L)));
        announce(event, "event.entry", "{player}", player.getName(),
                "{size}", String.valueOf(event.size()), "{min}", String.valueOf(event.minPlayers()));
        try {
            event.provider().onJoin(event, player);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The join hook of " + event.provider().id() + " failed", throwable);
        }
        return true;
    }

    @Override
    public boolean leave(Player player) {
        GameEvent event = active;
        if (player == null || event == null || !event.isParticipant(player.getUniqueId())) {
            core.messages().send(player, "event.not-entered");
            return false;
        }
        if (event.state() == EventState.RUNNING) {
            Match match = match(event);
            MatchManager matches = core.optional(MatchManager.class);
            if (match != null && matches != null && match.isParticipant(player.getUniqueId())
                    && match.isLive()) {
                matches.forfeit(player.getUniqueId(), EndCause.SURRENDER);
            }
        }
        event.leave(player.getUniqueId());
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null) {
            session.eventId(null);
        }
        core.messages().send(player, "event.left", "{event}", event.name());
        announce(event, "event.exit", "{player}", player.getName(), "{size}", String.valueOf(event.size()));
        try {
            event.provider().onLeave(event, player);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The leave hook of " + event.provider().id() + " failed", throwable);
        }
        if (event.state() == EventState.GATHERING && !event.hasEnoughPlayers()
                && event.remainingMillis() <= 0L) {
            stop(event, "not enough players remained");
        }
        return true;
    }

    @Override
    public boolean start(Player starter) {
        GameEvent event = active;
        if (event == null) {
            core.messages().send(starter, "event.none");
            return false;
        }
        if (event.state() != EventState.GATHERING) {
            core.messages().send(starter, "event.already-started", "{state}", event.state().configKey());
            return false;
        }
        if (!event.hasEnoughPlayers()) {
            core.messages().send(starter, "event.not-enough-players",
                    "{size}", String.valueOf(event.size()),
                    "{min}", String.valueOf(event.minPlayers()));
            return false;
        }
        event.state(EventState.STARTING);
        event.countdownSeconds(countdownSeconds);
        core.messages().send(starter, "event.starting-now", "{event}", event.name());
        core.messages().broadcast("event.starting",
                "{event}", event.name(),
                "{size}", String.valueOf(event.size()),
                "{seconds}", String.valueOf(countdownSeconds));
        return true;
    }

    @Override
    public boolean stop(Player stopper, String reason) {
        GameEvent event = active;
        if (event == null) {
            core.messages().send(stopper, "event.none");
            return false;
        }
        stop(event, reason);
        if (stopper != null) {
            core.messages().send(stopper, "event.stopped", "{event}", event.name(),
                    "{reason}", event.endReason());
        }
        return true;
    }

    /** Stops a session, ending its match and releasing every entry. */
    public boolean stop(GameEvent event, String reason) {
        if (event == null) {
            return false;
        }
        String cause = reason == null || reason.trim().isEmpty() ? "stopped by an administrator" : reason.trim();
        event.endReason(cause);
        event.state(EventState.CANCELLED);
        abortMatch(event);
        try {
            event.provider().onStop(event, cause);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The stop hook of " + event.provider().id() + " failed", throwable);
        }
        releaseEntries(event);
        core.messages().broadcast("event.cancelled",
                "{event}", event.name(), "{reason}", cause, "{size}", String.valueOf(event.size()));
        event.log("cancelled: " + cause);
        event.matchId(null);
        if (this.active == event) {
            this.active = null;
        }
        this.completed = event;
        Debug.log(DebugCategory.EVENT, "Event {} cancelled: {}", event.id(), cause);
        return true;
    }

    // ---------------------------------------------------------------- scheduling

    private void tick() {
        GameEvent event = active;
        if (event == null) {
            return;
        }
        try {
            switch (event.state()) {
                case GATHERING:
                    tickGathering(event);
                    break;
                case STARTING:
                    tickStarting(event);
                    break;
                case RUNNING:
                    event.provider().onTick(event);
                    if (!event.live()) {
                        break;
                    }
                    Match match = match(event);
                    if (event.provider().usesMatch() && match != null && match.isFinished()) {
                        handleMatchEnd(match);
                    }
                    break;
                default:
                    this.active = null;
                    break;
            }
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The event task of " + event.id() + " failed", throwable);
        }
    }

    private void tickGathering(GameEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastAnnouncement >= announceInterval * 1000L) {
            lastAnnouncement = now;
            core.messages().broadcast("event.reminder",
                    "{event}", event.name(),
                    "{size}", String.valueOf(event.size()),
                    "{min}", String.valueOf(event.minPlayers()),
                    "{seconds}", String.valueOf(Math.max(0L, event.remainingMillis() / 1000L)));
        }
        if (event.remainingMillis() > 0L) {
            return;
        }
        if (!event.hasEnoughPlayers()) {
            stop(event, "not enough players joined");
            return;
        }
        event.state(EventState.STARTING);
        event.countdownSeconds(countdownSeconds);
        core.messages().broadcast("event.starting",
                "{event}", event.name(),
                "{size}", String.valueOf(event.size()),
                "{seconds}", String.valueOf(countdownSeconds));
    }

    private void tickStarting(GameEvent event) {
        int seconds = event.countdownSeconds();
        if (seconds > 0) {
            if (seconds <= 5 || seconds % 5 == 0) {
                core.messages().broadcast("event.countdown",
                        "{event}", event.name(), "{seconds}", String.valueOf(seconds));
            }
            event.countdownSeconds(seconds - 1);
            return;
        }
        dropOffline(event);
        if (!event.hasEnoughPlayers()) {
            stop(event, "not enough players remained online");
            return;
        }
        event.state(EventState.RUNNING);
        Player host = event.host() == null ? null : Bukkit.getPlayer(event.host());
        Bukkit.getPluginManager().callEvent(new LightPracticeGameEventStartEvent(event, host));
        event.log("started with " + event.size() + " player(s)");
        try {
            event.provider().onStart(event);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The start hook of " + event.provider().id() + " failed", throwable);
            stop(event, "the event type failed to start");
        }
    }

    private void dropOffline(GameEvent event) {
        List<UUID> offline = new ArrayList<UUID>();
        for (UUID uuid : event.participantsList()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                offline.add(uuid);
            }
        }
        for (UUID uuid : offline) {
            event.leave(uuid);
            PlayerManager players = core.optional(PlayerManager.class);
            LightPlayer session = players == null ? null : players.get(uuid);
            if (session != null) {
                session.eventId(null);
            }
        }
        if (!offline.isEmpty()) {
            Debug.log(DebugCategory.EVENT, "Removed {} offline entr(y/ies) from event {}", offline.size(),
                    event.id());
        }
    }

    // -------------------------------------------------------------- provider API

    public PluginCore core() {
        return core;
    }

    public int matchCountdown() {
        return matchCountdown;
    }

    public boolean allowSpectators() {
        return allowSpectators;
    }

    /** Kit of a session, {@code null} when it disappeared from the configuration. */
    public Kit kit(GameEvent event) {
        KitManager kits = core.optional(KitManager.class);
        if (kits == null || event == null) {
            return null;
        }
        Kit kit = kits.get(event.kitId());
        return kit != null && kit.enabled() ? kit : null;
    }

    /** Arena of a session: the configured one when it is usable, otherwise a free arena for the kit. */
    public Arena arena(GameEvent event) {
        ArenaManager arenas = core.optional(ArenaManager.class);
        if (arenas == null || event == null) {
            return null;
        }
        String name = event.arenaName();
        if (name != null && !name.isEmpty()) {
            Arena configured = arenas.get(name);
            if (configured != null && configured.enabled()) {
                return configured;
            }
            Debug.log(DebugCategory.ARENA, "Event arena '{}' is unknown, picking a free one", name);
        }
        return arenas.findAvailable(kit(event));
    }

    /** Starts the match of a session, returning false when it could not be launched. */
    public boolean launch(GameEvent event, MatchRequest request) {
        MatchManager matches = core.optional(MatchManager.class);
        if (event == null || matches == null || request == null) {
            return false;
        }
        Arena arena = arena(event);
        if (arena != null && request.arena() == null) {
            request.arena(arena);
        }
        Match match = matches.startMatch(request);
        if (match == null) {
            Debug.log(DebugCategory.EVENT, "Event {} could not launch a match ({})", event.id(), request);
            return false;
        }
        event.matchId(match.identifier());
        event.log("match " + match.identifier() + " started");
        Debug.log(DebugCategory.EVENT, "Event {} started match {}", event.id(), match.identifier());
        return true;
    }

    /** Match a session is playing in, {@code null} when it has none. */
    public Match match(GameEvent event) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null || event == null || event.matchId() == null) {
            return null;
        }
        return matches.get(event.matchId());
    }

    /** Sends a message to everybody entered in a session plus its host. */
    public void announce(GameEvent event, String key, String... replacements) {
        if (event == null) {
            return;
        }
        core.messages().broadcastTo(receivers(event), key, replacements);
    }

    public List<Player> receivers(GameEvent event) {
        List<Player> receivers = new ArrayList<Player>();
        if (event == null) {
            return receivers;
        }
        for (UUID uuid : event.participantsList()) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !receivers.contains(player)) {
                receivers.add(player);
            }
        }
        Player host = event.host() == null ? null : Bukkit.getPlayer(event.host());
        if (host != null && host.isOnline() && !receivers.contains(host)) {
            receivers.add(host);
        }
        return receivers;
    }

    public List<Player> online(GameEvent event) {
        List<Player> online = new ArrayList<Player>();
        if (event == null) {
            return online;
        }
        for (UUID uuid : event.participantsList()) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    public String names(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "nobody";
        }
        List<String> names = new ArrayList<String>();
        ProfileManager profiles = core.optional(ProfileManager.class);
        for (UUID uuid : uuids) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
                continue;
            }
            Profile profile = profiles == null ? null : profiles.getProfile(uuid);
            names.add(profile == null || profile.name() == null ? "unknown" : profile.name());
        }
        return Text.color("&a" + Text.join(names, "&7, &a"));
    }

    /** Called by the match engine when a match ended so the session can react. */
    public void handleMatchEnd(Match match) {
        GameEvent event = active;
        if (event == null || match == null || event.state() != EventState.RUNNING) {
            return;
        }
        if (event.matchId() == null || !event.matchId().equals(match.identifier())) {
            return;
        }
        try {
            event.provider().onMatchEnd(event, match);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The match end hook of " + event.provider().id() + " failed",
                    throwable);
            stop(event, "the event type failed");
        }
    }

    /** Finishes a session with winners, pays rewards and releases the entries. */
    public boolean finish(GameEvent event, Collection<UUID> winners, String reason) {
        if (event == null) {
            return false;
        }
        event.state(EventState.FINISHED);
        event.winners(winners);
        event.endReason(reason == null ? "finished" : reason);
        event.matchId(null);
        event.log("finished: " + event.endReason());
        try {
            event.provider().onFinish(event);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.EVENT, "The finish hook of " + event.provider().id() + " failed", throwable);
        }
        reward(event);
        core.messages().broadcast("event.winner",
                "{event}", event.name(),
                "{winner}", names(event.winners()),
                "{players}", String.valueOf(event.size()));
        for (UUID uuid : event.winners()) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null) {
                core.messages().title(player, "event.winner-title", "event.winner-subtitle",
                        "{event}", event.name());
            }
        }
        releaseEntries(event);
        if (this.active == event) {
            this.active = null;
        }
        this.completed = event;
        Debug.log(DebugCategory.EVENT, "Event {} finished, winner(s) {}", event.id(), names(event.winners()));
        return true;
    }

    private void reward(GameEvent event) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles == null) {
            return;
        }
        List<UUID> winners = event.winners();
        for (UUID uuid : event.participantsList()) {
            Profile profile = profiles.getProfile(uuid);
            if (profile == null) {
                continue;
            }
            boolean won = winners.contains(uuid);
            long coins = won ? winCoins : participationCoins;
            long experience = won ? winExperience : 0L;
            if (coins > 0L) {
                profile.addCoins(coins);
            }
            if (experience > 0L) {
                profile.addExperience(experience);
            }
            profiles.save(profile);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                core.messages().send(player, won ? "event.reward-win" : "event.reward-participation",
                        "{coins}", String.valueOf(coins),
                        "{experience}", String.valueOf(experience),
                        "{event}", event.name());
            }
        }
    }

    private void abortMatch(GameEvent event) {
        MatchManager matches = core.optional(MatchManager.class);
        Match match = match(event);
        if (matches != null && match != null && !match.isFinished()) {
            matches.end(match, null, EndCause.ABANDONED);
        }
    }

    private void releaseEntries(GameEvent event) {
        PlayerManager players = core.optional(PlayerManager.class);
        for (UUID uuid : event.participantsList()) {
            LightPlayer session = players == null ? null : players.get(uuid);
            if (session != null) {
                session.eventId(null);
            }
        }
    }

    /** Removes a player from the active event when they disconnect. */
    public void handleQuit(UUID uuid) {
        GameEvent event = active;
        if (event == null || uuid == null || !event.isParticipant(uuid)) {
            return;
        }
        if (event.state() == EventState.RUNNING) {
            // the match engine decides the result of a disconnect, the entry is dropped afterwards
            return;
        }
        event.leave(uuid);
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(uuid);
        if (session != null) {
            session.eventId(null);
        }
        announce(event, "event.exit", "{player}", names(Collections.singletonList(uuid)),
                "{size}", String.valueOf(event.size()));
    }

    // --------------------------------------------------------------- introspection

    public boolean enabled() {
        return enabled;
    }

    public boolean isEntered(UUID uuid) {
        GameEvent event = active;
        return uuid != null && event != null && event.isParticipant(uuid);
    }

    public int gatherSeconds() {
        return gatherSeconds;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public String defaultKit() {
        return defaultKit;
    }

    public String remaining(GameEvent event) {
        return event == null ? "" : Text.duration(event.remainingMillis());
    }

    /** One configured event, resolved while {@code events.yml} is read. */
    public static final class EventDefinition {

        private final String id;
        private String typeId = "";
        private String name = "";
        private String kitId = "";
        private String arenaName = "";
        private int minPlayers;
        private int maxPlayers;
        private int gatherSeconds;
        private boolean enabled = true;
        private Location objective;
        private double objectiveRadius;
        private int objectiveSeconds;

        EventDefinition(String id) {
            this.id = id == null ? "event" : id.trim().toLowerCase(Locale.ROOT);
        }

        public String id() {
            return id;
        }

        public String typeId() {
            return typeId;
        }

        public String name() {
            return name;
        }

        public String kitId() {
            return kitId;
        }

        public String arenaName() {
            return arenaName;
        }

        public int minPlayers() {
            return minPlayers;
        }

        public int maxPlayers() {
            return maxPlayers;
        }

        public int gatherSeconds() {
            return gatherSeconds;
        }

        public boolean enabled() {
            return enabled;
        }

        public Location objective() {
            return objective;
        }

        public double objectiveRadius() {
            return objectiveRadius;
        }

        public int objectiveSeconds() {
            return objectiveSeconds;
        }

        static EventDefinition read(String id, ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            EventDefinition definition = new EventDefinition(id);
            definition.typeId = section.getString("type", id).trim().toLowerCase(Locale.ROOT);
            definition.name = Text.color(section.getString("name", Text.capitalize(id)));
            definition.kitId = section.getString("kit", "");
            definition.arenaName = section.getString("arena", "");
            definition.minPlayers = section.getInt("min-players", 2);
            definition.maxPlayers = Math.max(definition.minPlayers, section.getInt("max-players", 16));
            definition.gatherSeconds = section.getInt("gather-seconds", 0);
            definition.enabled = section.getBoolean("enabled", true);
            definition.objective = Locations.read(section, "objective");
            definition.objectiveRadius = section.getDouble("objective-radius", 0.0D);
            definition.objectiveSeconds = section.getInt("objective-seconds", 0);
            return definition;
        }

        @Override
        public String toString() {
            return "EventDefinition{" + id + ", type=" + typeId + ", kit=" + kitId + ", " + minPlayers
                    + "-" + maxPlayers + " players}";
        }
    }
}
