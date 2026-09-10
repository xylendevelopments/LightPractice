package gg.lightpractice.api;

/**
 * Static entry point for developers.
 *
 * <pre>{@code
 * if (LightPracticeAPI.isAvailable()) {
 *     Profile profile = LightPracticeAPI.profiles().getProfile(player);
 *     int rating = LightPracticeAPI.statistics().elo(player.getUniqueId(), "nodebuff");
 * }
 * }</pre>
 *
 * <p>The facade is registered while the plugin is enabled and cleared on disable, so every getter
 * throws a descriptive {@link IllegalStateException} instead of returning a stale service.</p>
 */
public final class LightPracticeAPI {

    private static volatile LightPracticeServices services;
    private static volatile String version = "unknown";

    private LightPracticeAPI() {
    }

    /** Called by the plugin while enabling; not part of the developer contract. */
    public static void register(LightPracticeServices provider, String pluginVersion) {
        services = provider;
        version = pluginVersion == null ? "unknown" : pluginVersion;
    }

    /** Called by the plugin while disabling. */
    public static void unregister() {
        services = null;
        version = "unknown";
    }

    public static boolean isAvailable() {
        return services != null;
    }

    public static String getVersion() {
        return version;
    }

    public static LightPracticeServices services() {
        return require();
    }

    public static ProfileService profiles() {
        return require().profiles();
    }

    public static PlayerService players() {
        return require().players();
    }

    public static ArenaService arenas() {
        return require().arenas();
    }

    public static KitService kits() {
        return require().kits();
    }

    public static QueueService queues() {
        return require().queues();
    }

    public static MatchService matches() {
        return require().matches();
    }

    public static PartyService parties() {
        return require().parties();
    }

    public static TournamentService tournaments() {
        return require().tournaments();
    }

    public static StatsService statistics() {
        return require().statistics();
    }

    public static LeaderboardService leaderboards() {
        return require().leaderboards();
    }

    public static SpectatorService spectators() {
        return require().spectators();
    }

    public static BotService bots() {
        return require().bots();
    }

    public static CosmeticService cosmetics() {
        return require().cosmetics();
    }

    public static DuelService duels() {
        return require().duels();
    }

    public static HistoryService history() {
        return require().history();
    }

    public static RewardService rewards() {
        return require().rewards();
    }

    public static BanService bans() {
        return require().bans();
    }

    public static GameEventService gameEvents() {
        return require().gameEvents();
    }

    private static LightPracticeServices require() {
        LightPracticeServices current = services;
        if (current == null) {
            throw new IllegalStateException(
                    "LightPractice is not enabled. Check LightPracticeAPI.isAvailable() before using the API.");
        }
        return current;
    }
}
