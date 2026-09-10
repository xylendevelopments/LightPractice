package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Allows placing blocks, optionally restricted to a material whitelist.
 *
 * <pre>
 * build:
 *   allowed-blocks: [WOOL, SAND]
 *   deny-blocks: []
 *   notify: true
 * </pre>
 */
public final class BuildRule implements KitRule {

    private Set<Material> allowed;
    private Set<Material> denied;
    private boolean notify;
    private String denialKey;

    @Override
    public String id() {
        return "build";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        List<String> defaults = Arrays.asList("WOOL", "SAND", "GRAVEL", "COBBLESTONE", "WOOD", "GLASS");
        this.allowed = values.materials("allowed-blocks", defaults);
        this.denied = values.materials("deny-blocks");
        this.notify = values.bool("notify", true);
        this.denialKey = values.string("message", "match.cannot-place");
    }

    @Override
    public boolean allowBuilding() {
        return true;
    }

    @Override
    public boolean canPlace(Match match, Player player, Block block, ItemStack held) {
        if (block == null) {
            return false;
        }
        Material placed = held == null ? block.getType() : held.getType();
        if (placed == null || placed == Material.AIR) {
            placed = block.getType();
        }
        if (denied.contains(placed)) {
            return false;
        }
        return allowed.isEmpty() || allowed.contains(placed);
    }

    @Override
    public void onBlockPlaced(Match match, Player player, Block block) {
        // nothing to track, placement is validated before it happens
    }

    /** Message key sent when a placement is rejected. */
    public String denialKey() {
        return denialKey;
    }

    public boolean notify() {
        return notify;
    }

    public Set<Material> allowedBlocks() {
        return allowed;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (allowed == null || allowed.isEmpty()) {
            return "Any block may be placed.";
        }
        return "Placeable blocks: " + allowed;
    }
}
