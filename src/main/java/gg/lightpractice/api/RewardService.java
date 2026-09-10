package gg.lightpractice.api;

import gg.lightpractice.match.Match;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.model.RewardBundle;
import gg.lightpractice.profile.Profile;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Coins, experience and daily rewards. */
public interface RewardService {

    /** Reward a participant earns for a finished match. */
    RewardBundle matchReward(Match match, UUID uuid, MatchOutcome outcome);

    /** Grants coins and experience, applying level ups and messages. */
    void grant(Player player, Profile profile, RewardBundle bundle, String reason);

    boolean canClaimDaily(Profile profile);

    long dailyRemainingMillis(Profile profile);

    /** Claims the daily reward for the current streak, returns false when on cooldown. */
    boolean claimDaily(Player player);

    RewardBundle dailyReward(Profile profile);

    int dailyStreak(Profile profile);

    int dailyStreakCap();
}
