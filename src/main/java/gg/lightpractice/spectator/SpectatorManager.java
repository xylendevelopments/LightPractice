package gg.lightpractice.spectator;

import gg.lightpractice.api.SpectatorService;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.config.Messages;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.player.InventorySnapshot;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.player.PlayerState;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Locations;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spectating live matches.
 *
 * <p>A spectator keeps its previous inventory in a snapshot, moves in spectator game mode, is hidden from
 * the fighters and gets a small hotbar to switch between participants and to leave again. Leaving restores
 * the snapshot, the player state and the lobby position.</p>
 */
public final class SpectatorManager implements SpectatorService, LightService {

    private final PluginCore core;
    private final Map<UUID, UUID> matchOf = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, UUID> targetOf = new ConcurrentHashMap<UUID, UUID>();
    private Material switchItem = Material.COMPASS;
    private String switchName = "&bSwitch player";
    private Material leaveItem = Material.BED;
    private String leaveName = "&cLeave spectator";
    private boolean allowFlight = true;

    public SpectatorManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "spectators";
    }

    @Override
    public int startupOrder() {
        return 78;
    }

    @Override
    public void onLoad() {
        readConfiguration();
    }

    @Override
    public void onEnable() {
        readConfiguration();
    }

    @Override
    public void onDisable() {
        cleanup();
    }

    @Override
    public void onReload() {
        readConfiguration();
    }

    private void readConfiguration() {
        ConfigFile gui = core.configs().gui();
        this.switchItem = Items.material(gui.getString("spectator.switch-item", "COMPASS"), Material.COMPASS);
        this.switchName = Text.color(gui.getString("spectator.switch-name", "&bSwitch player"));
        this.leaveItem = Items.material(gui.getString("spectator.leave-item", "BED"), Material.BED);
        this.leaveName = Text.color(gui.getString("spectator.leave-name", "&cLeave spectator"));
        this.allowFlight = core.configs().config().getBoolean("spectator.allow-flight", true);
    }

    // --------------------------------------------------------- SpectatorService

    @Override
    public boolean spectate(Player spectator, Match match) {
        if (spectator == null || match == null) {
            return false;
        }
        Messages messages = core.messages();
        if (!match.isLive()) {
            messages.send(spectator, "spectator.match-not-live");
            return false;
        }
        if (match.isParticipant(spectator.getUniqueId())) {
            messages.send(spectator, "spectator.participant");
            return false;
        }
        if (match.arena() != null && !match.arena().enabled()) {
            messages.send(spectator, "spectator.arena-disabled");
            return false;
        }
        if (!spectator.hasPermission("lightpractice.spectate")) {
            messages.send(spectator, "spectator.no-permission");
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(spectator);
        if (session == null) {
            messages.send(spectator, "spectator.not-ready");
            return false;
        }
        if (!session.state().canSpectate()) {
            messages.send(spectator, "spectator.busy", "{state}",
                    session.state().name().toLowerCase(java.util.Locale.ROOT));
            return false;
        }
        if (isSpectating(spectator.getUniqueId())) {
            leave(spectator);
        }
        if (session.snapshot() == null) {
            session.snapshot(InventorySnapshot.capture(spectator));
        }
        if (!players.setState(session, PlayerState.SPECTATING)) {
            messages.send(spectator, "spectator.busy", "{state}", session.state().name());
            return false;
        }
        session.spectatedMatchId(match.identifier());
        matchOf.put(spectator.getUniqueId(), match.id());
        MatchManager matches = core.optional(MatchManager.class);
        if (matches != null) {
            matches.addSpectator(match, spectator);
        } else {
            match.addSpectator(spectator.getUniqueId());
        }
        applySpectatorState(spectator, match);
        MatchPlayer target = firstAlive(match);
        if (target != null) {
            targetOf.put(spectator.getUniqueId(), target.uuid());
        }
        match.refreshVisibility();
        messages.send(spectator, "spectator.started",
                "{kit}", match.kit() == null ? "unknown" : match.kit().displayName(),
                "{arena}", match.arena() == null ? "none" : match.arena().name(),
                "{players}", String.valueOf(match.aliveCount()));
        announce(match, "spectator.joined", "{player}", spectator.getName());
        Debug.log(DebugCategory.SPECTATOR, "{} started spectating match {}", spectator.getName(),
                match.identifier());
        return true;
    }

    @Override
    public boolean spectatePlayer(Player spectator, Player target) {
        if (spectator == null || target == null) {
            return false;
        }
        MatchManager matches = core.optional(MatchManager.class);
        Match match = matches == null ? null : matches.getMatch(target.getUniqueId());
        if (match == null || !match.isParticipant(target.getUniqueId())) {
            core.messages().send(spectator, "spectator.target-not-in-match", "{player}", target.getName());
            return false;
        }
        if (!spectate(spectator, match)) {
            return false;
        }
        targetOf.put(spectator.getUniqueId(), target.getUniqueId());
        return true;
    }

    /** Puts a player into spectator game mode with the switching hotbar. */
    private void applySpectatorState(Player spectator, Match match) {
        spectator.getInventory().clear();
        spectator.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect effect : new ArrayList<PotionEffect>(spectator.getActivePotionEffects())) {
            spectator.removePotionEffect(effect.getType());
        }
        spectator.setFireTicks(0);
        spectator.setFallDistance(0.0F);
        spectator.setFoodLevel(20);
        spectator.setSaturation(5.0F);
        try {
            spectator.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.SPECTATOR, "Spectator gamemode unavailable: {}", throwable.getMessage());
        }
        if (allowFlight) {
            spectator.setAllowFlight(true);
            spectator.setFlying(true);
        }
        giveTools(spectator);
        Location spawn = spectatorSpawn(match);
        if (spawn != null) {
            spectator.teleport(spawn);
        }
        spectator.updateInventory();
    }

    private void giveTools(Player spectator) {
        ItemStack switcher = Items.item(switchItem, 1, (short) 0, switchName,
                Collections.singletonList("&7Right click to cycle players"));
        ItemStack exit = Items.item(leaveItem, 1, (short) 0, leaveName,
                Collections.singletonList("&7Right click to stop spectating"));
        spectator.getInventory().setItem(0, switcher);
        spectator.getInventory().setItem(8, exit);
    }

    private Location spectatorSpawn(Match match) {
        Arena arena = match == null ? null : match.arena();
        if (arena != null) {
            Location spawn = arena.spectatorSpawn();
            if (spawn != null) {
                return spawn;
            }
            Location center = arena.center();
            if (center != null) {
                return center.clone().add(0.0D, 6.0D, 0.0D);
            }
        }
        return Locations.read(core.configs().config().config(), "lobby.spawn");
    }

    private MatchPlayer firstAlive(Match match) {
        for (MatchPlayer participant : match.participants()) {
            if (!participant.eliminated() && participant.isOnline()) {
                return participant;
            }
        }
        return null;
    }

    @Override
    public boolean leave(Player spectator) {
        if (spectator == null) {
            return false;
        }
        UUID uuid = spectator.getUniqueId();
        UUID matchId = matchOf.remove(uuid);
        targetOf.remove(uuid);
        Match match = null;
        if (matchId != null) {
            MatchManager matches = core.optional(MatchManager.class);
            match = matches == null ? null : matches.get(String.valueOf(matchId));
        }
        if (match == null) {
            match = matchByScan(uuid);
        }
        if (match != null) {
            match.removeSpectator(uuid);
            MatchManager matches = core.optional(MatchManager.class);
            if (matches != null) {
                matches.removeSpectator(match, spectator);
            }
            match.refreshVisibility();
            announce(match, "spectator.left", "{player}", spectator.getName());
        }
        restore(spectator);
        Debug.log(DebugCategory.SPECTATOR, "{} stopped spectating", spectator.getName());
        return true;
    }

    private Match matchByScan(UUID uuid) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return null;
        }
        for (Match match : matches.matches()) {
            if (match.isSpectator(uuid)) {
                return match;
            }
        }
        return null;
    }

    /** Restores a spectator: snapshot, state, gamemode and lobby position. */
    private void restore(Player spectator) {
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(spectator);
        if (session != null) {
            InventorySnapshot snapshot = session.snapshot();
            if (snapshot != null) {
                snapshot.restore(spectator, false);
            }
            session.clearSnapshot();
            session.spectatedMatchId(null);
            session.spectatedTarget(null);
            players.forceState(session, PlayerState.LOBBY);
        }
        spectator.getInventory().clear();
        spectator.getInventory().setArmorContents(new ItemStack[4]);
        spectator.updateInventory();
        try {
            spectator.setGameMode(GameMode.SURVIVAL);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.SPECTATOR, "Could not restore the gamemode: {}", throwable.getMessage());
        }
        spectator.setAllowFlight(false);
        spectator.setFlying(false);
        spectator.setFallDistance(0.0F);
        spectator.setFireTicks(0);
        spectator.setHealth(Math.max(1.0D, spectator.getMaxHealth()));
        spectator.setFoodLevel(20);
        spectator.setSaturation(5.0F);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != null && other.isOnline()) {
                spectator.showPlayer(other);
                other.showPlayer(spectator);
            }
        }
        Location lobby = Locations.read(core.configs().config().config(), "lobby.spawn");
        if (lobby != null && lobby.getWorld() != null) {
            spectator.teleport(lobby);
        }
        core.messages().send(spectator, "spectator.stopped");
    }

    @Override
    public boolean isSpectating(UUID uuid) {
        return uuid != null && matchOf.containsKey(uuid);
    }

    @Override
    public Match matchOf(UUID uuid) {
        UUID matchId = uuid == null ? null : matchOf.get(uuid);
        if (matchId == null) {
            return null;
        }
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null ? null : matches.get(String.valueOf(matchId));
    }

    /** The participant a spectator follows right now, {@code null} when watching the whole match. */
    public UUID targetOf(UUID spectator) {
        return spectator == null ? null : targetOf.get(spectator);
    }

    /** Name of the followed participant, used by scoreboards and the tab list. */
    public String targetName(UUID spectator) {
        UUID target = targetOf(spectator);
        if (target == null) {
            return null;
        }
        Match match = matchOf(spectator);
        MatchPlayer participant = match == null ? null : match.participant(target);
        if (participant != null) {
            return participant.name();
        }
        Player online = Bukkit.getPlayer(target);
        return online == null ? null : online.getName();
    }

    @Override
    public Collection<UUID> spectatorsOf(Match match) {
        return match == null ? Collections.<UUID>emptySet() : match.spectators();
    }

    @Override
    public boolean switchTarget(Player spectator, UUID target) {
        if (spectator == null || target == null) {
            return false;
        }
        Match match = matchOf(spectator.getUniqueId());
        if (match == null || !match.isParticipant(target)) {
            core.messages().send(spectator, "spectator.invalid-target");
            return false;
        }
        targetOf.put(spectator.getUniqueId(), target);
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            try {
                spectator.setSpectatorTarget(targetPlayer);
            } catch (Throwable throwable) {
                Debug.log(DebugCategory.SPECTATOR, "Camera lock unavailable: {}", throwable.getMessage());
            }
            spectator.teleport(targetPlayer.getLocation());
        }
        MatchPlayer participant = match.participant(target);
        core.messages().send(spectator, "spectator.target-switched",
                "{player}", participant == null ? "unknown" : participant.name());
        return true;
    }

    /** Cycles to the next alive participant, used by the spectator hotbar item. */
    public boolean nextTarget(Player spectator) {
        return cycle(spectator, 1);
    }

    /** Cycles to the previous alive participant. */
    public boolean previousTarget(Player spectator) {
        return cycle(spectator, -1);
    }

    private boolean cycle(Player spectator, int direction) {
        if (spectator == null) {
            return false;
        }
        Match match = matchOf(spectator.getUniqueId());
        if (match == null) {
            return false;
        }
        List<MatchPlayer> alive = new ArrayList<MatchPlayer>();
        for (MatchPlayer participant : match.participants()) {
            if (!participant.eliminated() && participant.isOnline()) {
                alive.add(participant);
            }
        }
        if (alive.isEmpty()) {
            core.messages().send(spectator, "spectator.no-targets");
            return false;
        }
        UUID current = targetOf.get(spectator.getUniqueId());
        int index = 0;
        if (current != null) {
            for (int position = 0; position < alive.size(); position++) {
                if (current.equals(alive.get(position).uuid())) {
                    index = position;
                    break;
                }
            }
        }
        int next = (index + direction + alive.size()) % alive.size();
        return switchTarget(spectator, alive.get(next).uuid());
    }

    @Override
    public void cleanup() {
        List<UUID> spectators = new ArrayList<UUID>(matchOf.keySet());
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leave(player);
                continue;
            }
            matchOf.remove(uuid);
            targetOf.remove(uuid);
        }
    }

    // ------------------------------------------------------------------- extras

    /** Whether a click used one of the spectator tools, so listeners can cancel it. */
    public boolean handleToolClick(Player spectator, ItemStack item, boolean rightClick) {
        if (spectator == null || item == null || !isSpectating(spectator.getUniqueId())) {
            return false;
        }
        if (item.getType() == leaveItem) {
            leave(spectator);
            return true;
        }
        if (item.getType() == switchItem) {
            if (rightClick) {
                nextTarget(spectator);
            } else {
                previousTarget(spectator);
            }
            return true;
        }
        return false;
    }

    private void announce(Match match, String key, String... replacements) {
        List<Player> receivers = new ArrayList<Player>(match.onlinePlayers());
        for (UUID uuid : match.spectators()) {
            Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player != null && !receivers.contains(player)) {
                receivers.add(player);
            }
        }
        core.messages().broadcastTo(receivers, key, replacements);
    }

    /** Live matches a player may spectate, used by the spectate menu. */
    public List<Match> spectatable() {
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null ? new ArrayList<Match>() : matches.spectatable();
    }

    public int spectatorCount() {
        return matchOf.size();
    }

    /** Item used to switch targets, exposed so listeners can detect it. */
    public Material switchItem() {
        return switchItem;
    }

    public Material leaveItem() {
        return leaveItem;
    }
}
