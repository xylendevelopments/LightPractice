package gg.lightpractice.arena;

import org.bukkit.entity.Player;

/**
 * Moves a player out of an arena before it is reset. Implemented by the lobby manager and injected into
 * the arena manager during bootstrapping, so no player can ever be inside a region that is being
 * rebuilt and the arena code does not depend on lobby internals.
 */
public interface ArenaEvacuator {

    void evacuate(Player player);
}
