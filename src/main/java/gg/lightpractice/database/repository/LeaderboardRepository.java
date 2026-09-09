package gg.lightpractice.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.model.LeaderboardEntry;
import gg.lightpractice.model.LeaderboardType;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Leaderboard queries.
 *
 * <p>Results are produced by indexed, sorted, limited queries and then cached by
 * {@code LeaderboardManager}; nothing here is called from a scoreboard tick.</p>
 */
public final class LeaderboardRepository {

    private final DatabaseService database;
    private final List<String> ratingIndexes = new ArrayList<String>();

    public LeaderboardRepository(DatabaseService database) {
        this.database = database;
    }

    /** Rating fields are dynamic because they depend on configured kits, so index them on demand. */
    public void ensureRatingIndex(final String kitId) {
        final String field = LeaderboardType.ELO.field(kitId);
        synchronized (ratingIndexes) {
            if (ratingIndexes.contains(field)) {
                return;
            }
            ratingIndexes.add(field);
        }
        database.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    MongoCollection<Document> collection = database.profiles();
                    collection.createIndex(new Document(field, -1), new IndexOptions().name("rating_" + field));
                    Debug.log(DebugCategory.LEADERBOARD, "Created rating index {}", field);
                } catch (Throwable throwable) {
                    Debug.error(DebugCategory.LEADERBOARD, "Could not create rating index " + field, throwable);
                    synchronized (ratingIndexes) {
                        ratingIndexes.remove(field);
                    }
                }
            }
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> top(final LeaderboardType type, final String kitId,
                                                         final int limit) {
        return database.supply(new Callable<List<LeaderboardEntry>>() {
            @Override
            public List<LeaderboardEntry> call() {
                List<LeaderboardEntry> entries = new ArrayList<LeaderboardEntry>();
                if (type == null) {
                    return entries;
                }
                if (type.isKitSpecific()) {
                    ensureRatingIndex(kitId);
                }
                final String field = type.field(kitId);
                int size = Math.max(1, Math.min(100, limit));
                Document projection = new Document("name", 1).append("uuid", 1);
                List<Document> documents = database.profiles()
                        .find(Filters.gt(field, 0))
                        .projection(projection.append(field, 1))
                        .sort(new Document(field, -1))
                        .limit(size)
                        .into(new ArrayList<Document>());
                int position = 1;
                for (Document document : documents) {
                    long value = readField(document, field);
                    UUID uuid = uuid(document.getString("uuid"));
                    String name = document.getString("name");
                    entries.add(new LeaderboardEntry(uuid, name == null ? "Unknown" : name, value, position++));
                }
                return entries;
            }
        });
    }

    /** Rank of one player: count of documents with a strictly higher value plus one. */
    public CompletableFuture<Integer> position(final UUID uuid, final LeaderboardType type, final String kitId) {
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                if (uuid == null || type == null) {
                    return Integer.valueOf(-1);
                }
                if (type.isKitSpecific()) {
                    ensureRatingIndex(kitId);
                }
                String field = type.field(kitId);
                Document document = database.profiles()
                        .find(Filters.eq("uuid", uuid.toString()))
                        .projection(new Document(field, 1))
                        .first();
                if (document == null) {
                    return Integer.valueOf(-1);
                }
                long value = readField(document, field);
                long higher = database.profiles().countDocuments(Filters.gt(field, value));
                return Integer.valueOf((int) (higher + 1L));
            }
        });
    }

    static long readField(Document document, String path) {
        if (document == null || path == null) {
            return 0L;
        }
        String[] parts = path.split("\\.");
        Document current = document;
        for (int index = 0; index < parts.length - 1; index++) {
            Object value = current.get(parts[index]);
            if (!(value instanceof Document)) {
                return 0L;
            }
            current = (Document) value;
        }
        Object value = current.get(parts[parts.length - 1]);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
