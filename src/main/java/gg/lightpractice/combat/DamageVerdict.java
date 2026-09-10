package gg.lightpractice.combat;

/**
 * Result of running a damage event through the kit rules.
 *
 * <p>A verdict either cancels the hit or states the damage that should actually be applied, so the
 * listener never has to know which rule made the decision.</p>
 */
public final class DamageVerdict {

    private static final DamageVerdict CANCELLED = new DamageVerdict(false, 0.0D, "cancelled");

    private final boolean allowed;
    private final double damage;
    private final String reason;

    public DamageVerdict(boolean allowed, double damage, String reason) {
        this.allowed = allowed;
        this.damage = Math.max(0.0D, damage);
        this.reason = reason == null ? "" : reason;
    }

    public static DamageVerdict allow(double damage) {
        return new DamageVerdict(true, damage, "allowed");
    }

    public static DamageVerdict allow(double damage, String reason) {
        return new DamageVerdict(true, damage, reason);
    }

    public static DamageVerdict deny() {
        return CANCELLED;
    }

    public static DamageVerdict deny(String reason) {
        return new DamageVerdict(false, 0.0D, reason);
    }

    public boolean allowed() {
        return allowed;
    }

    public double damage() {
        return damage;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "DamageVerdict{" + (allowed ? "allow " + damage : "deny") + ", " + reason + '}';
    }
}
