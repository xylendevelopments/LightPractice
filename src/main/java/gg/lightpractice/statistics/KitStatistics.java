package gg.lightpractice.statistics;

/** Per kit statistics. Mutated only by the statistics manager on the main thread. */
public final class KitStatistics {

    private int wins;
    private int losses;
    private int kills;
    private int deaths;
    private int gamesPlayed;

    public KitStatistics() {
    }

    public KitStatistics(int wins, int losses, int kills, int deaths, int gamesPlayed) {
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.gamesPlayed = Math.max(0, gamesPlayed);
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int gamesPlayed() {
        return gamesPlayed;
    }

    public void recordWin() {
        wins++;
        gamesPlayed++;
    }

    public void recordLoss() {
        losses++;
        gamesPlayed++;
    }

    public void recordDraw() {
        gamesPlayed++;
    }

    public void addKill(int amount) {
        kills = Math.max(0, kills + amount);
    }

    public void addDeath(int amount) {
        deaths = Math.max(0, deaths + amount);
    }

    public double ratio() {
        if (deaths <= 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    public double winPercentage() {
        if (gamesPlayed <= 0) {
            return 0.0D;
        }
        return (double) wins / gamesPlayed * 100.0D;
    }
}
