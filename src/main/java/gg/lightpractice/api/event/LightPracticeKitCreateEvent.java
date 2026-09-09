package gg.lightpractice.api.event;

import gg.lightpractice.kit.Kit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when an administrator creates a kit. */
public class LightPracticeKitCreateEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Kit kit;
    private boolean cancelled;

    public LightPracticeKitCreateEvent(Kit kit) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.kit = kit;
    }

    public Kit getKit() {
        return kit;
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
