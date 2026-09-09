package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import gg.lightpractice.model.KnockbackProfile;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * One configurable behaviour of a kit.
 *
 * <p>Rules are the reason no kit behaviour is hardcoded into listeners: a listener receives an event,
 * asks the match's {@link KitRuleSet} what the kit allows and acts on the answer. Every hook has a
 * sensible default so a rule only implements what it actually changes.</p>
 */
public interface KitRule {

    /** Configuration identifier, for example {@code no-fall-damage}. */
    String id();

    /**
     * Reads the options of this rule from a kit.
     *
     * <p>Called once when the kit is loaded and again on every reload, so a rule must not keep state
     * that belongs to a running match in fields set here.</p>
     */
    default void load(KitRuleOptions options) {
    }

    /** One line explanation of what the rule does, shown in kit tooltips and the kit info command. */
    default String describe(KitRuleOptions options) {
        return id();
    }

    /** Called once when the match starts, before players are teleported. */
    default void onMatchStart(Match match) {
    }

    /** Called for every participant right after the kit was applied. */
    default void onPlayerEnter(Match match, Player player) {
    }

    /** Called from the central match ticker, at most once per second per match. */
    default void onTick(Match match) {
    }

    /** Called when the match finished, before players are restored. */
    default void onMatchEnd(Match match) {
    }

    default boolean allowFallDamage() {
        return true;
    }

    default boolean allowHunger() {
        return true;
    }

    default boolean allowNaturalRegeneration() {
        return true;
    }

    default boolean allowBuilding() {
        return false;
    }

    default boolean allowBreaking() {
        return false;
    }

    default boolean allowItemDrops() {
        return false;
    }

    default boolean allowRespawn() {
        return false;
    }

    /** Lives granted when respawning is allowed; {@code -1} means unlimited. */
    default int lives() {
        return -1;
    }

    default boolean allowProjectiles() {
        return true;
    }

    default boolean allowEnderPearls() {
        return true;
    }

    default boolean allowPotions() {
        return true;
    }

    default boolean allowGoldenApples() {
        return true;
    }

    /** Whether the kit allows hitting teammates. */
    default boolean allowFriendlyFire() {
        return false;
    }

    default boolean canPlace(Match match, Player player, Block block, ItemStack held) {
        return allowBuilding();
    }

    default boolean canBreak(Match match, Player player, Block block) {
        return allowBreaking();
    }

    default boolean canUseItem(Match match, Player player, ItemStack item) {
        return true;
    }

    /** Whether damage of this cause may be applied at all. */
    default boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        return true;
    }

    /** Last chance to scale damage, applied after every other rule. */
    default double modifyDamage(Match match, Player victim, Player attacker, double damage,
                                EntityDamageEvent.DamageCause cause) {
        return damage;
    }

    /**
     * Instant loss conditions such as touching the void in sumo or leaving the arena bounds in spleef.
     */
    default boolean instantLoss(Match match, Player player, EntityDamageEvent.DamageCause cause, Location location) {
        return false;
    }

    default void onPlayerEliminated(Match match, Player player) {
    }

    default void onPlayerRespawn(Match match, Player player) {
    }

    /** Custom knockback for this kit, {@code null} to use the global combat profile. */
    default KnockbackProfile knockback() {
        return null;
    }

    /** Called when a participant lands a melee hit, used by hit counting kits such as boxing. */
    default void onPlayerHit(Match match, Player attacker, Player victim) {
    }

    /** Called after a block inside the arena was broken by a participant. */
    default void onBlockBroken(Match match, Player player, Block block) {
    }

    /** Called after a block inside the arena was placed by a participant. */
    default void onBlockPlaced(Match match, Player player, Block block) {
    }

    /** Fishing rod pull strength, negative to use the global combat configuration. */
    default double rodPullStrength() {
        return -1.0D;
    }

    /** Whether explosions of this kit may destroy blocks. */
    default boolean allowExplosionBlockDamage() {
        return false;
    }

    /** Multiplier applied to explosion damage, {@code 1.0} keeps vanilla behaviour. */
    default double explosionDamageScale() {
        return 1.0D;
    }

    /** Cooldown between golden apples in milliseconds, {@code 0} disables the cooldown. */
    default long goldenAppleCooldownMillis() {
        return 0L;
    }

    /** Cooldown between ender pearls in milliseconds. */
    default long pearlCooldownMillis() {
        return 0L;
    }

    /** Damage applied when a pearl teleports, {@code 0} for none. */
    default double pearlDamage() {
        return 0.0D;
    }

    /** Whether drinking (as opposed to throwing) potions is allowed. */
    default boolean allowDrinkingPotions() {
        return allowPotions();
    }

    /** Whether splash potions are allowed. */
    default boolean allowSplashPotions() {
        return allowPotions();
    }

    /**
     * Lets a rule take over an item interaction, for example throwing a fireball.
     *
     * @return true when the rule handled the interaction and the vanilla behaviour must be cancelled
     */
    default boolean handleItemUse(Match match, Player player, ItemStack item,
                                  org.bukkit.event.block.Action action) {
        return false;
    }

    /** Whether this specific player may still respawn, for example while lives or a bed remain. */
    default boolean canRespawn(Match match, Player player) {
        return allowRespawn();
    }

    /** Where a respawn should place the player, null to use the team spawn. */
    default Location respawnLocation(Match match, Player player) {
        return null;
    }

    /** Called when a participant dies, before the match decides whether they are eliminated. */
    default void onDeath(Match match, Player player) {
    }

    /** Whether the vanilla death screen may appear. */
    default boolean allowDeathScreen() {
        return true;
    }

    /** Extra items handed out on respawn, empty by default. */
    default ItemStack[] respawnItems(Match match, Player player) {
        return new ItemStack[0];
    }
}
