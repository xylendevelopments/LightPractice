package gg.lightpractice.profile;

import gg.lightpractice.model.CosmeticType;
import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.statistics.Statistics;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything LightPractice persists about a player.
 *
 * <p>Profiles are keyed by UUID, cached in memory while the player is online and mutated on the main
 * thread only. Persistence happens asynchronously through {@code ProfileManager}; the {@code dirty}
 * flag lets the periodic saver skip profiles that did not change instead of writing to MongoDB on a
 * timer regardless of activity.</p>
 */
public final class Profile {

    /** Rating bucket used when a kit has no dedicated rating. */
    public static final String GLOBAL_KIT_KEY = "global";
    /** Number of finished matches kept directly on the profile for instant history lookups. */
    public static final int HISTORY_LIMIT = 20;

    private final UUID uuid;
    private final Statistics overall = new Statistics();
    private final Statistics ranked = new Statistics();
    private final Statistics unranked = new Statistics();
    private final Map<String, Integer> elo = new HashMap<String, Integer>();
    private final Map<String, KitLoadout> loadouts = new HashMap<String, KitLoadout>();
    private final Set<String> unlockedCosmetics = new HashSet<String>();
    private final Map<CosmeticType, String> equippedCosmetics = new EnumMap<CosmeticType, String>(CosmeticType.class);
    private final List<MatchHistoryEntry> history = new ArrayList<MatchHistoryEntry>();
    private String name;
    private long coins;
    private long experience;
    private int level = 1;
    private PlayerSettings settings = new PlayerSettings();
    private long lastDailyClaim;
    private int dailyStreak;
    private long firstJoin;
    private long lastSeen;
    private boolean loaded;
    private transient boolean dirty;
    private transient long lastSave;

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = this.firstJoin;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name == null || name.isEmpty() || name.equals(this.name)) {
            return;
        }
        this.name = name;
        markDirty();
    }

    public boolean loaded() {
        return loaded;
    }

    public void loaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
        this.lastSave = System.currentTimeMillis();
    }

    public long lastSave() {
        return lastSave;
    }

    // ---------------------------------------------------------------- currency

    public long coins() {
        return coins;
    }

    public void coins(long coins) {
        long previous = this.coins;
        this.coins = Math.max(0L, coins);
        if (previous != this.coins) {
            markDirty();
        }
    }

    public boolean hasCoins(long amount) {
        return coins >= amount;
    }

    /** Grants coins, rejecting negative amounts so rewards can never be inverted. */
    public void addCoins(long amount) {
        if (amount <= 0L) {
            return;
        }
        coins += amount;
        markDirty();
    }

    /** Attempts to spend coins, returning false when the balance is too low. */
    public boolean spendCoins(long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (coins < amount) {
            return false;
        }
        coins -= amount;
        markDirty();
        return true;
    }

    // -------------------------------------------------------------- progression

    public long experience() {
        return experience;
    }

    public void experience(long experience) {
        long previous = this.experience;
        this.experience = Math.max(0L, experience);
        if (previous != this.experience) {
            markDirty();
        }
    }

    public void addExperience(long amount) {
        if (amount <= 0L) {
            return;
        }
        experience += amount;
        markDirty();
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        int updated = Math.max(1, level);
        if (updated != this.level) {
            this.level = updated;
            markDirty();
        }
    }

    // -------------------------------------------------------------- statistics

    public Statistics statistics() {
        return overall;
    }

    public Statistics rankedStatistics() {
        return ranked;
    }

    public Statistics unrankedStatistics() {
        return unranked;
    }

    public Statistics statistics(boolean rankedMatch) {
        return rankedMatch ? ranked : unranked;
    }

    // ------------------------------------------------------------------ rating

    public int elo(String kitId) {
        Integer value = elo.get(key(kitId));
        return value == null ? -1 : value;
    }

    public boolean hasElo(String kitId) {
        return elo.containsKey(key(kitId));
    }

    public void elo(String kitId, int value) {
        String resolved = key(kitId);
        Integer previous = elo.put(resolved, Math.max(0, value));
        if (previous == null || previous != value) {
            markDirty();
        }
    }

    /** Returns the stored rating or the supplied default, without writing the default back. */
    public int eloOr(String kitId, int fallback) {
        Integer value = elo.get(key(kitId));
        return value == null ? fallback : value;
    }

    public Map<String, Integer> eloMap() {
        return elo;
    }

    /** Highest rating across every kit, used for the default division and placeholders. */
    public int highestElo(int fallback) {
        int highest = Integer.MIN_VALUE;
        for (Integer value : elo.values()) {
            if (value != null && value > highest) {
                highest = value;
            }
        }
        return highest == Integer.MIN_VALUE ? fallback : highest;
    }

    public Set<String> eloKits() {
        return Collections.unmodifiableSet(elo.keySet());
    }

    private static String key(String kitId) {
        return kitId == null || kitId.isEmpty() ? GLOBAL_KIT_KEY : kitId;
    }

    // ---------------------------------------------------------------- loadouts

    public KitLoadout loadout(String kitId) {
        return kitId == null ? null : loadouts.get(kitId);
    }

    public void loadout(String kitId, KitLoadout loadout) {
        if (kitId == null) {
            return;
        }
        if (loadout == null) {
            if (loadouts.remove(kitId) != null) {
                markDirty();
            }
            return;
        }
        loadouts.put(kitId, loadout);
        markDirty();
    }

    public Map<String, KitLoadout> loadouts() {
        return loadouts;
    }

    public Set<String> loadoutKits() {
        return Collections.unmodifiableSet(loadouts.keySet());
    }

    // --------------------------------------------------------------- cosmetics

    public boolean hasCosmetic(String cosmeticId) {
        return cosmeticId != null && unlockedCosmetics.contains(cosmeticId);
    }

    public void unlockCosmetic(String cosmeticId) {
        if (cosmeticId == null || unlockedCosmetics.add(cosmeticId)) {
            if (cosmeticId != null) {
                markDirty();
            }
        }
    }

    public void removeCosmetic(String cosmeticId) {
        if (cosmeticId != null && unlockedCosmetics.remove(cosmeticId)) {
            equippedCosmetics.values().remove(cosmeticId);
            markDirty();
        }
    }

    public Set<String> unlockedCosmetics() {
        return unlockedCosmetics;
    }

    public String equipped(CosmeticType type) {
        return type == null ? null : equippedCosmetics.get(type);
    }

    public void equip(CosmeticType type, String cosmeticId) {
        if (type == null) {
            return;
        }
        if (cosmeticId == null) {
            if (equippedCosmetics.remove(type) != null) {
                markDirty();
            }
            return;
        }
        equippedCosmetics.put(type, cosmeticId);
        markDirty();
    }

    public Map<CosmeticType, String> equippedCosmetics() {
        return equippedCosmetics;
    }

    // ------------------------------------------------------------------ daily

    public long lastDailyClaim() {
        return lastDailyClaim;
    }

    public int dailyStreak() {
        return dailyStreak;
    }

    /**
     * Records a daily claim. The timestamp is authoritative, so reconnecting or restarting the server
     * can never be used to claim twice.
     */
    public void claimDaily(long timestamp, int streak) {
        this.lastDailyClaim = timestamp;
        this.dailyStreak = Math.max(0, streak);
        markDirty();
    }

    public void daily(long lastClaim, int streak) {
        this.lastDailyClaim = Math.max(0L, lastClaim);
        this.dailyStreak = Math.max(0, streak);
    }

    // ---------------------------------------------------------------- history

    public List<MatchHistoryEntry> history() {
        return history;
    }

    /** Keeps the most recent entries first and trims the list to {@link #HISTORY_LIMIT}. */
    public void addHistory(MatchHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        history.add(0, entry);
        while (history.size() > HISTORY_LIMIT) {
            history.remove(history.size() - 1);
        }
        markDirty();
    }

    public void clearHistory() {
        if (!history.isEmpty()) {
            history.clear();
            markDirty();
        }
    }

    public MatchOutcome lastOutcome() {
        return history.isEmpty() ? null : history.get(0).outcome();
    }

    // --------------------------------------------------------------- settings

    public PlayerSettings settings() {
        return settings;
    }

    public void settings(PlayerSettings settings) {
        this.settings = settings == null ? new PlayerSettings() : settings;
        markDirty();
    }

    public long firstJoin() {
        return firstJoin;
    }

    public void firstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        markDirty();
    }

    /** Win/loss record line used by menus, placeholders and the tab list. */
    public String record(boolean rankedMatch) {
        Statistics target = statistics(rankedMatch);
        return target.wins() + "-" + target.losses();
    }

    /** Applies a finished match to the profile, called once per participant by the statistics manager. */
    public void recordMatchResult(MatchOutcome outcome, String kitId, boolean rankedMatch,
                                  int kills, int deaths, long durationMillis, Integer newElo) {
        Statistics target = statistics(rankedMatch);
        Statistics kitTarget = overall;
        if (outcome == MatchOutcome.WIN) {
            target.recordWin();
            kitTarget.recordWin();
            target.kit(kitId).recordWin();
            kitTarget.kit(kitId).recordWin();
        } else if (outcome == MatchOutcome.DRAW) {
            target.recordDraw();
            kitTarget.recordDraw();
            target.kit(kitId).recordDraw();
            kitTarget.kit(kitId).recordDraw();
        } else {
            target.recordLoss();
            kitTarget.recordLoss();
            target.kit(kitId).recordLoss();
            kitTarget.kit(kitId).recordLoss();
        }
        if (kills > 0) {
            target.addKill(kills);
            overall.addKill(kills);
            target.kit(kitId).addKill(kills);
            overall.kit(kitId).addKill(kills);
        }
        if (deaths > 0) {
            target.addDeath(deaths);
            overall.addDeath(deaths);
            target.kit(kitId).addDeath(deaths);
            overall.kit(kitId).addDeath(deaths);
        }
        if (durationMillis > 0L) {
            target.addTimePlayed(durationMillis);
            overall.addTimePlayed(durationMillis);
        }
        if (rankedMatch && newElo != null) {
            elo(kitId, newElo);
        }
        markDirty();
    }

    /** Called when a match is abandoned (disconnect, shutdown) so streaks never stay inflated. */
    public void recordAbandon(String kitId, boolean rankedMatch) {
        statistics(rankedMatch).recordAbandoned();
        overall.recordAbandoned();
        Debug.log(DebugCategory.STATISTICS, "{} abandoned a {} match", name, rankedMatch ? "ranked" : "unranked");
    }

    /** Drops every reference to a removed kit so renamed kits do not leak statistics. */
    public void purgeKit(String kitId) {
        if (kitId == null) {
            return;
        }
        elo.remove(kitId);
        loadouts.remove(kitId);
        overall.kitStatistics().remove(kitId);
        ranked.kitStatistics().remove(kitId);
        unranked.kitStatistics().remove(kitId);
        Iterator<MatchHistoryEntry> iterator = history.iterator();
        while (iterator.hasNext()) {
            if (kitId.equals(iterator.next().kitId())) {
                iterator.remove();
            }
        }
        markDirty();
    }

    @Override
    public String toString() {
        return "Profile{" + name + "/" + uuid + ", level=" + level + ", coins=" + coins + '}';
    }
}
