package gg.lightpractice.api;

import gg.lightpractice.tournament.Tournament;
import org.bukkit.entity.Player;

/** Bracketed tournaments. */
public interface TournamentService {

    /** Currently running or gathering tournament, {@code null} when none exists. */
    Tournament active();

    boolean isRunning();

    boolean create(Player host, String kitId, int size);

    boolean join(Player player);

    boolean leave(Player player);

    boolean start(Player starter);

    boolean stop(Player stopper, String reason);

    /** Announces the bracket to participants and spectators. */
    void broadcast(Tournament tournament);
}
