package gg.lightpractice.player;

/**
 * What a player is currently doing.
 *
 * <p>The state machine is the single source of truth for "may this player start something new". Every
 * request (queue join, duel accept, kit edit, tournament join, spectate) is validated against
 * {@link #canTransitionTo(PlayerState)} server side, so client side state can never be trusted.</p>
 */
public enum PlayerState {

    /** Profile is still loading; nothing is allowed yet. */
    LOADING,
    LOBBY,
    QUEUE,
    DUEL,
    MATCH,
    SPECTATING,
    TOURNAMENT,
    KIT_EDITOR,
    OFFLINE;

    public boolean busy() {
        return this == MATCH || this == SPECTATING || this == TOURNAMENT || this == KIT_EDITOR || this == LOADING;
    }

    public boolean inGame() {
        return this == MATCH || this == TOURNAMENT;
    }

    public boolean canQueue() {
        return this == LOBBY;
    }

    public boolean canDuel() {
        return this == LOBBY;
    }

    public boolean canEditKit() {
        return this == LOBBY;
    }

    public boolean canSpectate() {
        return this == LOBBY || this == SPECTATING;
    }

    /** Allowed transitions; anything not listed here is refused and logged. */
    public boolean canTransitionTo(PlayerState target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case LOADING:
                return target == LOBBY || target == OFFLINE;
            case LOBBY:
                return target == QUEUE || target == DUEL || target == MATCH || target == SPECTATING
                        || target == TOURNAMENT || target == KIT_EDITOR || target == OFFLINE;
            case QUEUE:
                return target == LOBBY || target == MATCH || target == DUEL || target == TOURNAMENT
                        || target == OFFLINE;
            case DUEL:
                return target == LOBBY || target == MATCH || target == QUEUE || target == OFFLINE;
            case MATCH:
                return target == LOBBY || target == SPECTATING || target == TOURNAMENT || target == OFFLINE;
            case SPECTATING:
                return target == LOBBY || target == SPECTATING || target == OFFLINE;
            case TOURNAMENT:
                return target == LOBBY || target == MATCH || target == SPECTATING || target == OFFLINE;
            case KIT_EDITOR:
                return target == LOBBY || target == OFFLINE;
            case OFFLINE:
                return target == LOADING || target == LOBBY;
            default:
                return false;
        }
    }
}
