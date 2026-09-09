package gg.lightpractice.api;

import gg.lightpractice.gameevent.GameEvent;
import org.bukkit.entity.Player;

import java.util.List;

/** Hosted game events. Event types are registered by services, never hardcoded here. */
public interface GameEventService {

    List<String> types();

    /** Event that is gathering, running or finishing; {@code null} when nothing is hosted. */
    GameEvent active();

    boolean isRunning();

    boolean host(Player host, String typeId, String kitId, String arenaName);

    boolean join(Player player);

    boolean leave(Player player);

    boolean start(Player starter);

    boolean stop(Player stopper, String reason);

    /** Registers an additional event type at runtime. */
    void register(gg.lightpractice.gameevent.GameEventProvider provider);
}
