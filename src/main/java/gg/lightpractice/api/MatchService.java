package gg.lightpractice.api;

import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchTeam;

import java.util.Collection;
import java.util.UUID;

/** Match engine entry point. */
public interface MatchService {

    Match get(String matchId);

    /** Match a player is currently part of, as participant or spectator. */
    Match getMatch(UUID uuid);

    Collection<Match> matches();

    int activeCount();

    boolean isInMatch(UUID uuid);

    /**
     * Validates a request, reserves an arena and starts the match. Returns {@code false} when the
     * request is invalid, no arena is free or a participant is in an incompatible state.
     */
    boolean start(MatchRequest request);

    /** Ends a match with the given winner, applying rewards, statistics and cleanup. */
    void end(Match match, MatchTeam winningTeam, EndCause cause);

    /** Ends every match a player takes part in, used on disconnect or shutdown. */
    void forfeit(UUID uuid, EndCause cause);

    /** Ends every running match safely, used during plugin shutdown. */
    void endAll(EndCause cause);

    MatchTeam teamOf(Match match, UUID uuid);
}
