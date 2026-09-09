package gg.lightpractice.api;

import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerState;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/** Runtime session data: what every online player is currently doing. */
public interface PlayerService {

    LightPlayer get(Player player);

    LightPlayer get(UUID uuid);

    Collection<LightPlayer> online();

    /** Creates the session object for a joining player. */
    LightPlayer register(Player player);

    /** Removes the session object of a leaving player. */
    void unregister(Player player);

    /**
     * Attempts a state transition, refusing incompatible ones (a player inside a match cannot join a
     * queue, edit a kit or accept a duel). Returns whether the transition happened.
     */
    boolean setState(LightPlayer player, PlayerState state);

    PlayerState getState(UUID uuid);

    boolean isInMatch(UUID uuid);

    boolean isInQueue(UUID uuid);

    boolean isSpectating(UUID uuid);

    /** True when the player is in any state that forbids starting something new. */
    boolean isBusy(UUID uuid);
}
