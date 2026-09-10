package gg.lightpractice.kit.rule;

/**
 * Configures how strongly the fishing rod pulls opponents.
 *
 * <pre>
 * rod:
 *   pull-strength: 0.75
 *   damage: false
 *   cooldown: 500
 * </pre>
 */
public final class RodRule implements KitRule {

    private double pullStrength = 0.75D;
    private boolean damage;
    private long cooldownMillis;

    @Override
    public String id() {
        return "rod";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.pullStrength = Math.max(0.0D, values.decimal("pull-strength", 0.75D));
        this.damage = values.bool("damage", false);
        this.cooldownMillis = Math.max(0L, values.integer("cooldown", 0));
    }

    @Override
    public double rodPullStrength() {
        return pullStrength;
    }

    public boolean damage() {
        return damage;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Fishing rod pulls with strength " + pullStrength + ".";
    }
}
