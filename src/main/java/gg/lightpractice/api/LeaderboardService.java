package gg.lightpractice.api;

import gg.lightpractice.model.LeaderboardEntry;
import gg.lightpractice.model.LeaderboardType;

import java.util.List;
import java.util.UUID;

/** Cached leaderboards backed by indexed MongoDB queries. */
public interface LeaderboardService {

    List<LeaderboardEntry> entries(LeaderboardType type, String kitId);

    List<LeaderboardEntry> entries(LeaderboardType type, String kitId, int limit);

    /** Refreshes every cached board on the database executor. */
    void refresh();

    /** Rank lookup, delivered asynchronously through the callback on the main thread. */
    void position(UUID uuid, LeaderboardType type, String kitId, java.util.function.Consumer<Integer> callback);

    long lastRefresh();

    int cacheSeconds();
}
