package gg.lightpractice.api.event;

import gg.lightpractice.gameevent.GameEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when a hosted game event is about to start. */
public class LightPracticeGameEventStartEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameEvent gameEvent;
    private final Player host;
    private boolean cancelled;

    public LightPracticeGameEventStartEvent(GameEvent gameEvent, Player host) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.gameEvent = gameEvent;
        this.host = host;
    }

    public GameEvent getGameEvent() {
        return gameEvent;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getHost() {
        return host;
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
