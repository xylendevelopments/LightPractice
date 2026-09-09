package gg.lightpractice.service;

import gg.lightpractice.api.ArenaService;
import gg.lightpractice.api.BanService;
import gg.lightpractice.api.BotService;
import gg.lightpractice.api.CosmeticService;
import gg.lightpractice.api.DuelService;
import gg.lightpractice.api.GameEventService;
import gg.lightpractice.api.HistoryService;
import gg.lightpractice.api.KitService;
import gg.lightpractice.api.LeaderboardService;
import gg.lightpractice.api.LightPracticeServices;
import gg.lightpractice.api.MatchService;
import gg.lightpractice.api.PartyService;
import gg.lightpractice.api.PlayerService;
import gg.lightpractice.api.ProfileService;
import gg.lightpractice.api.QueueService;
import gg.lightpractice.api.RewardService;
import gg.lightpractice.api.SpectatorService;
import gg.lightpractice.api.StatsService;
import gg.lightpractice.api.TournamentService;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dependency container and lifecycle owner.
 *
 * <p>Services are registered in dependency order, then enabled in that order and disabled in reverse.
 * Nothing else in the plugin holds a static reference to another manager: managers receive their
 * collaborators through constructors and reach the rest of the system through this registry.</p>
 */
public final class ServiceRegistry implements LightPracticeServices {

    private final Map<Class<?>, Object> services = new LinkedHashMap<Class<?>, Object>();
    private final List<LightService> lifecycle = new ArrayList<LightService>();
    private final Logger logger;
    private String version = "unknown";
    private boolean enabled;

    public ServiceRegistry(Logger logger) {
        this.logger = logger;
    }

    public void version(String version) {
        this.version = version == null ? "unknown" : version;
    }

    /** Registers a service under its public API type. */
    public <T> T register(Class<T> type, T instance) {
        if (type == null || instance == null) {
            throw new IllegalArgumentException("service type and instance are required");
        }
        services.put(type, instance);
        if (instance instanceof LightService) {
            lifecycle.add((LightService) instance);
        }
        return instance;
    }

    /** Registers an internal manager that has no public API type but still needs lifecycle calls. */
    public <T extends LightService> T registerInternal(Class<T> type, T instance) {
        services.put(type, instance);
        lifecycle.add(instance);
        return instance;
    }

    /**
     * Exposes an already registered instance under an additional type, for example a manager under both
     * its public API interface and its concrete class. No second lifecycle entry is created, so the
     * service is still enabled and disabled exactly once.
     */
    public void alias(Class<?> type, Object instance) {
        if (type == null || instance == null) {
            throw new IllegalArgumentException("alias type and instance are required");
        }
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException(instance.getClass().getName() + " is not a " + type.getName());
        }
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        T instance = (T) services.get(type);
        if (instance == null) {
            throw new IllegalStateException("Service " + type.getSimpleName() + " is not registered");
        }
        return instance;
    }

    public <T> T find(Class<T> type) {
        return type.cast(services.get(type));
    }

    public boolean contains(Class<?> type) {
        return services.containsKey(type);
    }

    public List<LightService> lifecycle() {
        return Collections.unmodifiableList(lifecycle);
    }

    /**
     * Calls {@link LightService#onLoad()} on every service in startup order.
     *
     * <p>Runs while the plugin loads, after all constructors have run, so a service may look at another
     * service during this phase but must not start tasks or touch the world yet.</p>
     */
    public void loadAll() {
        for (LightService service : ordered()) {
            try {
                service.onLoad();
                Debug.log(DebugCategory.API, "Loaded service {} (order {})", service.name(),
                        service.startupOrder());
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Could not load the " + service.name() + " service", throwable);
            }
        }
    }

    /** The lifecycle list sorted by {@link LightService#startupOrder()}, registration order breaks ties. */
    private List<LightService> ordered() {
        List<LightService> result = new ArrayList<LightService>(lifecycle);
        Collections.sort(result, new java.util.Comparator<LightService>() {
            @Override
            public int compare(LightService first, LightService second) {
                return Integer.compare(first.startupOrder(), second.startupOrder());
            }
        });
        return result;
    }

    public void enableAll() {
        enabled = true;
        for (LightService service : ordered()) {
            try {
                service.onEnable();
                Debug.log(DebugCategory.API, "Enabled service {}", service.name());
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Could not enable the " + service.name() + " service", throwable);
            }
        }
    }

    public void disableAll() {
        enabled = false;
        List<LightService> reversed = ordered();
        Collections.reverse(reversed);
        for (LightService service : reversed) {
            try {
                service.onDisable();
                Debug.log(DebugCategory.SHUTDOWN, "Disabled service {}", service.name());
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Could not disable the " + service.name() + " service cleanly", throwable);
            }
        }
    }

    public void reloadAll() {
        for (LightService service : lifecycle) {
            try {
                service.onReload();
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Could not reload the " + service.name() + " service", throwable);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int size() {
        return services.size();
    }

    @Override
    public ProfileService profiles() {
        return get(ProfileService.class);
    }

    @Override
    public PlayerService players() {
        return get(PlayerService.class);
    }

    @Override
    public ArenaService arenas() {
        return get(ArenaService.class);
    }

    @Override
    public KitService kits() {
        return get(KitService.class);
    }

    @Override
    public QueueService queues() {
        return get(QueueService.class);
    }

    @Override
    public MatchService matches() {
        return get(MatchService.class);
    }

    @Override
    public PartyService parties() {
        return get(PartyService.class);
    }

    @Override
    public TournamentService tournaments() {
        return get(TournamentService.class);
    }

    @Override
    public StatsService statistics() {
        return get(StatsService.class);
    }

    @Override
    public LeaderboardService leaderboards() {
        return get(LeaderboardService.class);
    }

    @Override
    public SpectatorService spectators() {
        return get(SpectatorService.class);
    }

    @Override
    public BotService bots() {
        return get(BotService.class);
    }

    @Override
    public CosmeticService cosmetics() {
        return get(CosmeticService.class);
    }

    @Override
    public DuelService duels() {
        return get(DuelService.class);
    }

    @Override
    public HistoryService history() {
        return get(HistoryService.class);
    }

    @Override
    public RewardService rewards() {
        return get(RewardService.class);
    }

    @Override
    public BanService bans() {
        return get(BanService.class);
    }

    @Override
    public GameEventService gameEvents() {
        return get(GameEventService.class);
    }

    @Override
    public String version() {
        return version;
    }
}
