package gg.lightpractice.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.serialization.ProfileSerializer;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous profile persistence.
 *
 * <p>Indexes cover the identifier, the username and every leaderboard field so a top list never has
 * to scan the collection. Rating indexes are created per kit on demand by
 * {@code LeaderboardRepository} instead of up front, because kits are dynamic.</p>
 */
public final class PlayerProfileRepository {

    private final DatabaseService database;
    private volatile boolean indexesCreated;

    public PlayerProfileRepository(DatabaseService database) {
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
                    MongoCollection<Document> collection = database.profiles();
                    collection.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
                    collection.createIndex(new Document("name", 1));
                    collection.createIndex(new Document("last_seen", -1));
                    collection.createIndex(new Document("statistics.wins", -1));
                    collection.createIndex(new Document("statistics.kills", -1));
                    collection.createIndex(new Document("statistics.winstreak", -1));
                    collection.createIndex(new Document("statistics.best_winstreak", -1));
                    collection.createIndex(new Document("experience", -1));
                    collection.createIndex(new Document("coins", -1));
                    collection.createIndex(new Document("level", -1));
                    indexesCreated = true;
                    Debug.log(DebugCategory.DATABASE, "Profile indexes verified");
                } catch (Throwable throwable) {
                    Debug.error(DebugCategory.DATABASE, "Could not create profile indexes", throwable);
                }
            }
        });
    }

    public CompletableFuture<Profile> load(final UUID uuid) {
        if (uuid == null) {
            return failed(new IllegalArgumentException("uuid is required"));
        }
        return database.supply(new Callable<Profile>() {
            @Override
            public Profile call() {
                Document document = database.profiles().find(Filters.eq("uuid", uuid.toString())).first();
                if (document == null) {
                    return null;
                }
                return ProfileSerializer.fromDocument(document);
            }
        });
    }

    public CompletableFuture<Profile> loadByName(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return failed(new IllegalArgumentException("name is required"));
        }
        final String target = name.trim();
        return database.supply(new Callable<Profile>() {
            @Override
            public Profile call() {
                Document document = database.profiles().find(Filters.eq("name", target)).first();
                if (document == null) {
                    // Usernames are case sensitive in the index, retry without case sensitivity.
                    document = database.profiles()
                            .find(Filters.regex("name", "^" + java.util.regex.Pattern.quote(target) + "$", "i"))
                            .first();
                }
                return document == null ? null : ProfileSerializer.fromDocument(document);
            }
        });
    }

    public CompletableFuture<UUID> findUuid(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return failed(new IllegalArgumentException("name is required"));
        }
        final String target = name.trim();
        return database.supply(new Callable<UUID>() {
            @Override
            public UUID call() {
                Document document = database.profiles()
                        .find(Filters.regex("name", "^" + java.util.regex.Pattern.quote(target) + "$", "i"))
                        .projection(new Document("uuid", 1))
                        .first();
                return document == null ? null : ProfileSerializer.parseUuid(document.getString("uuid"));
            }
        });
    }

    public CompletableFuture<Boolean> exists(final UUID uuid) {
        if (uuid == null) {
            return failed(new IllegalArgumentException("uuid is required"));
        }
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return database.profiles().countDocuments(Filters.eq("uuid", uuid.toString())) > 0L;
            }
        });
    }

    public CompletableFuture<Boolean> save(final Profile profile) {
        if (profile == null) {
            return failed(new IllegalArgumentException("profile is required"));
        }
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Document document = ProfileSerializer.toDocument(profile);
                Bson filter = Filters.eq("uuid", profile.uuid().toString());
                database.profiles().replaceOne(filter, document, new ReplaceOptions().upsert(true));
                Debug.log(DebugCategory.PROFILE, "Saved profile {} ({} coins, {} xp)",
                        profile.name(), profile.coins(), profile.experience());
                return Boolean.TRUE;
            }
        });
    }

    /** Bulk save used on shutdown and by the periodic saver; every failure is reported, none abort. */
    public CompletableFuture<Integer> saveAll(final Collection<Profile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return CompletableFuture.completedFuture(Integer.valueOf(0));
        }
        final List<Profile> copy = new ArrayList<Profile>(profiles);
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                int saved = 0;
                MongoCollection<Document> collection = database.profiles();
                ReplaceOptions options = new ReplaceOptions().upsert(true);
                for (Profile profile : copy) {
                    try {
                        collection.replaceOne(Filters.eq("uuid", profile.uuid().toString()),
                                ProfileSerializer.toDocument(profile), options);
                        saved++;
                    } catch (Throwable throwable) {
                        Debug.error(DebugCategory.PROFILE, "Could not save profile " + profile.uuid(), throwable);
                    }
                }
                Debug.log(DebugCategory.PROFILE, "Bulk saved {} of {} profiles", saved, copy.size());
                return Integer.valueOf(saved);
            }
        });
    }

    public CompletableFuture<Boolean> delete(final UUID uuid) {
        if (uuid == null) {
            return failed(new IllegalArgumentException("uuid is required"));
        }
        return database.supply(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return database.profiles().deleteOne(Filters.eq("uuid", uuid.toString())).getDeletedCount() > 0L;
            }
        });
    }

    public CompletableFuture<Integer> count() {
        return database.supply(new Callable<Integer>() {
            @Override
            public Integer call() {
                return Integer.valueOf((int) database.profiles().countDocuments());
            }
        });
    }

    private static <T> CompletableFuture<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(throwable);
        return future;
    }
}
