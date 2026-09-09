package gg.lightpractice.api;

import gg.lightpractice.queue.Queue;
import gg.lightpractice.queue.QueueEntry;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Queue registry and membership. */
public interface QueueService {

    Queue get(String id);

    Collection<Queue> queues();

    List<String> names();

    /** Ranked or unranked queue of a kit, {@code null} when the kit has no such queue. */
    Queue forKit(String kitId, boolean ranked);

    /** Adds a player, or their whole party when they are a leader or member of one. */
    boolean join(Player player, Queue queue);

    boolean leave(Player player);

    boolean leave(UUID uuid);

    boolean isQueued(UUID uuid);

    Queue queueOf(UUID uuid);

    QueueEntry entryOf(UUID uuid);

    /** Queues the player may join right now, used by {@code /randomqueue}. */
    List<Queue> eligible(Player player);

    /** Picks and joins a random eligible queue. */
    boolean joinRandom(Player player);

    void removeAll(UUID uuid);

    int queuedCount();
}
