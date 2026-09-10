package gg.lightpractice.statistics;

import gg.lightpractice.api.StatsService;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.model.Division;
import gg.lightpractice.model.EloChange;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns finished matches into statistics, ratings and division movement.
 *
 * <p>All rating maths lives in {@link EloCalculator} and all persistence in {@link ProfileManager}, so
 * this class only decides <em>what</em> a result means: who won, how much rating moved and which
 * profile fields have to be updated.</p>
 */
public final class StatisticsManager implements StatsService, LightService {

    private final PluginCore core;
    private final EloCalculator calculator;
    private final DivisionManager divisions;

    public StatisticsManager(PluginCore core, EloCalculator calculator, DivisionManager divisions) {
        this.core = core;
        this.calculator = calculator == null ? new EloCalculator() : calculator;
        this.divisions = divisions;
    }

    @Override
    public String name() {
        return "statistics";
    }

    @Override
    public int startupOrder() {
        return 60;
    }

    @Override
    public void onLoad() {
        calculator.load(core.configs().matchmaking());
    }

    @Override
    public void onReload() {
        calculator.load(core.configs().matchmaking());
        Debug.log(DebugCategory.STATISTICS, "Reloaded the rating configuration");
    }

    public EloCalculator calculator() {
        return calculator;
    }

    public DivisionManager divisions() {
        return divisions;
    }

    // ------------------------------------------------------------------ lookups

    private Profile profile(UUID uuid) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null ? null : profiles.getProfile(uuid);
    }

    @Override
    public Statistics statistics(UUID uuid) {
        Profile profile = profile(uuid);
        return profile == null ? null : profile.statistics();
    }

    @Override
    public Statistics statistics(UUID uuid, boolean ranked) {
        Profile profile = profile(uuid);
        return profile == null ? null : profile.statistics(ranked);
    }

    @Override
    public KitStatistics kitStatistics(UUID uuid, String kitId, boolean ranked) {
        Profile profile = profile(uuid);
        if (profile == null || kitId == null) {
            return null;
        }
        return profile.statistics(ranked).kitOrNull(kitId);
    }

    @Override
    public int elo(UUID uuid, String kitId) {
        Profile profile = profile(uuid);
        return profile == null ? calculator.startingElo() : profile.eloOr(kitId, calculator.startingElo());
    }

    @Override
    public Division division(UUID uuid, String kitId) {
        if (uuid == null) {
            return divisions == null ? null : divisions.unranked();
        }
        Profile profile = profile(uuid);
        if (profile == null || divisions == null) {
            return divisions == null ? null : divisions.unranked();
        }
        boolean ranked = profile.hasElo(kitId);
        if (!ranked) {
            return divisions.unranked();
        }
        return divisions.byElo(profile.eloOr(kitId, calculator.startingElo()));
    }

    @Override
    public int placementsRemaining(UUID uuid, String kitId) {
        Profile profile = profile(uuid);
        int played = profile == null ? 0 : profile.statistics(true).kit(kitId).gamesPlayed();
        return calculator.placementGamesRemaining(played);
    }

    // ------------------------------------------------------------------ updates

    @Override
    public void recordKill(UUID uuid, String kitId, boolean ranked) {
        Profile profile = profile(uuid);
        if (profile == null) {
            return;
        }
        Statistics target = profile.statistics(ranked);
        target.addKill(1);
        target.kit(kitId).addKill(1);
        profile.statistics().addKill(1);
        profile.statistics().kit(kitId).addKill(1);
        profile.markDirty();
    }

    @Override
    public void recordDeath(UUID uuid, String kitId, boolean ranked) {
        Profile profile = profile(uuid);
        if (profile == null) {
            return;
        }
        Statistics target = profile.statistics(ranked);
        target.addDeath(1);
        target.kit(kitId).addDeath(1);
        profile.statistics().addDeath(1);
        profile.statistics().kit(kitId).addDeath(1);
        profile.markDirty();
    }

    @Override
    public void addPlayTime(UUID uuid, long millis) {
        Profile profile = profile(uuid);
        if (profile == null || millis <= 0L) {
            return;
        }
        profile.statistics().addTimePlayed(millis);
        profile.markDirty();
    }

    /**
     * Applies a finished match to every participant.
     *
     * <p>Ratings are computed from the average rating of each team so team and FFA matches behave the
     * same way as duels. Participants whose profile is not cached any more (kick during shutdown,
     * database outage) are skipped and reported instead of losing the whole result.</p>
     */
    @Override
    public void applyMatchResult(Match match) {
        if (match == null || match.kit() == null) {
            return;
        }
        Kit kit = match.kit();
        String kitId = kit.id();
        boolean ranked = match.ranked();
        MatchTeam winningTeam = match.winningTeam();
        Map<MatchTeam, Integer> averages = teamRatings(match, kitId);
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles == null) {
            Debug.warn(DebugCategory.STATISTICS, "No profile manager available, match result dropped");
            return;
        }
        // a shutdown or an administrative drop must not touch ratings, records or streaks
        boolean counted = match.endCause() == null || match.endCause().counted();
        Collection<MatchPlayer> participants = match.participants();
        int applied = 0;
        for (MatchPlayer participant : participants) {
            if (participant == null || participant.uuid() == null) {
                continue;
            }
            Profile profile = profiles.getProfile(participant.uuid());
            MatchOutcome outcome = outcomeOf(participant, winningTeam, match.teams().size());
            if (profile == null) {
                Debug.log(DebugCategory.STATISTICS, "Profile of {} was not cached, {} result skipped",
                        participant.name(), outcome);
                continue;
            }
            if (!counted) {
                profile.recordAbandon(kitId, ranked);
                profile.markDirty();
                profiles.save(profile);
                applied++;
                continue;
            }
            Integer newElo = null;
            int eloBefore = profile.eloOr(kitId, calculator.startingElo());
            participant.eloBefore(eloBefore);
            if (ranked && outcome != MatchOutcome.FORFEIT && participant.team() != null) {
                int opponents = opponentRating(match, participant.team(), averages);
                boolean placements = calculator.inPlacements(profile.statistics(true).kit(kitId).gamesPlayed());
                EloChange change;
                if (outcome == MatchOutcome.WIN) {
                    change = calculator.winner(eloBefore, opponents, placements);
                } else if (outcome == MatchOutcome.DRAW) {
                    change = calculator.draw(eloBefore, opponents, placements);
                } else {
                    change = calculator.loser(eloBefore, opponents, placements);
                }
                newElo = Integer.valueOf(change.after());
                participant.eloAfter(change.after());
                Debug.log(DebugCategory.STATISTICS, "{} rating {} -> {} ({}) in {}", participant.name(),
                        change.before(), change.after(), change.formatted(), kitId);
            }
            profile.recordMatchResult(outcome, kitId, ranked, participant.kills(), participant.deaths(),
                    match.durationMillis(), newElo);
            if (ranked && newElo != null && divisions != null) {
                divisions.check(profile, kitId, eloBefore, newElo.intValue());
            }
            profiles.save(profile);
            applied++;
        }
        Debug.log(DebugCategory.STATISTICS, "Applied {} result of match {} to {} participant(s)",
                kitId, match.id(), applied);
    }

    private MatchOutcome outcomeOf(MatchPlayer participant, MatchTeam winningTeam, int teamCount) {
        MatchOutcome outcome = participant.outcome();
        if (outcome != null) {
            return outcome;
        }
        if (winningTeam == null) {
            return MatchOutcome.DRAW;
        }
        if (winningTeam.equals(participant.team())) {
            return MatchOutcome.WIN;
        }
        return teamCount <= 1 ? MatchOutcome.DRAW : MatchOutcome.LOSS;
    }

    /** Average rating of every team, used as the opponent rating for team based modes. */
    private Map<MatchTeam, Integer> teamRatings(Match match, String kitId) {
        Map<MatchTeam, Integer> averages = new HashMap<MatchTeam, Integer>();
        for (MatchTeam team : match.teams()) {
            if (team == null) {
                continue;
            }
            long total = 0L;
            int count = 0;
            for (MatchPlayer member : team.members()) {
                Profile profile = profile(member.uuid());
                total += profile == null ? calculator.startingElo() : profile.eloOr(kitId, calculator.startingElo());
                count++;
            }
            averages.put(team, count == 0 ? calculator.startingElo() : (int) (total / count));
        }
        return averages;
    }

    private int opponentRating(Match match, MatchTeam own, Map<MatchTeam, Integer> averages) {
        if (match.teams().size() <= 1) {
            // free for all: every other participant counts as an opponent
            long total = 0L;
            int count = 0;
            for (MatchPlayer participant : match.participants()) {
                if (participant.team() != null && participant.team().equals(own)) {
                    continue;
                }
                total += ratingOf(participant.uuid(), match.kit().id());
                count++;
            }
            return count == 0 ? calculator.startingElo() : (int) (total / count);
        }
        long total = 0L;
        int count = 0;
        for (Map.Entry<MatchTeam, Integer> entry : averages.entrySet()) {
            if (entry.getKey() == null || entry.getKey().equals(own)) {
                continue;
            }
            total += entry.getValue().intValue();
            count++;
        }
        return count == 0 ? calculator.startingElo() : (int) (total / count);
    }

    private int ratingOf(UUID uuid, String kitId) {
        Profile profile = profile(uuid);
        return profile == null ? calculator.startingElo() : profile.eloOr(kitId, calculator.startingElo());
    }
}
