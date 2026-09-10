package gg.lightpractice.party;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A group of players that queues, chats and fights together.
 *
 * <p>Members are kept in join order so team splits are predictable, and offline members stay in the party
 * for a configured grace period instead of being dropped on a momentary disconnect.</p>
 */
public final class Party {

    private final String id;
    private final long createdAt = System.currentTimeMillis();
    private final Map<UUID, PartyMember> members = new ConcurrentHashMap<UUID, PartyMember>();
    private final Map<UUID, Long> invites = new ConcurrentHashMap<UUID, Long>();
    private final List<UUID> joinOrder = Collections.synchronizedList(new ArrayList<UUID>());
    private UUID leader;
    private boolean open;
    private boolean ffaAllowed = true;
    private String kitId;
    private int maxSize = 8;

    public Party(String id, UUID leader) {
        this.id = id == null ? UUID.randomUUID().toString().substring(0, 8) : id;
        this.leader = leader;
    }

    public String id() {
        return id;
    }

    public long createdAt() {
        return createdAt;
    }

    public UUID leader() {
        return leader;
    }

    public void leader(UUID leader) {
        this.leader = leader;
        PartyMember member = leader == null ? null : members.get(leader);
        if (member != null) {
            member.rank(PartyRank.LEADER);
        }
    }

    public boolean isLeader(UUID uuid) {
        return uuid != null && uuid.equals(leader);
    }

    public PartyMember member(UUID uuid) {
        return uuid == null ? null : members.get(uuid);
    }

    public Collection<PartyMember> members() {
        List<PartyMember> ordered = new ArrayList<PartyMember>();
        synchronized (joinOrder) {
            for (UUID uuid : joinOrder) {
                PartyMember member = members.get(uuid);
                if (member != null) {
                    ordered.add(member);
                }
            }
        }
        for (PartyMember member : members.values()) {
            if (!ordered.contains(member)) {
                ordered.add(member);
            }
        }
        return ordered;
    }

    public List<UUID> memberIds() {
        List<UUID> ids = new ArrayList<UUID>();
        for (PartyMember member : members()) {
            ids.add(member.uuid());
        }
        return ids;
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID uuid) {
        return uuid != null && members.containsKey(uuid);
    }

    public boolean add(PartyMember member) {
        if (member == null || member.uuid() == null || members.containsKey(member.uuid())) {
            return false;
        }
        members.put(member.uuid(), member);
        joinOrder.add(member.uuid());
        invites.remove(member.uuid());
        return true;
    }

    public PartyMember remove(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PartyMember removed = members.remove(uuid);
        joinOrder.remove(uuid);
        invites.remove(uuid);
        if (uuid.equals(leader) && !members.isEmpty()) {
            leader(nextLeader());
        }
        return removed;
    }

    /** Oldest remaining member, used when the leader leaves or disconnects. */
    public UUID nextLeader() {
        for (PartyMember member : members()) {
            if (!member.uuid().equals(leader)) {
                return member.uuid();
            }
        }
        return null;
    }

    public boolean promote(UUID uuid) {
        PartyMember member = member(uuid);
        if (member == null) {
            return false;
        }
        PartyMember previous = member(leader);
        if (previous != null) {
            previous.rank(PartyRank.MEMBER);
        }
        member.rank(PartyRank.LEADER);
        leader = uuid;
        return true;
    }

    public int maxSize() {
        return maxSize;
    }

    public void maxSize(int maxSize) {
        this.maxSize = Math.max(2, maxSize);
    }

    public boolean isFull() {
        return maxSize > 0 && size() >= maxSize;
    }

    public boolean open() {
        return open;
    }

    public void open(boolean open) {
        this.open = open;
    }

    public boolean ffaAllowed() {
        return ffaAllowed;
    }

    public void ffaAllowed(boolean ffaAllowed) {
        this.ffaAllowed = ffaAllowed;
    }

    /** Kit selected for party matches, {@code null} until the leader picks one. */
    public String kitId() {
        return kitId;
    }

    public void kitId(String kitId) {
        this.kitId = kitId == null || kitId.trim().isEmpty() ? null : kitId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ invites

    public boolean invite(UUID uuid, long expiryMillis) {
        if (uuid == null || contains(uuid) || isFull()) {
            return false;
        }
        invites.put(uuid, Long.valueOf(System.currentTimeMillis() + Math.max(1000L, expiryMillis)));
        return true;
    }

    public boolean revokeInvite(UUID uuid) {
        return uuid != null && invites.remove(uuid) != null;
    }

    public boolean hasInvited(UUID uuid) {
        Long expiry = uuid == null ? null : invites.get(uuid);
        if (expiry == null) {
            return false;
        }
        if (expiry.longValue() < System.currentTimeMillis()) {
            invites.remove(uuid);
            return false;
        }
        return true;
    }

    public Set<UUID> invites() {
        purgeInvites();
        return Collections.unmodifiableSet(invites.keySet());
    }

    public void purgeInvites() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, Long> entry : invites.entrySet()) {
            if (entry.getValue() == null || entry.getValue().longValue() < now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            invites.remove(uuid);
        }
    }

    /** Parties this player was invited to are resolved by the manager, this only answers for one party. */
    public boolean invited(UUID uuid) {
        return hasInvited(uuid);
    }

    // ------------------------------------------------------------------ online

    public List<Player> onlineMembers() {
        List<Player> players = new ArrayList<Player>();
        for (PartyMember member : members()) {
            Player player = member.player();
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public int onlineCount() {
        int count = 0;
        for (PartyMember member : members()) {
            if (member.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public List<UUID> onlineIds() {
        List<UUID> ids = new ArrayList<UUID>();
        for (PartyMember member : members()) {
            if (member.isOnline()) {
                ids.add(member.uuid());
            }
        }
        return ids;
    }

    /** Splits online members into two sides as evenly as possible, leader first. */
    public List<List<UUID>> split() {
        List<UUID> online = onlineIds();
        List<List<UUID>> sides = new ArrayList<List<UUID>>();
        sides.add(new ArrayList<UUID>());
        sides.add(new ArrayList<UUID>());
        int index = 0;
        for (UUID uuid : online) {
            sides.get(index % 2).add(uuid);
            index++;
        }
        return sides;
    }

    /** Members that joined but are offline, used by the quit grace period. */
    public List<UUID> offlineIds() {
        List<UUID> ids = new ArrayList<UUID>();
        for (PartyMember member : members()) {
            if (!member.isOnline()) {
                ids.add(member.uuid());
            }
        }
        return ids;
    }

    public Map<UUID, PartyMember> memberMap() {
        return new LinkedHashMap<UUID, PartyMember>(members);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Party)) {
            return false;
        }
        return id.equals(((Party) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Party{" + id + ", members=" + size() + ", online=" + onlineCount() + ", leader=" + leader + '}';
    }
}
