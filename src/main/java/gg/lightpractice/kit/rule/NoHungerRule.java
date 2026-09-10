package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.entity.Player;

/**
 * Keeps the food bar frozen so matches never turn into a hunger race.
 *
 * <pre>no-hunger: true</pre>
 */
public final class NoHungerRule implements KitRule {

    @Override
    public String id() {
        return "no-hunger";
    }

    @Override
    public boolean allowHunger() {
        return false;
    }

    @Override
    public void onPlayerEnter(Match match, Player player) {
        if (player != null) {
            player.setFoodLevel(20);
            player.setSaturation(5.0F);
            player.setExhaustion(0.0F);
        }
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "The food bar never drops.";
    }
}
