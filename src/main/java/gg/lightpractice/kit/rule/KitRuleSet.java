package gg.lightpractice.kit.rule;

import gg.lightpractice.kit.Kit;
import gg.lightpractice.match.Match;
import gg.lightpractice.model.KnockbackProfile;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolved rules of one kit.
 *
 * <p>Aggregation is deliberate and documented per question: restrictive hooks (damage, fall damage,
 * hunger, regeneration, projectiles, pearls, potions, golden apples) require <em>every</em> rule to
 * agree, while permissive hooks (building, breaking, drops, respawning, friendly fire) only need
 * <em>one</em> rule to allow them. That keeps a kit that combines {@code build} and {@code break}
 * predictable without rules having to know about each other.</p>
 */
public final class KitRuleSet {

    private final Kit kit;
    private final List<KitRule> rules;

    public KitRuleSet(Kit kit, List<KitRule> rules) {
        this.kit = kit;
        this.rules = rules == null ? new ArrayList<KitRule>() : new ArrayList<KitRule>(rules);
    }

    public Kit kit() {
        return kit;
    }

    public List<KitRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean has(String id) {
        return find(id) != null;
    }

    public KitRule find(String id) {
        if (id == null) {
            return null;
        }
        for (KitRule rule : rules) {
            if (rule.id().equalsIgnoreCase(id)) {
                return rule;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ restrictive

    public boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        for (KitRule rule : rules) {
            if (!rule.allowDamage(match, victim, cause)) {
                return false;
            }
        }
        return true;
    }

    public boolean allowFallDamage() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowFallDamage();
            }
        }, true);
    }

    public boolean allowHunger() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowHunger();
            }
        }, true);
    }

    public boolean allowNaturalRegeneration() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowNaturalRegeneration();
            }
        }, true);
    }

    public boolean allowProjectiles() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowProjectiles();
            }
        }, true);
    }

    public boolean allowEnderPearls() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowEnderPearls();
            }
        }, true);
    }

    public boolean allowPotions() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowPotions();
            }
        }, true);
    }

    public boolean allowGoldenApples() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowGoldenApples();
            }
        }, true);
    }

    // ----------------------------------------------------------- permissive

    public boolean allowBuilding() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowBuilding();
            }
        });
    }

    public boolean allowBreaking() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowBreaking();
            }
        });
    }

    public boolean allowItemDrops() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowItemDrops();
            }
        });
    }

    public boolean allowRespawn() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowRespawn();
            }
        });
    }

    public boolean allowFriendlyFire() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowFriendlyFire();
            }
        });
    }

    /** Lives granted by the most restrictive respawn rule, {@code -1} when unlimited. */
    public int lives() {
        int lowest = -1;
        boolean respawn = false;
        for (KitRule rule : rules) {
            if (!rule.allowRespawn()) {
                continue;
            }
            respawn = true;
            int value = rule.lives();
            if (value >= 0 && (lowest < 0 || value < lowest)) {
                lowest = value;
            }
        }
        return respawn ? lowest : 0;
    }

    public boolean canPlace(final Match match, final Player player, final Block block, final ItemStack held) {
        for (KitRule rule : rules) {
            if (rule.canPlace(match, player, block, held)) {
                return true;
            }
        }
        return false;
    }

    public boolean canBreak(final Match match, final Player player, final Block block) {
        for (KitRule rule : rules) {
            if (rule.canBreak(match, player, block)) {
                return true;
            }
        }
        return false;
    }

    public boolean canUseItem(Match match, Player player, ItemStack item) {
        for (KitRule rule : rules) {
            if (!rule.canUseItem(match, player, item)) {
                return false;
            }
        }
        return true;
    }

    public double modifyDamage(Match match, Player victim, Player attacker, double damage,
                               EntityDamageEvent.DamageCause cause) {
        double value = damage;
        for (KitRule rule : rules) {
            value = rule.modifyDamage(match, victim, attacker, value, cause);
            if (value <= 0.0D) {
                return 0.0D;
            }
        }
        return Math.max(0.0D, value);
    }

    public boolean instantLoss(Match match, Player player, EntityDamageEvent.DamageCause cause, Location location) {
        for (KitRule rule : rules) {
            if (rule.instantLoss(match, player, cause, location)) {
                return true;
            }
        }
        return false;
    }

    public KnockbackProfile knockback() {
        for (KitRule rule : rules) {
            KnockbackProfile profile = rule.knockback();
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }

    public ItemStack[] respawnItems(Match match, Player player) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (KitRule rule : rules) {
            ItemStack[] extra = rule.respawnItems(match, player);
            if (extra != null) {
                Collections.addAll(items, extra);
            }
        }
        return items.toArray(new ItemStack[0]);
    }

    // ------------------------------------------------------------- lifecycle

    public void onMatchStart(Match match) {
        for (KitRule rule : rules) {
            rule.onMatchStart(match);
        }
    }

    public void onPlayerEnter(Match match, Player player) {
        for (KitRule rule : rules) {
            rule.onPlayerEnter(match, player);
        }
    }

    public void onTick(Match match) {
        for (KitRule rule : rules) {
            rule.onTick(match);
        }
    }

    public void onMatchEnd(Match match) {
        for (KitRule rule : rules) {
            rule.onMatchEnd(match);
        }
    }

    public void onPlayerEliminated(Match match, Player player) {
        for (KitRule rule : rules) {
            rule.onPlayerEliminated(match, player);
        }
    }

    public void onPlayerRespawn(Match match, Player player) {
        for (KitRule rule : rules) {
            rule.onPlayerRespawn(match, player);
        }
    }

    public void onPlayerHit(Match match, Player attacker, Player victim) {
        for (KitRule rule : rules) {
            rule.onPlayerHit(match, attacker, victim);
        }
    }

    public void onBlockBroken(Match match, Player player, Block block) {
        for (KitRule rule : rules) {
            rule.onBlockBroken(match, player, block);
        }
    }

    public void onBlockPlaced(Match match, Player player, Block block) {
        for (KitRule rule : rules) {
            rule.onBlockPlaced(match, player, block);
        }
    }

    public double rodPullStrength() {
        for (KitRule rule : rules) {
            double value = rule.rodPullStrength();
            if (value >= 0.0D) {
                return value;
            }
        }
        return -1.0D;
    }

    public boolean allowExplosionBlockDamage() {
        return any(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowExplosionBlockDamage();
            }
        });
    }

    public double explosionDamageScale() {
        double scale = 1.0D;
        for (KitRule rule : rules) {
            scale *= Math.max(0.0D, rule.explosionDamageScale());
        }
        return scale;
    }

    public long goldenAppleCooldownMillis() {
        long cooldown = 0L;
        for (KitRule rule : rules) {
            cooldown = Math.max(cooldown, Math.max(0L, rule.goldenAppleCooldownMillis()));
        }
        return cooldown;
    }

    public long pearlCooldownMillis() {
        long cooldown = 0L;
        for (KitRule rule : rules) {
            cooldown = Math.max(cooldown, Math.max(0L, rule.pearlCooldownMillis()));
        }
        return cooldown;
    }

    public double pearlDamage() {
        double damage = 0.0D;
        for (KitRule rule : rules) {
            damage = Math.max(damage, Math.max(0.0D, rule.pearlDamage()));
        }
        return damage;
    }

    public boolean allowDrinkingPotions() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowDrinkingPotions();
            }
        }, true);
    }

    public boolean allowSplashPotions() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowSplashPotions();
            }
        }, true);
    }

    public boolean handleItemUse(Match match, Player player, ItemStack item,
                                 org.bukkit.event.block.Action action) {
        for (KitRule rule : rules) {
            if (rule.handleItemUse(match, player, item, action)) {
                return true;
            }
        }
        return false;
    }

    public boolean canRespawn(Match match, Player player) {
        for (KitRule rule : rules) {
            if (!rule.canRespawn(match, player)) {
                return false;
            }
        }
        return allowRespawn();
    }

    public Location respawnLocation(Match match, Player player) {
        for (KitRule rule : rules) {
            Location location = rule.respawnLocation(match, player);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    public void onDeath(Match match, Player player) {
        for (KitRule rule : rules) {
            rule.onDeath(match, player);
        }
    }

    public boolean allowDeathScreen() {
        return all(new Question() {
            @Override
            public boolean ask(KitRule rule) {
                return rule.allowDeathScreen();
            }
        }, true);
    }

    private boolean all(Question question, boolean whenEmpty) {
        if (rules.isEmpty()) {
            return whenEmpty;
        }
        for (KitRule rule : rules) {
            if (!question.ask(rule)) {
                return false;
            }
        }
        return true;
    }

    private boolean any(Question question) {
        for (KitRule rule : rules) {
            if (question.ask(rule)) {
                return true;
            }
        }
        return false;
    }

    private interface Question {
        boolean ask(KitRule rule);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("KitRuleSet[");
        for (KitRule rule : rules) {
            builder.append(rule.id()).append(' ');
        }
        return builder.append(']').toString();
    }
}
