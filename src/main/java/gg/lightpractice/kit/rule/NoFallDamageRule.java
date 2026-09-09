package gg.lightpractice.kit.rule;

/**
 * Disables fall damage.
 *
 * <pre>no-fall-damage: true</pre>
 */
public final class NoFallDamageRule implements KitRule {

    @Override
    public String id() {
        return "no-fall-damage";
    }

    @Override
    public boolean allowFallDamage() {
        return false;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Fall damage is disabled.";
    }
}
