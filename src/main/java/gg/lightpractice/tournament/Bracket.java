package gg.lightpractice.tournament;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One pairing of a tournament round.
 *
 * <p>A bracket holds the two sides, the match that plays it out and its result. When the number of teams
 * in a round is odd the last bracket is a bye: it has no away side and its home team advances without a
 * match, which keeps every round playable without dropping anybody.</p>
 */
public final class Bracket {

    private final int index;
    private final List<UUID> home;
    private final List<UUID> away;
    private final List<UUID> winners = new ArrayList<UUID>();
    private String matchId;
    private boolean launched;
    private boolean finished;
    private int attempts;

    public Bracket(int index, Collection<UUID> home, Collection<UUID> away) {
        this.index = index;
        this.home = home == null ? new ArrayList<UUID>() : new ArrayList<UUID>(home);
        this.away = away == null ? new ArrayList<UUID>() : new ArrayList<UUID>(away);
        if (bye()) {
            finish(this.home);
        }
    }

    public int index() {
        return index;
    }

    public List<UUID> home() {
        return Collections.unmodifiableList(home);
    }

    public List<UUID> away() {
        return Collections.unmodifiableList(away);
    }

    /** True when nobody has to fight because only one team was left to pair. */
    public boolean bye() {
        return away.isEmpty();
    }

    public List<UUID> participants() {
        List<UUID> everyone = new ArrayList<UUID>(home);
        everyone.addAll(away);
        return Collections.unmodifiableList(everyone);
    }

    public boolean involves(UUID uuid) {
        return uuid != null && (home.contains(uuid) || away.contains(uuid));
    }

    public String matchId() {
        return matchId;
    }

    public void matchId(String matchId) {
        this.matchId = matchId;
    }

    public boolean launched() {
        return launched;
    }

    public void launched(boolean launched) {
        this.launched = launched;
    }

    public boolean finished() {
        return finished;
    }

    /** Launch attempts that did not produce a match, used to give up on a stuck bracket. */
    public int attempts() {
        return attempts;
    }

    public void attempted() {
        this.attempts++;
    }

    public List<UUID> winners() {
        return Collections.unmodifiableList(winners);
    }

    /** Records the advancing side. An empty result means both sides were eliminated. */
    public void finish(Collection<UUID> advancing) {
        winners.clear();
        if (advancing != null) {
            for (UUID uuid : advancing) {
                if (uuid != null && !winners.contains(uuid)) {
                    winners.add(uuid);
                }
            }
        }
        finished = true;
    }

    /** Winners of this bracket, or the home side when the match ended without a winner. */
    public List<UUID> survivors() {
        if (!winners.isEmpty()) {
            return winners();
        }
        return home();
    }

    @Override
    public String toString() {
        return "Bracket{" + index + ", home=" + home.size() + ", away=" + away.size()
                + (bye() ? ", bye" : "") + (finished ? ", finished" : ", open") + '}';
    }
}
