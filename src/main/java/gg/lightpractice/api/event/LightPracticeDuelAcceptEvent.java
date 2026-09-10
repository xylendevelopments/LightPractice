package gg.lightpractice.api.event;

import gg.lightpractice.duel.DuelRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when the receiver accepts a duel request, before the match is created. */
public class LightPracticeDuelAcceptEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final Player target;
    private final DuelRequest request;
    private boolean cancelled;

    public LightPracticeDuelAcceptEvent(Player sender, Player target, DuelRequest request) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.sender = sender;
        this.target = target;
        this.request = request;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getSender() {
        return sender;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getTarget() {
        return target;
    }

    public DuelRequest getRequest() {
        return request;
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
