package gg.lightpractice.database.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.serialization.ProfileSerializer;
import gg.lightpractice.model.TournamentRecord;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Tournament snapshots. A running bracket is written as it progresses so an unexpected restart leaves
 * a record that can be reported and cleaned up instead of losing the results silently.
 */
public final class TournamentRepository {

    private final DatabaseService database;
    private volatile boolean indexesCreated;

    public TournamentRepository(DatabaseService database) {
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
                    database.tournaments().createIndex(new Document("tournament_id", 1),
                            new IndexOptions().unique(true));
                    database.tournaments().createIndex(new Document("state", 1));
                    indexesCreated = true;
                } catch (Throwable throwable) {
                    Debug.error(DebugCategory.DATABASE, "Could not create tournament indexes", throwable);
                }
            }
        });
    }

    public CompletableFuture<Boolean> save(final TournamentRecord record) {
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Document document = new Document();
                document.put("tournament_id", record.id());
                document.put("kit_id", record.kitId());
                document.put("host", record.host() == null ? "" : record.host().toString());
                document.put("host_name", record.hostName());
                document.put("state", record.state());
                document.put("started_at", record.startedAt());
                document.put("updated_at", record.updatedAt());
                document.put("size", record.size());
                document.put("winner", record.winner() == null ? "" : record.winner().toString());
                List<String> participants = new ArrayList<String>();
                for (UUID uuid : record.participants()) {
                    if (uuid != null) {
                        participants.add(uuid.toString());
                    }
                }
                document.put("participants", participants);
                database.tournaments().replaceOne(Filters.eq("tournament_id", record.id()), document,
                        new ReplaceOptions().upsert(true));
                return Boolean.TRUE;
            }
        });
    }

    public CompletableFuture<List<TournamentRecord>> loadUnfinished() {
        return database.supply(new Callable<List<TournamentRecord>>() {
            @Override
            public List<TournamentRecord> call() {
                List<TournamentRecord> records = new ArrayList<TournamentRecord>();
                List<Document> documents = database.tournaments()
                        .find(Filters.ne("state", "FINISHED"))
                        .into(new ArrayList<Document>());
                for (Document document : documents) {
                    TournamentRecord record = from(document);
                    if (record != null && !record.finished()) {
                        records.add(record);
                    }
                }
                return records;
            }
        });
    }

    public CompletableFuture<List<TournamentRecord>> recent(final int limit) {
        return database.supply(new Callable<List<TournamentRecord>>() {
            @Override
            public List<TournamentRecord> call() {
                List<TournamentRecord> records = new ArrayList<TournamentRecord>();
                List<Document> documents = database.tournaments()
                        .find(new Document())
                        .sort(new Document("started_at", -1))
                        .limit(Math.max(1, Math.min(100, limit)))
                        .into(new ArrayList<Document>());
                for (Document document : documents) {
                    TournamentRecord record = from(document);
                    if (record != null) {
                        records.add(record);
                    }
                }
                return records;
            }
        });
    }

    public CompletableFuture<Boolean> delete(final String tournamentId) {
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return database.tournaments()
                        .deleteOne(Filters.eq("tournament_id", tournamentId)).getDeletedCount() > 0L;
            }
        });
    }

    private static TournamentRecord from(Document document) {
        if (document == null) {
            return null;
        }
        String id = document.getString("tournament_id");
        if (id == null || id.isEmpty()) {
            return null;
        }
        List<UUID> participants = new ArrayList<UUID>();
        for (Object value : ProfileSerializer.rawList(document, "participants")) {
            if (value instanceof String) {
                UUID uuid = ProfileSerializer.parseUuid((String) value);
                if (uuid != null) {
                    participants.add(uuid);
                }
            }
        }
        UUID winner = ProfileSerializer.parseUuid(document.getString("winner"));
        return new TournamentRecord(id,
                ProfileSerializer.stringOf(document, "kit_id", ""),
                ProfileSerializer.parseUuid(document.getString("host")),
                ProfileSerializer.stringOf(document, "host_name", "Console"),
                ProfileSerializer.stringOf(document, "state", "WAITING"),
                ProfileSerializer.longOf(document, "started_at", 0L),
                ProfileSerializer.longOf(document, "updated_at", 0L),
                ProfileSerializer.intOf(document, "size", participants.size()),
                participants,
                winner);
    }
}
