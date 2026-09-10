package gg.lightpractice.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One waiting party in a queue.
 *
 * <p>A single player is an entry of one, a party queues as one entry holding every member, so the
 * matchmaker always places a group on the same side instead of splitting friends apart.</p>
 */
public final class QueueEntry implements Comparable<QueueEntry> {

    private final UUID uuid;
    private final String name;
    private final List<UUID> group;
    private final long joinedAt = System.currentTimeMillis();
    private int rating;
    private long notifiedAt;

    public QueueEntry(UUID uuid, String name, Collection<UUID> group, int rating) {
        this.uuid = uuid;
        this.name = name == null ? "unknown" : name;
        List<UUID> members = new ArrayList<UUID>();
        if (uuid != null) {
            members.add(uuid);
        }
        if (group != null) {
            for (UUID member : group) {
                if (member != null && !members.contains(member)) {
                    members.add(member);
                }
            }
        }
        this.group = Collections.unmodifiableList(members);
        this.rating = rating;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** Every queued player of this entry, the leader first. */
    public List<UUID> group() {
        return group;
    }

    public int groupSize() {
        return group.size();
    }

    public boolean isGroup() {
        return group.size() > 1;
    }

    public boolean contains(UUID other) {
        return other != null && group.contains(other);
    }

    public long joinedAt() {
        return joinedAt;
    }

    public long ageMillis() {
        return Math.max(0L, System.currentTimeMillis() - joinedAt);
    }

    public int rating() {
        return rating;
    }

    public void rating(int rating) {
        this.rating = rating;
    }

    public long notifiedAt() {
        return notifiedAt;
    }

    public void notifiedAt(long notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    /** Longest waiting entries are matched first. */
    @Override
    public int compareTo(QueueEntry other) {
        if (other == null) {
            return -1;
        }
        return Long.compare(joinedAt, other.joinedAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QueueEntry)) {
            return false;
        }
        return uuid == null ? ((QueueEntry) other).uuid == null : uuid.equals(((QueueEntry) other).uuid);
    }

    @Override
    public int hashCode() {
        return uuid == null ? name.hashCode() : uuid.hashCode();
    }

    @Override
    public String toString() {
        return "QueueEntry{" + name + ", group=" + group.size() + ", rating=" + rating
                + ", waiting=" + (ageMillis() / 1000L) + "s}";
    }
}
