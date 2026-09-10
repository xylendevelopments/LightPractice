package gg.lightpractice.kit.rule;

import gg.lightpractice.kit.Kit;
import gg.lightpractice.service.LightService;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creates kit rules from configuration.
 *
 * <p>Every rule id maps to a factory, so {@code kits.yml} decides the behaviour of a kit and no
 * listener ever has to branch on a kit name. Adding a kit type therefore means adding a rule (or
 * reusing existing ones) rather than touching the match code.</p>
 */
public final class KitRuleRegistry implements LightService {

    private final Map<String, KitRuleFactory> factories = new LinkedHashMap<String, KitRuleFactory>();
    private final Map<String, String> aliases = new LinkedHashMap<String, String>();

    public KitRuleRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        register("no-fall-damage", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new NoFallDamageRule();
            }
        });
        register("no-hunger", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new NoHungerRule();
            }
        });
        register("no-regen", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new NoNaturalRegenerationRule();
            }
        });
        register("drop-items", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new DropItemsRule();
            }
        });
        register("build", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new BuildRule();
            }
        });
        register("break", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new BreakRule();
            }
        });
        register("time-limit", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new TimeLimitRule();
            }
        });
        register("void-loss", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new VoidLossRule();
            }
        });
        register("no-damage", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new NoDamageRule();
            }
        });
        register("sumo", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new SumoRule();
            }
        });
        register("spleef", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new SpleefRule();
            }
        });
        register("boxing", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new BoxingRule();
            }
        });
        register("combo", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new ComboRule();
            }
        });
        register("knockback", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new KnockbackRule();
            }
        });
        register("rod", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new RodRule();
            }
        });
        register("golden-apples", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new GoldenAppleRule();
            }
        });
        register("pearls", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new PearlRule();
            }
        });
        register("potions", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new PotionRule();
            }
        });
        register("tnt", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new TntRule();
            }
        });
        register("fireball", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new FireballRule();
            }
        });
        register("crystals", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new CrystalRule();
            }
        });
        register("beds", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new BedRule();
            }
        });
        register("respawn", new KitRuleFactory() {
            @Override
            public KitRule create(Kit kit, KitRuleOptions options) {
                return new RespawnRule();
            }
        });

        alias("nofalldamage", "no-fall-damage");
        alias("no-fall", "no-fall-damage");
        alias("falldamage", "no-fall-damage");
        alias("hunger", "no-hunger");
        alias("nohunger", "no-hunger");
        alias("regen", "no-regen");
        alias("natural-regen", "no-regen");
        alias("noregen", "no-regen");
        alias("drops", "drop-items");
        alias("item-drops", "drop-items");
        alias("place", "build");
        alias("building", "build");
        alias("destroy", "break");
        alias("breaking", "break");
        alias("limit", "time-limit");
        alias("timelimit", "time-limit");
        alias("void", "void-loss");
        alias("voidloss", "void-loss");
        alias("lives", "respawn");
        alias("respawning", "respawn");
        alias("bed", "beds");
        alias("bedfight", "beds");
        alias("pearl", "pearls");
        alias("ender-pearls", "pearls");
        alias("potion", "potions");
        alias("gapple", "golden-apples");
        alias("gapples", "golden-apples");
        alias("golden-apple", "golden-apples");
        alias("crystal", "crystals");
        alias("end-crystals", "crystals");
        alias("fireballs", "fireball");
        alias("fishing-rod", "rod");
        alias("kb", "knockback");
        alias("damage", "no-damage");
        alias("nodamage", "no-damage");
        alias("invulnerable", "no-damage");
    }

    /** Registers a rule factory, replacing any previous registration of the same id. */
    public void register(String id, KitRuleFactory factory) {
        if (id == null || factory == null) {
            return;
        }
        factories.put(id.trim().toLowerCase(Locale.ROOT), factory);
    }

    public boolean unregister(String id) {
        return id != null && factories.remove(id.trim().toLowerCase(Locale.ROOT)) != null;
    }

    public void alias(String alias, String id) {
        if (alias == null || id == null) {
            return;
        }
        aliases.put(alias.trim().toLowerCase(Locale.ROOT), id.trim().toLowerCase(Locale.ROOT));
    }

    /** Normalises a configured id, following aliases when present. */
    public String resolve(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        String target = aliases.get(key);
        return target == null ? key : target;
    }

    public boolean known(String id) {
        return id != null && factories.containsKey(resolve(id));
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(factories.keySet()));
    }

    public KitRuleFactory factory(String id) {
        return id == null ? null : factories.get(resolve(id));
    }

    /**
     * Builds the resolved rule set of a kit.
     *
     * <p>Unknown ids and rules that fail to load are reported and skipped so one broken entry can
     * never stop a kit from working.</p>
     */
    public KitRuleSet build(Kit kit) {
        List<KitRule> rules = new ArrayList<KitRule>();
        if (kit == null) {
            return new KitRuleSet(null, rules);
        }
        for (Map.Entry<String, Object> entry : kit.ruleOptions().entrySet()) {
            String id = resolve(entry.getKey());
            KitRuleFactory factory = factories.get(id);
            if (factory == null) {
                Debug.log(DebugCategory.CONFIG, "Kit {} uses unknown rule '{}' - ignored", kit.id(), entry.getKey());
                continue;
            }
            KitRuleOptions options = KitRuleOptions.of(entry.getValue());
            if (!options.enabled()) {
                Debug.log(DebugCategory.KIT, "Kit {} has rule '{}' disabled", kit.id(), id);
                continue;
            }
            KitRule rule;
            try {
                rule = factory.create(kit, options);
                if (rule == null) {
                    Debug.log(DebugCategory.CONFIG, "Rule '{}' of kit {} produced no instance", id, kit.id());
                    continue;
                }
                rule.load(options);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.KIT, "Could not load rule '" + id + "' of kit " + kit.id(), throwable);
                continue;
            }
            rules.add(rule);
            Debug.log(DebugCategory.KIT, "Kit {} enabled rule {}: {}", kit.id(), id, rule.describe(options));
        }
        return new KitRuleSet(kit, rules);
    }

    /** Creates a single rule instance, mainly for API consumers and tests. */
    public KitRule create(String id, Kit kit, KitRuleOptions options) {
        KitRuleFactory factory = factory(id);
        if (factory == null) {
            return null;
        }
        KitRule rule = factory.create(kit, options == null ? KitRuleOptions.disabled() : options);
        if (rule != null) {
            try {
                rule.load(options);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.KIT, "Could not load rule " + resolve(id), throwable);
                return null;
            }
        }
        return rule;
    }

    @Override
    public String name() {
        return "kit-rules";
    }

    @Override
    public int startupOrder() {
        return 40;
    }
}
