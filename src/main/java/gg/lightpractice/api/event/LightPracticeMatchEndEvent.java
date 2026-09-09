package gg.lightpractice.api.event;

import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchTeam;
import org.bukkit.event.HandlerList;

/** Fired when a winner has been decided, before rewards and statistics are applied. */
public class LightPracticeMatchEndEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Match match;
    private final MatchTeam winningTeam;
    private final EndCause cause;

    public LightPracticeMatchEndEvent(Match match, MatchTeam winningTeam, EndCause cause) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.match = match;
        this.winningTeam = winningTeam;
        this.cause = cause;
    }

    public Match getMatch() {
        return match;
    }

    public MatchTeam getWinningTeam() {
        return winningTeam;
    }

    public EndCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
