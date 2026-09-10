package gg.lightpractice.combat;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.model.KnockbackProfile;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Cooldowns;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Ping;
import gg.lightpractice.util.Visuals;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything that happens around a hit: combat tagging, kit knockback, combos, rod pulls and the item
 * cooldowns that kit rules define.
 *
 * <p>Listeners stay thin: they hand the event to this manager and apply the answer. All numbers come from
 * {@code combat.yml} or from the kit's rule set, so tuning never means editing listeners.</p>
 */
public final class CombatManager implements LightService {

    private final PluginCore core;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, UUID> taggedBy = new ConcurrentHashMap<UUID, UUID>();
    private final Cooldowns<UUID> rodCooldowns = new Cooldowns<UUID>();
    private final Cooldowns<UUID> pearlCooldowns = new Cooldowns<UUID>();
    private final Cooldowns<UUID> appleCooldowns = new Cooldowns<UUID>();
    private KnockbackProfile defaultProfile = KnockbackProfile.DEFAULT;
    private boolean reapplyKnockback = true;
    private long combatTagMillis = 10000L;
    private long comboWindowMillis = 2000L;
    private double rodPullStrength = 0.75D;
    private boolean rodDamage;
    private long rodCooldownMillis = 500L;
    private long pearlCooldownMillis = 10000L;
    private double pearlDamage;
    private long appleCooldownMillis;
    private String hitSound = "";
    private String rodSound = "";
    private List<String> blockedCommands = new ArrayList<String>();
    private boolean punishCombatLog = true;

    public CombatManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "combat";
    }

    @Override
    public int startupOrder() {
        return 72;
    }

    @Override
    public void onLoad() {
        load(core.configs().combat());
    }

    @Override
    public void onEnable() {
        load(core.configs().combat());
    }

    @Override
    public void onDisable() {
        taggedUntil.clear();
        taggedBy.clear();
        rodCooldowns.clear();
        pearlCooldowns.clear();
        appleCooldowns.clear();
    }

    @Override
    public void onReload() {
        load(core.configs().combat());
    }

    public void load(ConfigFile file) {
        if (file == null) {
            return;
        }
        this.defaultProfile = new KnockbackProfile(file.getString("knockback.name", "default"),
                file.getDouble("knockback.horizontal", 0.40D),
                file.getDouble("knockback.vertical", 0.36D),
                file.getDouble("knockback.sprint-horizontal", 0.40D),
                file.getDouble("knockback.sprint-vertical", 0.40D),
                file.getDouble("knockback.air-multiplier", 1.0D),
                file.getDouble("knockback.max-vertical", 0.45D),
                file.getDouble("knockback.resistance", 0.0D));
        this.reapplyKnockback = file.getBoolean("knockback.reapply-next-tick", true);
        this.combatTagMillis = Math.max(0L, file.getLong("combat-tag.seconds", 10L) * 1000L);
        this.punishCombatLog = file.getBoolean("combat-tag.punish-combat-log", true);
        this.comboWindowMillis = Math.max(0L, file.getLong("combo.window-millis", 2000L));
        this.rodPullStrength = Math.max(0.0D, file.getDouble("rod.pull-strength", 0.75D));
        this.rodDamage = file.getBoolean("rod.damage", false);
        this.rodCooldownMillis = Math.max(0L, file.getLong("rod.cooldown-millis", 500L));
        this.pearlCooldownMillis = Math.max(0L, file.getLong("pearls.cooldown-millis", 10000L));
        this.pearlDamage = Math.max(0.0D, file.getDouble("pearls.damage", 0.0D));
        this.appleCooldownMillis = Math.max(0L, file.getLong("golden-apples.cooldown-millis", 0L));
        this.hitSound = file.getString("sounds.hit", "");
        this.rodSound = file.getString("sounds.rod", "");
        this.blockedCommands = new ArrayList<String>();
        for (String command : file.getStringList("combat-tag.blocked-commands")) {
            if (command != null && !command.trim().isEmpty()) {
                blockedCommands.add(command.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Debug.log(DebugCategory.COMBAT, "Combat tuned: tag {}s, combo window {}ms, rod {}, knockback {}",
                combatTagMillis / 1000L, comboWindowMillis, rodPullStrength, defaultProfile.name());
    }

    public KnockbackProfile defaultProfile() {
        return defaultProfile;
    }

    public long combatTagMillis() {
        return combatTagMillis;
    }

    public long comboWindowMillis() {
        return comboWindowMillis;
    }

    public boolean punishCombatLog() {
        return punishCombatLog;
    }

    /** Knockback profile of a match: the kit rule wins, the global profile is the fallback. */
    public KnockbackProfile profile(Match match) {
        KitRuleSet rules = match == null ? null : match.rules();
        KnockbackProfile profile = rules == null ? null : rules.knockback();
        return profile == null ? defaultProfile : profile;
    }

    // ------------------------------------------------------------------- damage

    /**
     * Decides whether a hit inside a match is allowed and how much damage it deals.
     *
     * <p>Damage the plugin applied itself (pearls, event damage) is always let through so rules cannot
     * cancel it. Friendly fire, disabled damage and explosion scaling all come from the rule set.</p>
     */
    public DamageVerdict verdict(Match match, Player victim, Player attacker,
                                 EntityDamageEvent.DamageCause cause, double damage) {
        if (match == null || victim == null) {
            return DamageVerdict.allow(damage);
        }
        MatchPlayer participant = match.participant(victim);
        if (participant == null) {
            return DamageVerdict.allow(damage);
        }
        if (participant.eliminated()) {
            return DamageVerdict.deny("eliminated");
        }
        if (match.consumeInternalDamage(victim)) {
            return DamageVerdict.allow(damage, "internal");
        }
        if (!match.isRunning()) {
            return DamageVerdict.deny("not-running");
        }
        KitRuleSet rules = match.rules();
        double value = damage;
        if (rules != null) {
            if (!rules.allowDamage(match, victim, cause)) {
                return DamageVerdict.deny("rule");
            }
            value = rules.modifyDamage(match, victim, attacker, value, cause);
            if (value <= 0.0D) {
                return DamageVerdict.deny("modified");
            }
        }
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            double scale = rules == null ? 1.0D : rules.explosionDamageScale();
            value *= Math.max(0.0D, scale);
            if (value <= 0.0D) {
                return DamageVerdict.deny("explosion-scale");
            }
        }
        if (attacker != null && !attacker.equals(victim)) {
            MatchTeam own = match.teamOf(attacker);
            boolean friendly = own != null && own.contains(victim);
            if (friendly && (rules == null || !rules.allowFriendlyFire())) {
                return DamageVerdict.deny("friendly-fire");
            }
        }
        return DamageVerdict.allow(value);
    }

    /** Applies kit knockback and records the hit on both participants. */
    public void registerHit(Match match, Player attacker, Player victim, double damage) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return;
        }
        tag(victim.getUniqueId(), attacker.getUniqueId());
        tag(attacker.getUniqueId(), victim.getUniqueId());
        MatchPlayer attackerParticipant = match == null ? null : match.participant(attacker);
        if (attackerParticipant != null) {
            attackerParticipant.addDamage(damage);
            attackerParticipant.registerHit(comboWindowMillis);
            attackerParticipant.lastAttacker(victim.getUniqueId());
        }
        MatchPlayer victimParticipant = match == null ? null : match.participant(victim);
        if (victimParticipant != null) {
            victimParticipant.lastAttacker(attacker.getUniqueId());
            applyKnockback(victim, attacker, profile(match));
        }
        if (hitSound != null && !hitSound.isEmpty()) {
            Visuals.sound(attacker, hitSound, 1.0F, 1.0F);
        }
    }

    /** Applies a knockback profile to a victim, optionally reapplying it one tick later. */
    public void applyKnockback(Player victim, Player attacker, KnockbackProfile profile) {
        if (victim == null || attacker == null) {
            return;
        }
        applyKnockback(victim, attacker.getLocation(), attacker.isSprinting(), profile);
    }

    /**
     * Applies knockback away from a fixed point.
     *
     * <p>Used when the attacker is not a player, for example a practice bot whose entity position drives
     * the direction but which has no sprinting state of its own.</p>
     */
    public void applyKnockback(final Player victim, Location source, boolean sprinting,
                               KnockbackProfile profile) {
        if (victim == null || source == null) {
            return;
        }
        KnockbackProfile used = profile == null ? defaultProfile : profile;
        final Vector velocity = calculate(victim, source, sprinting, used);
        if (velocity == null) {
            return;
        }
        victim.setVelocity(velocity);
        if (reapplyKnockback) {
            core.tasks().syncLater(new Runnable() {
                @Override
                public void run() {
                    if (victim.isOnline() && !victim.isDead()) {
                        victim.setVelocity(velocity);
                    }
                }
            }, 1L);
        }
    }

    private Vector calculate(Player victim, Location from, boolean sprinting, KnockbackProfile profile) {
        Location to = victim.getLocation();
        if (from == null || to == null) {
            return null;
        }
        Vector direction = to.toVector().subtract(from.toVector()).setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            Vector look = from.getDirection().setY(0.0D);
            if (look.lengthSquared() < 0.0001D) {
                return null;
            }
            direction = look;
        }
        boolean airborne = !victim.isOnGround();
        return profile.calculate(direction.normalize(), sprinting, airborne);
    }

    // --------------------------------------------------------------- combat tag

    /** Marks both sides of a fight as being in combat. */
    public void tag(UUID uuid, UUID attacker) {
        if (uuid == null || combatTagMillis <= 0L) {
            return;
        }
        taggedUntil.put(uuid, Long.valueOf(System.currentTimeMillis() + combatTagMillis));
        if (attacker != null) {
            taggedBy.put(uuid, attacker);
        }
    }

    public boolean isTagged(UUID uuid) {
        Long until = uuid == null ? null : taggedUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until.longValue() <= System.currentTimeMillis()) {
            taggedUntil.remove(uuid);
            taggedBy.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingTag(UUID uuid) {
        Long until = uuid == null ? null : taggedUntil.get(uuid);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until.longValue() - System.currentTimeMillis());
    }

    /** Player that tagged someone, used to credit a kill after a combat log. */
    public UUID taggedBy(UUID uuid) {
        return uuid == null ? null : taggedBy.get(uuid);
    }

    public void clearTag(UUID uuid) {
        if (uuid == null) {
            return;
        }
        taggedUntil.remove(uuid);
        taggedBy.remove(uuid);
    }

    /** Drops every expired tag, run by the central housekeeping task. */
    public void housekeep() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, Long> entry : taggedUntil.entrySet()) {
            Long until = entry.getValue();
            if (until == null || until.longValue() <= now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            taggedUntil.remove(uuid);
            taggedBy.remove(uuid);
        }
        rodCooldowns.purge();
        pearlCooldowns.purge();
        appleCooldowns.purge();
    }

    /** Whether a command may be used while tagged, checked by the command listener. */
    public boolean commandAllowed(String command) {
        if (command == null || blockedCommands.isEmpty()) {
            return true;
        }
        String name = command.trim().toLowerCase(java.util.Locale.ROOT);
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        int space = name.indexOf(' ');
        if (space > 0) {
            name = name.substring(0, space);
        }
        for (String blocked : blockedCommands) {
            if (blocked.equals(name)) {
                return false;
            }
        }
        return true;
    }

    public List<String> blockedCommands() {
        return Collections.unmodifiableList(blockedCommands);
    }

    // ---------------------------------------------------------------------- rod

    /** Pulls a victim towards the rod user, using the kit rule strength when one is set. */
    public boolean handleRod(Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return false;
        }
        Match match = matchOf(attacker);
        if (match != null && !match.isRunning()) {
            return false;
        }
        if (!rodCooldowns.tryUse(attacker.getUniqueId(), rodCooldownMillis)) {
            return false;
        }
        double strength = rodPullStrength;
        KitRuleSet rules = match == null ? null : match.rules();
        if (rules != null) {
            double kitStrength = rules.rodPullStrength();
            if (kitStrength >= 0.0D) {
                strength = kitStrength;
            }
        }
        Vector pull = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0.0D);
        if (pull.lengthSquared() < 0.0001D || strength <= 0.0D) {
            return true;
        }
        pull.normalize().multiply(strength).setY(Math.max(0.1D, strength * 0.2D));
        victim.setVelocity(victim.getVelocity().setX(pull.getX()).setY(pull.getY()).setZ(pull.getZ()));
        victim.setFallDistance(0.0F);
        tag(victim.getUniqueId(), attacker.getUniqueId());
        tag(attacker.getUniqueId(), victim.getUniqueId());
        MatchPlayer participant = match == null ? null : match.participant(attacker);
        if (participant != null) {
            participant.registerHit(comboWindowMillis);
        }
        if (rodDamage && match != null) {
            match.allowNextDamage(victim);
            victim.damage(1.0D, attacker);
        }
        if (rodSound != null && !rodSound.isEmpty()) {
            Visuals.sound(attacker, rodSound, 1.0F, 1.0F);
        }
        Debug.log(DebugCategory.COMBAT, "{} pulled {} with the rod (strength {})", attacker.getName(),
                victim.getName(), strength);
        return true;
    }

    public double rodPullStrength() {
        return rodPullStrength;
    }

    // ------------------------------------------------------------------- pearls

    /** Whether a pearl may be thrown right now; the kit rule cooldown wins over the global one. */
    public boolean pearlReady(Player player, Match match) {
        if (player == null) {
            return false;
        }
        KitRuleSet rules = match == null ? null : match.rules();
        if (rules != null && !rules.allowEnderPearls()) {
            return false;
        }
        long cooldown = pearlCooldownMillis;
        if (rules != null && rules.pearlCooldownMillis() > 0L) {
            cooldown = rules.pearlCooldownMillis();
        }
        if (cooldown <= 0L) {
            return true;
        }
        return pearlCooldowns.ready(player.getUniqueId());
    }

    /** Starts the pearl cooldown and returns it in milliseconds. */
    public long usePearl(Player player, Match match) {
        if (player == null) {
            return 0L;
        }
        KitRuleSet rules = match == null ? null : match.rules();
        long cooldown = pearlCooldownMillis;
        if (rules != null && rules.pearlCooldownMillis() > 0L) {
            cooldown = rules.pearlCooldownMillis();
        }
        pearlCooldowns.mark(player.getUniqueId(), cooldown);
        return cooldown;
    }

    /** Damage applied when a pearl lands, zero when the kit or the global config disables it. */
    public double pearlDamage(Match match) {
        KitRuleSet rules = match == null ? null : match.rules();
        double damage = pearlDamage;
        if (rules != null && rules.pearlDamage() > 0.0D) {
            damage = rules.pearlDamage();
        }
        return damage;
    }

    public long pearlRemaining(Player player) {
        return player == null ? 0L : pearlCooldowns.remaining(player.getUniqueId());
    }

    // ------------------------------------------------------------ golden apples

    /** Whether a golden apple may be eaten, honouring the kit rule cooldown. */
    public boolean appleReady(Player player, Match match) {
        if (player == null) {
            return false;
        }
        KitRuleSet rules = match == null ? null : match.rules();
        if (rules != null && !rules.allowGoldenApples()) {
            return false;
        }
        long cooldown = appleCooldownMillis;
        if (rules != null && rules.goldenAppleCooldownMillis() > 0L) {
            cooldown = rules.goldenAppleCooldownMillis();
        }
        if (cooldown <= 0L) {
            return true;
        }
        return appleCooldowns.ready(player.getUniqueId());
    }

    /** Starts the golden apple cooldown, returning its length in milliseconds. */
    public long useApple(Player player, Match match) {
        if (player == null) {
            return 0L;
        }
        KitRuleSet rules = match == null ? null : match.rules();
        long cooldown = appleCooldownMillis;
        if (rules != null && rules.goldenAppleCooldownMillis() > 0L) {
            cooldown = rules.goldenAppleCooldownMillis();
        }
        appleCooldowns.mark(player.getUniqueId(), cooldown);
        return cooldown;
    }

    public long appleRemaining(Player player) {
        return player == null ? 0L : appleCooldowns.remaining(player.getUniqueId());
    }

    // ------------------------------------------------------------------ potions

    /** Whether a potion may be drunk or thrown in this match. */
    public boolean potionAllowed(Player player, Match match, boolean splash) {
        KitRuleSet rules = match == null ? null : match.rules();
        if (rules == null) {
            return true;
        }
        if (!rules.allowPotions()) {
            return false;
        }
        return splash ? rules.allowSplashPotions() : rules.allowDrinkingPotions();
    }

    // ------------------------------------------------------------------ helpers

    public Match matchOf(Player player) {
        if (player == null) {
            return null;
        }
        gg.lightpractice.api.MatchService matches = core.optional(gg.lightpractice.api.MatchService.class);
        return matches == null ? null : matches.getMatch(player.getUniqueId());
    }

    /** Clears every combat related state of a player, used on death, match end and quit. */
    public void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        clearTag(uuid);
        rodCooldowns.remove(uuid);
        pearlCooldowns.remove(uuid);
        appleCooldowns.remove(uuid);
    }

    /** Whether a held item counts as a weapon, used by hit detection and statistics. */
    public static boolean isWeapon(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = item.getType();
        return material == Material.DIAMOND_SWORD || material == Material.IRON_SWORD
                || material == Material.GOLD_SWORD || material == Material.STONE_SWORD
                || material == Material.WOOD_SWORD || material == Material.DIAMOND_AXE
                || material == Material.IRON_AXE || material == Material.GOLD_AXE
                || material == Material.STONE_AXE || material == Material.WOOD_AXE;
    }

    /** Ping based sanity check used by the anti cheat hooks of the combat listener. */
    public int pingOf(Player player) {
        return player == null ? -1 : Ping.of(player);
    }

    public int taggedCount() {
        return taggedUntil.size();
    }
}
