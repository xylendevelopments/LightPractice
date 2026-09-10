package gg.lightpractice.tournament;

import gg.lightpractice.model.TournamentRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One tournament: its entries, its bracket and the results of every round.
 *
 * <p>The object is plain state, all decisions about starting matches live in
 * {@link TournamentManager}. Teams are formed when the bracket is built, and every round produces a list
 * of {@link Bracket} pairings; an odd number of teams gives the last team a bye instead of dropping it.</p>
 */
public final class Tournament {

    private final String id;
    private final String kitId;
    private final String arenaId;
    private final UUID host;
    private final String hostName;
    private final int teamSize;
    private final int minPlayers;
    private final int maxPlayers;
    private final long createdAt;
    private final Set<UUID> participants = Collections.synchronizedSet(new LinkedHashSet<UUID>());
    private final List<List<UUID>> teams = new ArrayList<List<UUID>>();
    private final List<Bracket> brackets = new ArrayList<Bracket>();
    private final List<String> history = new ArrayList<String>();
    private TournamentState state = TournamentState.GATHERING;
    private long startAt;
    private long updatedAt;
    private int countdownSeconds;
    private int round;
    private UUID winner;
    private String endReason = "";

    public Tournament(String id, String kitId, String arenaId, UUID host, String hostName, int teamSize,
                      int minPlayers, int maxPlayers) {
        this.id = id == null ? UUID.randomUUID().toString().substring(0, 8) : id;
        this.kitId = kitId == null ? "" : kitId;
        this.arenaId = arenaId == null ? "" : arenaId;
        this.host = host;
        this.hostName = hostName == null ? "console" : hostName;
        this.teamSize = Math.max(1, teamSize);
        this.minPlayers = Math.max(2, minPlayers);
        this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // ------------------------------------------------------------------ identity

    public String id() {
        return id;
    }

    public String kitId() {
        return kitId;
    }

    public String arenaId() {
        return arenaId;
    }

    public UUID host() {
        return host;
    }

    public String hostName() {
        return hostName;
    }

    public int teamSize() {
        return teamSize;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public long createdAt() {
        return createdAt;
    }

    // --------------------------------------------------------------------- state

    public TournamentState state() {
        return state;
    }

    public void state(TournamentState state) {
        this.state = state == null ? TournamentState.CANCELLED : state;
        touch();
    }

    public boolean live() {
        return state.live();
    }

    public long startAt() {
        return startAt;
    }

    public void startAt(long startAt) {
        this.startAt = startAt;
        touch();
    }

    /** Milliseconds until the gathering period closes, never negative. */
    public long remainingMillis() {
        return Math.max(0L, startAt - System.currentTimeMillis());
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public void countdownSeconds(int countdownSeconds) {
        this.countdownSeconds = Math.max(0, countdownSeconds);
    }

    public int round() {
        return round;
    }

    public UUID winner() {
        return winner;
    }

    public void winner(UUID winner) {
        this.winner = winner;
        touch();
    }

    public String endReason() {
        return endReason;
    }

    public void endReason(String endReason) {
        this.endReason = endReason == null ? "" : endReason;
    }

    // -------------------------------------------------------------- participants

    public boolean join(UUID uuid) {
        if (uuid == null || !state.joinable() || isFull() || participants.contains(uuid)) {
            return false;
        }
        boolean added = participants.add(uuid);
        if (added) {
            touch();
        }
        return added;
    }

    public boolean leave(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = participants.remove(uuid);
        if (removed) {
            touch();
        }
        return removed;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid != null && participants.contains(uuid);
    }

    public int size() {
        return participants.size();
    }

    public boolean isFull() {
        return participants.size() >= maxPlayers;
    }

    public boolean hasEnoughPlayers() {
        return participants.size() >= minPlayers;
    }

    public List<UUID> participantsList() {
        synchronized (participants) {
            return new ArrayList<UUID>(participants);
        }
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    // -------------------------------------------------------------------- bracket

    public List<List<UUID>> teams() {
        return Collections.unmodifiableList(teams);
    }

    public List<Bracket> brackets() {
        return Collections.unmodifiableList(brackets);
    }

    /** Brackets of the current round that still have to be played. */
    public List<Bracket> pendingBrackets() {
        List<Bracket> pending = new ArrayList<Bracket>();
        for (Bracket bracket : brackets) {
            if (!bracket.finished() && !bracket.bye()) {
                pending.add(bracket);
            }
        }
        return pending;
    }

    public boolean allBracketsFinished() {
        for (Bracket bracket : brackets) {
            if (!bracket.finished()) {
                return false;
            }
        }
        return !brackets.isEmpty();
    }

    public Bracket bracketOfMatch(String matchId) {
        if (matchId == null) {
            return null;
        }
        for (Bracket bracket : brackets) {
            if (matchId.equals(bracket.matchId())) {
                return bracket;
            }
        }
        return null;
    }

    public Bracket bracketOf(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Bracket bracket : brackets) {
            if (bracket.involves(uuid)) {
                return bracket;
            }
        }
        return null;
    }

    /** Players still in the tournament after the current round. */
    public List<UUID> survivors() {
        List<UUID> survivors = new ArrayList<UUID>();
        for (Bracket bracket : brackets) {
            for (UUID uuid : bracket.survivors()) {
                if (!survivors.contains(uuid)) {
                    survivors.add(uuid);
                }
            }
        }
        return survivors;
    }

    /**
     * Builds the next round from a pool of players.
     *
     * <p>The pool is shuffled so brackets differ every round, split into teams of the configured size and
     * paired. An odd number of teams leaves the last one with a bye.</p>
     *
     * @return the number of brackets created
     */
    public int buildRound(Collection<UUID> pool) {
        teams.clear();
        brackets.clear();
        List<UUID> players = new ArrayList<UUID>();
        if (pool != null) {
            for (UUID uuid : pool) {
                if (uuid != null && !players.contains(uuid)) {
                    players.add(uuid);
                }
            }
        }
        Collections.shuffle(players);
        for (int index = 0; index < players.size(); index += teamSize) {
            List<UUID> team = new ArrayList<UUID>();
            for (int member = index; member < Math.min(players.size(), index + teamSize); member++) {
                team.add(players.get(member));
            }
            teams.add(team);
        }
        round++;
        for (int index = 0; index < teams.size(); index += 2) {
            List<UUID> home = teams.get(index);
            List<UUID> away = index + 1 < teams.size() ? teams.get(index + 1) : Collections.<UUID>emptyList();
            brackets.add(new Bracket(brackets.size(), home, away));
        }
        touch();
        return brackets.size();
    }

    /** Builds the opening round from the current entries. */
    public int begin() {
        state(TournamentState.RUNNING);
        countdownSeconds(0);
        return buildRound(participantsList());
    }

    public List<String> history() {
        return Collections.unmodifiableList(history);
    }

    public void log(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        history.add(line);
        if (history.size() > 100) {
            history.remove(0);
        }
        touch();
    }

    // ---------------------------------------------------------------- persistence

    public TournamentRecord record() {
        return new TournamentRecord(id, kitId, host, hostName, state.name(), createdAt, updatedAt,
                participants.size(), participantsList(), winner);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tournament && ((Tournament) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Tournament{" + id + ", kit=" + kitId + ", state=" + state + ", players=" + participants.size()
                + ", round=" + round + '}';
    }
}
