package gg.lightpractice.progress;

import gg.lightpractice.api.event.LightPracticeLevelUpEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Experience curves and level ups.
 *
 * <p>The curve is configuration (base experience, growth multiplier, hard cap per step, maximum level),
 * so a level is always derived from the stored experience instead of being written back and forth. That
 * keeps profiles consistent when the curve is retuned.</p>
 */
public final class LevelManager implements LightService {

    private final PluginCore core;
    private long baseExperience = 100L;
    private double growth = 1.15D;
    private long maximumStep = 25000L;
    private int maximumLevel = 100;
    private boolean linear;
    private int levelCoins = 25;
    private String levelSound = "LEVEL_UP";
    private String barFilled = "&a|";
    private String barEmpty = "&7|";
    private int barLength = 20;

    public LevelManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "levels";
    }

    @Override
    public int startupOrder() {
        return 62;
    }

    @Override
    public void onLoad() {
        load(core.configs().rewards());
    }

    @Override
    public void onReload() {
        load(core.configs().rewards());
    }

    public void load(ConfigFile file) {
        if (file == null) {
            return;
        }
        this.baseExperience = Math.max(1L, file.getLong("levels.base-experience", 100L));
        this.growth = Math.max(1.0D, file.getDouble("levels.growth", 1.15D));
        this.maximumStep = Math.max(baseExperience, file.getLong("levels.maximum-step", 25000L));
        this.maximumLevel = Math.max(1, file.getInt("levels.maximum-level", 100));
        this.linear = file.getBoolean("levels.linear", false);
        this.levelCoins = Math.max(0, file.getInt("level-up.coins", 25));
        this.levelSound = file.getString("level-up.sound", "LEVEL_UP");
        this.barFilled = Text.color(file.getString("level-up.bar-filled", "&a|"));
        this.barEmpty = Text.color(file.getString("level-up.bar-empty", "&7|"));
        this.barLength = Math.max(1, Math.min(60, file.getInt("level-up.bar-length", 20)));
        Debug.log(DebugCategory.REWARD, "Levels: base {} xp, growth {}, max level {}", baseExperience, growth,
                maximumLevel);
    }

    /** Experience needed to advance from {@code level} to the next one. */
    public long experienceForLevel(int level) {
        int current = Math.max(1, Math.min(maximumLevel, level));
        if (linear) {
            return Math.max(1L, baseExperience * current);
        }
        double value = baseExperience * Math.pow(growth, current - 1);
        if (Double.isNaN(value) || Double.isInfinite(value) || value > maximumStep) {
            value = maximumStep;
        }
        return Math.max(1L, (long) Math.ceil(value));
    }

    /** Total experience a level starts at. */
    public long totalExperienceForLevel(int level) {
        long total = 0L;
        int target = Math.max(1, Math.min(maximumLevel, level));
        for (int index = 1; index < target; index++) {
            total += experienceForLevel(index);
            if (total < 0L) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    /** Level that matches a total experience value. */
    public int levelOf(long experience) {
        long total = 0L;
        for (int level = 1; level <= maximumLevel; level++) {
            long need = experienceForLevel(level);
            if (total + need > experience || level == maximumLevel) {
                return level;
            }
            total += need;
        }
        return maximumLevel;
    }

    /** Experience collected inside the current level. */
    public long intoLevel(long experience) {
        int level = levelOf(experience);
        long start = totalExperienceForLevel(level);
        return Math.max(0L, experience - start);
    }

    /** Progress through the current level between {@code 0.0} and {@code 1.0}. */
    public double progress(long experience) {
        long need = experienceForLevel(levelOf(experience));
        if (need <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) intoLevel(experience) / (double) need));
    }

    public long remaining(Profile profile) {
        if (profile == null) {
            return 0L;
        }
        long need = experienceForLevel(levelOf(profile.experience()));
        return Math.max(0L, need - intoLevel(profile.experience()));
    }

    /** Textual progress bar used by scoreboards and menus. */
    public String bar(Profile profile) {
        return bar(profile == null ? 0.0D : progress(profile.experience()), barLength);
    }

    public String bar(double progress, int length) {
        int size = Math.max(1, length);
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * size);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < size; index++) {
            builder.append(index < filled ? barFilled : barEmpty);
        }
        return builder.toString();
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    /**
     * Synchronises the stored level with the stored experience and reports level ups.
     *
     * @return how many levels were gained
     */
    public int checkLevelUp(Player player, Profile profile) {
        if (profile == null) {
            return 0;
        }
        int previous = Math.max(1, profile.level());
        int target = levelOf(profile.experience());
        if (target <= previous) {
            if (target != previous) {
                profile.level(target);
            }
            return 0;
        }
        profile.level(target);
        Bukkit.getPluginManager().callEvent(new LightPracticeLevelUpEvent(profile, previous, target));
        long coins = (long) levelCoins * (target - previous);
        if (coins > 0L) {
            profile.addCoins(coins);
        }
        Player receiver = player != null ? player : Bukkit.getPlayer(profile.uuid());
        if (receiver != null) {
            core.messages().send(receiver, "progress.level-up",
                    "{level}", String.valueOf(target),
                    "{previous}", String.valueOf(previous),
                    "{coins}", String.valueOf(coins),
                    "{experience}", String.valueOf(profile.experience()),
                    "{next}", String.valueOf(experienceForLevel(target)));
            core.messages().title(receiver, "progress.level-up-title", "progress.level-up-subtitle",
                    "{level}", String.valueOf(target));
            if (levelSound != null && !levelSound.isEmpty()) {
                Visuals.sound(receiver, levelSound, 1.0F, 1.0F);
            }
        }
        Debug.log(DebugCategory.REWARD, "{} reached level {} ({} levels gained)", profile.name(), target,
                target - previous);
        return target - previous;
    }

    /** Level information for placeholders, menus and scoreboards. */
    public String describe(Profile profile) {
        if (profile == null) {
            return "level 1";
        }
        int level = levelOf(profile.experience());
        return "level " + level + " (" + intoLevel(profile.experience()) + "/" + experienceForLevel(level) + " xp)";
    }
}
