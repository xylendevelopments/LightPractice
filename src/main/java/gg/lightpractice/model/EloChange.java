package gg.lightpractice.model;

/** Rating transition applied to a profile after a ranked match. */
public final class EloChange {

    private final int before;
    private final int after;

    public EloChange(int before, int after) {
        this.before = before;
        this.after = after;
    }

    public int before() {
        return before;
    }

    public int after() {
        return after;
    }

    public int delta() {
        return after - before;
    }

    public boolean gained() {
        return after > before;
    }

    /** Signed value formatted for messages, for example {@code +18} or {@code -12}. */
    public String formatted() {
        int delta = delta();
        return delta >= 0 ? "+" + delta : String.valueOf(delta);
    }

    @Override
    public String toString() {
        return before + " -> " + after + " (" + formatted() + ")";
    }
}
