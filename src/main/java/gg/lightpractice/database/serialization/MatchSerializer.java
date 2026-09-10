package gg.lightpractice.database.serialization;

import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.model.MatchRecord;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Serialises finished matches for MongoDB and projects them back into history entries. */
public final class MatchSerializer {

    private MatchSerializer() {
    }

    public static Document toDocument(MatchRecord record) {
        Document document = new Document();
        document.put("match_id", record.matchId());
        document.put("timestamp", record.timestamp());
        document.put("kit_id", record.kitId());
        document.put("kit_name", record.kitName());
        document.put("arena", record.arenaName());
        document.put("type", record.matchType());
        document.put("end_cause", record.endCause());
        document.put("ranked", record.ranked());
        document.put("duration", record.durationMillis());
        document.put("winning_team", record.winningTeam());

        List<String> participants = new ArrayList<String>();
        List<Document> players = new ArrayList<Document>();
        for (MatchRecord.PlayerRecord player : record.players()) {
            if (player.uuid() != null) {
                participants.add(player.uuid().toString());
            }
            Document playerDocument = new Document();
            playerDocument.put("uuid", player.uuid() == null ? "" : player.uuid().toString());
            playerDocument.put("name", player.name());
            playerDocument.put("team", player.team());
            playerDocument.put("outcome", player.outcome().name());
            playerDocument.put("elo_before", player.eloBefore());
            playerDocument.put("elo_after", player.eloAfter());
            playerDocument.put("kills", player.kills());
            playerDocument.put("deaths", player.deaths());
            playerDocument.put("damage", player.damageDealt());
            playerDocument.put("combo", player.longestCombo());
            players.add(playerDocument);
        }
        document.put("participants", participants);
        document.put("players", players);
        document.put("spectators", new ArrayList<String>(record.spectators()));
        return document;
    }

    public static MatchRecord fromDocument(Document document) {
        if (document == null) {
            return null;
        }
        List<MatchRecord.PlayerRecord> players = new ArrayList<MatchRecord.PlayerRecord>();
        for (Document playerDocument : ProfileSerializer.listOfDocuments(document, "players")) {
            UUID uuid = ProfileSerializer.parseUuid(playerDocument.getString("uuid"));
            MatchOutcome outcome = MatchOutcome.parse(playerDocument.getString("outcome"));
            players.add(new MatchRecord.PlayerRecord(uuid,
                    playerDocument.getString("name"),
                    playerDocument.getString("team"),
                    outcome == null ? MatchOutcome.LOSS : outcome,
                    ProfileSerializer.intOf(playerDocument, "elo_before", 0),
                    ProfileSerializer.intOf(playerDocument, "elo_after", 0),
                    ProfileSerializer.intOf(playerDocument, "kills", 0),
                    ProfileSerializer.intOf(playerDocument, "deaths", 0),
                    ProfileSerializer.longOf(playerDocument, "damage", 0L),
                    ProfileSerializer.intOf(playerDocument, "combo", 0)));
        }
        List<String> spectators = new ArrayList<String>();
        for (Object value : ProfileSerializer.rawList(document, "spectators")) {
            if (value instanceof String) {
                spectators.add((String) value);
            }
        }
        String matchId = document.getString("match_id");
        if (matchId == null || matchId.isEmpty()) {
            Debug.log(DebugCategory.DATABASE, "Ignoring stored match without an identifier");
            return null;
        }
        return new MatchRecord(matchId,
                ProfileSerializer.longOf(document, "timestamp", 0L),
                document.getString("kit_id"),
                document.getString("kit_name"),
                document.getString("arena"),
                document.getString("type"),
                document.getString("end_cause"),
                ProfileSerializer.boolOf(document, "ranked", false),
                ProfileSerializer.longOf(document, "duration", 0L),
                document.getString("winning_team"),
                players,
                spectators);
    }

    /**
     * Projects a stored match into the history entry of one viewer, which is what {@code /history}
     * and the history menu display.
     */
    public static MatchHistoryEntry historyFor(Document document, UUID viewer) {
        if (document == null || viewer == null) {
            return null;
        }
        MatchRecord record = fromDocument(document);
        if (record == null) {
            return null;
        }
        MatchRecord.PlayerRecord own = record.record(viewer);
        List<String> opponents = new ArrayList<String>();
        String ownTeam = own == null ? null : own.team();
        for (MatchRecord.PlayerRecord player : record.players()) {
            if (player.uuid() == null || viewer.equals(player.uuid())) {
                continue;
            }
            if (ownTeam == null || !ownTeam.equals(player.team())) {
                opponents.add(player.name());
            }
        }
        MatchOutcome outcome = own == null ? MatchOutcome.LOSS : own.outcome();
        return new MatchHistoryEntry(record.matchId(),
                record.timestamp(),
                record.kitId(),
                record.kitName(),
                record.arenaName(),
                record.matchType(),
                record.durationMillis(),
                opponents,
                outcome,
                record.ranked(),
                own == null ? 0 : own.eloBefore(),
                own == null ? 0 : own.eloAfter(),
                own == null ? 0 : own.kills(),
                own == null ? 0 : own.deaths());
    }
}
