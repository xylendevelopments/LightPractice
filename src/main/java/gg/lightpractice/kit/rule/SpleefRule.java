package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.Set;

/**
 * Spleef: break the floor under your opponent, no direct damage, falling means a loss.
 *
 * <pre>
 * spleef:
 *   allowed-blocks: [SNOW_BLOCK, ICE, PACKED_ICE]
 *   tool: DIAMOND_SPADE
 * </pre>
 */
public final class SpleefRule implements KitRule {

    private Set<Material> allowed;

    @Override
    public String id() {
        return "spleef";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.allowed = values.materials("allowed-blocks", Arrays.asList("SNOW_BLOCK", "ICE", "PACKED_ICE"));
    }

    @Override
    public boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID;
    }

    @Override
    public boolean allowFallDamage() {
        return false;
    }

    @Override
    public boolean allowBreaking() {
        return true;
    }

    @Override
    public boolean canBreak(Match match, Player player, Block block) {
        return block != null && (allowed.isEmpty() || allowed.contains(block.getType()));
    }

    @Override
    public boolean instantLoss(Match match, Player player, EntityDamageEvent.DamageCause cause, Location location) {
        return cause == EntityDamageEvent.DamageCause.VOID;
    }

    public Set<Material> allowedBlocks() {
        return allowed;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Break the blocks below your opponent, they lose when they fall.";
    }
}
