package gg.lightpractice.api.event;

import gg.lightpractice.profile.Profile;
import org.bukkit.event.HandlerList;

/** Fired for every level a player gained. */
public class LightPracticeLevelUpEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile profile;
    private final int previousLevel;
    private final int newLevel;

    public LightPracticeLevelUpEvent(Profile profile, int previousLevel, int newLevel) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.profile = profile;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Profile getProfile() {
        return profile;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
