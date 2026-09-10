package gg.lightpractice.api.event;

import gg.lightpractice.profile.Profile;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Fired on the main thread once a profile finished loading. */
public class LightPracticeProfileLoadEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile profile;
    private final Player player;

    public LightPracticeProfileLoadEvent(Profile profile, Player player) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.profile = profile;
        this.player = player;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Profile getProfile() {
        return profile;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
