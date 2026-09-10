package gg.lightpractice.statistics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate statistics. A profile owns three instances: overall, ranked and unranked, which keeps
 * competitive numbers clean without duplicating fields everywhere.
 */
public final class Statistics {

    private int wins;
    private int losses;
    private int draws;
    private int kills;
    private int deaths;
    private int winstreak;
    private int bestWinstreak;
    private int gamesPlayed;
    private long timePlayedMillis;
    private final Map<String, KitStatistics> kitStatistics = new HashMap<String, KitStatistics>();

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int draws() {
        return draws;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int winstreak() {
        return winstreak;
    }

    public int bestWinstreak() {
        return bestWinstreak;
    }

    public int gamesPlayed() {
        return gamesPlayed;
    }

    public long timePlayedMillis() {
        return timePlayedMillis;
    }

    public double ratio() {
        return deaths <= 0 ? kills : (double) kills / deaths;
    }

    public double winPercentage() {
        if (gamesPlayed <= 0) {
            return 0.0D;
        }
        return (double) wins / gamesPlayed * 100.0D;
    }

    /** Returns the statistics of a kit, creating an empty record when it is seen for the first time. */
    public KitStatistics kit(String kitId) {
        if (kitId == null) {
            return new KitStatistics();
        }
        KitStatistics existing = kitStatistics.get(kitId);
        if (existing == null) {
            existing = new KitStatistics();
            kitStatistics.put(kitId, existing);
        }
        return existing;
    }

    public KitStatistics kitOrNull(String kitId) {
        return kitId == null ? null : kitStatistics.get(kitId);
    }

    public Set<String> kitIds() {
        return Collections.unmodifiableSet(kitStatistics.keySet());
    }

    public Map<String, KitStatistics> kitStatistics() {
        return kitStatistics;
    }

    public void recordWin() {
        wins++;
        gamesPlayed++;
        winstreak++;
        if (winstreak > bestWinstreak) {
            bestWinstreak = winstreak;
        }
    }

    public void recordLoss() {
        losses++;
        gamesPlayed++;
        winstreak = 0;
    }

    public void recordDraw() {
        draws++;
        gamesPlayed++;
    }

    /** Abandoned matches reset streaks without touching win/loss counters. */
    public void recordAbandoned() {
        winstreak = 0;
    }

    public void addKill(int amount) {
        kills = Math.max(0, kills + amount);
    }

    public void addDeath(int amount) {
        deaths = Math.max(0, deaths + amount);
    }

    public void addTimePlayed(long millis) {
        timePlayedMillis = Math.max(0L, timePlayedMillis + Math.max(0L, millis));
    }

    /** Direct setters used when loading documents; every value is clamped to a sane range. */
    public void load(int wins, int losses, int draws, int kills, int deaths, int winstreak,
                     int bestWinstreak, int gamesPlayed, long timePlayedMillis) {
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.draws = Math.max(0, draws);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.winstreak = Math.max(0, winstreak);
        this.bestWinstreak = Math.max(this.winstreak, bestWinstreak);
        this.gamesPlayed = Math.max(0, gamesPlayed);
        this.timePlayedMillis = Math.max(0L, timePlayedMillis);
    }

    public void putKitStatistics(String kitId, KitStatistics statistics) {
        if (kitId == null || statistics == null) {
            return;
        }
        kitStatistics.put(kitId, statistics);
    }

    public Statistics copy() {
        Statistics copy = new Statistics();
        copy.load(wins, losses, draws, kills, deaths, winstreak, bestWinstreak, gamesPlayed, timePlayedMillis);
        for (Map.Entry<String, KitStatistics> entry : kitStatistics.entrySet()) {
            KitStatistics value = entry.getValue();
            copy.kitStatistics.put(entry.getKey(),
                    new KitStatistics(value.wins(), value.losses(), value.kills(), value.deaths(), value.gamesPlayed()));
        }
        return copy;
    }
}
