package gg.lightpractice.api.event;

import gg.lightpractice.tournament.Tournament;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Fired when a tournament bracket is about to begin. */
public class LightPracticeTournamentStartEvent extends LightPracticeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Tournament tournament;
    private boolean cancelled;

    public LightPracticeTournamentStartEvent(Tournament tournament) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.tournament = tournament;
    }

    public Tournament getTournament() {
        return tournament;
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
