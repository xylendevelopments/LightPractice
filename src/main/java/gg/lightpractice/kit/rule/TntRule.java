package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

/**
 * Instant ignite TNT that explodes without destroying the arena.
 *
 * <pre>
 * tnt:
 *   enabled: true
 *   instant: true
 *   fuse-ticks: 20
 *   block-damage: false
 *   damage-scale: 1.0
 *   ignite-item: FLINT_AND_STEEL
 * </pre>
 */
public final class TntRule implements KitRule {

    private boolean enabled = true;
    private boolean instant = true;
    private int fuseTicks = 20;
    private boolean blockDamage;
    private double damageScale = 1.0D;
    private Material igniteItem = Material.FLINT_AND_STEEL;

    @Override
    public String id() {
        return "tnt";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.instant = values.bool("instant", true);
        this.fuseTicks = Math.max(0, values.integer("fuse-ticks", 20));
        this.blockDamage = values.bool("block-damage", false);
        this.damageScale = Math.max(0.0D, values.decimal("damage-scale", 1.0D));
        this.igniteItem = values.material("ignite-item", Material.FLINT_AND_STEEL);
    }

    @Override
    public boolean canUseItem(Match match, Player player, ItemStack item) {
        return enabled || item == null || item.getType() != igniteItem;
    }

    @Override
    public boolean handleItemUse(Match match, Player player, ItemStack item, Action action) {
        if (!enabled || item == null || item.getType() != igniteItem || player == null) {
            return false;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        org.bukkit.block.Block target = player.getTargetBlock(null, 5);
        if (target == null || target.getType() != Material.TNT) {
            return false;
        }
        if (match != null && !match.canModify(target.getLocation())) {
            return true;
        }
        target.setType(Material.AIR);
        org.bukkit.entity.TNTPrimed primed = target.getWorld().spawn(target.getLocation().add(0.5D, 0.0D, 0.5D),
                org.bukkit.entity.TNTPrimed.class);
        primed.setFuseTicks(instant ? fuseTicks : 80);
        primed.setSource(player);
        primed.setVelocity(player.getLocation().getDirection().multiply(0.6D).setY(0.35D));
        return true;
    }

    @Override
    public boolean allowExplosionBlockDamage() {
        return enabled && blockDamage;
    }

    @Override
    public double explosionDamageScale() {
        return enabled ? damageScale : 1.0D;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean instant() {
        return instant;
    }

    public int fuseTicks() {
        return fuseTicks;
    }

    public Material igniteItem() {
        return igniteItem;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "TNT is disabled.";
        }
        return "TNT ignites instantly" + (blockDamage ? " and breaks blocks" : " without breaking blocks") + ".";
    }
}
