package gg.lightpractice.kit.rule;

import gg.lightpractice.model.KnockbackProfile;

/**
 * Applies a custom knockback profile to the kit.
 *
 * <pre>
 * knockback:
 *   name: custom
 *   horizontal: 0.40
 *   vertical: 0.36
 *   sprint-horizontal: 0.40
 *   sprint-vertical: 0.40
 *   air-multiplier: 1.0
 *   max-vertical: 0.45
 *   resistance: 0.0
 * </pre>
 */
public final class KnockbackRule implements KitRule {

    private KnockbackProfile profile;

    @Override
    public String id() {
        return "knockback";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        String name = values.string("name", "custom");
        this.profile = new KnockbackProfile(name,
                values.decimal("horizontal", 0.40D),
                values.decimal("vertical", 0.36D),
                values.decimal("sprint-horizontal", 0.40D),
                values.decimal("sprint-vertical", 0.40D),
                values.decimal("air-multiplier", 1.0D),
                values.decimal("max-vertical", 0.45D),
                values.decimal("resistance", 0.0D));
    }

    @Override
    public KnockbackProfile knockback() {
        return profile;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return profile == null ? "Vanilla knockback." : "Knockback profile: " + profile.name();
    }
}
