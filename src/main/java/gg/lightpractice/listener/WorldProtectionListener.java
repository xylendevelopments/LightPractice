package gg.lightpractice.listener;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeafDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * World hygiene for lobbies and arenas.
 *
 * <p>Fire, liquids, growth, weather, portals and mob spawns are stopped in every world the plugin owns, so
 * an arena looks the same after a hundred matches as it did after the first reset. Explosions keep their
 * entity damage but lose their block damage unless the kit of the running match asks for it.</p>
 */
public final class WorldProtectionListener implements Listener {

    private final PluginCore core;

    public WorldProtectionListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDecay(LeafDecayEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Lava and water may not creep into an arena and wash away a build. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event == null) {
            return;
        }
        if (managed(event.getBlock()) || managed(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event == null || !managed(event.getBlock())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && matchOf(player) != null) {
            // a flint and steel inside a match is the kit's business, the build listener decides
            return;
        }
        if (player != null && player.hasPermission("lightpractice.admin.build")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("lightpractice.admin.build")) {
            event.setCancelled(true);
        }
    }

    /** Mobs only appear when a plugin puts them there, never on their own. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event == null || event.getLocation() == null || !managed(event.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event != null && managed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Explosions hurt players according to the kit but never reshape an arena. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event == null || event.getLocation() == null) {
            return;
        }
        if (!managed(event.getLocation().getWorld())) {
            return;
        }
        Match match = matchAt(event.getLocation());
        KitRuleSet rules = match == null ? null : match.rules();
        if (rules != null && rules.allowExplosionBlockDamage()) {
            Debug.log(DebugCategory.MATCH, "Explosion in match {} may damage blocks",
                    match.identifier());
            return;
        }
        event.blockList().clear();
        event.setYield(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event == null || !managed(event.getWorld()) || !weatherLocked()) {
            return;
        }
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (event == null || !managed(event.getWorld()) || !weatherLocked()) {
            return;
        }
        if (event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        if (event != null && managed(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        if (event != null && managed(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (event != null && managed(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Whether a world belongs to this plugin, either as a lobby or because it holds arenas. */
    private boolean managed(World world) {
        if (world == null) {
            return false;
        }
        LobbyManager lobby = core.optional(LobbyManager.class);
        if (lobby != null && lobby.isLobbyWorld(world)) {
            return true;
        }
        ArenaManager arenas = core.optional(ArenaManager.class);
        return arenas != null && !arenas.arenasInWorld(world).isEmpty();
    }

    private boolean managed(Block block) {
        return block != null && managed(block.getWorld());
    }

    private boolean weatherLocked() {
        return core.configs().config().getBoolean("world.lock-weather", true);
    }

    private Match matchOf(Player player) {
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null || player == null ? null : matches.getMatch(player.getUniqueId());
    }

    /** The match that owns a location, found through the arena that contains it. */
    private Match matchAt(Location location) {
        ArenaManager arenas = core.optional(ArenaManager.class);
        MatchManager matches = core.optional(MatchManager.class);
        if (arenas == null || matches == null || location == null) {
            return null;
        }
        Arena arena = arenas.arenaAt(location);
        if (arena == null) {
            return null;
        }
        for (Match match : matches.matchesIn(arena)) {
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
