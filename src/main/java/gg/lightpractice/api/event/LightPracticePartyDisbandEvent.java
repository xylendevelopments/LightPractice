package gg.lightpractice.api.event;

import gg.lightpractice.party.Party;
import java.util.UUID;
import org.bukkit.event.HandlerList;

/** Fired after a party has been disbanded and its members released. */
public class LightPracticePartyDisbandEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party party;
    private final UUID leader;

    public LightPracticePartyDisbandEvent(Party party, UUID leader) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.party = party;
        this.leader = leader;
    }

    public Party getParty() {
        return party;
    }

    public UUID getLeader() {
        return leader;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
