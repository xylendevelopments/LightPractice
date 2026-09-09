package gg.lightpractice.bot;

import java.util.Locale;

/**
 * Difficulty label of a bot preset.
 *
 * <p>The label drives the defaults used when a preset leaves a value unset and is shown in menus. The
 * behaviour itself always comes from the numeric fields of the preset, so an administrator can build a
 * hard bot that still reacts slowly or an easy bot with perfect aim.</p>
 */
public enum BotDifficulty {

    EASY(700L, 0.35D, 0.45D, 2.0D, 4.0D),
    NORMAL(450L, 0.55D, 0.65D, 3.0D, 6.0D),
    HARD(280L, 0.75D, 0.80D, 4.0D, 7.5D),
    INSANE(160L, 0.92D, 0.95D, 5.0D, 9.0D);

    private final long reactionMillis;
    private final double accuracy;
    private final double aggression;
    private final double minDamage;
    private final double maxDamage;

    BotDifficulty(long reactionMillis, double accuracy, double aggression, double minDamage, double maxDamage) {
        this.reactionMillis = reactionMillis;
        this.accuracy = accuracy;
        this.aggression = aggression;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
    }

    public long reactionMillis() {
        return reactionMillis;
    }

    public double accuracy() {
        return accuracy;
    }

    public double aggression() {
        return aggression;
    }

    public double minDamage() {
        return minDamage;
    }

    public double maxDamage() {
        return maxDamage;
    }

    public String displayName() {
        return gg.lightpractice.util.Text.color("&f" + gg.lightpractice.util.Text.capitalize(name()));
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BotDifficulty parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (BotDifficulty difficulty : values()) {
            if (difficulty.name().equals(key)) {
                return difficulty;
            }
        }
        if ("MEDIUM".equals(key)) {
            return NORMAL;
        }
        if ("IMPOSSIBLE".equals(key) || "GOD".equals(key)) {
            return INSANE;
        }
        return null;
    }
}
