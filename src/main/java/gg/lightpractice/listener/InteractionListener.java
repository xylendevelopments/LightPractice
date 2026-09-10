package gg.lightpractice.listener;

import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.gui.MenuRouter;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.spectator.SpectatorManager;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Everything a player does with an item: lobby items, spectator tools, pearls, apples, potions and rods.
 *
 * <p>The order matters. Spectator tools are checked first because a spectator holds nothing else, then the
 * lobby items of a player who is not fighting, and finally the kit rules of the running match. Cooldowns
 * are enforced where the item is actually used, so a pearl that is blocked never starts its cooldown.</p>
 */
public final class InteractionListener implements Listener {

    private final PluginCore core;

    public InteractionListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (action == Action.PHYSICAL) {
            return;
        }
        SpectatorManager spectators = core.optional(SpectatorManager.class);
        if (spectators != null && spectators.isSpectating(player.getUniqueId())) {
            event.setCancelled(true);
            if (item != null) {
                spectators.handleToolClick(player, item, rightClick);
            }
            return;
        }
        Match match = matchOf(player);
        if (match == null) {
            LobbyManager lobby = core.optional(LobbyManager.class);
            if (lobby != null && item != null) {
                String lobbyAction = lobby.actionOf(item);
                if (lobbyAction != null) {
                    event.setCancelled(true);
                    MenuRouter.run(core, player, lobbyAction);
                    return;
                }
            }
            // outside a match nothing is consumed, thrown or placed
            if (item != null && (isPearl(item.getType()) || isGoldenApple(item.getType())
                    || item.getType() == Material.POTION)) {
                event.setCancelled(true);
            }
            return;
        }
        if (item == null) {
            return;
        }
        KitRuleSet rules = match.rules();
        if (rules != null && rules.handleItemUse(match, player, item, action)) {
            event.setCancelled(true);
            return;
        }
        if (rules != null && !rules.canUseItem(match, player, item)) {
            event.setCancelled(true);
            refresh(player);
            core.messages().send(player, "match.item-blocked", "{item}", name(item));
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat == null) {
            return;
        }
        if (isPearl(item.getType())) {
            if (!combat.pearlReady(player, match)) {
                event.setCancelled(true);
                refresh(player);
                long remaining = combat.pearlRemaining(player);
                if (remaining > 0L) {
                    core.messages().send(player, "combat.pearl-cooldown", "{seconds}",
                            Text.duration(remaining));
                } else {
                    core.messages().send(player, "match.pearls-disabled");
                }
            }
            return;
        }
        if (isGoldenApple(item.getType())) {
            if (!combat.appleReady(player, match)) {
                event.setCancelled(true);
                refresh(player);
                long remaining = combat.appleRemaining(player);
                if (remaining > 0L) {
                    core.messages().send(player, "combat.apple-cooldown", "{seconds}",
                            Text.duration(remaining));
                } else {
                    core.messages().send(player, "match.apples-disabled");
                }
            }
            return;
        }
        if (item.getType() == Material.POTION && !combat.potionAllowed(player, match, splash(item))) {
            event.setCancelled(true);
            refresh(player);
            core.messages().send(player, "match.potions-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        Projectile projectile = event.getEntity();
        LivingEntity shooterEntity = projectile.getShooter();
        if (!(shooterEntity instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player shooter = (Player) shooterEntity;
        Match match = matchOf(shooter);
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        KitRuleSet rules = match.rules();
        CombatManager combat = core.optional(CombatManager.class);
        if (projectile instanceof EnderPearl) {
            if (combat == null || !combat.pearlReady(shooter, match)) {
                event.setCancelled(true);
                refresh(shooter);
                core.messages().send(shooter, "match.pearls-disabled");
                return;
            }
            long cooldown = combat.usePearl(shooter, match);
            if (cooldown > 0L) {
                Debug.log(DebugCategory.COMBAT, "{} threw a pearl in match {}, cooldown {}ms",
                        shooter.getName(), match.identifier(), cooldown);
            }
        } else if (projectile instanceof ThrownPotion) {
            if (combat != null && !combat.potionAllowed(shooter, match, true)) {
                event.setCancelled(true);
                refresh(shooter);
                core.messages().send(shooter, "match.potions-disabled");
                return;
            }
        } else if (rules != null && !rules.allowProjectiles()) {
            event.setCancelled(true);
            refresh(shooter);
            core.messages().send(shooter, "match.projectiles-disabled");
            return;
        }
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics != null) {
            cosmetics.trackProjectile(projectile, shooter);
        }
    }

    /** Arrows that land inside our worlds are removed, they are never meant to be collected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event == null || !(event.getEntity() instanceof Arrow)) {
            return;
        }
        final Arrow arrow = (Arrow) event.getEntity();
        Location at = arrow.getLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        boolean managed = false;
        LobbyManager lobby = core.optional(LobbyManager.class);
        if (lobby != null && lobby.isLobbyWorld(at.getWorld())) {
            managed = true;
        }
        gg.lightpractice.arena.ArenaManager arenas = core.optional(gg.lightpractice.arena.ArenaManager.class);
        if (!managed && arenas != null && arenas.arenaAt(at) != null) {
            managed = true;
        }
        if (!managed) {
            return;
        }
        core.tasks().syncLater(new Runnable() {
            @Override
            public void run() {
                arrow.remove();
            }
        }, 1L);
    }

    /** The rod pull comes from the combat profile, never from vanilla fishing mechanics. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        Entity caught = event.getCaught();
        Player player = event.getPlayer();
        if (!(caught instanceof Player)) {
            if (matchOf(player) == null) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        Player victim = (Player) caught;
        Match match = matchOf(victim);
        if (match == null) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null) {
            combat.handleRod(player, victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event == null || event.getPlayer() == null || event.getItem() == null) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        ItemStack item = event.getItem();
        CombatManager combat = core.optional(CombatManager.class);
        if (isGoldenApple(item.getType())) {
            if (combat == null || !combat.appleReady(player, match)) {
                event.setCancelled(true);
                refresh(player);
                return;
            }
            long cooldown = combat.useApple(player, match);
            if (cooldown > 0L) {
                core.messages().send(player, "combat.apple-used", "{seconds}", Text.duration(cooldown));
            }
            return;
        }
        if (item.getType() == Material.POTION && combat != null
                && !combat.potionAllowed(player, match, splash(item))) {
            event.setCancelled(true);
            refresh(player);
            core.messages().send(player, "match.potions-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            event.setCancelled(true);
            if (event.getItemDrop() != null) {
                event.getItemDrop().remove();
            }
            return;
        }
        KitRuleSet rules = match.rules();
        if (rules == null || !rules.allowItemDrops()) {
            event.setCancelled(true);
            core.messages().send(event.getPlayer(), "match.drops-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (matchOf(player) == null) {
            event.setCancelled(true);
        }
    }

    /** Pearls may only land inside the arena and cost the configured amount of health. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        final Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        KitRuleSet rules = match.rules();
        if (rules != null && !rules.allowEnderPearls()) {
            event.setCancelled(true);
            core.messages().send(player, "match.pearls-disabled");
            return;
        }
        if (event.getTo() != null && !match.canModify(event.getTo())) {
            event.setCancelled(true);
            core.messages().send(player, "match.pearl-outside");
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        final double damage = combat == null ? 0.0D : combat.pearlDamage(match);
        if (damage <= 0.0D) {
            return;
        }
        match.allowNextDamage(player);
        final UUID uuid = player.getUniqueId();
        core.tasks().syncLater(new Runnable() {
            @Override
            public void run() {
                Player online = core.plugin().getServer().getPlayer(uuid);
                if (online != null && online.isOnline() && !online.isDead()) {
                    online.damage(damage);
                }
            }
        }, 1L);
    }

    private Match matchOf(Player player) {
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null || player == null ? null : matches.getMatch(player.getUniqueId());
    }

    /** 1.8 needs a nudge after a cancelled interaction, otherwise the client keeps using the item. */
    @SuppressWarnings("deprecation")
    private void refresh(Player player) {
        if (player != null) {
            player.updateInventory();
        }
    }

    private String name(ItemStack item) {
        if (item == null) {
            return "nothing";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Text.strip(item.getItemMeta().getDisplayName());
        }
        return Text.capitalize(item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    /** Ender pearls kept as a material check so no version specific class is needed. */
    private boolean isPearl(Material material) {
        return material == Material.ENDER_PEARL;
    }

    private boolean isGoldenApple(Material material) {
        return material == Material.GOLDEN_APPLE;
    }

    /** Splash potions carry the splash bit in their damage value on 1.8. */
    private boolean splash(ItemStack item) {
        return item != null && item.getType() == Material.POTION && item.getDurability() >= 16384;
    }
}
