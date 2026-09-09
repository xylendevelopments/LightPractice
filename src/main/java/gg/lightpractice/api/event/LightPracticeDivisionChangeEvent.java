package gg.lightpractice.api.event;

import gg.lightpractice.model.Division;
import gg.lightpractice.profile.Profile;
import org.bukkit.event.HandlerList;

/** Fired when a rating change moved a player into another division. */
public class LightPracticeDivisionChangeEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile profile;
    private final Division previous;
    private final Division current;
    private final String kitId;

    public LightPracticeDivisionChangeEvent(Profile profile, Division previous, Division current, String kitId) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.profile = profile;
        this.previous = previous;
        this.current = current;
        this.kitId = kitId;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Profile getProfile() {
        return profile;
    }

    public Division getPrevious() {
        return previous;
    }

    public Division getCurrent() {
        return current;
    }

    public String getKitId() {
        return kitId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
