package gg.lightpractice.api;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaType;
import gg.lightpractice.kit.Kit;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Arena registry, reservations and schematic resets. */
public interface ArenaService {

    Arena get(String name);

    Collection<Arena> arenas();

    List<String> names();

    /** Creates and registers an arena; returns {@code null} when the name is already taken. */
    Arena create(String name, ArenaType type);

    boolean delete(String name);

    /** Free arena that accepts the given kit, or {@code null} when every candidate is busy. */
    Arena findAvailable(Kit kit);

    /** Reserves capacity on an arena for a match, returning false when it is full. */
    boolean reserve(Arena arena, String matchId);

    /** Releases a reservation; always safe to call, even twice. */
    void release(Arena arena, String matchId);

    boolean isAvailable(Arena arena);

    /** Asynchronously resets an arena and reports the result on the main thread. */
    void reset(Arena arena, Consumer<Boolean> callback);

    void resetAll(Consumer<Integer> callback);

    boolean save(Arena arena);

    /** Persists every arena definition to {@code arenas.yml}. */
    void saveAll();

    int activeCount();
}
