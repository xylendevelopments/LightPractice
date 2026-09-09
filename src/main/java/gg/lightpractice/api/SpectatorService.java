package gg.lightpractice.api;

import gg.lightpractice.match.Match;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/** Spectating, including the visibility and state handling around it. */
public interface SpectatorService {

    boolean spectate(Player spectator, Match match);

    /** Spectates whatever match the target is currently in. */
    boolean spectatePlayer(Player spectator, Player target);

    boolean leave(Player spectator);

    boolean isSpectating(UUID uuid);

    Match matchOf(UUID uuid);

    Collection<UUID> spectatorsOf(Match match);

    /** Moves a spectator to another participant of the same match. */
    boolean switchTarget(Player spectator, UUID target);

    /** Restores every spectator, used during shutdown. */
    void cleanup();
}
