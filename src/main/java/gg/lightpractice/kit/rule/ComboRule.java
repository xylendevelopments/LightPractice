package gg.lightpractice.kit.rule;

import gg.lightpractice.model.KnockbackProfile;

/**
 * Near zero vertical knockback so hits chain into combos.
 *
 * <pre>
 * combo:
 *   horizontal: 0.32
 *   vertical: 0.01
 * </pre>
 */
public final class ComboRule implements KitRule {

    private KnockbackProfile profile;

    @Override
    public String id() {
        return "combo";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.profile = new KnockbackProfile(values.string("name", "combo"),
                values.decimal("horizontal", 0.32D),
                values.decimal("vertical", 0.01D),
                values.decimal("sprint-horizontal", 0.34D),
                values.decimal("sprint-vertical", 0.01D),
                values.decimal("air-multiplier", 0.0D),
                values.decimal("max-vertical", 0.02D),
                values.decimal("resistance", 0.0D));
    }

    @Override
    public KnockbackProfile knockback() {
        return profile;
    }

    @Override
    public boolean allowFallDamage() {
        return false;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Reduced knockback makes hits chain into combos.";
    }
}
