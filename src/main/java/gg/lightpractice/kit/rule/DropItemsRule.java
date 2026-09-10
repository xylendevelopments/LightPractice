package gg.lightpractice.kit.rule;

/**
 * Lets players drop items and keeps dropped items after a death.
 *
 * <pre>drop-items: true</pre>
 */
public final class DropItemsRule implements KitRule {

    @Override
    public String id() {
        return "drop-items";
    }

    @Override
    public boolean allowItemDrops() {
        return true;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Dropped items stay on the ground.";
    }
}
