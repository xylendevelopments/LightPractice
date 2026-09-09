package gg.lightpractice.duel;

import gg.lightpractice.api.DuelService;
import gg.lightpractice.api.MatchService;
import gg.lightpractice.api.event.LightPracticeDuelAcceptEvent;
import gg.lightpractice.api.event.LightPracticeDuelRequestEvent;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Cooldowns;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct challenges between two players.
 *
 * <p>A request stores both players, the kit, an optional arena and whether rating is at stake. Both sides
 * are re-validated when the request is accepted, because the state of either player can change while the
 * challenge is pending.</p>
 */
public final class DuelManager implements DuelService, LightService {

    private final PluginCore core;
    private final Map<UUID, DuelRequest> incoming = new ConcurrentHashMap<UUID, DuelRequest>();
    private final Map<UUID, DuelRequest> outgoing = new ConcurrentHashMap<UUID, DuelRequest>();
    private final Cooldowns<UUID> sendCooldown = new Cooldowns<UUID>();
    private BukkitTask expiryTask;
    private long expiryMillis = 60000L;
    private long cooldownMillis = 3000L;

    public DuelManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "duels";
    }

    @Override
    public int startupOrder() {
        return 76;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
        expiryTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                expireRequests();
            }
        }, 100L, 100L);
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(expiryTask);
        expiryTask = null;
        incoming.clear();
        outgoing.clear();
        sendCooldown.clear();
    }

    @Override
    public void onReload() {
        readConfiguration();
    }

    private void readConfiguration() {
        ConfigFile config = core.configs().config();
        this.expiryMillis = Math.max(5000L, config.getLong("duels.expiry-seconds", 60L) * 1000L);
        this.cooldownMillis = Math.max(0L, config.getLong("duels.send-cooldown-millis", 3000L));
    }

    @Override
    public long expiryMillis() {
        return expiryMillis;
    }

    // ---------------------------------------------------------------- DuelService

    @Override
    public boolean send(Player sender, Player target, String kitId, String arenaName, boolean ranked) {
        if (sender == null || target == null) {
            return false;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            core.messages().send(sender, "duel.self");
            return false;
        }
        if (!sendCooldown.ready(sender.getUniqueId())) {
            core.messages().send(sender, "duel.cooldown",
                    "{seconds}", String.valueOf((sendCooldown.remaining(sender.getUniqueId()) + 999L) / 1000L));
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer senderSession = players == null ? null : players.get(sender);
        LightPlayer targetSession = players == null ? null : players.get(target);
        if (senderSession == null || targetSession == null) {
            core.messages().send(sender, "duel.not-ready");
            return false;
        }
        if (!senderSession.state().canDuel()) {
            core.messages().send(sender, "duel.self-busy", "{state}",
                    senderSession.state().name().toLowerCase(java.util.Locale.ROOT));
            return false;
        }
        if (!targetSession.state().canDuel()) {
            core.messages().send(sender, "duel.target-busy", "{player}", target.getName(),
                    "{state}", targetSession.state().name().toLowerCase(java.util.Locale.ROOT));
            return false;
        }
        if (!targetSession.hasProfile() || !senderSession.hasProfile()) {
            core.messages().send(sender, "duel.profile-loading");
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(kitId);
        if (kit == null || !kit.enabled()) {
            core.messages().send(sender, "duel.unknown-kit", "{kit}", String.valueOf(kitId));
            return false;
        }
        if (!kit.duel()) {
            core.messages().send(sender, "duel.kit-not-duellable", "{kit}", kit.displayName());
            return false;
        }
        if (ranked && !kit.ranked()) {
            core.messages().send(sender, "duel.kit-not-ranked", "{kit}", kit.displayName());
            return false;
        }
        if (!kit.hasPermission(sender)) {
            core.messages().send(sender, "duel.no-kit-permission", "{kit}", kit.displayName());
            return false;
        }
        Arena arena = null;
        if (arenaName != null && !arenaName.trim().isEmpty()) {
            ArenaManager arenas = core.optional(ArenaManager.class);
            arena = arenas == null ? null : arenas.get(arenaName);
            if (arena == null) {
                core.messages().send(sender, "duel.unknown-arena", "{arena}", arenaName);
                return false;
            }
            if (!arena.acceptsKit(kit)) {
                core.messages().send(sender, "duel.arena-kit-mismatch", "{arena}", arena.name(),
                        "{kit}", kit.displayName());
                return false;
            }
        }
        if (hasOutgoing(sender.getUniqueId())) {
            core.messages().send(sender, "duel.already-sent");
            return false;
        }
        if (hasIncoming(target.getUniqueId())) {
            core.messages().send(sender, "duel.target-has-request", "{player}", target.getName());
            return false;
        }
        LightPracticeDuelRequestEvent event = new LightPracticeDuelRequestEvent(sender, target, kit, arena);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.DUEL, "A plugin cancelled the duel request of {}", sender.getName());
            return false;
        }
        DuelRequest request = new DuelRequest(sender.getUniqueId(), sender.getName(), target.getUniqueId(),
                target.getName(), kit.id(), kit.displayName(), arena == null ? null : arena.name(), ranked,
                expiryMillis);
        outgoing.put(sender.getUniqueId(), request);
        incoming.put(target.getUniqueId(), request);
        sendCooldown.mark(sender.getUniqueId(), cooldownMillis);
        core.messages().send(sender, "duel.sent",
                "{player}", target.getName(), "{kit}", kit.displayName(),
                "{arena}", arena == null ? "random" : arena.name(),
                "{ranked}", ranked ? "yes" : "no",
                "{seconds}", String.valueOf(expiryMillis / 1000L));
        core.messages().send(target, "duel.received",
                "{player}", sender.getName(), "{kit}", kit.displayName(),
                "{arena}", arena == null ? "random" : arena.name(),
                "{ranked}", ranked ? "yes" : "no",
                "{seconds}", String.valueOf(expiryMillis / 1000L));
        Debug.log(DebugCategory.DUEL, "{} challenged {} to a {} {} duel", sender.getName(), target.getName(),
                ranked ? "ranked" : "unranked", kit.id());
        return true;
    }

    @Override
    public boolean accept(Player receiver) {
        if (receiver == null) {
            return false;
        }
        DuelRequest request = incoming.get(receiver.getUniqueId());
        if (request == null) {
            core.messages().send(receiver, "duel.no-request");
            return false;
        }
        if (request.isExpired()) {
            remove(request);
            core.messages().send(receiver, "duel.expired", "{player}", request.senderName());
            return false;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            remove(request);
            core.messages().send(receiver, "duel.sender-offline", "{player}", request.senderName());
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer senderSession = players == null ? null : players.get(sender);
        LightPlayer receiverSession = players == null ? null : players.get(receiver);
        if (senderSession == null || receiverSession == null) {
            core.messages().send(receiver, "duel.not-ready");
            return false;
        }
        if (!senderSession.state().canDuel() || !receiverSession.state().canDuel()) {
            core.messages().send(receiver, "duel.no-longer-available", "{player}", sender.getName());
            core.messages().send(sender, "duel.no-longer-available", "{player}", receiver.getName());
            remove(request);
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(request.kitId());
        if (kit == null || !kit.enabled() || !kit.duel()) {
            core.messages().send(receiver, "duel.kit-unavailable", "{kit}", request.kitName());
            core.messages().send(sender, "duel.kit-unavailable", "{kit}", request.kitName());
            remove(request);
            return false;
        }
        Arena arena = null;
        if (request.arenaName() != null) {
            ArenaManager arenas = core.optional(ArenaManager.class);
            arena = arenas == null ? null : arenas.get(request.arenaName());
            if (arena == null || !arena.isAvailable() || !arena.acceptsKit(kit)) {
                core.messages().send(receiver, "duel.arena-unavailable", "{arena}", request.arenaName());
                core.messages().send(sender, "duel.arena-unavailable", "{arena}", request.arenaName());
                return false;
            }
        }
        LightPracticeDuelAcceptEvent event = new LightPracticeDuelAcceptEvent(sender, receiver, request);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.DUEL, "A plugin cancelled the accepted duel of {}", receiver.getName());
            return false;
        }
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            core.messages().send(receiver, "duel.not-ready");
            return false;
        }
        MatchRequest matchRequest = MatchRequest.duel(sender, receiver, kit, request.ranked())
                .source(request.ranked() ? "ranked-duel" : "duel");
        if (arena != null) {
            matchRequest.arena(arena);
        }
        request.accepted(true);
        remove(request);
        Match match = matches.startMatch(matchRequest);
        if (match == null) {
            core.messages().send(receiver, "duel.start-failed", "{player}", sender.getName());
            core.messages().send(sender, "duel.start-failed", "{player}", receiver.getName());
            return false;
        }
        core.messages().send(sender, "duel.accepted", "{player}", receiver.getName(), "{kit}", kit.displayName());
        core.messages().send(receiver, "duel.accepted-by-you", "{player}", sender.getName(),
                "{kit}", kit.displayName());
        Debug.log(DebugCategory.DUEL, "{} accepted the duel of {}, match {}", receiver.getName(),
                sender.getName(), match.identifier());
        return true;
    }

    @Override
    public boolean deny(Player receiver) {
        if (receiver == null) {
            return false;
        }
        DuelRequest request = incoming.get(receiver.getUniqueId());
        if (request == null) {
            core.messages().send(receiver, "duel.no-request");
            return false;
        }
        remove(request);
        core.messages().send(receiver, "duel.denied", "{player}", request.senderName());
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            core.messages().send(sender, "duel.denied-by-target", "{player}", receiver.getName());
        }
        Debug.log(DebugCategory.DUEL, "{} denied the duel of {}", receiver.getName(), request.senderName());
        return true;
    }

    /** A sender takes back their own challenge. */
    public boolean cancel(Player sender) {
        if (sender == null) {
            return false;
        }
        DuelRequest request = outgoing.get(sender.getUniqueId());
        if (request == null) {
            core.messages().send(sender, "duel.no-outgoing");
            return false;
        }
        remove(request);
        core.messages().send(sender, "duel.cancelled", "{player}", request.receiverName());
        Player receiver = Bukkit.getPlayer(request.receiver());
        if (receiver != null) {
            core.messages().send(receiver, "duel.cancelled-by-sender", "{player}", sender.getName());
        }
        return true;
    }

    @Override
    public DuelRequest incoming(UUID uuid) {
        DuelRequest request = uuid == null ? null : incoming.get(uuid);
        if (request != null && request.isExpired()) {
            remove(request);
            return null;
        }
        return request;
    }

    @Override
    public DuelRequest outgoing(UUID uuid) {
        DuelRequest request = uuid == null ? null : outgoing.get(uuid);
        if (request != null && request.isExpired()) {
            remove(request);
            return null;
        }
        return request;
    }

    @Override
    public boolean hasIncoming(UUID uuid) {
        return incoming(uuid) != null;
    }

    @Override
    public boolean hasOutgoing(UUID uuid) {
        return outgoing(uuid) != null;
    }

    @Override
    public void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        DuelRequest received = incoming.remove(uuid);
        if (received != null) {
            outgoing.remove(received.sender());
            incoming.remove(received.receiver());
        }
        DuelRequest sent = outgoing.remove(uuid);
        if (sent != null) {
            incoming.remove(sent.receiver());
            outgoing.remove(sent.sender());
        }
        sendCooldown.remove(uuid);
    }

    /** Every pending request, used by the staff overview. */
    public List<DuelRequest> requests() {
        List<DuelRequest> requests = new ArrayList<DuelRequest>(incoming.values());
        return Collections.unmodifiableList(requests);
    }

    public int pendingCount() {
        return incoming.size();
    }

    private void remove(DuelRequest request) {
        if (request == null) {
            return;
        }
        if (request.sender() != null) {
            outgoing.remove(request.sender());
        }
        if (request.receiver() != null) {
            incoming.remove(request.receiver());
        }
    }

    /** Drops expired requests and tells both players, run by one central task. */
    private void expireRequests() {
        if (incoming.isEmpty()) {
            return;
        }
        for (DuelRequest request : new ArrayList<DuelRequest>(incoming.values())) {
            if (!request.isExpired()) {
                continue;
            }
            remove(request);
            Player sender = Bukkit.getPlayer(request.sender());
            Player receiver = Bukkit.getPlayer(request.receiver());
            if (sender != null) {
                core.messages().send(sender, "duel.expired-sender", "{player}", request.receiverName());
            }
            if (receiver != null) {
                core.messages().send(receiver, "duel.expired", "{player}", request.senderName());
            }
            Debug.log(DebugCategory.DUEL, "Duel request {} -> {} expired", request.senderName(),
                    request.receiverName());
        }
        sendCooldown.purge();
    }

    /** Whether two players have a pending request between them, used by tab and scoreboards. */
    public boolean hasRequestBetween(UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }
        DuelRequest request = incoming(first);
        if (request != null && second.equals(request.sender())) {
            return true;
        }
        DuelRequest sent = outgoing(first);
        return sent != null && second.equals(sent.receiver());
    }

    @Override
    public String toString() {
        return "DuelManager{pending=" + incoming.size() + '}';
    }

    /** Human readable summary of a request, used by menus. */
    public static String describe(DuelRequest request) {
        if (request == null) {
            return "none";
        }
        return request.senderName() + " -> " + request.receiverName() + " (" + Text.strip(request.kitName()) + ")";
    }
}
