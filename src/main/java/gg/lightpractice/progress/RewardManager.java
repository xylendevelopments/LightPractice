package gg.lightpractice.progress;

import gg.lightpractice.api.RewardService;
import gg.lightpractice.api.event.LightPracticeDailyRewardClaimEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.model.RewardBundle;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Coins, experience and daily rewards.
 *
 * <p>Rewards are computed from configuration and the outcome of a match, granted through the profile and
 * reported through the level manager. Daily rewards use the stored claim timestamp, so a reconnect or a
 * server restart can never be used to claim twice.</p>
 */
public final class RewardManager implements RewardService, LightService {

    private final PluginCore core;
    private final LevelManager levels;
    private RewardBundle win = new RewardBundle(50, 100L);
    private RewardBundle loss = new RewardBundle(15, 40L);
    private RewardBundle draw = new RewardBundle(10, 25L);
    private RewardBundle forfeit = RewardBundle.EMPTY;
    private RewardBundle perMinute = new RewardBundle(5, 10L);
    private double rankedMultiplier = 1.5D;
    private int playtimeCapMinutes = 30;
    private boolean winstreakEnabled = true;
    private int winstreakStep = 5;
    private RewardBundle winstreakReward = new RewardBundle(25, 50L);
    private int winstreakMaximumSteps = 4;
    private long dailyIntervalMillis = 24L * 60L * 60L * 1000L;
    private RewardBundle dailyBase = new RewardBundle(100, 200L);
    private double dailyStreakMultiplier = 0.25D;
    private int dailyStreakCap = 7;
    private boolean dailyResetOnMiss = true;
    private String rewardSound = "";

    public RewardManager(PluginCore core, LevelManager levels) {
        this.core = core;
        this.levels = levels;
    }

    @Override
    public String name() {
        return "rewards";
    }

    @Override
    public int startupOrder() {
        return 64;
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
        this.win = bundle(file, "match.win", 50, 100L);
        this.loss = bundle(file, "match.loss", 15, 40L);
        this.draw = bundle(file, "match.draw", 10, 25L);
        this.forfeit = bundle(file, "match.forfeit", 0, 0L);
        this.perMinute = bundle(file, "match.per-minute", 5, 10L);
        this.rankedMultiplier = Math.max(0.0D, file.getDouble("match.ranked-multiplier", 1.5D));
        this.playtimeCapMinutes = Math.max(0, file.getInt("match.playtime-cap-minutes", 30));
        this.winstreakEnabled = file.getBoolean("match.winstreak.enabled", true);
        this.winstreakStep = Math.max(1, file.getInt("match.winstreak.step", 5));
        this.winstreakReward = bundle(file, "match.winstreak.reward", 25, 50L);
        this.winstreakMaximumSteps = Math.max(0, file.getInt("match.winstreak.maximum-steps", 4));
        this.dailyIntervalMillis = Math.max(60000L, file.getLong("daily.interval-hours", 24L) * 60L * 60L * 1000L);
        this.dailyBase = bundle(file, "daily.reward", 100, 200L);
        this.dailyStreakMultiplier = Math.max(0.0D, file.getDouble("daily.streak-multiplier", 0.25D));
        this.dailyStreakCap = Math.max(1, file.getInt("daily.streak-cap", 7));
        this.dailyResetOnMiss = file.getBoolean("daily.reset-on-miss", true);
        this.rewardSound = file.getString("sounds.reward", "");
        Debug.log(DebugCategory.REWARD, "Rewards: win {} coins/{} xp, ranked x{}, daily every {}h",
                win.coins(), win.experience(), rankedMultiplier, dailyIntervalMillis / 3600000L);
    }

    private RewardBundle bundle(ConfigFile file, String path, int coins, long experience) {
        return new RewardBundle(Math.max(0, file.getInt(path + ".coins", coins)),
                Math.max(0L, file.getLong(path + ".experience", experience)));
    }

    public LevelManager levels() {
        return levels;
    }

    // ----------------------------------------------------------- RewardService

    @Override
    public RewardBundle matchReward(Match match, UUID uuid, MatchOutcome outcome) {
        if (match == null || uuid == null) {
            return RewardBundle.EMPTY;
        }
        RewardBundle base;
        if (outcome == MatchOutcome.WIN) {
            base = win;
        } else if (outcome == MatchOutcome.DRAW) {
            base = draw;
        } else if (outcome == MatchOutcome.FORFEIT) {
            base = forfeit;
        } else {
            base = loss;
        }
        double multiplier = match.ranked() ? Math.max(0.0D, rankedMultiplier) : 1.0D;
        int coins = (int) Math.round(base.coins() * multiplier);
        long experience = Math.round(base.experience() * multiplier);
        long minutes = match.durationMillis() / 60000L;
        if (playtimeCapMinutes > 0 && minutes > playtimeCapMinutes) {
            minutes = playtimeCapMinutes;
        }
        if (minutes > 0L && !perMinute.isEmpty()) {
            coins += (int) (perMinute.coins() * minutes);
            experience += perMinute.experience() * minutes;
        }
        if (winstreakEnabled && outcome == MatchOutcome.WIN) {
            int streak = winstreakOf(uuid, match.ranked());
            int steps = streak / winstreakStep;
            if (winstreakMaximumSteps > 0 && steps > winstreakMaximumSteps) {
                steps = winstreakMaximumSteps;
            }
            if (steps > 0) {
                coins += winstreakReward.coins() * steps;
                experience += winstreakReward.experience() * steps;
            }
        }
        return new RewardBundle(Math.max(0, coins), Math.max(0L, experience));
    }

    private int winstreakOf(UUID uuid, boolean ranked) {
        Profile profile = profile(uuid);
        if (profile == null) {
            return 0;
        }
        return profile.statistics(ranked).winstreak();
    }

    @Override
    public void grant(Player player, Profile profile, RewardBundle bundle, String reason) {
        if (profile == null || bundle == null || bundle.isEmpty()) {
            return;
        }
        if (bundle.coins() > 0) {
            profile.addCoins(bundle.coins());
        }
        if (bundle.experience() > 0L) {
            profile.addExperience(bundle.experience());
        }
        Player receiver = player != null ? player : Bukkit.getPlayer(profile.uuid());
        if (receiver != null) {
            core.messages().send(receiver, "progress.reward",
                    "{coins}", String.valueOf(bundle.coins()),
                    "{experience}", String.valueOf(bundle.experience()),
                    "{reason}", reason == null ? "" : reason,
                    "{level}", String.valueOf(profile.level()),
                    "{next-level}", String.valueOf(levels == null ? 0 : levels.remaining(profile)));
            if (rewardSound != null && !rewardSound.isEmpty()) {
                Visuals.sound(receiver, rewardSound, 1.0F, 1.0F);
            }
        }
        if (levels != null) {
            levels.checkLevelUp(receiver, profile);
        }
        profile.markDirty();
        Debug.log(DebugCategory.REWARD, "{} earned {} coins and {} xp ({})", profile.name(), bundle.coins(),
                bundle.experience(), reason);
    }

    /** Rewards every participant of a finished match; called once by the match cleanup. */
    public void handleMatchEnd(Match match) {
        if (match == null || match.type() == null || !match.type().counted()) {
            return;
        }
        if (match.endCause() != null && !match.endCause().counted()) {
            Debug.log(DebugCategory.REWARD, "Match {} ended with {}, no rewards granted", match.identifier(),
                    match.endCause());
            return;
        }
        for (MatchPlayer participant : match.participants()) {
            MatchOutcome outcome = participant.outcome() == null ? MatchOutcome.LOSS : participant.outcome();
            RewardBundle bundle = matchReward(match, participant.uuid(), outcome);
            Profile profile = profile(participant.uuid());
            if (profile == null) {
                continue;
            }
            Player player = participant.player();
            grant(player, profile, bundle, outcome.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private Profile profile(UUID uuid) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || uuid == null ? null : profiles.getProfile(uuid);
    }

    // ------------------------------------------------------------------ dailies

    @Override
    public boolean canClaimDaily(Profile profile) {
        return profile != null && dailyRemainingMillis(profile) <= 0L;
    }

    @Override
    public long dailyRemainingMillis(Profile profile) {
        if (profile == null) {
            return 0L;
        }
        long last = profile.lastDailyClaim();
        if (last <= 0L) {
            return 0L;
        }
        return Math.max(0L, last + dailyIntervalMillis - System.currentTimeMillis());
    }

    @Override
    public int dailyStreakCap() {
        return dailyStreakCap;
    }

    @Override
    public int dailyStreak(Profile profile) {
        return profile == null ? 0 : Math.max(0, Math.min(dailyStreakCap, profile.dailyStreak()));
    }

    /** Streak the next claim would have, honouring the missed day reset. */
    public int nextStreak(Profile profile) {
        if (profile == null) {
            return 1;
        }
        long last = profile.lastDailyClaim();
        if (last <= 0L) {
            return 1;
        }
        long since = System.currentTimeMillis() - last;
        if (since < dailyIntervalMillis) {
            return dailyStreak(profile);
        }
        if (!dailyResetOnMiss && since < dailyIntervalMillis * 2L) {
            return Math.min(dailyStreakCap, dailyStreak(profile) + 1);
        }
        if (since > dailyIntervalMillis * 2L) {
            return 1;
        }
        return Math.min(dailyStreakCap, dailyStreak(profile) + 1);
    }

    @Override
    public RewardBundle dailyReward(Profile profile) {
        int streak = Math.max(1, nextStreak(profile));
        double multiplier = 1.0D + (dailyStreakMultiplier * (streak - 1));
        int coins = (int) Math.round(dailyBase.coins() * multiplier);
        long experience = Math.round(dailyBase.experience() * multiplier);
        return new RewardBundle(Math.max(0, coins), Math.max(0L, experience));
    }

    @Override
    public boolean claimDaily(Player player) {
        if (player == null) {
            return false;
        }
        Profile profile = profile(player.getUniqueId());
        if (profile == null) {
            core.messages().send(player, "daily.profile-missing");
            return false;
        }
        if (!canClaimDaily(profile)) {
            core.messages().send(player, "daily.cooldown",
                    "{time}", Text.duration(dailyRemainingMillis(profile)));
            return false;
        }
        int streak = nextStreak(profile);
        RewardBundle bundle = dailyReward(profile);
        LightPracticeDailyRewardClaimEvent event =
                new LightPracticeDailyRewardClaimEvent(player, profile, bundle, streak);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.REWARD, "A plugin cancelled the daily reward of {}", player.getName());
            return false;
        }
        profile.claimDaily(System.currentTimeMillis(), streak);
        grant(player, profile, bundle, "daily");
        core.messages().send(player, "daily.claimed",
                "{streak}", String.valueOf(streak),
                "{cap}", String.valueOf(dailyStreakCap),
                "{coins}", String.valueOf(bundle.coins()),
                "{experience}", String.valueOf(bundle.experience()),
                "{next}", Text.duration(dailyIntervalMillis));
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles != null) {
            profiles.save(profile);
        }
        Debug.log(DebugCategory.REWARD, "{} claimed daily reward #{} ({} coins, {} xp)", player.getName(),
                streak, bundle.coins(), bundle.experience());
        return true;
    }

    public long dailyIntervalMillis() {
        return dailyIntervalMillis;
    }

    public RewardBundle win() {
        return win;
    }

    public RewardBundle loss() {
        return loss;
    }

    public RewardBundle draw() {
        return draw;
    }
}
