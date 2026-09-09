package gg.lightpractice.api.event;

import gg.lightpractice.queue.Queue;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired before a player or a party is added to a queue. */
public class LightPracticeQueueJoinEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Queue queue;
    private boolean cancelled;

    public LightPracticeQueueJoinEvent(Player player, Queue queue) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.player = player;
        this.queue = queue;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getPlayer() {
        return player;
    }

    public Queue getQueue() {
        return queue;
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
