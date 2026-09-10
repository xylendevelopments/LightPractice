package gg.lightpractice.match;

/**
 * Lifecycle of a match.
 *
 * <p>Transitions are validated because listeners, tasks and commands can all try to move a match
 * forward at the same time; an illegal transition is refused and logged instead of corrupting the
 * match.</p>
 */
public enum MatchState {

    /** Created and validated, players are being moved and equipped. */
    PREPARING,
    /** Countdown running, players may not fight yet. */
    STARTING,
    /** Fight is live. */
    IN_PROGRESS,
    /** Winner decided, cleanup running. */
    ENDING,
    /** Finished and removed from the active list. */
    ENDED;

    public boolean canTransitionTo(MatchState target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PREPARING:
                return target == STARTING || target == IN_PROGRESS || target == ENDING || target == ENDED;
            case STARTING:
                return target == IN_PROGRESS || target == ENDING || target == ENDED;
            case IN_PROGRESS:
                return target == ENDING || target == ENDED;
            case ENDING:
                return target == ENDED;
            case ENDED:
            default:
                return false;
        }
    }

    public boolean isLive() {
        return this == STARTING || this == IN_PROGRESS;
    }

    public boolean isFinished() {
        return this == ENDING || this == ENDED;
    }
}
