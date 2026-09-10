package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import gg.lightpractice.util.Cooldowns;
import org.bukkit.Material;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Throws fireballs from a fire charge, with recoil and configurable explosion damage.
 *
 * <pre>
 * fireball:
 *   enabled: true
 *   cooldown: 5000
 *   power: 1.6
 *   recoil: 0.9
 *   block-damage: false
 *   damage-scale: 0.6
 *   consume-item: true
 * </pre>
 */
public final class FireballRule implements KitRule {

    private final Cooldowns<UUID> cooldown = new Cooldowns<UUID>();
    private boolean enabled = true;
    private long cooldownMillis = 5000L;
    private double power = 1.6D;
    private double recoil = 0.9D;
    private boolean blockDamage;
    private double damageScale = 0.6D;
    private boolean consume = true;

    @Override
    public String id() {
        return "fireball";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.cooldownMillis = Math.max(0L, values.integer("cooldown", 5000));
        this.power = Math.max(0.1D, values.decimal("power", 1.6D));
        this.recoil = Math.max(0.0D, values.decimal("recoil", 0.9D));
        this.blockDamage = values.bool("block-damage", false);
        this.damageScale = Math.max(0.0D, values.decimal("damage-scale", 0.6D));
        this.consume = values.bool("consume-item", true);
    }

    @Override
    public boolean canUseItem(Match match, Player player, ItemStack item) {
        return enabled || item == null || item.getType() != Material.FIREBALL;
    }

    @Override
    public boolean handleItemUse(Match match, Player player, ItemStack item, Action action) {
        if (!enabled || player == null || item == null || item.getType() != Material.FIREBALL) {
            return false;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        if (!cooldown.ready(player.getUniqueId())) {
            return true;
        }
        Vector direction = player.getLocation().getDirection().normalize();
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setShooter(player);
        fireball.setIsIncendiary(false);
        fireball.setYield(0.0F);
        fireball.setVelocity(direction.clone().multiply(power));
        if (recoil > 0.0D) {
            Vector knock = direction.clone().multiply(-recoil).setY(recoil * 0.6D);
            player.setVelocity(player.getVelocity().add(knock));
            player.setFallDistance(0.0F);
        }
        if (consume) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
        }
        cooldown.mark(player.getUniqueId(), cooldownMillis);
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

    @Override
    public void onMatchEnd(Match match) {
        if (match != null) {
            for (Player player : match.alivePlayers()) {
                cooldown.remove(player.getUniqueId());
            }
        }
    }

    /** Milliseconds left on a player's fireball cooldown. */
    public long remaining(UUID uuid) {
        return Math.max(0L, cooldown.remaining(uuid));
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "Fireballs are disabled.";
        }
        return "Right click with a fire charge to launch a fireball.";
    }
}
