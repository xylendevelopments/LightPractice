package gg.lightpractice.matchmaking;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.queue.Queue;
import gg.lightpractice.queue.QueueEntry;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Rating based matchmaking with a range that grows while players wait.
 *
 * <p>Every pass walks the queue from the longest waiting entry and pairs it with the closest rating it
 * is allowed to accept. The allowed range starts narrow and widens by a configured step every few
 * seconds until a hard maximum, and after a configured wait the entry accepts anyone so nobody is stuck
 * in an empty queue. Groups widen the range slightly because a whole party has to fit on one side.</p>
 */
public final class Matchmaker implements LightService {

    private final PluginCore core;
    private int initialRange = 50;
    private int rangeStep = 25;
    private int stepSeconds = 15;
    private int maximumRange = 300;
    private int unlimitedAfterSeconds = 120;
    private int groupExtraRange = 50;
    private int teamRangeMultiplier = 100;
    private long retryDelayMillis = 2000L;

    public Matchmaker(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "matchmaking";
    }

    @Override
    public int startupOrder() {
        return 55;
    }

    @Override
    public void onLoad() {
        load(core.configs().matchmaking());
    }

    @Override
    public void onReload() {
        load(core.configs().matchmaking());
    }

    public void load(ConfigFile file) {
        if (file == null) {
            return;
        }
        this.initialRange = Math.max(0, file.getInt("range.initial", 50));
        this.rangeStep = Math.max(0, file.getInt("range.step", 25));
        this.stepSeconds = Math.max(1, file.getInt("range.step-seconds", 15));
        this.maximumRange = Math.max(0, file.getInt("range.maximum", 300));
        this.unlimitedAfterSeconds = Math.max(0, file.getInt("range.unlimited-after-seconds", 120));
        this.groupExtraRange = Math.max(0, file.getInt("groups.extra-range", 50));
        this.teamRangeMultiplier = Math.max(0, file.getInt("teams.extra-range-per-player", 100));
        this.retryDelayMillis = Math.max(0L, file.getLong("retry-delay-millis", 2000L));
        Debug.log(DebugCategory.MATCHMAKING,
                "Matchmaking range {} +{} every {}s, max {}, unlimited after {}s",
                initialRange, rangeStep, stepSeconds, maximumRange, unlimitedAfterSeconds);
    }

    public int initialRange() {
        return initialRange;
    }

    public int maximumRange() {
        return maximumRange;
    }

    public long retryDelayMillis() {
        return retryDelayMillis;
    }

    /** Range an entry accepts right now, {@link Integer#MAX_VALUE} once waiting stopped mattering. */
    public int allowedRange(QueueEntry entry, long now) {
        if (entry == null) {
            return 0;
        }
        long waitedSeconds = Math.max(0L, (now - entry.joinedAt()) / 1000L);
        if (unlimitedAfterSeconds > 0 && waitedSeconds >= unlimitedAfterSeconds) {
            return Integer.MAX_VALUE;
        }
        int steps = (int) Math.min(Integer.MAX_VALUE / Math.max(1, rangeStep + 1), waitedSeconds / stepSeconds);
        long range = (long) initialRange + ((long) steps * rangeStep);
        if (entry.isGroup()) {
            range += groupExtraRange;
        }
        if (maximumRange > 0 && range > maximumRange) {
            range = maximumRange;
        }
        return (int) Math.min(range, Integer.MAX_VALUE);
    }

    /** Rating difference two single entries may have to be matched. */
    public boolean compatible(QueueEntry first, QueueEntry second, long now) {
        if (first == null || second == null) {
            return false;
        }
        int difference = Math.abs(first.rating() - second.rating());
        // the entry that waited longest decides how far apart the pair may be
        int allowed = Math.max(allowedRange(first, now), allowedRange(second, now));
        return difference <= allowed;
    }

    /** Whether two sides may fight, widening the range by the number of players per side. */
    public boolean compatible(List<QueueEntry> first, List<QueueEntry> second, long now) {
        int difference = Math.abs(averageRating(first) - averageRating(second));
        int allowed = Math.max(sideRange(first, now), sideRange(second, now));
        return difference <= allowed;
    }

    private int sideRange(List<QueueEntry> side, long now) {
        int best = 0;
        int players = 0;
        for (QueueEntry entry : side) {
            int range = allowedRange(entry, now);
            if (range == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            best = Math.max(best, range);
            players += entry.groupSize();
        }
        if (players <= 1) {
            return best;
        }
        long widened = (long) best + ((long) (players - 1) * teamRangeMultiplier);
        return (int) Math.min(widened, Integer.MAX_VALUE);
    }

    public int averageRating(List<QueueEntry> side) {
        if (side == null || side.isEmpty()) {
            return 0;
        }
        long total = 0L;
        int players = 0;
        for (QueueEntry entry : side) {
            total += (long) entry.rating() * entry.groupSize();
            players += entry.groupSize();
        }
        return players == 0 ? 0 : (int) (total / players);
    }

    // ------------------------------------------------------------------ matching

    /** Pairings a queue can start right now, empty when nobody is compatible yet. */
    public List<MatchPairing> findMatches(Queue queue) {
        List<MatchPairing> result = new ArrayList<MatchPairing>();
        if (queue == null || !queue.enabled() || queue.entryCount() < 2) {
            return result;
        }
        long now = System.currentTimeMillis();
        if (queue.type().everyoneAlone()) {
            MatchPairing pairing = freeForAll(queue, now);
            if (pairing != null) {
                result.add(pairing);
            }
            return result;
        }
        if (queue.teamSize() > 1) {
            result.addAll(teamMatches(queue, now));
            return result;
        }
        result.addAll(soloMatches(queue, now));
        return result;
    }

    /** Greedy one versus one pairing, longest waiting entry first. */
    private List<MatchPairing> soloMatches(Queue queue, long now) {
        List<MatchPairing> pairings = new ArrayList<MatchPairing>();
        List<QueueEntry> pool = queue.entries();
        while (pool.size() >= 2) {
            QueueEntry first = pool.remove(0);
            QueueEntry best = null;
            int bestDifference = Integer.MAX_VALUE;
            for (QueueEntry candidate : pool) {
                if (!compatible(first, candidate, now)) {
                    continue;
                }
                int difference = Math.abs(first.rating() - candidate.rating());
                if (difference < bestDifference
                        || (difference == bestDifference && best != null && candidate.joinedAt() < best.joinedAt())) {
                    best = candidate;
                    bestDifference = difference;
                }
            }
            if (best == null) {
                // nobody fits yet, the entry keeps waiting and its range keeps growing
                pool.add(first);
                break;
            }
            pool.remove(best);
            List<List<QueueEntry>> sides = new ArrayList<List<QueueEntry>>();
            sides.add(Collections.singletonList(first));
            sides.add(Collections.singletonList(best));
            MatchPairing pairing = new MatchPairing(queue, sides);
            if (pairing.isValid()) {
                pairings.add(pairing);
                Debug.log(DebugCategory.MATCHMAKING, "Matched {} ({}) against {} ({}) in {}, difference {}",
                        first.name(), first.rating(), best.name(), best.rating(), queue.id(), bestDifference);
            }
        }
        return pairings;
    }

    /**
     * Team pairing: ready made groups are used as sides, remaining players are drafted into balanced
     * teams by rating, then sides are paired by their average rating.
     */
    private List<MatchPairing> teamMatches(Queue queue, long now) {
        List<MatchPairing> pairings = new ArrayList<MatchPairing>();
        int teamSize = queue.teamSize();
        List<List<QueueEntry>> sides = new ArrayList<List<QueueEntry>>();
        List<QueueEntry> pool = new ArrayList<QueueEntry>();
        for (QueueEntry entry : queue.entries()) {
            if (entry.groupSize() == teamSize) {
                sides.add(new ArrayList<QueueEntry>(Collections.singletonList(entry)));
            } else if (entry.groupSize() < teamSize) {
                pool.add(entry);
            } else {
                Debug.log(DebugCategory.MATCHMAKING, "Entry {} of {} is larger than the team size {}",
                        entry.name(), queue.id(), teamSize);
            }
        }
        sides.addAll(draft(pool, teamSize));
        if (sides.size() < 2) {
            return pairings;
        }
        Collections.sort(sides, new Comparator<List<QueueEntry>>() {
            @Override
            public int compare(List<QueueEntry> left, List<QueueEntry> right) {
                return Integer.compare(averageRating(left), averageRating(right));
            }
        });
        List<Boolean> used = new ArrayList<Boolean>(Collections.nCopies(sides.size(), Boolean.FALSE));
        for (int index = 0; index < sides.size(); index++) {
            if (used.get(index).booleanValue()) {
                continue;
            }
            List<QueueEntry> side = sides.get(index);
            int partner = -1;
            int bestDifference = Integer.MAX_VALUE;
            for (int other = index + 1; other < sides.size(); other++) {
                if (used.get(other).booleanValue()) {
                    continue;
                }
                if (!compatible(side, sides.get(other), now)) {
                    continue;
                }
                int difference = Math.abs(averageRating(side) - averageRating(sides.get(other)));
                if (difference < bestDifference) {
                    bestDifference = difference;
                    partner = other;
                }
            }
            if (partner < 0) {
                continue;
            }
            used.set(index, Boolean.TRUE);
            used.set(partner, Boolean.TRUE);
            List<List<QueueEntry>> pairing = new ArrayList<List<QueueEntry>>();
            pairing.add(side);
            pairing.add(sides.get(partner));
            MatchPairing result = new MatchPairing(queue, pairing);
            if (result.isValid()) {
                pairings.add(result);
                Debug.log(DebugCategory.MATCHMAKING, "Matched two {}v{} sides in {} ({} vs {}, difference {})",
                        teamSize, teamSize, queue.id(), averageRating(side),
                        averageRating(sides.get(partner)), bestDifference);
            }
        }
        return pairings;
    }

    /**
     * Snake draft of single players into balanced teams.
     *
     * <p>Players are sorted by rating and picked in a snake order (0,1,2,2,1,0,...) so every side ends up
     * with a similar total rating. Only complete sides are returned; leftover players stay queued.</p>
     */
    private List<List<QueueEntry>> draft(List<QueueEntry> pool, int teamSize) {
        List<List<QueueEntry>> sides = new ArrayList<List<QueueEntry>>();
        if (pool == null || pool.isEmpty() || teamSize < 1) {
            return sides;
        }
        List<QueueEntry> sorted = new ArrayList<QueueEntry>(pool);
        Collections.sort(sorted, new Comparator<QueueEntry>() {
            @Override
            public int compare(QueueEntry left, QueueEntry right) {
                return Integer.compare(right.rating(), left.rating());
            }
        });
        int sideCount = sorted.size() / teamSize;
        if (sideCount < 2) {
            return sides;
        }
        for (int index = 0; index < sideCount; index++) {
            sides.add(new ArrayList<QueueEntry>());
        }
        int pick = 0;
        for (QueueEntry entry : sorted.subList(0, sideCount * teamSize)) {
            int cycle = pick / sideCount;
            int offset = pick % sideCount;
            int side = cycle % 2 == 0 ? offset : sideCount - 1 - offset;
            sides.get(side).add(entry);
            pick++;
        }
        return sides;
    }

    /** Free for all: one pairing with a side per player, longest waiting entries first. */
    private MatchPairing freeForAll(Queue queue, long now) {
        int minimum = Math.max(2, queue.minimumPlayers());
        int maximum = queue.maximumPlayers() > 0 ? Math.max(minimum, queue.maximumPlayers()) : minimum;
        List<QueueEntry> sorted = queue.entries();
        if (sorted.size() < minimum) {
            return null;
        }
        List<List<QueueEntry>> sides = new ArrayList<List<QueueEntry>>();
        for (QueueEntry entry : sorted) {
            if (sides.size() >= maximum) {
                break;
            }
            if (entry.isGroup()) {
                // groups fight as one side, which is allowed in free for all party modes only
                Debug.log(DebugCategory.MATCHMAKING, "Group {} cannot join the free for all queue {}",
                        entry.name(), queue.id());
                continue;
            }
            sides.add(new ArrayList<QueueEntry>(Collections.singletonList(entry)));
        }
        if (sides.size() < minimum) {
            return null;
        }
        MatchPairing pairing = new MatchPairing(queue, sides);
        if (!pairing.isValid()) {
            return null;
        }
        Debug.log(DebugCategory.MATCHMAKING, "Matched {} player(s) into a free for all {}", sides.size(),
                queue.id());
        return pairing;
    }

    /** Estimated waiting time of a queue in milliseconds, based on how long entries already waited. */
    public long estimateWait(Queue queue) {
        if (queue == null || queue.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        int count = 0;
        for (QueueEntry entry : queue.entries()) {
            total += entry.ageMillis();
            count++;
        }
        if (count == 0) {
            return 0L;
        }
        long average = total / count;
        // a queue that already has opponents available should not report a long wait
        return queue.hasEnoughPlayers() ? Math.min(average, 5000L) : Math.max(average, 5000L);
    }
}
