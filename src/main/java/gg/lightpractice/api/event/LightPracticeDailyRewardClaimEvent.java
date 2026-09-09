package gg.lightpractice.api.event;

import gg.lightpractice.model.RewardBundle;
import gg.lightpractice.profile.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired before a daily reward is granted. */
public class LightPracticeDailyRewardClaimEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Profile profile;
    private final RewardBundle reward;
    private final int streak;
    private boolean cancelled;

    public LightPracticeDailyRewardClaimEvent(Player player, Profile profile, RewardBundle reward, int streak) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.player = player;
        this.profile = profile;
        this.reward = reward;
        this.streak = streak;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getPlayer() {
        return player;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Profile getProfile() {
        return profile;
    }

    public RewardBundle getReward() {
        return reward;
    }

    public int getStreak() {
        return streak;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
