package gg.lightpractice.api.event;

import gg.lightpractice.match.Match;
import java.util.UUID;
import org.bukkit.event.HandlerList;

/** Fired when a participant is eliminated inside a match. */
public class LightPracticeMatchDeathEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Match match;
    private final UUID victim;
    private final UUID killer;

    public LightPracticeMatchDeathEvent(Match match, UUID victim, UUID killer) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.match = match;
        this.victim = victim;
        this.killer = killer;
    }

    public Match getMatch() {
        return match;
    }

    public UUID getVictim() {
        return victim;
    }

    public UUID getKiller() {
        return killer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
