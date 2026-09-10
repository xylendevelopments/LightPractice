package gg.lightpractice.api.event;

import gg.lightpractice.gameevent.GameEvent;
import java.util.UUID;
import org.bukkit.event.HandlerList;

/** Fired when a hosted game event finished or was cancelled. */
public class LightPracticeGameEventEndEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameEvent gameEvent;
    private final UUID winner;

    public LightPracticeGameEventEndEvent(GameEvent gameEvent, UUID winner) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.gameEvent = gameEvent;
        this.winner = winner;
    }

    public GameEvent getGameEvent() {
        return gameEvent;
    }

    public UUID getWinner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
