package gg.lightpractice.api.event;

import gg.lightpractice.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when a new party is about to be registered. */
public class LightPracticePartyCreateEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player leader;
    private final Party party;
    private boolean cancelled;

    public LightPracticePartyCreateEvent(Player leader, Party party) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.leader = leader;
        this.party = party;
    }

    /** May be {@code null} when the actor is offline or not a player. */
    public Player getLeader() {
        return leader;
    }

    public Party getParty() {
        return party;
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
