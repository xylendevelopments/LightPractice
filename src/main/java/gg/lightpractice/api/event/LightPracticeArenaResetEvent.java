package gg.lightpractice.api.event;

import gg.lightpractice.arena.Arena;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired before an arena schematic is pasted again. */
public class LightPracticeArenaResetEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private boolean cancelled;

    public LightPracticeArenaResetEvent(Arena arena) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.arena = arena;
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
