package gg.lightpractice.api;

import gg.lightpractice.match.Match;
import gg.lightpractice.model.Division;
import gg.lightpractice.statistics.KitStatistics;
import gg.lightpractice.statistics.Statistics;

import java.util.UUID;

/** Statistics and rating access. */
public interface StatsService {

    /** Overall statistics of a cached profile, {@code null} when the profile is not loaded. */
    Statistics statistics(UUID uuid);

    Statistics statistics(UUID uuid, boolean ranked);

    KitStatistics kitStatistics(UUID uuid, String kitId, boolean ranked);

    /** Applies a finished match to every participant, then persists the profiles. */
    void applyMatchResult(Match match);

    void recordKill(UUID uuid, String kitId, boolean ranked);

    void recordDeath(UUID uuid, String kitId, boolean ranked);

    void addPlayTime(UUID uuid, long millis);

    int elo(UUID uuid, String kitId);

    Division division(UUID uuid, String kitId);

    /** Placement matches left before the standard K factor applies. */
    int placementsRemaining(UUID uuid, String kitId);
}
