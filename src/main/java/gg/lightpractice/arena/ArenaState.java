package gg.lightpractice.arena;

/** Lifecycle of an arena, kept separate from match state so resets can outlive a match. */
public enum ArenaState {

    IDLE,
    IN_USE,
    RESETTING,
    DISABLED;

    public boolean usable() {
        return this == IDLE;
    }

    public boolean busy() {
        return this == IN_USE || this == RESETTING;
    }
}
