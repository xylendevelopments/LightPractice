package gg.lightpractice.queue;

import gg.lightpractice.api.BanService;
import gg.lightpractice.api.PartyService;
import gg.lightpractice.api.QueueService;
import gg.lightpractice.api.StatsService;
import gg.lightpractice.api.event.LightPracticeQueueJoinEvent;
import gg.lightpractice.api.event.LightPracticeQueueLeaveEvent;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.matchmaking.MatchPairing;
import gg.lightpractice.matchmaking.Matchmaker;
import gg.lightpractice.party.Party;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.player.PlayerState;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Waiting lists and the loop that turns them into matches.
 *
 * <p>One central task runs the matchmaker over every enabled queue, so queues never own a task of their
 * own. Joining is validated against the player state machine, the kit, the queue configuration and, when
 * the player is in a party, against the party rules for the requested format.</p>
 */
public final class QueueManager implements QueueService, LightService {

    private final PluginCore core;
    private final Matchmaker matchmaker;
    private final Map<String, Queue> queues = new ConcurrentHashMap<String, Queue>();
    private final Map<UUID, String> queueOf = new ConcurrentHashMap<UUID, String>();
    private BukkitTask task;
    private long tickInterval = 20L;
    private long notifyIntervalMillis = 10000L;
    private boolean notifyPositions = true;

    public QueueManager(PluginCore core, Matchmaker matchmaker) {
        this.core = core;
        this.matchmaker = matchmaker == null ? new Matchmaker(core) : matchmaker;
    }

    @Override
    public String name() {
        return "queues";
    }

    @Override
    public int startupOrder() {
        return 75;
    }

    @Override
    public void onLoad() {
        load();
    }

    @Override
    public void onEnable() {
        readSettings();
        load();
        if (queues.isEmpty()) {
            installDefaults();
        }
        task = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, tickInterval, tickInterval);
        Debug.log(DebugCategory.QUEUE, "Queue system ready with {} queue(s)", queues.size());
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(task);
        task = null;
        for (UUID uuid : new ArrayList<UUID>(queueOf.keySet())) {
            leave(uuid);
        }
        for (Queue queue : queues.values()) {
            queue.clear();
        }
        queueOf.clear();
    }

    @Override
    public void onReload() {
        readSettings();
        load();
    }

    private void readSettings() {
        ConfigFile config = core.configs().config();
        this.tickInterval = Math.max(5L, config.getLong("queue.tick-interval", 20L));
        this.notifyIntervalMillis = Math.max(0L, config.getLong("queue.notify-interval-millis", 10000L));
        this.notifyPositions = config.getBoolean("queue.notify-position", true);
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        ConfigFile file = core.configs().queues();
        file.reload();
        queues.clear();
        queueOf.clear();
        ConfigurationSection root = file.section("queues");
        if (root == null) {
            Debug.log(DebugCategory.CONFIG, "queues.yml holds no 'queues' section");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Queue queue = Queue.read(key, section);
            if (queue == null) {
                continue;
            }
            List<String> problems = queue.problems();
            if (!problems.isEmpty()) {
                Debug.warn(DebugCategory.CONFIG, "Queue " + queue.id() + " is misconfigured: " + problems);
            }
            queues.put(queue.id(), queue);
        }
        Debug.log(DebugCategory.QUEUE, "Loaded {} queue(s)", queues.size());
    }

    public void save() {
        ConfigFile file = core.configs().queues();
        ConfigurationSection root = file.config().getConfigurationSection("queues");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                root.set(key, null);
            }
        }
        for (Queue queue : sorted()) {
            queue.write(file.getOrCreateSection("queues." + queue.id()));
        }
        file.save();
    }

    /** Creates a ranked and an unranked queue for every playable kit on a first run. */
    public void installDefaults() {
        KitManager kits = core.optional(KitManager.class);
        if (kits == null) {
            Debug.warn(DebugCategory.QUEUE, "No kit manager available, default queues were not created");
            return;
        }
        int created = 0;
        int order = 1;
        for (Kit kit : kits.sorted()) {
            if (!kit.enabled()) {
                continue;
            }
            if (kit.unranked()) {
                queues.put(Queue.idOf(kit.id(), false), defaultQueue(kit, false, order++));
                created++;
            }
            if (kit.ranked()) {
                queues.put(Queue.idOf(kit.id(), true), defaultQueue(kit, true, order++));
                created++;
            }
        }
        if (created > 0) {
            save();
            Debug.log(DebugCategory.QUEUE, "Created {} default queue(s)", created);
        }
    }

    private Queue defaultQueue(Kit kit, boolean ranked, int order) {
        Queue queue = new Queue(Queue.idOf(kit.id(), ranked), kit.id(), ranked);
        queue.displayName((ranked ? "&c" : "&a") + Text.strip(kit.displayName())
                + (ranked ? " &7(Ranked)" : " &7(Unranked)"));
        queue.type(MatchType.SOLO);
        queue.teamSize(1);
        queue.minimumPlayers(2);
        queue.maximumPlayers(2);
        queue.order(order);
        queue.icon(kit.iconMaterial(), kit.iconData());
        queue.lore(Collections.singletonList("&7Click to join this queue"));
        return queue;
    }

    // ------------------------------------------------------------- QueueService

    @Override
    public Queue get(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        Queue queue = queues.get(key);
        if (queue != null) {
            return queue;
        }
        for (Queue candidate : queues.values()) {
            if (candidate.displayName() != null
                    && Text.strip(candidate.displayName()).equalsIgnoreCase(Text.strip(id))) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Collection<Queue> queues() {
        return Collections.unmodifiableCollection(queues.values());
    }

    public List<Queue> sorted() {
        List<Queue> result = new ArrayList<Queue>(queues.values());
        Collections.sort(result, new Comparator<Queue>() {
            @Override
            public int compare(Queue left, Queue right) {
                int byOrder = Integer.compare(left.order(), right.order());
                return byOrder != 0 ? byOrder : left.id().compareTo(right.id());
            }
        });
        return result;
    }

    @Override
    public List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Queue queue : sorted()) {
            names.add(queue.id());
        }
        return names;
    }

    @Override
    public Queue forKit(String kitId, boolean ranked) {
        if (kitId == null) {
            return null;
        }
        String key = kitId.trim().toLowerCase(Locale.ROOT);
        for (Queue queue : queues.values()) {
            if (queue.kitId().equals(key) && queue.ranked() == ranked) {
                return queue;
            }
        }
        return null;
    }

    @Override
    public boolean isQueued(UUID uuid) {
        return uuid != null && queueOf.containsKey(uuid);
    }

    @Override
    public Queue queueOf(UUID uuid) {
        String id = uuid == null ? null : queueOf.get(uuid);
        return id == null ? null : queues.get(id);
    }

    @Override
    public QueueEntry entryOf(UUID uuid) {
        Queue queue = queueOf(uuid);
        return queue == null ? null : queue.entry(uuid);
    }

    @Override
    public int queuedCount() {
        return queueOf.size();
    }

    /** Position of a player inside their queue, {@code -1} when they are not queued. */
    public int positionOf(UUID uuid) {
        Queue queue = queueOf(uuid);
        return queue == null ? -1 : queue.position(uuid);
    }

    /** Estimated waiting time in milliseconds, used by scoreboards and the queue menu. */
    public long estimatedWait(UUID uuid) {
        Queue queue = queueOf(uuid);
        return queue == null ? 0L : matchmaker.estimateWait(queue);
    }

    @Override
    public List<Queue> eligible(Player player) {
        List<Queue> result = new ArrayList<Queue>();
        if (player == null) {
            return result;
        }
        KitManager kits = core.optional(KitManager.class);
        for (Queue queue : sorted()) {
            if (!queue.canJoin(player)) {
                continue;
            }
            Kit kit = kits == null ? null : kits.get(queue.kitId());
            if (kit == null || !kit.enabled()) {
                continue;
            }
            if (queue.ranked() ? !kit.ranked() : !kit.unranked()) {
                continue;
            }
            result.add(queue);
        }
        return result;
    }

    @Override
    public boolean joinRandom(Player player) {
        List<Queue> options = eligible(player);
        if (options.isEmpty()) {
            core.messages().send(player, "queue.none-available");
            return false;
        }
        // prefer queues that already have players waiting, then pick randomly among them
        List<Queue> busy = new ArrayList<Queue>();
        for (Queue queue : options) {
            if (!queue.isEmpty()) {
                busy.add(queue);
            }
        }
        List<Queue> pool = busy.isEmpty() ? options : busy;
        Queue queue = pool.get((int) (Math.random() * pool.size()));
        return join(player, queue);
    }

    @Override
    public boolean join(Player player, Queue queue) {
        if (player == null || queue == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session == null) {
            core.messages().send(player, "queue.not-ready");
            return false;
        }
        if (!queue.enabled()) {
            core.messages().send(player, "queue.disabled", "{queue}", queue.displayName());
            return false;
        }
        if (!queue.hasPermission(player)) {
            core.messages().send(player, "queue.no-permission", "{queue}", queue.displayName());
            return false;
        }
        if (!session.hasProfile()) {
            core.messages().send(player, "queue.profile-loading");
            return false;
        }
        if (!session.state().canQueue() && !queue.id().equals(queueOf.get(uuid))) {
            core.messages().send(player, "queue.busy", "{state}", session.state().name().toLowerCase(Locale.ROOT));
            return false;
        }
        BanService bans = core.optional(BanService.class);
        if (bans != null && bans.isBanned(uuid)) {
            core.messages().send(player, "queue.banned");
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(queue.kitId());
        if (kit == null || !kit.enabled()) {
            core.messages().send(player, "queue.kit-missing", "{queue}", queue.displayName());
            Debug.warn(DebugCategory.QUEUE, "Queue " + queue.id() + " points at an unknown kit");
            return false;
        }
        if (queue.ranked() && !kit.ranked()) {
            core.messages().send(player, "queue.kit-not-ranked", "{kit}", kit.displayName());
            return false;
        }
        if (!queue.ranked() && !kit.unranked()) {
            core.messages().send(player, "queue.kit-not-unranked", "{kit}", kit.displayName());
            return false;
        }
        List<UUID> group = resolveGroup(player, queue, session);
        if (group == null) {
            return false;
        }
        if (isQueued(uuid)) {
            Queue current = queueOf(uuid);
            if (current != null && current.id().equals(queue.id())) {
                core.messages().send(player, "queue.already-in", "{queue}", queue.displayName());
                return false;
            }
            leave(uuid);
        }
        LightPracticeQueueJoinEvent event = new LightPracticeQueueJoinEvent(player, queue);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.QUEUE, "{} was denied joining {} by a plugin", player.getName(), queue.id());
            return false;
        }
        int rating = ratingOf(uuid, kit, queue.ranked());
        QueueEntry entry = new QueueEntry(uuid, player.getName(), group, rating);
        if (!queue.add(entry)) {
            core.messages().send(player, "queue.join-failed", "{queue}", queue.displayName());
            return false;
        }
        for (UUID member : group) {
            queueOf.put(member, queue.id());
        }
        for (UUID member : group) {
            LightPlayer memberSession = players.get(member);
            if (memberSession != null) {
                players.setState(memberSession, PlayerState.QUEUE);
                memberSession.queueId(queue.id());
            }
        }
        queue.attemptMade();
        core.messages().send(player, "queue.joined",
                "{queue}", queue.displayName(),
                "{kit}", kit.displayName(),
                "{ranked}", queue.ranked() ? "yes" : "no",
                "{position}", String.valueOf(queue.position(uuid)),
                "{size}", String.valueOf(queue.size()),
                "{players}", String.valueOf(queue.size()),
                "{rating}", String.valueOf(rating));
        for (UUID member : group) {
            if (member.equals(uuid)) {
                continue;
            }
            Player other = Bukkit.getPlayer(member);
            if (other != null) {
                core.messages().send(other, "queue.joined-by-leader",
                        "{queue}", queue.displayName(), "{leader}", player.getName());
            }
        }
        Debug.log(DebugCategory.QUEUE, "{} joined {} with {} player(s) at rating {}", player.getName(),
                queue.id(), group.size(), rating);
        return true;
    }

    /** Players that have to enter the queue together, or {@code null} when joining is refused. */
    private List<UUID> resolveGroup(Player player, Queue queue, LightPlayer session) {
        List<UUID> group = new ArrayList<UUID>();
        group.add(player.getUniqueId());
        PartyService parties = core.optional(PartyService.class);
        Party party = parties == null ? null : parties.get(player);
        if (party == null) {
            return group;
        }
        if (!party.isLeader(player.getUniqueId())) {
            core.messages().send(player, "queue.party-leader-only");
            return null;
        }
        List<UUID> members = party.memberIds();
        if (members.size() <= 1) {
            return group;
        }
        if (queue.type().everyoneAlone()) {
            for (UUID member : members) {
                if (!canFollow(member, player)) {
                    core.messages().send(player, "queue.party-member-busy", "{player}", nameOf(member));
                    return null;
                }
            }
            return new ArrayList<UUID>(members);
        }
        if (members.size() != queue.teamSize()) {
            core.messages().send(player, "queue.party-size-mismatch",
                    "{required}", String.valueOf(queue.teamSize()),
                    "{size}", String.valueOf(members.size()));
            return null;
        }
        for (UUID member : members) {
            if (!canFollow(member, player)) {
                core.messages().send(player, "queue.party-member-busy", "{player}", nameOf(member));
                return null;
            }
        }
        return new ArrayList<UUID>(members);
    }

    private boolean canFollow(UUID member, Player leader) {
        if (member == null || member.equals(leader.getUniqueId())) {
            return true;
        }
        Player player = Bukkit.getPlayer(member);
        if (player == null || !player.isOnline()) {
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        return session != null && session.hasProfile() && session.state().canQueue();
    }

    private String nameOf(UUID uuid) {
        Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        return player == null ? "unknown" : player.getName();
    }

    private int ratingOf(UUID uuid, Kit kit, boolean ranked) {
        if (!ranked) {
            return 0;
        }
        StatsService statistics = core.optional(StatsService.class);
        return statistics == null ? 1000 : statistics.elo(uuid, kit == null ? null : kit.id());
    }

    @Override
    public boolean leave(Player player) {
        return player != null && leave(player.getUniqueId());
    }

    @Override
    public boolean leave(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        String id = queueOf.remove(uuid);
        if (id == null) {
            return false;
        }
        Queue queue = queues.get(id);
        QueueEntry entry = queue == null ? null : queue.remove(uuid);
        if (entry != null) {
            for (UUID member : entry.group()) {
                queueOf.remove(member);
            }
        }
        PlayerManager players = core.optional(PlayerManager.class);
        List<UUID> affected = entry == null ? Collections.singletonList(uuid) : entry.group();
        for (UUID member : affected) {
            LightPlayer session = players == null ? null : players.get(member);
            if (session != null && session.state() == PlayerState.QUEUE) {
                players.setState(session, PlayerState.LOBBY);
            }
            if (session != null) {
                session.queueId(null);
            }
            Player player = Bukkit.getPlayer(member);
            if (player != null && queue != null) {
                Bukkit.getPluginManager().callEvent(new LightPracticeQueueLeaveEvent(player, queue));
                core.messages().send(player, "queue.left", "{queue}", queue.displayName());
            }
        }
        Debug.log(DebugCategory.QUEUE, "{} left queue {}", nameOf(uuid), id);
        return true;
    }

    @Override
    public void removeAll(UUID uuid) {
        if (uuid == null) {
            return;
        }
        leave(uuid);
        for (Queue queue : queues.values()) {
            QueueEntry entry = queue.entry(uuid);
            if (entry != null) {
                queue.remove(uuid);
                for (UUID member : entry.group()) {
                    queueOf.remove(member);
                }
            }
        }
    }

    // -------------------------------------------------------------- matchmaking

    /** Central matchmaking pass, run by one task for every queue. */
    public void tick() {
        if (queues.isEmpty()) {
            return;
        }
        for (Queue queue : sorted()) {
            try {
                process(queue);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.QUEUE, "Queue " + queue.id() + " failed during matchmaking", throwable);
            }
        }
    }

    private void process(Queue queue) {
        if (!queue.enabled()) {
            return;
        }
        if (queue.hasEnoughPlayers()
                && System.currentTimeMillis() - queue.lastAttemptAt() >= matchmaker.retryDelayMillis()) {
            queue.attemptMade();
            List<MatchPairing> pairings = matchmaker.findMatches(queue);
            for (MatchPairing pairing : pairings) {
                start(pairing);
            }
        }
        if (notifyPositions && notifyIntervalMillis > 0L) {
            notifyPositions(queue);
        }
    }

    private void notifyPositions(Queue queue) {
        long now = System.currentTimeMillis();
        for (QueueEntry entry : queue.entries()) {
            if (now - entry.notifiedAt() < notifyIntervalMillis) {
                continue;
            }
            entry.notifiedAt(now);
            int position = queue.position(entry.uuid());
            for (UUID member : entry.group()) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) {
                    core.messages().send(player, "queue.position",
                            "{queue}", queue.displayName(),
                            "{position}", String.valueOf(position),
                            "{size}", String.valueOf(queue.size()),
                            "{players}", String.valueOf(queue.size()),
                            "{wait}", Text.duration(matchmaker.estimateWait(queue)));
                }
            }
        }
    }

    /** Turns a pairing into a match; the entries stay queued when no arena is free. */
    public boolean start(MatchPairing pairing) {
        if (pairing == null || !pairing.isValid()) {
            return false;
        }
        Queue queue = pairing.queue();
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(queue.kitId());
        if (kit == null) {
            Debug.warn(DebugCategory.QUEUE, "Queue " + queue.id() + " points at an unknown kit");
            return false;
        }
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            Debug.warn(DebugCategory.QUEUE, "No match manager registered, queue " + queue.id() + " is stuck");
            return false;
        }
        MatchType type = queue.type().everyoneAlone() ? MatchType.FFA
                : queue.teamSize() > 1 ? MatchType.TEAMS : MatchType.SOLO;
        MatchRequest request = new MatchRequest(kit, type, queue.ranked()).source("queue");
        int index = 0;
        for (List<QueueEntry> side : pairing.sides()) {
            List<UUID> members = new ArrayList<UUID>();
            for (QueueEntry entry : side) {
                members.addAll(entry.group());
            }
            String name = members.size() == 1 ? nameOf(members.get(0)) : "team" + (index + 1);
            request.addTeam(name, members);
            index++;
        }
        if (queue.arenaName() != null) {
            ArenaManager arenas = core.optional(ArenaManager.class);
            Arena arena = arenas == null ? null : arenas.get(queue.arenaName());
            if (arena != null) {
                request.arena(arena);
            } else {
                Debug.warn(DebugCategory.QUEUE, "Queue " + queue.id() + " points at unknown arena "
                        + queue.arenaName());
            }
        }
        Match match = matches.startMatch(request);
        if (match == null) {
            for (UUID uuid : pairing.participants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    core.messages().send(player, "queue.no-arena", "{kit}", kit.displayName());
                }
            }
            return false;
        }
        for (QueueEntry entry : pairing.entries()) {
            queue.remove(entry.uuid());
            for (UUID member : entry.group()) {
                queueOf.remove(member);
            }
        }
        queue.matchStarted();
        for (UUID uuid : pairing.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                core.messages().send(player, "queue.match-found",
                        "{kit}", kit.displayName(),
                        "{arena}", match.arena() == null ? "none" : match.arena().name(),
                        "{ranked}", match.ranked() ? "yes" : "no",
                        "{opponents}", opponentNames(match, uuid));
            }
        }
        Debug.log(DebugCategory.QUEUE, "Queue {} produced match {} with {} player(s)", queue.id(),
                match.identifier(), pairing.participantCount());
        return true;
    }

    private String opponentNames(Match match, UUID uuid) {
        List<String> names = new ArrayList<String>();
        for (gg.lightpractice.match.MatchPlayer participant : match.participants()) {
            if (participant.uuid() == null || participant.uuid().equals(uuid)) {
                continue;
            }
            gg.lightpractice.match.MatchTeam own = match.teamOf(uuid);
            if (own != null && own.equals(participant.team()) && match.teams().size() > 1) {
                continue;
            }
            names.add(participant.name());
        }
        return Text.join(names, ", ");
    }

    // -------------------------------------------------------------------- admin

    /** Creates a queue, returning {@code null} when the id is taken or the kit is unknown. */
    public Queue create(String id, String kitId, boolean ranked) {
        if (id == null || id.trim().isEmpty() || kitId == null || kitId.trim().isEmpty()) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (queues.containsKey(key)) {
            return null;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(kitId);
        if (kit == null) {
            return null;
        }
        Queue queue = defaultQueue(kit, ranked, queues.size() + 1);
        queue.displayName((ranked ? "&c" : "&a") + Text.strip(kit.displayName())
                + (ranked ? " &7(Ranked)" : " &7(Unranked)"));
        queues.put(key, queue);
        save();
        Debug.log(DebugCategory.QUEUE, "Created queue {}", key);
        return queue;
    }

    public boolean delete(String id) {
        Queue queue = get(id);
        if (queue == null) {
            return false;
        }
        for (UUID uuid : queue.queuedPlayers()) {
            leave(uuid);
        }
        queues.remove(queue.id());
        save();
        Debug.log(DebugCategory.QUEUE, "Deleted queue {}", queue.id());
        return true;
    }

    public Matchmaker matchmaker() {
        return matchmaker;
    }

    /** Total number of players waiting across every queue, used by placeholders. */
    public int totalWaiting() {
        int total = 0;
        for (Queue queue : queues.values()) {
            total += queue.size();
        }
        return total;
    }

    /** Queues of one kit, used by the kit menu. */
    public List<Queue> forKit(String kitId) {
        List<Queue> result = new ArrayList<Queue>();
        if (kitId == null) {
            return result;
        }
        String key = kitId.trim().toLowerCase(Locale.ROOT);
        for (Queue queue : sorted()) {
            if (queue.kitId().equals(key)) {
                result.add(queue);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "QueueManager{queues=" + queues.size() + ", waiting=" + totalWaiting() + '}';
    }
}
