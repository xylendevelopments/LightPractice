package gg.lightpractice.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.serialization.ProfileSerializer;
import gg.lightpractice.model.BanEntry;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Practice bans. Kept in their own collection so lifting a ban never rewrites a profile. */
public final class BanRepository {

    private final DatabaseService database;
    private volatile boolean indexesCreated;

    public BanRepository(DatabaseService database) {
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
                    MongoCollection<Document> collection = database.bans();
                    collection.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
                    collection.createIndex(new Document("expires_at", 1));
                    indexesCreated = true;
                } catch (Throwable throwable) {
                    Debug.error(DebugCategory.DATABASE, "Could not create ban indexes", throwable);
                }
            }
        });
    }

    public CompletableFuture<BanEntry> find(final UUID uuid) {
        return database.supply(new Callable<BanEntry>() {
            @Override
            public BanEntry call() {
                Document document = database.bans().find(Filters.eq("uuid", uuid.toString())).first();
                return from(document);
            }
        });
    }

    public CompletableFuture<Boolean> save(final BanEntry entry) {
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Document document = new Document();
                document.put("uuid", entry.uuid().toString());
                document.put("name", entry.name());
                document.put("reason", entry.reason());
                document.put("issuer", entry.issuer() == null ? "" : entry.issuer().toString());
                document.put("issuer_name", entry.issuerName());
                document.put("issued_at", entry.issuedAt());
                document.put("expires_at", entry.expiresAt());
                database.bans().replaceOne(Filters.eq("uuid", entry.uuid().toString()), document,
                        new ReplaceOptions().upsert(true));
                return Boolean.TRUE;
            }
        });
    }

    public CompletableFuture<Boolean> remove(final UUID uuid) {
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return database.bans().deleteOne(Filters.eq("uuid", uuid.toString())).getDeletedCount() > 0L;
            }
        });
    }

    public CompletableFuture<List<BanEntry>> all(final int limit) {
        return database.supply(new Callable<List<BanEntry>>() {
            @Override
            public List<BanEntry> call() {
                List<BanEntry> entries = new ArrayList<BanEntry>();
                List<Document> documents = database.bans()
                        .find(new Document())
                        .sort(new Document("issued_at", -1))
                        .limit(Math.max(1, Math.min(500, limit)))
                        .into(new ArrayList<Document>());
                for (Document document : documents) {
                    BanEntry entry = from(document);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                return entries;
            }
        });
    }

    public CompletableFuture<Integer> purgeExpired() {
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                long deleted = database.bans()
                        .deleteMany(Filters.and(Filters.gte("expires_at", 0L), Filters.lt("expires_at", System.currentTimeMillis())))
                        .getDeletedCount();
                if (deleted > 0L) {
                    Debug.log(DebugCategory.DATABASE, "Removed {} expired bans", deleted);
                }
                return Integer.valueOf((int) deleted);
            }
        });
    }

    private static BanEntry from(Document document) {
        if (document == null) {
            return null;
        }
        UUID uuid = ProfileSerializer.parseUuid(document.getString("uuid"));
        if (uuid == null) {
            return null;
        }
        UUID issuer = ProfileSerializer.parseUuid(document.getString("issuer"));
        return new BanEntry(uuid,
                ProfileSerializer.stringOf(document, "name", "Unknown"),
                ProfileSerializer.stringOf(document, "reason", "No reason provided"),
                issuer,
                ProfileSerializer.stringOf(document, "issuer_name", "Console"),
                ProfileSerializer.longOf(document, "issued_at", System.currentTimeMillis()),
                ProfileSerializer.longOf(document, "expires_at", -1L));
    }
}
