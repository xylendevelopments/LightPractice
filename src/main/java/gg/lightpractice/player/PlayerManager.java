package gg.lightpractice.player;

import gg.lightpractice.api.PlayerService;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of online sessions and owner of the state machine.
 *
 * <p>Every "can I do this right now" question in the plugin is answered here, which is what stops a
 * player from queueing while in a match, editing a kit while spectating or accepting a duel during a
 * tournament round.</p>
 */
public final class PlayerManager implements PlayerService, LightService {

    private final PluginCore core;
    private final Map<UUID, LightPlayer> players = new HashMap<UUID, LightPlayer>();

    public PlayerManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "PlayerManager";
    }

    @Override
    public void onEnable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            register(player);
        }
        Debug.log(DebugCategory.PLAYER, "Tracking {} online session(s)", players.size());
    }

    @Override
    public void onDisable() {
        players.clear();
    }

    @Override
    public LightPlayer register(Player player) {
        if (player == null) {
            return null;
        }
        LightPlayer existing = players.get(player.getUniqueId());
        if (existing != null) {
            existing.player(player);
            existing.name(player.getName());
            return existing;
        }
        LightPlayer session = new LightPlayer(player);
        players.put(player.getUniqueId(), session);
        Debug.log(DebugCategory.PLAYER, "Registered session for {}", player.getName());
        return session;
    }

    @Override
    public void unregister(Player player) {
        if (player == null) {
            return;
        }
        LightPlayer removed = players.remove(player.getUniqueId());
        if (removed != null) {
            removed.player(null);
            removed.state(PlayerState.OFFLINE);
            removed.clearSnapshot();
            Debug.log(DebugCategory.PLAYER, "Unregistered session for {}", player.getName());
        }
    }

    @Override
    public LightPlayer get(Player player) {
        return player == null ? null : players.get(player.getUniqueId());
    }

    @Override
    public LightPlayer get(UUID uuid) {
        return uuid == null ? null : players.get(uuid);
    }

    /** Session of an online player, resolved by name for commands. */
    public LightPlayer getByName(String name) {
        if (name == null) {
            return null;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return players.get(player.getUniqueId());
        }
        for (LightPlayer session : players.values()) {
            if (session.name().equalsIgnoreCase(name)) {
                return session;
            }
        }
        return null;
    }

    @Override
    public Collection<LightPlayer> online() {
        return Collections.unmodifiableCollection(players.values());
    }

    public List<LightPlayer> inState(PlayerState state) {
        List<LightPlayer> result = new ArrayList<LightPlayer>();
        for (LightPlayer session : players.values()) {
            if (session.state() == state) {
                result.add(session);
            }
        }
        return result;
    }

    public List<LightPlayer> inMatch(String matchId) {
        List<LightPlayer> result = new ArrayList<LightPlayer>();
        if (matchId == null) {
            return result;
        }
        for (LightPlayer session : players.values()) {
            if (matchId.equals(session.matchId())) {
                result.add(session);
            }
        }
        return result;
    }

    public int count() {
        return players.size();
    }

    @Override
    public boolean setState(LightPlayer session, PlayerState state) {
        if (session == null || state == null) {
            return false;
        }
        PlayerState current = session.state();
        if (current == state) {
            return true;
        }
        if (!current.canTransitionTo(state)) {
            Debug.log(DebugCategory.PLAYER, "Refused state transition {} -> {} for {}",
                    current, state, session.name());
            return false;
        }
        session.state(state);
        Debug.log(DebugCategory.PLAYER, "{} moved {} -> {}", session.name(), current, state);
        return true;
    }

    /** Forces a state during cleanup (match end, shutdown) where transitions are known to be valid. */
    public void forceState(LightPlayer session, PlayerState state) {
        if (session == null || state == null) {
            return;
        }
        session.state(state);
    }

    @Override
    public PlayerState getState(UUID uuid) {
        LightPlayer session = get(uuid);
        return session == null ? PlayerState.OFFLINE : session.state();
    }

    @Override
    public boolean isInMatch(UUID uuid) {
        LightPlayer session = get(uuid);
        return session != null && (session.inMatch() || session.state().inGame());
    }

    @Override
    public boolean isInQueue(UUID uuid) {
        LightPlayer session = get(uuid);
        return session != null && session.inQueue();
    }

    @Override
    public boolean isSpectating(UUID uuid) {
        LightPlayer session = get(uuid);
        return session != null && session.spectating();
    }

    @Override
    public boolean isBusy(UUID uuid) {
        LightPlayer session = get(uuid);
        if (session == null) {
            return true;
        }
        return session.state().busy() || session.inMatch() || session.inQueue()
                || session.spectating() || session.editing() || session.inTournament()
                || session.inEvent();
    }

    /** True when the player may start anything new (queue, duel, kit edit, tournament). */
    public boolean isIdle(UUID uuid) {
        LightPlayer session = get(uuid);
        return session != null && session.state() == PlayerState.LOBBY && !session.inMatch()
                && !session.inQueue() && !session.spectating() && !session.editing()
                && !session.inTournament() && !session.inEvent();
    }

    public PluginCore core() {
        return core;
    }
}
