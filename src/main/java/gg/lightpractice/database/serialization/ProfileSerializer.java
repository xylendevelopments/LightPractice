package gg.lightpractice.database.serialization;

import gg.lightpractice.model.ChatChannel;
import gg.lightpractice.model.CosmeticType;
import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.profile.KitLoadout;
import gg.lightpractice.profile.PlayerSettings;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.statistics.KitStatistics;
import gg.lightpractice.statistics.Statistics;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps a {@link Profile} onto its MongoDB document and back, validating every value on the way in. */
public final class ProfileSerializer {

    /** Bumped whenever the document layout changes so old documents can be migrated. */
    public static final int SCHEMA = 1;

    private ProfileSerializer() {
    }

    public static Document toDocument(Profile profile) {
        Document document = new Document();
        document.put("schema", SCHEMA);
        document.put("uuid", profile.uuid().toString());
        document.put("name", profile.name());
        document.put("coins", profile.coins());
        document.put("experience", profile.experience());
        document.put("level", profile.level());
        document.put("first_join", profile.firstJoin());
        document.put("last_seen", profile.lastSeen());

        Document statistics = statistics(profile.statistics());
        statistics.put("ranked", statistics(profile.rankedStatistics()));
        statistics.put("unranked", statistics(profile.unrankedStatistics()));
        document.put("statistics", statistics);

        Document elo = new Document();
        for (Map.Entry<String, Integer> entry : profile.eloMap().entrySet()) {
            elo.put(entry.getKey(), entry.getValue());
        }
        document.put("elo", elo);

        Document loadouts = new Document();
        for (Map.Entry<String, KitLoadout> entry : profile.loadouts().entrySet()) {
            Document loadout = new Document();
            loadout.put("contents", ItemSerialization.toDocuments(entry.getValue().rawContents()));
            loadout.put("armor", ItemSerialization.toDocuments(entry.getValue().rawArmor()));
            loadouts.put(entry.getKey(), loadout);
        }
        document.put("loadouts", loadouts);

        Document cosmetics = new Document();
        cosmetics.put("unlocked", new ArrayList<String>(profile.unlockedCosmetics()));
        Document equipped = new Document();
        for (Map.Entry<CosmeticType, String> entry : profile.equippedCosmetics().entrySet()) {
            equipped.put(entry.getKey().name(), entry.getValue());
        }
        cosmetics.put("equipped", equipped);
        document.put("cosmetics", cosmetics);

        Document daily = new Document();
        daily.put("last_claim", profile.lastDailyClaim());
        daily.put("streak", profile.dailyStreak());
        document.put("daily", daily);

        PlayerSettings settings = profile.settings();
        Document settingsDocument = new Document();
        settingsDocument.put("scoreboard", settings.scoreboardVisible());
        settingsDocument.put("duel_requests", settings.receiveDuelRequests());
        settingsDocument.put("spectators", settings.allowSpectators());
        settingsDocument.put("party_invites", settings.allowPartyInvites());
        settingsDocument.put("cosmetic_effects", settings.cosmeticEffects());
        settingsDocument.put("global_chat", settings.globalChat());
        settingsDocument.put("chat_channel", settings.chatChannel().name());
        document.put("settings", settingsDocument);

        List<Document> history = new ArrayList<Document>();
        for (MatchHistoryEntry entry : profile.history()) {
            history.add(history(entry));
        }
        document.put("history", history);
        return document;
    }

    private static Document statistics(Statistics statistics) {
        Document document = new Document();
        document.put("wins", statistics.wins());
        document.put("losses", statistics.losses());
        document.put("draws", statistics.draws());
        document.put("kills", statistics.kills());
        document.put("deaths", statistics.deaths());
        document.put("winstreak", statistics.winstreak());
        document.put("best_winstreak", statistics.bestWinstreak());
        document.put("games_played", statistics.gamesPlayed());
        document.put("time_played", statistics.timePlayedMillis());
        Document kits = new Document();
        for (Map.Entry<String, KitStatistics> entry : statistics.kitStatistics().entrySet()) {
            KitStatistics value = entry.getValue();
            Document kit = new Document();
            kit.put("wins", value.wins());
            kit.put("losses", value.losses());
            kit.put("kills", value.kills());
            kit.put("deaths", value.deaths());
            kit.put("games_played", value.gamesPlayed());
            kits.put(entry.getKey(), kit);
        }
        document.put("kits", kits);
        return document;
    }

    private static Document history(MatchHistoryEntry entry) {
        Document document = new Document();
        document.put("match_id", entry.matchId());
        document.put("timestamp", entry.timestamp());
        document.put("kit_id", entry.kitId());
        document.put("kit_name", entry.kitName());
        document.put("arena", entry.arenaName());
        document.put("type", entry.matchType());
        document.put("duration", entry.durationMillis());
        document.put("opponents", new ArrayList<String>(entry.opponents()));
        document.put("outcome", entry.outcome().name());
        document.put("ranked", entry.ranked());
        document.put("elo_before", entry.eloBefore());
        document.put("elo_after", entry.eloAfter());
        document.put("kills", entry.kills());
        document.put("deaths", entry.deaths());
        return document;
    }

    /** Returns {@code null} when the document cannot be trusted (missing or malformed identifier). */
    public static Profile fromDocument(Document document) {
        if (document == null) {
            return null;
        }
        String uuidValue = document.getString("uuid");
        UUID uuid = parseUuid(uuidValue);
        if (uuid == null) {
            Debug.log(DebugCategory.PROFILE, "Skipping profile document with invalid uuid '{}'", uuidValue);
            return null;
        }
        String name = document.getString("name");
        Profile profile = new Profile(uuid, name == null || name.isEmpty() ? "Player" : name);
        profile.loaded(true);
        profile.coins(longOf(document, "coins", 0L));
        profile.experience(longOf(document, "experience", 0L));
        profile.level((int) longOf(document, "level", 1L));
        profile.firstJoin(longOf(document, "first_join", System.currentTimeMillis()));
        profile.lastSeen(longOf(document, "last_seen", System.currentTimeMillis()));

        Document statistics = section(document, "statistics");
        readStatistics(statistics, profile.statistics());
        readStatistics(section(statistics, "ranked"), profile.rankedStatistics());
        readStatistics(section(statistics, "unranked"), profile.unrankedStatistics());

        Document elo = section(document, "elo");
        for (Map.Entry<String, Object> entry : elo.entrySet()) {
            if (entry.getValue() instanceof Number) {
                profile.elo(entry.getKey(), Math.max(0, ((Number) entry.getValue()).intValue()));
            }
        }

        Document loadouts = section(document, "loadouts");
        for (Map.Entry<String, Object> entry : loadouts.entrySet()) {
            if (!(entry.getValue() instanceof Document)) {
                continue;
            }
            Document loadout = (Document) entry.getValue();
            ItemStack[] contents = ItemSerialization.toArray(listOfDocuments(loadout, "contents"), 36);
            ItemStack[] armor = ItemSerialization.toArray(listOfDocuments(loadout, "armor"), 4);
            profile.loadout(entry.getKey(), new KitLoadout(contents, armor));
        }

        Document cosmetics = section(document, "cosmetics");
        for (Object value : rawList(cosmetics, "unlocked")) {
            if (value instanceof String) {
                profile.unlockCosmetic((String) value);
            }
        }
        Document equipped = section(cosmetics, "equipped");
        for (Map.Entry<String, Object> entry : equipped.entrySet()) {
            CosmeticType type = CosmeticType.parse(entry.getKey());
            if (type != null && entry.getValue() instanceof String) {
                profile.equip(type, (String) entry.getValue());
            }
        }

        Document daily = section(document, "daily");
        profile.daily(longOf(daily, "last_claim", 0L), (int) longOf(daily, "streak", 0L));

        Document settingsDocument = section(document, "settings");
        PlayerSettings settings = new PlayerSettings();
        settings.scoreboardVisible(boolOf(settingsDocument, "scoreboard", true));
        settings.receiveDuelRequests(boolOf(settingsDocument, "duel_requests", true));
        settings.allowSpectators(boolOf(settingsDocument, "spectators", true));
        settings.allowPartyInvites(boolOf(settingsDocument, "party_invites", true));
        settings.cosmeticEffects(boolOf(settingsDocument, "cosmetic_effects", true));
        settings.globalChat(boolOf(settingsDocument, "global_chat", true));
        ChatChannel channel = ChatChannel.parse(settingsDocument.getString("chat_channel"));
        settings.chatChannel(channel == null ? ChatChannel.PUBLIC : channel);
        profile.settings(settings);

        for (Object value : rawList(document, "history")) {
            if (value instanceof Document) {
                MatchHistoryEntry entry = history((Document) value);
                if (entry != null) {
                    profile.addHistory(entry);
                }
            }
        }
        profile.markClean();
        return profile;
    }

    private static MatchHistoryEntry history(Document document) {
        UUID matchId = null;
        String idValue = document.getString("match_id");
        if (idValue == null || idValue.isEmpty()) {
            return null;
        }
        List<String> opponents = new ArrayList<String>();
        for (Object value : rawList(document, "opponents")) {
            if (value instanceof String) {
                opponents.add((String) value);
            }
        }
        MatchOutcome outcome = MatchOutcome.parse(document.getString("outcome"));
        return new MatchHistoryEntry(idValue,
                longOf(document, "timestamp", 0L),
                document.getString("kit_id"),
                document.getString("kit_name"),
                document.getString("arena"),
                document.getString("type"),
                longOf(document, "duration", 0L),
                opponents,
                outcome == null ? MatchOutcome.LOSS : outcome,
                boolOf(document, "ranked", false),
                (int) longOf(document, "elo_before", 0L),
                (int) longOf(document, "elo_after", 0L),
                (int) longOf(document, "kills", 0L),
                (int) longOf(document, "deaths", 0L));
    }

    private static void readStatistics(Document document, Statistics target) {
        if (document == null || target == null) {
            return;
        }
        target.load((int) longOf(document, "wins", 0L),
                (int) longOf(document, "losses", 0L),
                (int) longOf(document, "draws", 0L),
                (int) longOf(document, "kills", 0L),
                (int) longOf(document, "deaths", 0L),
                (int) longOf(document, "winstreak", 0L),
                (int) longOf(document, "best_winstreak", 0L),
                (int) longOf(document, "games_played", 0L),
                longOf(document, "time_played", 0L));
        Document kits = section(document, "kits");
        for (Map.Entry<String, Object> entry : kits.entrySet()) {
            if (!(entry.getValue() instanceof Document)) {
                continue;
            }
            Document kit = (Document) entry.getValue();
            target.putKitStatistics(entry.getKey(), new KitStatistics(
                    (int) longOf(kit, "wins", 0L),
                    (int) longOf(kit, "losses", 0L),
                    (int) longOf(kit, "kills", 0L),
                    (int) longOf(kit, "deaths", 0L),
                    (int) longOf(kit, "games_played", 0L)));
        }
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Document section(Document parent, String key) {
        if (parent == null) {
            return new Document();
        }
        Object value = parent.get(key);
        return value instanceof Document ? (Document) value : new Document();
    }

    static List<Document> listOfDocuments(Document parent, String key) {
        List<Document> result = new ArrayList<Document>();
        for (Object value : rawList(parent, key)) {
            if (value instanceof Document) {
                result.add((Document) value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Object> rawList(Document parent, String key) {
        if (parent == null) {
            return new ArrayList<Object>();
        }
        Object value = parent.get(key);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<Object>();
    }

    static long longOf(Document document, String key, long fallback) {
        if (document == null) {
            return fallback;
        }
        Object value = document.get(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    static int intOf(Document document, String key, int fallback) {
        return (int) longOf(document, key, fallback);
    }

    static boolean boolOf(Document document, String key, boolean fallback) {
        if (document == null) {
            return fallback;
        }
        Object value = document.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return fallback;
    }

    static String stringOf(Document document, String key, String fallback) {
        if (document == null) {
            return fallback;
        }
        String value = document.getString(key);
        return value == null ? fallback : value;
    }
}
