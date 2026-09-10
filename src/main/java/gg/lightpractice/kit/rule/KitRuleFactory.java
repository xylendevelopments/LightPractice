package gg.lightpractice.kit.rule;

import gg.lightpractice.kit.Kit;

/** Creates a rule instance for a kit from its configured options. */
public interface KitRuleFactory {

    KitRule create(Kit kit, KitRuleOptions options);
}
