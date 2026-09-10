package gg.lightpractice.statistics;

import gg.lightpractice.api.HistoryService;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.database.DatabaseService;
import gg.lightpractice.database.repository.MatchRepository;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.model.MatchHistoryEntry;
import gg.lightpractice.model.MatchOutcome;
import gg.lightpractice.model.MatchRecord;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persists finished matches and keeps the most recent results on each profile.
 *
 * <p>Writing the document happens on the database executor, while the profile cache entry is added on
 * the main thread right away so a player opening their history menu sees the match they just
 * played.</p>
 */
public final class HistoryManager implements HistoryService, LightService {

    private final PluginCore core;
    private final MatchRepository repository;
    private int pageSize = 10;
    private int retentionDays = 60;

    public HistoryManager(PluginCore core, MatchRepository repository) {
        this.core = core;
        this.repository = repository;
    }

    @Override
    public String name() {
        return "history";
    }

    @Override
    public int startupOrder() {
        return 66;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onReload() {
        readConfiguration();
    }

    private void readConfiguration() {
        ConfigFile config = core.configs().database();
        this.pageSize = Math.max(1, Math.min(50, config.getInt("history.page-size", 10)));
        this.retentionDays = Math.max(0, config.getInt("history.retention-days", 60));
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    public int retentionDays() {
        return retentionDays;
    }

    @Override
    public void save(final Match match) {
        if (match == null || repository == null) {
            return;
        }
        MatchRecord record = record(match);
        cacheEntries(match, record);
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            Debug.log(DebugCategory.DATABASE, "Match {} finished while the database was down, history not stored",
                    match.id());
            return;
        }
        database.onMain(repository.save(record), new Consumer<Boolean>() {
            @Override
            public void accept(Boolean saved) {
                Debug.log(DebugCategory.DATABASE, "Stored the history of match {}: {}", match.id(), saved);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not store the history of match " + match.id(), error);
            }
        });
    }

    /** Builds the document of a finished match. */
    public MatchRecord record(Match match) {
        Kit kit = match.kit();
        MatchTeam winning = match.winningTeam();
        List<MatchRecord.PlayerRecord> players = new ArrayList<MatchRecord.PlayerRecord>();
        for (MatchPlayer participant : match.participants()) {
            MatchTeam team = participant.team();
            players.add(new MatchRecord.PlayerRecord(participant.uuid(), participant.name(),
                    team == null ? "none" : team.name(), participant.outcome(),
                    participant.eloBefore(), participant.eloAfter(), participant.kills(), participant.deaths(),
                    participant.damage(), participant.longestCombo()));
        }
        return new MatchRecord(String.valueOf(match.id()), match.startMillis(), kit == null ? "unknown" : kit.id(),
                kit == null ? "Unknown" : kit.displayName(), match.arena() == null ? "none" : match.arena().name(),
                match.type() == null ? "UNKNOWN" : match.type().name(),
                match.endCause() == null ? "UNKNOWN" : match.endCause().name(), match.ranked(),
                match.durationMillis(), winning == null ? null : winning.name(), players, match.spectatorNames());
    }

    /** Adds one entry per participant to their cached profile. */
    private void cacheEntries(Match match, MatchRecord record) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles == null) {
            return;
        }
        for (MatchPlayer participant : match.participants()) {
            Profile profile = profiles.getProfile(participant.uuid());
            if (profile == null) {
                continue;
            }
            profile.addHistory(entry(record, participant, match));
        }
    }

    /** View of one match for one participant, with their opponents listed. */
    public MatchHistoryEntry entry(MatchRecord record, MatchPlayer participant, Match match) {
        List<String> opponents = new ArrayList<String>();
        for (MatchPlayer other : match.participants()) {
            if (other == null || other.uuid() == null || other.uuid().equals(participant.uuid())) {
                continue;
            }
            MatchTeam own = participant.team();
            MatchTeam theirs = other.team();
            if (own != null && own.equals(theirs) && match.teams().size() > 1) {
                continue;
            }
            opponents.add(other.name());
        }
        MatchOutcome outcome = participant.outcome() == null ? MatchOutcome.LOSS : participant.outcome();
        return new MatchHistoryEntry(record.matchId(), record.timestamp(), record.kitId(), record.kitName(),
                record.arenaName(), record.matchType(), record.durationMillis(), opponents, outcome,
                record.ranked(), participant.eloBefore(), participant.eloAfter(), participant.kills(),
                participant.deaths());
    }

    @Override
    public List<MatchHistoryEntry> cached(UUID uuid) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        Profile profile = profiles == null || uuid == null ? null : profiles.getProfile(uuid);
        return profile == null ? Collections.<MatchHistoryEntry>emptyList() : profile.history();
    }

    @Override
    public void load(UUID uuid, int page, final Consumer<List<MatchHistoryEntry>> callback) {
        if (uuid == null || callback == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected() || repository == null) {
            callback.accept(cached(uuid));
            return;
        }
        final int requested = Math.max(0, page);
        database.onMain(repository.history(uuid, requested, pageSize), new Consumer<List<MatchHistoryEntry>>() {
            @Override
            public void accept(List<MatchHistoryEntry> entries) {
                callback.accept(entries == null ? new ArrayList<MatchHistoryEntry>() : entries);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not load match history page " + requested, error);
                callback.accept(Collections.<MatchHistoryEntry>emptyList());
            }
        });
    }

    /** Number of stored matches of a player, delivered on the main thread. */
    public void matchCount(UUID uuid, final Consumer<Integer> callback) {
        if (uuid == null || callback == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected() || repository == null) {
            callback.accept(Integer.valueOf(0));
            return;
        }
        database.onMain(repository.matchCount(uuid), callback, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not count the matches of " + uuid, error);
                callback.accept(Integer.valueOf(0));
            }
        });
    }

    /** Most recently finished matches, used by the staff menu. */
    public void recent(int limit, final Consumer<List<MatchRecord>> callback) {
        if (callback == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected() || repository == null) {
            callback.accept(Collections.<MatchRecord>emptyList());
            return;
        }
        database.onMain(repository.recent(Math.max(1, limit)), callback, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not load recent matches", error);
                callback.accept(Collections.<MatchRecord>emptyList());
            }
        });
    }

    /** Drops stored matches older than the configured retention period. */
    public void purgeExpired() {
        if (retentionDays <= 0 || repository == null) {
            return;
        }
        DatabaseService database = core.database();
        if (database == null || !database.isConnected()) {
            return;
        }
        final long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        database.onMain(repository.purgeOlderThan(cutoff), new Consumer<Integer>() {
            @Override
            public void accept(Integer removed) {
                if (removed != null && removed > 0) {
                    Debug.log(DebugCategory.DATABASE, "Purged {} expired match record(s)", removed);
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                Debug.error(DebugCategory.DATABASE, "Could not purge expired match records", error);
            }
        });
    }
}
