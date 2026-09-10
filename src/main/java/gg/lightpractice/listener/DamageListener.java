package gg.lightpractice.listener;

import gg.lightpractice.bot.BotManager;
import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.combat.DamageVerdict;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Damage: the single place that decides who may hurt whom and for how much.
 *
 * <p>Everything is routed through {@link CombatManager#verdict} so kit rules, friendly fire, eliminated
 * players and internal damage are treated identically no matter whether the hit came from a sword, an
 * arrow, a potion or the void. Bots own their health completely, so their events are handed over first.
 * Anything that is not part of a match takes no damage at all: the lobby is a safe zone.</p>
 */
public final class DamageListener implements Listener {

    private final PluginCore core;

    public DamageListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event == null || event instanceof EntityDamageByEntityEvent) {
            return;
        }
        BotManager bots = core.optional(BotManager.class);
        if (bots != null && bots.handleBotEnvironmentDamage(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            // mobs, armor stands and item frames are decoration only
            event.setCancelled(true);
            return;
        }
        Player victim = (Player) event.getEntity();
        Match match = matchOf(victim);
        if (match == null) {
            event.setCancelled(true);
            victim.setFireTicks(0);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                rescue(victim);
            }
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        DamageVerdict verdict = combat == null ? DamageVerdict.allow(event.getDamage())
                : combat.verdict(match, victim, null, event.getCause(), event.getDamage());
        if (!verdict.allowed()) {
            event.setCancelled(true);
            victim.setFireTicks(0);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                handleVoid(match, victim);
            }
            return;
        }
        event.setDamage(verdict.damage());
        if (instantLoss(match, victim, event.getCause())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            handleVoid(match, victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null) {
            return;
        }
        BotManager bots = core.optional(BotManager.class);
        if (bots != null && bots.handleBotDamaged(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = attackerOf(event.getDamager());
        if (event.getDamager() instanceof org.bukkit.entity.FishHook) {
            handleRod(event, attacker, victim);
            return;
        }
        Match match = matchOf(victim);
        if (match == null) {
            event.setCancelled(true);
            victim.setFireTicks(0);
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        DamageVerdict verdict = combat == null ? DamageVerdict.allow(event.getDamage())
                : combat.verdict(match, victim, attacker, event.getCause(), event.getDamage());
        if (!verdict.allowed()) {
            event.setCancelled(true);
            victim.setFireTicks(0);
            if (attacker != null) {
                Debug.log(DebugCategory.COMBAT, "Hit of {} on {} denied in match {}: {}",
                        attacker.getName(), victim.getName(), match.identifier(), verdict.reason());
            }
            return;
        }
        event.setDamage(verdict.damage());
        if (attacker != null && !attacker.equals(victim) && combat != null) {
            combat.registerHit(match, attacker, victim, verdict.damage());
        }
        instantLoss(match, victim, event.getCause());
    }

    /** A rod hit never deals vanilla damage, the pull strength of the combat profile decides. */
    private void handleRod(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        event.setCancelled(true);
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return;
        }
        Match match = matchOf(victim);
        if (match == null) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat == null) {
            return;
        }
        if (!combat.handleRod(attacker, victim)) {
            Debug.log(DebugCategory.COMBAT, "Rod pull of {} on {} was not ready", attacker.getName(),
                    victim.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event == null || event.getPotion() == null) {
            return;
        }
        ThrownPotion potion = event.getPotion();
        LivingEntity shooterEntity = potion.getShooter();
        Player thrower = shooterEntity instanceof Player ? (Player) shooterEntity : null;
        MatchManager matches = core.optional(MatchManager.class);
        CombatManager combat = core.optional(CombatManager.class);
        Match throwerMatch = thrower == null || matches == null ? null
                : matches.getMatch(thrower.getUniqueId());
        if (throwerMatch != null && combat != null && !combat.potionAllowed(thrower, throwerMatch, true)) {
            event.setCancelled(true);
            return;
        }
        boolean harmful = harmful(potion);
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player)) {
                event.setIntensity(affected, 0.0D);
                continue;
            }
            Player victim = (Player) affected;
            Match match = matches == null ? null : matches.getMatch(victim.getUniqueId());
            if (match == null) {
                event.setIntensity(victim, 0.0D);
                continue;
            }
            KitRuleSet rules = match.rules();
            if (rules != null && !rules.allowSplashPotions()) {
                event.setIntensity(victim, 0.0D);
                continue;
            }
            if (thrower != null && !thrower.equals(victim)) {
                MatchTeam own = match.teamOf(thrower);
                if (own != null && own.contains(victim) && (rules == null || !rules.allowFriendlyFire())) {
                    event.setIntensity(victim, 0.0D);
                    continue;
                }
                if (harmful && combat != null) {
                    combat.registerHit(match, thrower, victim, 0.0D);
                }
            }
        }
    }

    /** Whether a splash potion counts as an attack, used to tag combat and credit hits. */
    private boolean harmful(ThrownPotion potion) {
        for (PotionEffect effect : potion.getEffects()) {
            PotionEffectType type = effect == null ? null : effect.getType();
            if (type == null || type.getName() == null) {
                continue;
            }
            String name = type.getName().toUpperCase(Locale.ROOT);
            if ("HARM".equals(name) || "POISON".equals(name) || "WEAKNESS".equals(name)
                    || "SLOW".equals(name) || "WITHER".equals(name) || "INSTANT_DAMAGE".equals(name)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        KitRuleSet rules = match.rules();
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                && rules != null && !rules.allowNaturalRegeneration()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Match match = matchOf(player);
        KitRuleSet rules = match == null ? null : match.rules();
        if (match == null || (rules != null && !rules.allowHunger())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
    }

    /** A kit rule that ends the fight the moment it triggers, such as leaving a sumo ring. */
    private boolean instantLoss(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        KitRuleSet rules = match.rules();
        if (rules == null || !rules.instantLoss(match, victim, cause, victim.getLocation())) {
            return false;
        }
        Debug.log(DebugCategory.MATCH, "{} lost match {} to the {} rule", victim.getName(),
                match.identifier(), cause.name().toLowerCase(Locale.ROOT));
        match.eliminate(victim, EndCause.DISQUALIFIED);
        return true;
    }

    /** Void damage inside a match: the rules decide between a loss and a rescue. */
    private void handleVoid(Match match, Player victim) {
        MatchPlayer participant = match.participant(victim);
        Location point = participant == null ? null : participant.respawnPoint();
        KitRuleSet rules = match.rules();
        if (point == null && rules != null) {
            point = rules.respawnLocation(match, victim);
        }
        if (point == null) {
            rescue(victim);
            return;
        }
        victim.teleport(point);
        victim.setFallDistance(0.0F);
        core.messages().send(victim, "match.void-rescue");
    }

    /** Puts a player back on their feet when they fell out of a safe world. */
    private void rescue(Player player) {
        LobbyManager lobby = core.optional(LobbyManager.class);
        Location target = lobby == null || !lobby.hasSpawn() ? null : lobby.spawn();
        if (target == null && player.getWorld() != null) {
            target = player.getWorld().getSpawnLocation();
        }
        if (target != null) {
            player.teleport(target);
            player.setFallDistance(0.0F);
        }
    }

    private Match matchOf(Player player) {
        MatchManager matches = core.optional(MatchManager.class);
        return matches == null || player == null ? null : matches.getMatch(player.getUniqueId());
    }

    /** The player behind a damager, following projectiles back to their shooter. */
    static Player attackerOf(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            LivingEntity shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        if (damager instanceof org.bukkit.entity.TNTPrimed) {
            Entity source = ((org.bukkit.entity.TNTPrimed) damager).getSource();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }
}
