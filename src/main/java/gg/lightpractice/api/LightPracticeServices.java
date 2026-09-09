package gg.lightpractice.api;

/**
 * Every service LightPractice exposes. Implemented by the plugin service registry, so dependents can
 * reach the whole API through a single object without touching internals.
 */
public interface LightPracticeServices {

    ProfileService profiles();

    PlayerService players();

    ArenaService arenas();

    KitService kits();

    QueueService queues();

    MatchService matches();

    PartyService parties();

    TournamentService tournaments();

    StatsService statistics();

    LeaderboardService leaderboards();

    SpectatorService spectators();

    BotService bots();

    CosmeticService cosmetics();

    DuelService duels();

    HistoryService history();

    RewardService rewards();

    BanService bans();

    GameEventService gameEvents();

    String version();
}
