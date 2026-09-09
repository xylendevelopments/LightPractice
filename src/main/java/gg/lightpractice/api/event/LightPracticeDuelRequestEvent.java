package gg.lightpractice.api.event;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.kit.Kit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when a duel request is about to be delivered to another player. */
public class LightPracticeDuelRequestEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final Player target;
    private final Kit kit;
    private final Arena arena;
    private boolean cancelled;

    public LightPracticeDuelRequestEvent(Player sender, Player target, Kit kit, Arena arena) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.sender = sender;
        this.target = target;
        this.kit = kit;
        this.arena = arena;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getSender() {
        return sender;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getTarget() {
        return target;
    }

    public Kit getKit() {
        return kit;
    }

    public Arena getArena() {
        return arena;
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
