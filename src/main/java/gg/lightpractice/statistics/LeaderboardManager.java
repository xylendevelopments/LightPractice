package gg.lightpractice.statistics;

import gg.lightpractice.api.LeaderboardService;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.repository.LeaderboardRepository;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.model.LeaderboardEntry;
import gg.lightpractice.model.LeaderboardType;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Cached leaderboards served from indexed MongoDB queries.
 *
 * <p>Boards are refreshed on the database executor on a single timer, never per request, so opening a
 * menu costs a map lookup. A request for a board that is not cached yet starts one refresh and
 * answers from the cache as soon as it lands.</p>
 */
public final class LeaderboardManager implements LeaderboardService, LightService {

    private final PluginCore core;
    private final LeaderboardRepository repository;
    private final ConcurrentHashMap<String, List<LeaderboardEntry>> cache =
            new ConcurrentHashMap<String, List<LeaderboardEntry>>();
    private final Set<String> pending = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private BukkitTask timer;
    private int cacheSeconds = 60;
    private int defaultLimit = 10;
    private volatile long lastRefresh;

    public LeaderboardManager(PluginCore core, LeaderboardRepository repository) {
        this.core = core;
        this.repository = repository;
    }

    @Override
    public String name() {
        return "leaderboards";
    }

    @Override
    public int startupOrder() {
        return 65;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
        refresh();
        timer = core.tasks().asyncTimer(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, cacheSeconds * 20L, Math.max(20L, cacheSeconds * 20L));
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(timer);
        timer = null;
        cache.clear();
        pending.clear();
    }

    @Override
    public void onReload() {
        readConfiguration();
        refresh();
    }

    private void readConfiguration() {
        ConfigFile config = core.configs().config();
        this.cacheSeconds = Math.max(10, config.getInt("leaderboards.refresh-seconds", 60));
        this.defaultLimit = Math.max(1, Math.min(100, config.getInt("leaderboards.entries", 10)));
        Debug.log(DebugCategory.LEADERBOARD, "Leaderboards refresh every {}s with {} entries",
                cacheSeconds, defaultLimit);
    }

    @Override
    public int cacheSeconds() {
        return cacheSeconds;
    }

    @Override
    public long lastRefresh() {
        return lastRefresh;
    }

    /** Boards configured in config.yml, falling back to the common statistics plus every ranked kit. */
    public List<Board> boards() {
        List<Board> boards = new ArrayList<Board>();
        Set<String> seen = new LinkedHashSet<String>();
        ConfigFile config = core.configs().config();
        for (String line : config.getStringList("leaderboards.boards")) {
            Board board = Board.parse(line, defaultLimit);
            if (board != null && seen.add(board.key())) {
                boards.add(board);
            }
        }
        if (!boards.isEmpty()) {
            return boards;
        }
        for (LeaderboardType type : LeaderboardType.values()) {
            if (type.isKitSpecific()) {
                continue;
            }
            boards.add(new Board(type, null, defaultLimit));
        }
        KitManager kits = core.optional(KitManager.class);
        if (kits != null) {
            for (Kit kit : kits.queueable(true)) {
                boards.add(new Board(LeaderboardType.ELO, kit.id(), defaultLimit));
            }
        }
        return boards;
    }

    @Override
    public void refresh() {
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            Debug.log(DebugCategory.LEADERBOARD, "Skipping a leaderboard refresh, the database is not connected");
            return;
        }
        List<Board> boards = boards();
        int requested = 0;
        for (Board board : boards) {
            if (fetch(board.type, board.kitId, board.limit, null)) {
                requested++;
            }
        }
        Debug.log(DebugCategory.LEADERBOARD, "Requested {} leaderboard refresh(es)", requested);
    }

    /**
     * Loads one board on the database executor.
     *
     * @param callback optional consumer invoked on the main thread with the fresh entries
     * @return true when a query was started, false when the same board is already loading
     */
    public boolean fetch(LeaderboardType type, String kitId, int limit, final Consumer<List<LeaderboardEntry>> callback) {
        if (type == null || repository == null) {
            return false;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            return false;
        }
        final String key = key(type, kitId);
        if (!pending.add(key)) {
            if (callback != null) {
                database.onMain(java.util.concurrent.CompletableFuture.completedFuture(cached(type, kitId)), callback);
            }
            return false;
        }
        int size = limit <= 0 ? defaultLimit : Math.min(100, limit);
        core.database().onMain(repository.top(type, kitId, size), new Consumer<List<LeaderboardEntry>>() {
            @Override
            public void accept(List<LeaderboardEntry> entries) {
                pending.remove(key);
                lastRefresh = System.currentTimeMillis();
                List<LeaderboardEntry> safe = entries == null
                        ? new ArrayList<LeaderboardEntry>() : new ArrayList<LeaderboardEntry>(entries);
                cache.put(key, Collections.unmodifiableList(safe));
                if (callback != null) {
                    callback.accept(safe);
                }
                Debug.log(DebugCategory.LEADERBOARD, "Cached {} entries for {}", safe.size(), key);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                pending.remove(key);
                Debug.error(DebugCategory.LEADERBOARD, "Could not refresh leaderboard " + key, error);
            }
        });
        return true;
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardType type, String kitId) {
        return entries(type, kitId, defaultLimit);
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardType type, String kitId, int limit) {
        List<LeaderboardEntry> cached = cached(type, kitId);
        if (cached.isEmpty() && type != null) {
            fetch(type, kitId, limit, null);
        }
        if (limit > 0 && cached.size() > limit) {
            return new ArrayList<LeaderboardEntry>(cached.subList(0, limit));
        }
        return cached;
    }

    /** Cached entries, never null; a stale or missing board triggers a background refresh instead. */
    public List<LeaderboardEntry> cached(LeaderboardType type, String kitId) {
        if (type == null) {
            return Collections.emptyList();
        }
        List<LeaderboardEntry> entries = cache.get(key(type, kitId));
        if (entries == null) {
            return Collections.emptyList();
        }
        if (System.currentTimeMillis() - lastRefresh > (cacheSeconds * 2000L)) {
            fetch(type, kitId, defaultLimit, null);
        }
        return entries;
    }

    /** Entry of one player on a board, {@code null} when they are not listed. */
    public LeaderboardEntry entryOf(UUID uuid, LeaderboardType type, String kitId) {
        if (uuid == null) {
            return null;
        }
        for (LeaderboardEntry entry : cached(type, kitId)) {
            if (uuid.equals(entry.uuid())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void position(UUID uuid, LeaderboardType type, String kitId, final Consumer<Integer> callback) {
        if (uuid == null || type == null || callback == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            callback.accept(Integer.valueOf(-1));
            return;
        }
        database.onMain(repository.position(uuid, type, kitId), callback, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.LEADERBOARD, "Could not resolve a leaderboard position", error);
                callback.accept(Integer.valueOf(-1));
            }
        });
    }

    private static String key(LeaderboardType type, String kitId) {
        String kit = kitId == null ? "global" : kitId.toLowerCase(Locale.ROOT);
        return type.name() + ':' + kit;
    }

    /** One cached board: a leaderboard type, an optional kit and how many rows it holds. */
    public static final class Board {

        private final LeaderboardType type;
        private final String kitId;
        private final int limit;

        public Board(LeaderboardType type, String kitId, int limit) {
            this.type = type;
            this.kitId = kitId == null || kitId.trim().isEmpty() || "global".equalsIgnoreCase(kitId.trim())
                    ? null : kitId.trim().toLowerCase(Locale.ROOT);
            this.limit = Math.max(1, Math.min(100, limit));
        }

        public LeaderboardType type() {
            return type;
        }

        public String kitId() {
            return kitId;
        }

        public int limit() {
            return limit;
        }

        public String key() {
            return key(type, kitId);
        }

        /** Parses {@code TYPE}, {@code TYPE:kit} or {@code TYPE:kit:limit}. */
        public static Board parse(String line, int defaultLimit) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            String[] parts = line.trim().split(":");
            LeaderboardType type = LeaderboardType.parse(parts[0]);
            if (type == null) {
                Debug.log(DebugCategory.CONFIG, "Unknown leaderboard type '{}' in config.yml", parts[0]);
                return null;
            }
            String kit = parts.length > 1 ? parts[1] : null;
            int limit = defaultLimit;
            if (parts.length > 2) {
                try {
                    limit = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException ignored) {
                    Debug.log(DebugCategory.CONFIG, "Leaderboard '{}' has a non numeric size", line);
                }
            }
            return new Board(type, kit, limit);
        }

        @Override
        public String toString() {
            return type.name() + (kitId == null ? "" : ':' + kitId) + ':' + limit;
        }
    }
}
