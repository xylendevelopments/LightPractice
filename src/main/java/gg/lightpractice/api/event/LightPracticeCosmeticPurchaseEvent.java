package gg.lightpractice.api.event;

import gg.lightpractice.cosmetic.Cosmetic;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired before coins are deducted for a cosmetic. */
public class LightPracticeCosmeticPurchaseEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Cosmetic cosmetic;
    private final long cost;
    private boolean cancelled;

    public LightPracticeCosmeticPurchaseEvent(Player player, Cosmetic cosmetic, long cost) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.player = player;
        this.cosmetic = cosmetic;
        this.cost = cost;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getPlayer() {
        return player;
    }

    public Cosmetic getCosmetic() {
        return cosmetic;
    }

    public long getCost() {
        return cost;
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
