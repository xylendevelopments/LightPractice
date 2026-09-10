package gg.lightpractice.gameevent;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One hosted event session.
 *
 * <p>The session carries everything a {@link GameEventProvider} needs: the entries, the kit and arena, the
 * countdown, the match it is playing in and a scratch space for provider specific progress such as the
 * capture time of a king of the hill point. Providers never hold their own state, which is why a restart
 * or a reload cannot leave a half tracked event behind.</p>
 */
public final class GameEvent {

    private final String id;
    private final GameEventProvider provider;
    private final EventManager manager;
    private final UUID host;
    private final String hostName;
    private final long createdAt;
    private final Set<UUID> participants = Collections.synchronizedSet(new LinkedHashSet<UUID>());
    private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
    private final List<String> history = new ArrayList<String>();
    private final List<UUID> winners = new ArrayList<UUID>();
    private String name;
    private String kitId;
    private String arenaName;
    private EventState state = EventState.GATHERING;
    private long startAt;
    private long updatedAt;
    private int countdownSeconds;
    private String matchId;
    private int minPlayers = 2;
    private int maxPlayers = 32;
    private Location objective;
    private double objectiveRadius = 3.0D;
    private int objectiveSeconds = 60;
    private UUID winner;
    private String endReason = "";

    public GameEvent(String id, GameEventProvider provider, EventManager manager, UUID host, String hostName) {
        this.id = id == null ? UUID.randomUUID().toString().substring(0, 8) : id;
        this.provider = provider;
        this.manager = manager;
        this.host = host;
        this.hostName = hostName == null ? "console" : hostName;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.name = provider == null ? "Event" : provider.displayName();
    }

    // ------------------------------------------------------------------ identity

    public String id() {
        return id;
    }

    public GameEventProvider provider() {
        return provider;
    }

    public String typeId() {
        return provider == null ? "unknown" : provider.id();
    }

    public EventManager manager() {
        return manager;
    }

    public UUID host() {
        return host;
    }

    public String hostName() {
        return hostName;
    }

    public long createdAt() {
        return createdAt;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name == null || name.trim().isEmpty() ? typeId() : gg.lightpractice.util.Text.color(name);
        touch();
    }

    public String kitId() {
        return kitId;
    }

    public void kitId(String kitId) {
        this.kitId = kitId == null ? "" : kitId.trim();
        touch();
    }

    public String arenaName() {
        return arenaName;
    }

    public void arenaName(String arenaName) {
        this.arenaName = arenaName == null ? "" : arenaName.trim();
        touch();
    }

    // --------------------------------------------------------------------- state

    public EventState state() {
        return state;
    }

    public void state(EventState state) {
        this.state = state == null ? EventState.CANCELLED : state;
        touch();
    }

    public boolean live() {
        return state.live();
    }

    public long startAt() {
        return startAt;
    }

    public void startAt(long startAt) {
        this.startAt = startAt;
        touch();
    }

    public long remainingMillis() {
        return Math.max(0L, startAt - System.currentTimeMillis());
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public void countdownSeconds(int countdownSeconds) {
        this.countdownSeconds = Math.max(0, countdownSeconds);
    }

    public String matchId() {
        return matchId;
    }

    public void matchId(String matchId) {
        this.matchId = matchId;
        touch();
    }

    public int minPlayers() {
        return minPlayers;
    }

    public void minPlayers(int minPlayers) {
        this.minPlayers = Math.max(1, minPlayers);
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public void maxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(minPlayers, maxPlayers);
    }

    // ----------------------------------------------------------------- objective

    /** World position of the event objective, used by point based providers. */
    public Location objective() {
        return objective;
    }

    public void objective(Location objective) {
        this.objective = objective;
        touch();
    }

    public double objectiveRadius() {
        return objectiveRadius;
    }

    public void objectiveRadius(double objectiveRadius) {
        this.objectiveRadius = Math.max(0.5D, objectiveRadius);
    }

    public int objectiveSeconds() {
        return objectiveSeconds;
    }

    public void objectiveSeconds(int objectiveSeconds) {
        this.objectiveSeconds = Math.max(1, objectiveSeconds);
    }

    // -------------------------------------------------------------- participants

    public boolean join(UUID uuid) {
        if (uuid == null || !state.joinable() || isFull() || participants.contains(uuid)) {
            return false;
        }
        boolean added = participants.add(uuid);
        if (added) {
            touch();
        }
        return added;
    }

    public boolean leave(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = participants.remove(uuid);
        attributes.remove(attributeKey(uuid));
        if (removed) {
            touch();
        }
        return removed;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid != null && participants.contains(uuid);
    }

    public int size() {
        return participants.size();
    }

    public boolean isFull() {
        return participants.size() >= maxPlayers;
    }

    public boolean hasEnoughPlayers() {
        return participants.size() >= minPlayers;
    }

    public List<UUID> participantsList() {
        synchronized (participants) {
            return new ArrayList<UUID>(participants);
        }
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    // ---------------------------------------------------------------- attributes

    /** Attribute key scoped to one participant, used for per player progress. */
    public static String attributeKey(UUID uuid) {
        return "player:" + uuid;
    }

    public Object attribute(String key) {
        return key == null ? null : attributes.get(key);
    }

    public void attribute(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
        touch();
    }

    public int attributeInt(String key, int fallback) {
        Object value = attribute(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public long attributeLong(String key, long fallback) {
        Object value = attribute(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    public double attributeDouble(String key, double fallback) {
        Object value = attribute(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    public String attributeString(String key, String fallback) {
        Object value = attribute(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public UUID attributeUuid(String key) {
        Object value = attribute(key);
        if (value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof String) {
            try {
                return UUID.fromString((String) value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // ------------------------------------------------------------------- results

    public List<UUID> winners() {
        return Collections.unmodifiableList(winners);
    }

    public void winners(Collection<UUID> winners) {
        this.winners.clear();
        if (winners != null) {
            for (UUID uuid : winners) {
                if (uuid != null && !this.winners.contains(uuid)) {
                    this.winners.add(uuid);
                }
            }
        }
        this.winner = this.winners.isEmpty() ? null : this.winners.get(0);
        touch();
    }

    public UUID winner() {
        return winner;
    }

    public String endReason() {
        return endReason;
    }

    public void endReason(String endReason) {
        this.endReason = endReason == null ? "" : endReason;
    }

    public List<String> history() {
        return Collections.unmodifiableList(history);
    }

    public void log(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        history.add(line);
        if (history.size() > 100) {
            history.remove(0);
        }
        touch();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GameEvent && ((GameEvent) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GameEvent{" + id + ", type=" + typeId() + ", state=" + state + ", players=" + participants.size()
                + '}';
    }
}
