package gg.lightpractice.model;

/** Coins and experience granted together by wins, events and daily rewards. */
public final class RewardBundle {

    public static final RewardBundle EMPTY = new RewardBundle(0, 0L);

    private final int coins;
    private final long experience;

    public RewardBundle(int coins, long experience) {
        this.coins = Math.max(0, coins);
        this.experience = Math.max(0L, experience);
    }

    public static RewardBundle of(int coins, long experience) {
        if (coins <= 0 && experience <= 0L) {
            return EMPTY;
        }
        return new RewardBundle(coins, experience);
    }

    public int coins() {
        return coins;
    }

    public long experience() {
        return experience;
    }

    public boolean isEmpty() {
        return coins <= 0 && experience <= 0L;
    }

    public RewardBundle add(RewardBundle other) {
        if (other == null) {
            return this;
        }
        return new RewardBundle(coins + other.coins, experience + other.experience);
    }

    @Override
    public String toString() {
        return coins + " coins, " + experience + " xp";
    }
}
