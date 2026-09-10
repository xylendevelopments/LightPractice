package gg.lightpractice.kit.rule;

/**
 * Stops health regenerating from the food bar.
 *
 * <pre>no-regen: true</pre>
 */
public final class NoNaturalRegenerationRule implements KitRule {

    @Override
    public String id() {
        return "no-regen";
    }

    @Override
    public boolean allowNaturalRegeneration() {
        return false;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Natural regeneration is disabled.";
    }
}
