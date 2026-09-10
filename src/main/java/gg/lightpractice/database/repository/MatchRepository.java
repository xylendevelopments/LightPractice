package gg.lightpractice.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.serialization.MatchSerializer;
import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchRecord;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Persists finished matches and answers paginated history queries. */
public final class MatchRepository {

    private final DatabaseService database;
    private volatile boolean indexesCreated;

    public MatchRepository(DatabaseService database) {
        this.database = database;
    }

    public void ensureIndexes() {
        if (indexesCreated) {
            return;
        }
        database.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    MongoCollection<Document> collection = database.matches();
                    collection.createIndex(new Document("match_id", 1), new IndexOptions().unique(true));
                    collection.createIndex(new Document("timestamp", -1));
                    collection.createIndex(new Document("participants", 1).append("timestamp", -1));
                    collection.createIndex(new Document("kit_id", 1));
                    indexesCreated = true;
                    Debug.log(DebugCategory.DATABASE, "Match indexes verified");
                } catch (Throwable throwable) {
                    Debug.error(DebugCategory.DATABASE, "Could not create match indexes", throwable);
                }
            }
        });
    }

    public CompletableFuture<Boolean> save(final MatchRecord record) {
        if (record == null || record.matchId() == null) {
            CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
            future.completeExceptionally(new IllegalArgumentException("match record is required"));
            return future;
        }
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Document document = MatchSerializer.toDocument(record);
                Bson filter = Filters.eq("match_id", record.matchId());
                database.matches().replaceOne(filter, document, new ReplaceOptions().upsert(true));
                Debug.log(DebugCategory.DATABASE, "Stored match {} ({} players)",
                        record.matchId(), record.players().size());
                return Boolean.TRUE;
            }
        });
    }

    /** Guarantees match identifiers stay unique even if two matches finish in the same tick. */
    public CompletableFuture<Boolean> exists(final String matchId) {
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return database.matches().countDocuments(Filters.eq("match_id", matchId)) > 0L;
            }
        });
    }

    public CompletableFuture<List<MatchHistoryEntry>> history(final UUID uuid, final int page, final int pageSize) {
        return database.supply(new Callable<List<MatchHistoryEntry>>() {
            @Override
            public List<MatchHistoryEntry> call() {
                List<MatchHistoryEntry> entries = new ArrayList<MatchHistoryEntry>();
                int safePage = Math.max(0, page);
                int safeSize = Math.max(1, Math.min(50, pageSize));
                List<Document> documents = database.matches()
                        .find(Filters.eq("participants", uuid.toString()))
                        .sort(new Document("timestamp", -1))
                        .skip(safePage * safeSize)
                        .limit(safeSize)
                        .into(new ArrayList<Document>());
                for (Document document : documents) {
                    MatchHistoryEntry entry = MatchSerializer.historyFor(document, uuid);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                return entries;
            }
        });
    }

    public CompletableFuture<Integer> matchCount(final UUID uuid) {
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                return Integer.valueOf((int) database.matches()
                        .countDocuments(Filters.eq("participants", uuid.toString())));
            }
        });
    }

    public CompletableFuture<List<MatchRecord>> recent(final int limit) {
        return database.supply(new Callable<List<MatchRecord>>() {
            @Override
            public List<MatchRecord> call() {
                List<MatchRecord> records = new ArrayList<MatchRecord>();
                List<Document> documents = database.matches()
                        .find(new Document())
                        .sort(new Document("timestamp", -1))
                        .limit(Math.max(1, Math.min(200, limit)))
                        .into(new ArrayList<Document>());
                for (Document document : documents) {
                    MatchRecord record = MatchSerializer.fromDocument(document);
                    if (record != null) {
                        records.add(record);
                    }
                }
                return records;
            }
        });
    }

    /** Retention cleanup, driven by {@code database.yml} instead of growing forever. */
    public CompletableFuture<Integer> purgeOlderThan(final long timestamp) {
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                long deleted = database.matches().deleteMany(Filters.lt("timestamp", timestamp)).getDeletedCount();
                if (deleted > 0L) {
                    Debug.log(DebugCategory.DATABASE, "Purged {} matches older than the retention window", deleted);
                }
                return Integer.valueOf((int) deleted);
            }
        });
    }
}
