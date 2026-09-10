package gg.lightpractice.api.event;

import gg.lightpractice.match.Match;
import org.bukkit.event.HandlerList;

/** Fired once every participant has been teleported and the countdown finished. */
public class LightPracticeMatchStartEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Match match;

    public LightPracticeMatchStartEvent(Match match) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.match = match;
    }

    public Match getMatch() {
        return match;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
