package gg.lightpractice.bot;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * One running practice bot.
 *
 * <p>The bot is a real entity in the world (created through the Citizens bridge) with its own health,
 * position and cooldowns. It takes part in a normal match through a synthetic uuid, which is why the
 * match engine can pair it, eliminate it and end the fight when it dies.</p>
 */
public final class PracticeBot {

    private final String id;
    private final BotPreset preset;
    private final UUID owner;
    private final String ownerName;
    private final UUID uuid;
    private final String name;
    private final long createdAt;
    private Object npc;
    private Entity entity;
    private BotState state = BotState.SPAWNING;
    private String matchId;
    private double health;
    private double maxHealth;
    private UUID target;
    private Location location;
    private long lastAttackAt;
    private long lastPotionAt;
    private long lastHealAt;
    private long lastPearlAt;
    private long lastRodAt;
    private long lastStrafeAt;
    private long lastDamageAt;
    private int strafeDirection = 1;
    private int combo;
    private long damageDealt;
    private long damageTaken;
    private int hitsLanded;
    private int hitsMissed;

    public PracticeBot(String id, BotPreset preset, UUID owner, String ownerName, UUID uuid, String name) {
        this.id = id == null ? UUID.randomUUID().toString().substring(0, 8) : id;
        this.preset = preset;
        this.owner = owner;
        this.ownerName = ownerName == null ? "unknown" : ownerName;
        this.uuid = uuid;
        this.name = name == null ? "Bot" : name;
        this.createdAt = System.currentTimeMillis();
        this.maxHealth = preset == null ? 20.0D : preset.health();
        this.health = maxHealth;
    }

    public String id() {
        return id;
    }

    public BotPreset preset() {
        return preset;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Synthetic uuid the bot fights under inside a match. */
    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public long ageMillis() {
        return System.currentTimeMillis() - createdAt;
    }

    public BotState state() {
        return state;
    }

    public void state(BotState state) {
        this.state = state == null ? BotState.REMOVED : state;
    }

    public boolean isAlive() {
        return state.alive() && health > 0.0D;
    }

    public String matchId() {
        return matchId;
    }

    public void matchId(String matchId) {
        this.matchId = matchId;
    }

    public Object npc() {
        return npc;
    }

    public void npc(Object npc) {
        this.npc = npc;
    }

    public Entity entity() {
        return entity;
    }

    public void entity(Entity entity) {
        this.entity = entity;
        if (entity != null) {
            this.location = entity.getLocation();
        }
    }

    public boolean hasBody() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public void health(double health) {
        this.health = Math.max(0.0D, Math.min(maxHealth, health));
    }

    /** Applies damage and reports whether the bot died from it. */
    public boolean damage(double amount) {
        if (!isAlive()) {
            return false;
        }
        this.health = Math.max(0.0D, this.health - Math.max(0.0D, amount));
        this.damageTaken += (long) Math.ceil(Math.max(0.0D, amount));
        this.lastDamageAt = System.currentTimeMillis();
        this.combo = 0;
        if (this.health <= 0.0D) {
            this.state = BotState.DEAD;
            return true;
        }
        return false;
    }

    public void heal(double amount) {
        if (!isAlive()) {
            return;
        }
        this.health = Math.min(maxHealth, this.health + Math.max(0.0D, amount));
    }

    public UUID target() {
        return target;
    }

    public void target(UUID target) {
        this.target = target;
    }

    public Location location() {
        if (entity != null) {
            Location current = entity.getLocation();
            if (current != null) {
                this.location = current;
            }
        }
        return location;
    }

    public void location(Location location) {
        this.location = location;
    }

    // ------------------------------------------------------------------ cooldowns

    public long lastAttackAt() {
        return lastAttackAt;
    }

    public boolean attackReady() {
        return preset != null && System.currentTimeMillis() - lastAttackAt >= preset.reactionMillis();
    }

    public void attacked() {
        this.lastAttackAt = System.currentTimeMillis();
        this.combo++;
        this.hitsLanded++;
    }

    public void missed() {
        this.hitsMissed++;
    }

    public boolean potionReady() {
        return preset != null && preset.usePotions()
                && System.currentTimeMillis() - lastPotionAt >= preset.potionCooldownMillis();
    }

    public void usedPotion() {
        this.lastPotionAt = System.currentTimeMillis();
    }

    public boolean healReady() {
        return preset != null && preset.useApples() && preset.healAmount() > 0.0D
                && health < preset.healThreshold()
                && System.currentTimeMillis() - lastHealAt >= preset.healCooldownMillis();
    }

    public void healed() {
        this.lastHealAt = System.currentTimeMillis();
    }

    public boolean pearlReady() {
        return preset != null && preset.usePearls()
                && System.currentTimeMillis() - lastPearlAt >= 8000L;
    }

    public void usedPearl() {
        this.lastPearlAt = System.currentTimeMillis();
    }

    public boolean rodReady() {
        return preset != null && preset.useRods()
                && System.currentTimeMillis() - lastRodAt >= 6000L;
    }

    public void usedRod() {
        this.lastRodAt = System.currentTimeMillis();
    }

    public long lastStrafeAt() {
        return lastStrafeAt;
    }

    public void strafed() {
        this.lastStrafeAt = System.currentTimeMillis();
    }

    /** Flips the strafe direction so the bot circles its target instead of standing still. */
    public int nextStrafeDirection() {
        strafeDirection = -strafeDirection;
        return strafeDirection;
    }

    public int strafeDirection() {
        return strafeDirection;
    }

    public long lastDamageAt() {
        return lastDamageAt;
    }

    // ------------------------------------------------------------------ statistics

    public int combo() {
        return combo;
    }

    public long damageDealt() {
        return damageDealt;
    }

    public void dealtDamage(double amount) {
        this.damageDealt += (long) Math.ceil(Math.max(0.0D, amount));
    }

    public long damageTaken() {
        return damageTaken;
    }

    public int hitsLanded() {
        return hitsLanded;
    }

    public int hitsMissed() {
        return hitsMissed;
    }

    /** Hit accuracy of this bot, used by the debug command. */
    public int accuracyPercent() {
        int total = hitsLanded + hitsMissed;
        return total == 0 ? 0 : (int) Math.round(hitsLanded * 100.0D / total);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PracticeBot && ((PracticeBot) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PracticeBot{" + id + ", preset=" + (preset == null ? "?" : preset.id()) + ", owner=" + ownerName
                + ", state=" + state + ", health=" + health + '/' + maxHealth + '}';
    }
}
