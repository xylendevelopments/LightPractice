package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Bed Fight style respawn gating: a team keeps respawning until its bed is broken.
 *
 * <pre>
 * beds:
 *   enabled: true
 *   protection-radius: 25.0
 *   message: match.bed-destroyed
 * </pre>
 *
 * <p>The bed owner is resolved as the team whose spawn is closest to the broken bed inside the
 * protection radius, so pre-placed arena beds work without extra configuration.</p>
 */
public final class BedRule implements KitRule {

    private boolean enabled = true;
    private double protectionRadius = 25.0D;
    private String messageKey = "match.bed-destroyed";

    @Override
    public String id() {
        return "beds";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.protectionRadius = Math.max(1.0D, values.decimal("protection-radius", 25.0D));
        this.messageKey = values.string("message", "match.bed-destroyed");
    }

    @Override
    public boolean allowBreaking() {
        return enabled;
    }

    @Override
    public boolean canBreak(Match match, Player player, Block block) {
        return enabled && block != null && block.getType() == Material.BED_BLOCK;
    }

    @Override
    public boolean canPlace(Match match, Player player, Block block, org.bukkit.inventory.ItemStack held) {
        return enabled && held != null && held.getType() == Material.BED;
    }

    @Override
    public void onBlockBroken(Match match, Player player, Block block) {
        if (!enabled || match == null || player == null || block == null || block.getType() != Material.BED_BLOCK) {
            return;
        }
        MatchTeam owner = ownerOf(match, block.getLocation());
        if (owner == null) {
            Debug.log(DebugCategory.KIT, "A bed was broken in match {} but no team owns it", match.id());
            return;
        }
        owner.respawnEnabled(false);
        match.broadcast(messageKey, "{team}", owner.coloredName(), "{player}", player.getName());
        Debug.log(DebugCategory.MATCH, "Bed of team {} destroyed in match {} by {}",
                owner.name(), match.id(), player.getName());
    }

    @Override
    public boolean canRespawn(Match match, Player player) {
        if (!enabled || match == null || player == null) {
            return true;
        }
        MatchTeam team = match.teamOf(player);
        return team == null || team.respawnEnabled();
    }

    /** Team whose spawn is nearest to the bed within the protection radius. */
    public MatchTeam ownerOf(Match match, Location location) {
        MatchTeam best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MatchTeam team : match.teams()) {
            Location spawn = team.spawn();
            if (spawn == null || spawn.getWorld() == null || location.getWorld() == null) {
                continue;
            }
            if (!spawn.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = spawn.distance(location);
            if (distance <= protectionRadius && distance < bestDistance) {
                best = team;
                bestDistance = distance;
            }
        }
        return best;
    }

    public boolean enabled() {
        return enabled;
    }

    public String messageKey() {
        return messageKey;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return enabled ? "Destroy enemy beds to stop their respawns." : "Beds are disabled.";
    }
}
