package gg.lightpractice.statistics;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.model.EloChange;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

/**
 * Rating mathematics.
 *
 * <p>Everything is configurable in {@code matchmaking.yml}: starting rating, K factor, rating bounds,
 * optional placement matches with their own K factor and how draws are scored. Results are always
 * clamped so a profile can never hold a negative or out of range rating, which is the main ELO
 * manipulation vector on practice servers.</p>
 */
public final class EloCalculator {

    private int startingElo = 1000;
    private int minimumElo = 0;
    private int maximumElo = 5000;
    private double kFactor = 32.0D;
    private boolean placementsEnabled = true;
    private int placementMatches = 10;
    private double placementKFactor = 48.0D;
    private double drawScore = 0.5D;

    public EloCalculator() {
    }

    public void load(ConfigFile matchmaking) {
        if (matchmaking == null) {
            return;
        }
        this.startingElo = clamp(matchmaking.getInt("elo.starting-elo", 1000), 0, 10000);
        this.minimumElo = clamp(matchmaking.getInt("elo.minimum-elo", 0), 0, 10000);
        this.maximumElo = clamp(matchmaking.getInt("elo.maximum-elo", 5000), minimumElo, 10000);
        this.kFactor = Math.max(1.0D, matchmaking.getDouble("elo.k-factor", 32.0D));
        this.placementsEnabled = matchmaking.getBoolean("elo.placements.enabled", true);
        this.placementMatches = clamp(matchmaking.getInt("elo.placements.matches", 10), 0, 100);
        this.placementKFactor = Math.max(1.0D, matchmaking.getDouble("elo.placements.k-factor", 48.0D));
        this.drawScore = Math.max(0.0D, Math.min(1.0D, matchmaking.getDouble("elo.draw-score", 0.5D)));
        Debug.log(DebugCategory.MATCHMAKING, "ELO loaded: start={} k={} range={}-{} placements={}({})",
                startingElo, kFactor, minimumElo, maximumElo, placementsEnabled, placementMatches);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public int startingElo() {
        return startingElo;
    }

    public int minimumElo() {
        return minimumElo;
    }

    public int maximumElo() {
        return maximumElo;
    }

    public boolean inPlacements(int completedRankedMatches) {
        return placementsEnabled && completedRankedMatches < placementMatches;
    }

    public int placementMatches() {
        return placementMatches;
    }

    public int placementGamesRemaining(int completedRankedMatches) {
        if (!placementsEnabled) {
            return 0;
        }
        return Math.max(0, placementMatches - completedRankedMatches);
    }

    /** Rating a fresh profile starts with. */
    public int initialElo() {
        return startingElo;
    }

    /** Validates any rating coming from the database or a command. */
    public int sanitize(int elo) {
        if (elo < minimumElo) {
            return minimumElo;
        }
        return Math.min(elo, maximumElo);
    }

    public EloChange winner(int winnerElo, int loserElo, boolean winnerPlacements) {
        return change(winnerElo, loserElo, 1.0D, winnerPlacements);
    }

    public EloChange loser(int loserElo, int winnerElo, boolean loserPlacements) {
        return change(loserElo, winnerElo, 0.0D, loserPlacements);
    }

    public EloChange draw(int elo, int opponentElo, boolean placements) {
        return change(elo, opponentElo, drawScore, placements);
    }

    private EloChange change(int ownElo, int opponentElo, double score, boolean placements) {
        int safeOwn = sanitize(ownElo);
        int safeOpponent = sanitize(opponentElo);
        double expected = 1.0D / (1.0D + Math.pow(10.0D, (safeOpponent - safeOwn) / 400.0D));
        double factor = placements ? placementKFactor : kFactor;
        int delta = (int) Math.round(factor * (score - expected));
        int updated = sanitize(safeOwn + delta);
        return new EloChange(safeOwn, updated);
    }

    public double expectedScore(int ownElo, int opponentElo) {
        return 1.0D / (1.0D + Math.pow(10.0D, (sanitize(opponentElo) - sanitize(ownElo)) / 400.0D));
    }
}
