package gg.lightpractice.api.event;

import gg.lightpractice.tournament.Tournament;
import java.util.UUID;
import org.bukkit.event.HandlerList;

/** Fired when a tournament finished or was stopped. */
public class LightPracticeTournamentEndEvent extends LightPracticeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Tournament tournament;
    private final UUID winner;

    public LightPracticeTournamentEndEvent(Tournament tournament, UUID winner) {
        super(false, gg.lightpractice.api.LightPracticeAPI.getVersion());
        this.tournament = tournament;
        this.winner = winner;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public UUID getWinner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
