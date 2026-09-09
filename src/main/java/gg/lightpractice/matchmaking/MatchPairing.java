package gg.lightpractice.matchmaking;

import gg.lightpractice.queue.Queue;
import gg.lightpractice.queue.QueueEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A set of sides the matchmaker decided should fight.
 *
 * <p>Two sides for a duel or a team match, one side per participant for free for all, so the queue
 * manager turns any pairing into a {@link gg.lightpractice.match.MatchRequest} the same way.</p>
 */
public final class MatchPairing {

    private final Queue queue;
    private final List<List<QueueEntry>> sides;

    public MatchPairing(Queue queue, List<List<QueueEntry>> sides) {
        this.queue = queue;
        List<List<QueueEntry>> copy = new ArrayList<List<QueueEntry>>();
        if (sides != null) {
            for (List<QueueEntry> side : sides) {
                if (side != null && !side.isEmpty()) {
                    copy.add(new ArrayList<QueueEntry>(side));
                }
            }
        }
        this.sides = Collections.unmodifiableList(copy);
    }

    public Queue queue() {
        return queue;
    }

    public List<List<QueueEntry>> sides() {
        return sides;
    }

    public int sideCount() {
        return sides.size();
    }

    /** Every entry of the pairing, in side order. */
    public List<QueueEntry> entries() {
        List<QueueEntry> entries = new ArrayList<QueueEntry>();
        for (List<QueueEntry> side : sides) {
            entries.addAll(side);
        }
        return entries;
    }

    /** Every player of the pairing, in side order. */
    public List<UUID> participants() {
        List<UUID> participants = new ArrayList<UUID>();
        for (QueueEntry entry : entries()) {
            participants.addAll(entry.group());
        }
        return participants;
    }

    public int participantCount() {
        int count = 0;
        for (QueueEntry entry : entries()) {
            count += entry.groupSize();
        }
        return count;
    }

    /** A pairing is usable when it has the sides the queue needs and nobody is listed twice. */
    public boolean isValid() {
        if (queue == null || sides.isEmpty()) {
            return false;
        }
        int required = queue.type().everyoneAlone() ? Math.max(2, queue.minimumPlayers()) : 2;
        if (sides.size() < required) {
            return false;
        }
        List<UUID> seen = new ArrayList<UUID>();
        for (QueueEntry entry : entries()) {
            for (UUID uuid : entry.group()) {
                if (uuid == null || seen.contains(uuid)) {
                    return false;
                }
                seen.add(uuid);
            }
        }
        int expected = queue.teamSize();
        for (List<QueueEntry> side : sides) {
            int size = 0;
            for (QueueEntry entry : side) {
                size += entry.groupSize();
            }
            if (expected > 0 && size != expected && !queue.type().everyoneAlone()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MatchPairing{" + (queue == null ? "no queue" : queue.id()) + ", sides=" + sides.size()
                + ", players=" + participantCount() + '}';
    }
}
