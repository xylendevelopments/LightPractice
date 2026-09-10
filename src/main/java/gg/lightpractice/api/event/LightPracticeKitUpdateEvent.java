package gg.lightpractice.api.event;

import gg.lightpractice.kit.Kit;
import org.bukkit.event.HandlerList;

/** Fired after a kit definition changed and was persisted. */
public class LightPracticeKitUpdateEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Kit kit;

    public LightPracticeKitUpdateEvent(Kit kit) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.kit = kit;
    }

    public Kit getKit() {
        return kit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
