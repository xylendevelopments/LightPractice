package gg.lightpractice.party;

import gg.lightpractice.api.PartyService;
import gg.lightpractice.api.event.LightPracticePartyCreateEvent;
import gg.lightpractice.api.event.LightPracticePartyDisbandEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.model.ChatChannel;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parties: grouping, invites, party chat and the three party match formats.
 *
 * <p>Party matches reuse the ordinary match engine, so a party FFA, a split team match and a party versus
 * party duel only differ in the {@link MatchRequest} they build. Members that disconnect are kept for a
 * grace period instead of being dropped immediately.</p>
 */
public final class PartyManager implements PartyService, LightService {

    private final PluginCore core;
    private final Map<String, Party> parties = new ConcurrentHashMap<String, Party>();
    private final Map<UUID, String> memberToParty = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, Long> offlineSince = new ConcurrentHashMap<UUID, Long>();
    private BukkitTask housekeepingTask;
    private int maxSize = 8;
    private long inviteExpiryMillis = 60000L;
    private long quitGraceMillis = 60000L;
    private boolean disbandOnLeaderQuit = true;
    private boolean ffaAllowedByDefault = true;

    public PartyManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "parties";
    }

    @Override
    public int startupOrder() {
        return 74;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
        housekeepingTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                housekeep();
            }
        }, 200L, 200L);
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(housekeepingTask);
        housekeepingTask = null;
        for (Party party : new ArrayList<Party>(parties.values())) {
            disbandParty(party, false);
        }
        parties.clear();
        memberToParty.clear();
        offlineSince.clear();
    }

    @Override
    public void onReload() {
        readConfiguration();
    }

    private void readConfiguration() {
        ConfigFile file = core.configs().parties();
        this.maxSize = Math.max(2, file.getInt("max-size", 8));
        this.inviteExpiryMillis = Math.max(5000L, file.getLong("invite-expiry-seconds", 60L) * 1000L);
        this.quitGraceMillis = Math.max(0L, file.getLong("quit-grace-seconds", 60L) * 1000L);
        this.disbandOnLeaderQuit = file.getBoolean("disband-on-leader-quit", true);
        this.ffaAllowedByDefault = file.getBoolean("allow-ffa", true);
        Debug.log(DebugCategory.PARTY, "Parties: max {} members, invites {}s, quit grace {}s",
                maxSize, inviteExpiryMillis / 1000L, quitGraceMillis / 1000L);
    }

    public int maxSize() {
        return maxSize;
    }

    public long inviteExpiryMillis() {
        return inviteExpiryMillis;
    }

    // ------------------------------------------------------------- PartyService

    @Override
    public Party get(UUID uuid) {
        String id = uuid == null ? null : memberToParty.get(uuid);
        return id == null ? null : parties.get(id);
    }

    @Override
    public Party get(Player player) {
        return player == null ? null : get(player.getUniqueId());
    }

    @Override
    public Party byId(String partyId) {
        return partyId == null ? null : parties.get(partyId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Collection<Party> parties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    @Override
    public boolean isInParty(UUID uuid) {
        return get(uuid) != null;
    }

    @Override
    public Party create(Player leader) {
        if (leader == null) {
            return null;
        }
        if (isInParty(leader.getUniqueId())) {
            core.messages().send(leader, "party.already-in");
            return null;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(leader);
        if (session != null && !session.state().canQueue()) {
            core.messages().send(leader, "party.busy", "{state}",
                    session.state().name().toLowerCase(java.util.Locale.ROOT));
            return null;
        }
        String id = newId();
        Party party = new Party(id, leader.getUniqueId());
        party.maxSize(maxSize);
        party.ffaAllowed(ffaAllowedByDefault);
        party.add(new PartyMember(leader.getUniqueId(), leader.getName(), PartyRank.LEADER));
        LightPracticePartyCreateEvent event = new LightPracticePartyCreateEvent(leader, party);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.PARTY, "A plugin cancelled the party creation of {}", leader.getName());
            return null;
        }
        parties.put(id, party);
        memberToParty.put(leader.getUniqueId(), id);
        if (session != null) {
            session.partyId(id);
        }
        core.messages().send(leader, "party.created", "{party}", id, "{max}", String.valueOf(maxSize));
        Debug.log(DebugCategory.PARTY, "{} created party {}", leader.getName(), id);
        return party;
    }

    private String newId() {
        String id;
        int attempt = 0;
        do {
            id = UUID.randomUUID().toString().substring(0, 6).toLowerCase(java.util.Locale.ROOT);
            attempt++;
        } while (parties.containsKey(id) && attempt < 50);
        return id;
    }

    @Override
    public boolean invite(Player leader, Player target) {
        if (leader == null || target == null) {
            return false;
        }
        Party party = get(leader);
        if (party == null) {
            core.messages().send(leader, "party.not-in");
            return false;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            core.messages().send(leader, "party.leader-only");
            return false;
        }
        if (leader.getUniqueId().equals(target.getUniqueId())) {
            core.messages().send(leader, "party.invite-self");
            return false;
        }
        if (isInParty(target.getUniqueId())) {
            core.messages().send(leader, "party.target-in-party", "{player}", target.getName());
            return false;
        }
        if (party.isFull()) {
            core.messages().send(leader, "party.full", "{max}", String.valueOf(party.maxSize()));
            return false;
        }
        if (party.hasInvited(target.getUniqueId())) {
            core.messages().send(leader, "party.already-invited", "{player}", target.getName());
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer targetSession = players == null ? null : players.get(target);
        if (targetSession != null && targetSession.state().busy()) {
            core.messages().send(leader, "party.target-busy", "{player}", target.getName());
            return false;
        }
        if (!party.invite(target.getUniqueId(), inviteExpiryMillis)) {
            core.messages().send(leader, "party.invite-failed", "{player}", target.getName());
            return false;
        }
        core.messages().send(leader, "party.invited", "{player}", target.getName(),
                "{seconds}", String.valueOf(inviteExpiryMillis / 1000L));
        core.messages().send(target, "party.invite-received", "{player}", leader.getName(),
                "{party}", party.id(), "{seconds}", String.valueOf(inviteExpiryMillis / 1000L));
        announce(party, "party.broadcast-invite", "{player}", target.getName(), "{sender}", leader.getName());
        Debug.log(DebugCategory.PARTY, "{} invited {} to party {}", leader.getName(), target.getName(),
                party.id());
        return true;
    }

    @Override
    public boolean accept(Player player) {
        if (player == null) {
            return false;
        }
        Party party = invitedParty(player.getUniqueId());
        if (party == null) {
            core.messages().send(player, "party.no-invite");
            return false;
        }
        if (isInParty(player.getUniqueId())) {
            core.messages().send(player, "party.already-in");
            return false;
        }
        if (party.isFull()) {
            core.messages().send(player, "party.full", "{max}", String.valueOf(party.maxSize()));
            party.revokeInvite(player.getUniqueId());
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(player);
        if (session != null && session.state().busy()) {
            core.messages().send(player, "party.busy", "{state}",
                    session.state().name().toLowerCase(java.util.Locale.ROOT));
            return false;
        }
        party.add(new PartyMember(player.getUniqueId(), player.getName(), PartyRank.MEMBER));
        memberToParty.put(player.getUniqueId(), party.id());
        if (session != null) {
            session.partyId(party.id());
        }
        offlineSince.remove(player.getUniqueId());
        core.messages().send(player, "party.joined", "{party}", party.id(), "{size}",
                String.valueOf(party.size()));
        announce(party, "party.broadcast-join", "{player}", player.getName());
        Debug.log(DebugCategory.PARTY, "{} joined party {}", player.getName(), party.id());
        return true;
    }

    /** Party that invited a player, {@code null} when there is none or it expired. */
    public Party invitedParty(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Party party : parties.values()) {
            if (party.hasInvited(uuid)) {
                return party;
            }
        }
        return null;
    }

    @Override
    public boolean deny(Player player) {
        if (player == null) {
            return false;
        }
        Party party = invitedParty(player.getUniqueId());
        if (party == null) {
            core.messages().send(player, "party.no-invite");
            return false;
        }
        party.revokeInvite(player.getUniqueId());
        core.messages().send(player, "party.invite-denied", "{party}", party.id());
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader != null) {
            core.messages().send(leader, "party.invite-denied-by-target", "{player}", player.getName());
        }
        return true;
    }

    @Override
    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        Party party = get(player);
        if (party == null) {
            core.messages().send(player, "party.not-in");
            return false;
        }
        boolean wasLeader = party.isLeader(player.getUniqueId());
        removeMember(party, player.getUniqueId(), player);
        if (wasLeader) {
            if (party.size() == 0 || disbandOnLeaderQuit) {
                disbandParty(party, true);
                core.messages().send(player, "party.left-as-leader", "{party}", party.id());
                return true;
            }
            UUID next = party.nextLeader();
            if (next != null) {
                party.promote(next);
                announce(party, "party.broadcast-promoted", "{player}", nameOf(next));
            }
        }
        core.messages().send(player, "party.left", "{party}", party.id());
        return true;
    }

    @Override
    public boolean kick(Player leader, Player target) {
        if (leader == null || target == null) {
            return false;
        }
        Party party = get(leader);
        if (party == null) {
            core.messages().send(leader, "party.not-in");
            return false;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            core.messages().send(leader, "party.leader-only");
            return false;
        }
        if (leader.getUniqueId().equals(target.getUniqueId())) {
            core.messages().send(leader, "party.kick-self");
            return false;
        }
        if (!party.contains(target.getUniqueId())) {
            core.messages().send(leader, "party.not-member", "{player}", target.getName());
            return false;
        }
        removeMember(party, target.getUniqueId(), target);
        core.messages().send(leader, "party.kicked", "{player}", target.getName());
        core.messages().send(target, "party.kicked-from", "{player}", leader.getName(), "{party}", party.id());
        announce(party, "party.broadcast-kick", "{player}", target.getName());
        Debug.log(DebugCategory.PARTY, "{} kicked {} from party {}", leader.getName(), target.getName(),
                party.id());
        return true;
    }

    @Override
    public boolean promote(Player leader, Player target) {
        if (leader == null || target == null) {
            return false;
        }
        Party party = get(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            core.messages().send(leader, party == null ? "party.not-in" : "party.leader-only");
            return false;
        }
        if (!party.contains(target.getUniqueId())) {
            core.messages().send(leader, "party.not-member", "{player}", target.getName());
            return false;
        }
        if (!party.promote(target.getUniqueId())) {
            core.messages().send(leader, "party.promote-failed", "{player}", target.getName());
            return false;
        }
        core.messages().send(leader, "party.promoted", "{player}", target.getName());
        core.messages().send(target, "party.promoted-you", "{player}", leader.getName());
        announce(party, "party.broadcast-promoted", "{player}", target.getName());
        return true;
    }

    @Override
    public boolean disband(Player leader) {
        if (leader == null) {
            return false;
        }
        Party party = get(leader);
        if (party == null) {
            core.messages().send(leader, "party.not-in");
            return false;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            core.messages().send(leader, "party.leader-only");
            return false;
        }
        disbandParty(party, true);
        core.messages().send(leader, "party.disbanded", "{party}", party.id());
        return true;
    }

    private void disbandParty(Party party, boolean notify) {
        if (party == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new LightPracticePartyDisbandEvent(party, party.leader()));
        parties.remove(party.id());
        for (PartyMember member : party.members()) {
            memberToParty.remove(member.uuid());
            offlineSince.remove(member.uuid());
            LightPlayer session = sessionOf(member.uuid());
            if (session != null) {
                session.partyId(null);
            }
        }
        if (notify) {
            announce(party, "party.broadcast-disband");
        }
        Debug.log(DebugCategory.PARTY, "Party {} was disbanded", party.id());
    }

    private void removeMember(Party party, UUID uuid, Player player) {
        party.remove(uuid);
        memberToParty.remove(uuid);
        offlineSince.remove(uuid);
        LightPlayer session = sessionOf(uuid);
        if (session != null) {
            session.partyId(null);
        }
        if (party.size() == 0) {
            disbandParty(party, false);
        }
    }

    @Override
    public boolean setOpen(Party party, boolean open) {
        if (party == null) {
            return false;
        }
        party.open(open);
        announce(party, open ? "party.broadcast-open" : "party.broadcast-closed");
        return true;
    }

    @Override
    public boolean setFfaAllowed(Party party, boolean allowed) {
        if (party == null) {
            return false;
        }
        party.ffaAllowed(allowed);
        announce(party, allowed ? "party.broadcast-ffa-on" : "party.broadcast-ffa-off");
        return true;
    }

    /** Selects the kit party matches use. */
    public boolean setKit(Party party, Player leader, String kitId) {
        if (party == null || leader == null) {
            return false;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            core.messages().send(leader, "party.leader-only");
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(kitId);
        if (kit == null || !kit.enabled()) {
            core.messages().send(leader, "party.unknown-kit", "{kit}", String.valueOf(kitId));
            return false;
        }
        party.kitId(kit.id());
        core.messages().send(leader, "party.kit-selected", "{kit}", kit.displayName());
        announce(party, "party.broadcast-kit", "{kit}", kit.displayName(), "{player}", leader.getName());
        return true;
    }

    // ------------------------------------------------------------ party matches

    @Override
    public boolean startFfa(Party party) {
        if (party == null) {
            return false;
        }
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader == null) {
            return false;
        }
        if (!party.ffaAllowed()) {
            core.messages().send(leader, "party.ffa-disabled");
            return false;
        }
        List<UUID> online = party.onlineIds();
        if (online.size() < 2) {
            core.messages().send(leader, "party.needs-players", "{required}", "2");
            return false;
        }
        Kit kit = resolveKit(party, leader);
        if (kit == null) {
            return false;
        }
        if (!kit.ffa()) {
            core.messages().send(leader, "party.kit-not-ffa", "{kit}", kit.displayName());
            return false;
        }
        if (!allReady(online, leader)) {
            return false;
        }
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return false;
        }
        Match match = matches.startMatch(MatchRequest.ffa(kit, online, "party"));
        if (match == null) {
            core.messages().send(leader, "party.match-failed", "{kit}", kit.displayName());
            return false;
        }
        announce(party, "party.broadcast-ffa-start", "{kit}", kit.displayName(), "{players}",
                String.valueOf(online.size()));
        Debug.log(DebugCategory.PARTY, "Party {} started an FFA with {} players", party.id(), online.size());
        return true;
    }

    @Override
    public boolean startSplit(Party party) {
        if (party == null) {
            return false;
        }
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader == null) {
            return false;
        }
        List<UUID> online = party.onlineIds();
        if (online.size() < 2) {
            core.messages().send(leader, "party.needs-players", "{required}", "2");
            return false;
        }
        if (online.size() % 2 != 0) {
            core.messages().send(leader, "party.needs-even", "{players}", String.valueOf(online.size()));
            return false;
        }
        Kit kit = resolveKit(party, leader);
        if (kit == null) {
            return false;
        }
        if (!kit.teams()) {
            core.messages().send(leader, "party.kit-not-teams", "{kit}", kit.displayName());
            return false;
        }
        if (!allReady(online, leader)) {
            return false;
        }
        List<List<UUID>> sides = party.split();
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return false;
        }
        MatchRequest request = new MatchRequest(kit, MatchType.TEAMS, false).source("party");
        request.addTeam("red", sides.get(0));
        request.addTeam("blue", sides.get(1));
        Match match = matches.startMatch(request);
        if (match == null) {
            core.messages().send(leader, "party.match-failed", "{kit}", kit.displayName());
            return false;
        }
        announce(party, "party.broadcast-split-start", "{kit}", kit.displayName(),
                "{red}", namesOf(sides.get(0)), "{blue}", namesOf(sides.get(1)));
        Debug.log(DebugCategory.PARTY, "Party {} started a split match with {} players", party.id(),
                online.size());
        return true;
    }

    @Override
    public boolean startDuel(Party challenger, Party opponent, String kitId) {
        if (challenger == null || opponent == null || challenger.equals(opponent)) {
            return false;
        }
        Player leader = Bukkit.getPlayer(challenger.leader());
        if (leader == null) {
            return false;
        }
        if (challenger.onlineIds().size() != opponent.onlineIds().size()) {
            core.messages().send(leader, "party.duel-size-mismatch",
                    "{own}", String.valueOf(challenger.onlineIds().size()),
                    "{other}", String.valueOf(opponent.onlineIds().size()));
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(kitId == null ? challenger.kitId() : kitId);
        if (kit == null || !kit.enabled()) {
            core.messages().send(leader, "party.unknown-kit", "{kit}", String.valueOf(kitId));
            return false;
        }
        if (!kit.teams() && challenger.onlineIds().size() > 1) {
            core.messages().send(leader, "party.kit-not-teams", "{kit}", kit.displayName());
            return false;
        }
        List<UUID> own = challenger.onlineIds();
        List<UUID> other = opponent.onlineIds();
        List<UUID> everyone = new ArrayList<UUID>(own);
        everyone.addAll(other);
        if (!allReady(everyone, leader)) {
            return false;
        }
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return false;
        }
        MatchRequest request = new MatchRequest(kit, own.size() > 1 ? MatchType.TEAMS : MatchType.SOLO, false)
                .source("party-duel");
        request.addTeam(challenger.id(), own);
        request.addTeam(opponent.id(), other);
        Match match = matches.startMatch(request);
        if (match == null) {
            core.messages().send(leader, "party.match-failed", "{kit}", kit.displayName());
            return false;
        }
        announce(challenger, "party.broadcast-duel-start", "{kit}", kit.displayName(),
                "{opponent}", opponent.id());
        announce(opponent, "party.broadcast-duel-start", "{kit}", kit.displayName(),
                "{opponent}", challenger.id());
        Debug.log(DebugCategory.PARTY, "Party {} duelled party {} with kit {}", challenger.id(),
                opponent.id(), kit.id());
        return true;
    }

    private Kit resolveKit(Party party, Player leader) {
        KitManager kits = core.optional(KitManager.class);
        Kit kit = kits == null ? null : kits.get(party.kitId());
        if (kit == null || !kit.enabled()) {
            core.messages().send(leader, "party.no-kit");
            return null;
        }
        return kit;
    }

    private boolean allReady(Collection<UUID> uuids, Player leader) {
        PlayerManager players = core.optional(PlayerManager.class);
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                core.messages().send(leader, "party.member-offline", "{player}", nameOf(uuid));
                return false;
            }
            LightPlayer session = players == null ? null : players.get(player);
            if (session == null || !session.state().canQueue()) {
                core.messages().send(leader, "party.member-busy", "{player}", player.getName());
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------- chat

    /** Sends a message to every online member, used by the party chat channel. */
    public boolean chat(Player player, String message) {
        if (player == null || message == null || message.trim().isEmpty()) {
            return false;
        }
        Party party = get(player);
        if (party == null) {
            core.messages().send(player, "party.not-in");
            return false;
        }
        String channel = ChatChannel.PARTY.prefix();
        for (Player member : party.onlineMembers()) {
            core.messages().sendRaw(member, Text.color(channel + "&7[" + party.id() + "] "
                    + player.getName() + "&7: &f" + message));
        }
        Debug.log(DebugCategory.PARTY, "Party chat {} in {}: {}", player.getName(), party.id(), message);
        return true;
    }

    // -------------------------------------------------------------- housekeeping

    @Override
    public void handleQuit(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Party party = get(uuid);
        if (party == null) {
            return;
        }
        if (quitGraceMillis <= 0L) {
            dropOfflineMember(party, uuid);
            return;
        }
        offlineSince.put(uuid, Long.valueOf(System.currentTimeMillis()));
        announce(party, "party.broadcast-disconnect", "{player}", nameOf(uuid),
                "{seconds}", String.valueOf(quitGraceMillis / 1000L));
        Debug.log(DebugCategory.PARTY, "{} disconnected, party {} keeps them for {}s", nameOf(uuid),
                party.id(), quitGraceMillis / 1000L);
    }

    /** A reconnecting member keeps their party slot. */
    public boolean handleJoin(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long since = offlineSince.remove(uuid);
        if (since == null) {
            return false;
        }
        Party party = get(uuid);
        if (party == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            core.messages().send(player, "party.reconnected", "{party}", party.id());
            announce(party, "party.broadcast-reconnect", "{player}", player.getName());
        }
        return true;
    }

    private void housekeep() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, Long> entry : offlineSince.entrySet()) {
            Long since = entry.getValue();
            if (since != null && now - since.longValue() >= quitGraceMillis) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            offlineSince.remove(uuid);
            Party party = get(uuid);
            if (party != null) {
                dropOfflineMember(party, uuid);
            }
        }
        for (Party party : parties.values()) {
            party.purgeInvites();
        }
    }

    private void dropOfflineMember(Party party, UUID uuid) {
        boolean wasLeader = party.isLeader(uuid);
        PartyMember member = party.remove(uuid);
        memberToParty.remove(uuid);
        LightPlayer session = sessionOf(uuid);
        if (session != null) {
            session.partyId(null);
        }
        if (member != null) {
            announce(party, "party.broadcast-removed-offline", "{player}", member.name());
        }
        if (party.size() == 0) {
            disbandParty(party, false);
            return;
        }
        if (wasLeader) {
            UUID next = party.nextLeader();
            if (next != null) {
                party.promote(next);
                announce(party, "party.broadcast-promoted", "{player}", nameOf(next));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private LightPlayer sessionOf(UUID uuid) {
        PlayerManager players = core.optional(PlayerManager.class);
        return players == null || uuid == null ? null : players.get(uuid);
    }

    private void announce(Party party, String key, String... replacements) {
        if (party == null) {
            return;
        }
        core.messages().broadcastTo(party.onlineMembers(), key, replacements);
    }

    private String nameOf(UUID uuid) {
        Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        Party party = get(uuid);
        if (party != null) {
            PartyMember member = party.member(uuid);
            if (member != null) {
                return member.name();
            }
        }
        return "unknown";
    }

    private String namesOf(List<UUID> uuids) {
        List<String> names = new ArrayList<String>();
        for (UUID uuid : uuids) {
            names.add(nameOf(uuid));
        }
        return Text.join(names, ", ");
    }

    /** Parties a player may join without an invite, used by the party menu. */
    public List<Party> openParties() {
        List<Party> result = new ArrayList<Party>();
        for (Party party : parties.values()) {
            if (party.open() && !party.isFull()) {
                result.add(party);
            }
        }
        return result;
    }

    /** Joins an open party without an invite. */
    public boolean joinOpen(Player player, Party party) {
        if (player == null || party == null || !party.open()) {
            return false;
        }
        if (isInParty(player.getUniqueId())) {
            core.messages().send(player, "party.already-in");
            return false;
        }
        if (party.isFull()) {
            core.messages().send(player, "party.full", "{max}", String.valueOf(party.maxSize()));
            return false;
        }
        party.invite(player.getUniqueId(), inviteExpiryMillis);
        return accept(player);
    }

    public int partyCount() {
        return parties.size();
    }

    public int memberCount() {
        return memberToParty.size();
    }

    @Override
    public String toString() {
        return "PartyManager{parties=" + parties.size() + ", members=" + memberToParty.size() + '}';
    }
}
