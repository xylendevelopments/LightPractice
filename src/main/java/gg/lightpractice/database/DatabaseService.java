package gg.lightpractice.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Tasks;
import org.bson.Document;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Owns the MongoDB connection and the executor every database operation runs on.
 *
 * <p>No repository or manager ever touches the main thread while waiting for the database: work is
 * submitted to {@link #supply(Callable)} and results are handed back with {@link #onMain}. When the
 * database is unreachable the service stays in a disconnected state, keeps reporting that through
 * {@link #isConnected()} and retries on demand, which lets profile loading queue players instead of
 * inventing empty profiles that would overwrite real data.</p>
 */
public final class DatabaseService {

    private final Plugin plugin;
    private final Tasks tasks;
    private final ConfigFile configFile;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private volatile DatabaseSettings settings;
    private volatile MongoClient client;
    private volatile MongoDatabase database;
    private volatile ExecutorService executor;
    private volatile boolean connected;
    private volatile CompletableFuture<Boolean> inFlight;

    public DatabaseService(Plugin plugin, Tasks tasks, ConfigFile configFile) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.configFile = configFile;
        this.settings = DatabaseSettings.load(configFile);
    }

    /** Re-reads {@code database.yml}; the live connection is untouched unless the target changed. */
    public void reload() {
        DatabaseSettings updated = DatabaseSettings.load(configFile);
        if (updated.sameTarget(settings)) {
            this.settings = updated;
            return;
        }
        plugin.getLogger().info("Database settings changed, reconnecting...");
        disconnect();
        this.settings = updated;
        connectAsync();
    }

    public DatabaseSettings settings() {
        return settings;
    }

    public boolean isConnected() {
        return connected && client != null;
    }

    public boolean isConnecting() {
        return connecting.get();
    }

    public MongoDatabase database() {
        return database;
    }

    /**
     * Opens the connection on the database executor. The returned future completes on the database
     * thread; callers that need the main thread should use {@link #onMain}.
     */
    public synchronized CompletableFuture<Boolean> connectAsync() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        if (inFlight != null && connecting.get()) {
            return inFlight;
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(Math.max(1, settings.threads()), threadFactory());
        }
        connecting.set(true);
        final CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        this.inFlight = future;
        final ExecutorService target = executor;
        try {
            target.submit(new Runnable() {
                @Override
                public void run() {
                    future.complete(connectBlocking());
                }
            });
        } catch (Throwable throwable) {
            connecting.set(false);
            future.complete(Boolean.FALSE);
            plugin.getLogger().log(Level.SEVERE, "Could not schedule the MongoDB connection attempt", throwable);
        }
        return future;
    }

    private boolean connectBlocking() {
        MongoClient created = null;
        try {
            MongoClientSettings clientSettings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(settings.connectionString()))
                    .build();
            created = MongoClients.create(clientSettings);
            MongoDatabase target = created.getDatabase(settings.database());
            // Forces server selection so a wrong host fails fast instead of on the first player join.
            target.runCommand(new Document("ping", 1));
            this.client = created;
            this.database = target;
            this.connected = true;
            plugin.getLogger().info("Connected to MongoDB database '" + settings.database() + "' at "
                    + settings.maskedConnectionString());
            Debug.log(DebugCategory.DATABASE, "Connection established, pool threads={}", settings.threads());
            return true;
        } catch (Throwable throwable) {
            this.connected = false;
            this.database = null;
            this.client = null;
            if (created != null) {
                try {
                    created.close();
                } catch (Throwable ignored) {
                    // nothing useful to do while already handling a connection failure
                }
            }
            plugin.getLogger().severe("Could not connect to MongoDB at " + settings.maskedConnectionString()
                    + " (" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + ").");
            plugin.getLogger().severe("LightPractice runs in a degraded state: profiles cannot be loaded or saved "
                    + "until the database is reachable. Check database.yml and the MongoDB server.");
            return false;
        } finally {
            connecting.set(false);
        }
    }

    /** Closes the connection and drains the executor. Safe to call more than once. */
    public synchronized void disconnect() {
        connected = false;
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(5L, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                }
            } catch (InterruptedException exception) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        MongoDatabase target = database;
        MongoClient open = client;
        database = null;
        client = null;
        if (open != null) {
            try {
                open.close();
                Debug.log(DebugCategory.DATABASE, "MongoDB connection closed (database {})",
                        target == null ? "unknown" : target.getName());
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Error while closing the MongoDB connection", throwable);
            }
        }
    }

    public MongoCollection<Document> profiles() {
        return collection(settings.profileCollection());
    }

    public MongoCollection<Document> matches() {
        return collection(settings.matchCollection());
    }

    public MongoCollection<Document> kits() {
        return collection(settings.kitCollection());
    }

    public MongoCollection<Document> tournaments() {
        return collection(settings.tournamentCollection());
    }

    public MongoCollection<Document> bans() {
        return collection(settings.banCollection());
    }

    public MongoCollection<Document> collection(String name) {
        MongoDatabase target = database;
        if (target == null) {
            throw new IllegalStateException("MongoDB is not connected");
        }
        return target.getCollection(name);
    }

    /** Runs blocking database work on the executor and returns its result asynchronously. */
    public <T> CompletableFuture<T> supply(final Callable<T> work) {
        final CompletableFuture<T> future = new CompletableFuture<T>();
        ExecutorService current = executor;
        if (current == null || current.isShutdown() || !connected) {
            future.completeExceptionally(new IllegalStateException("MongoDB is not connected"));
            return future;
        }
        try {
            current.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        future.complete(work.call());
                    } catch (Throwable throwable) {
                        future.completeExceptionally(throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    /** Fire and forget database work. */
    public CompletableFuture<Void> execute(final Runnable work) {
        return supply(new Callable<Void>() {
            @Override
            public Void call() {
                work.run();
                return null;
            }
        });
    }

    /** Delivers a future result on the main thread, logging failures through the debug logger. */
    public <T> void onMain(CompletableFuture<T> future, final Consumer<T> success) {
        onMain(future, success, null);
    }

    public <T> void onMain(CompletableFuture<T> future, final Consumer<T> success, final Consumer<Throwable> failure) {
        if (future == null) {
            return;
        }
        future.whenComplete(new java.util.function.BiConsumer<T, Throwable>() {
            @Override
            public void accept(final T result, final Throwable error) {
                Runnable dispatch = new Runnable() {
                    @Override
                    public void run() {
                        if (error != null) {
                            if (failure != null) {
                                failure.accept(error);
                            } else {
                                Debug.error(DebugCategory.DATABASE, "Asynchronous database operation failed", error);
                            }
                            return;
                        }
                        if (success != null) {
                            success.accept(result);
                        }
                    }
                };
                if (tasks == null) {
                    dispatch.run();
                } else {
                    tasks.syncOrRun(dispatch);
                }
            }
        });
    }

    private ThreadFactory threadFactory() {
        final AtomicInteger counter = new AtomicInteger();
        final String pluginName = plugin.getName();
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, pluginName + "-Database-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /** Number of queued database operations, exposed for the admin info command. */
    public String status() {
        if (isConnected()) {
            return "connected to " + settings.maskedConnectionString() + " (database " + settings.database() + ")";
        }
        return connecting.get() ? "connecting" : "disconnected";
    }
}
