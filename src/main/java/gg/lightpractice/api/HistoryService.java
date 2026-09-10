package gg.lightpractice.api;

import gg.lightpractice.match.Match;
import gg.lightpractice.model.MatchHistoryEntry;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Finished match history stored in MongoDB. */
public interface HistoryService {

    /** Builds and stores the record of a finished match. */
    void save(Match match);

    /** Entries already present on the profile, returned instantly. */
    List<MatchHistoryEntry> cached(UUID uuid);

    /** Paginated lookup for offline or older matches, delivered on the main thread. */
    void load(UUID uuid, int page, Consumer<List<MatchHistoryEntry>> callback);

    int pageSize();
}
