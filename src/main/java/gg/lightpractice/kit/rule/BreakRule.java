package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Allows breaking blocks, normally restricted to a whitelist such as snow for spleef.
 *
 * <pre>
 * break:
 *   allowed-blocks: [SNOW_BLOCK, ICE, PACKED_ICE]
 *   deny-blocks: []
 *   message: match.cannot-break
 * </pre>
 */
public final class BreakRule implements KitRule {

    private Set<Material> allowed;
    private Set<Material> denied;
    private String denialKey;

    @Override
    public String id() {
        return "break";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        List<String> defaults = Arrays.asList("SNOW_BLOCK", "ICE", "PACKED_ICE");
        this.allowed = values.materials("allowed-blocks", defaults);
        this.denied = values.materials("deny-blocks");
        this.denialKey = values.string("message", "match.cannot-break");
    }

    @Override
    public boolean allowBreaking() {
        return true;
    }

    @Override
    public boolean canBreak(Match match, Player player, Block block) {
        if (block == null) {
            return false;
        }
        Material material = block.getType();
        if (denied.contains(material)) {
            return false;
        }
        return allowed.isEmpty() || allowed.contains(material);
    }

    public String denialKey() {
        return denialKey;
    }

    public Set<Material> allowedBlocks() {
        return allowed;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (allowed == null || allowed.isEmpty()) {
            return "Any block may be broken.";
        }
        return "Breakable blocks: " + allowed;
    }
}
