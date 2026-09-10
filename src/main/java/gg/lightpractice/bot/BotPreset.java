package gg.lightpractice.bot;

import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Configured behaviour of one kind of practice bot.
 *
 * <p>Everything the artificial player does is described here: how fast it reacts, how often it lands a
 * hit, how hard it hits, how far it keeps away and which items it uses. Values that are left out fall
 * back to the defaults of the chosen {@link BotDifficulty}, so a preset can be one line long.</p>
 */
public final class BotPreset {

    private final String id;
    private String displayName;
    private List<String> description = new ArrayList<String>();
    private String kitId;
    private String namePattern = "{name}";
    private String skinName = "";
    private BotDifficulty difficulty = BotDifficulty.NORMAL;
    private double health = 20.0D;
    private double attackRange = 3.2D;
    private double preferredRange = 1.6D;
    private long reactionMillis;
    private double accuracy;
    private double aggression;
    private double minDamage;
    private double maxDamage;
    private boolean strafe = true;
    private boolean usePotions;
    private boolean usePearls;
    private boolean useRods;
    private boolean useApples;
    private String potionEffect = "SLOWNESS";
    private int potionDurationSeconds = 5;
    private int potionAmplifier = 0;
    private double potionRange = 8.0D;
    private long potionCooldownMillis = 12000L;
    private double potionChance = 0.25D;
    private double healThreshold = 8.0D;
    private double healAmount = 4.0D;
    private long healCooldownMillis = 15000L;
    private Material icon = Material.IRON_SWORD;
    private short iconData;
    private int order;
    private boolean enabled = true;
    private String permission = "";

    public BotPreset(String id) {
        this.id = id == null ? "bot" : id.trim().toLowerCase(Locale.ROOT);
        this.displayName = Text.capitalize(this.id);
        applyDifficulty(difficulty);
    }

    /** Copies the reaction, accuracy and damage defaults of a difficulty into this preset. */
    public void applyDifficulty(BotDifficulty difficulty) {
        this.difficulty = difficulty == null ? BotDifficulty.NORMAL : difficulty;
        this.reactionMillis = this.difficulty.reactionMillis();
        this.accuracy = this.difficulty.accuracy();
        this.aggression = this.difficulty.aggression();
        this.minDamage = this.difficulty.minDamage();
        this.maxDamage = this.difficulty.maxDamage();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = Text.color(displayName == null || displayName.isEmpty() ? id : displayName);
    }

    public List<String> description() {
        return description;
    }

    public void description(List<String> description) {
        this.description = Text.color(description == null ? new ArrayList<String>() : description);
    }

    public String kitId() {
        return kitId;
    }

    public void kitId(String kitId) {
        this.kitId = kitId == null ? "" : kitId.trim().toLowerCase(Locale.ROOT);
    }

    /** Name of the spawned bot, {@code {name}} is replaced with the owner and {@code {preset}} with the id. */
    public String namePattern() {
        return namePattern;
    }

    public void namePattern(String namePattern) {
        this.namePattern = namePattern == null || namePattern.isEmpty() ? "{name}" : namePattern;
    }

    public String skinName() {
        return skinName;
    }

    public void skinName(String skinName) {
        this.skinName = skinName == null ? "" : skinName.trim();
    }

    public BotDifficulty difficulty() {
        return difficulty;
    }

    public void difficulty(BotDifficulty difficulty) {
        applyDifficulty(difficulty);
    }

    public double health() {
        return health;
    }

    public void health(double health) {
        this.health = Math.max(1.0D, health);
    }

    public double attackRange() {
        return attackRange;
    }

    public void attackRange(double attackRange) {
        this.attackRange = Math.max(1.0D, Math.min(8.0D, attackRange));
    }

    public double preferredRange() {
        return preferredRange;
    }

    public void preferredRange(double preferredRange) {
        this.preferredRange = Math.max(0.5D, Math.min(attackRange, preferredRange));
    }

    public long reactionMillis() {
        return reactionMillis;
    }

    public void reactionMillis(long reactionMillis) {
        this.reactionMillis = Math.max(20L, reactionMillis);
    }

    public double accuracy() {
        return accuracy;
    }

    public void accuracy(double accuracy) {
        this.accuracy = Math.max(0.0D, Math.min(1.0D, accuracy));
    }

    public double aggression() {
        return aggression;
    }

    public void aggression(double aggression) {
        this.aggression = Math.max(0.0D, Math.min(1.0D, aggression));
    }

    public double minDamage() {
        return minDamage;
    }

    public double maxDamage() {
        return maxDamage;
    }

    public void damage(double minDamage, double maxDamage) {
        this.minDamage = Math.max(0.0D, minDamage);
        this.maxDamage = Math.max(this.minDamage, maxDamage);
    }

    /** One randomised hit, between the configured minimum and maximum. */
    public double rollDamage(java.util.Random random) {
        double spread = maxDamage - minDamage;
        return spread <= 0.0D ? maxDamage : minDamage + random.nextDouble() * spread;
    }

    public boolean strafe() {
        return strafe;
    }

    public void strafe(boolean strafe) {
        this.strafe = strafe;
    }

    public boolean usePotions() {
        return usePotions;
    }

    public void usePotions(boolean usePotions) {
        this.usePotions = usePotions;
    }

    public boolean usePearls() {
        return usePearls;
    }

    public void usePearls(boolean usePearls) {
        this.usePearls = usePearls;
    }

    public boolean useRods() {
        return useRods;
    }

    public void useRods(boolean useRods) {
        this.useRods = useRods;
    }

    public boolean useApples() {
        return useApples;
    }

    public void useApples(boolean useApples) {
        this.useApples = useApples;
    }

    public String potionEffect() {
        return potionEffect;
    }

    public void potionEffect(String potionEffect) {
        this.potionEffect = potionEffect == null || potionEffect.isEmpty() ? "SLOWNESS" : potionEffect.trim();
    }

    public int potionDurationSeconds() {
        return potionDurationSeconds;
    }

    public int potionAmplifier() {
        return potionAmplifier;
    }

    public void potion(String effect, int durationSeconds, int amplifier) {
        potionEffect(effect);
        this.potionDurationSeconds = Math.max(1, durationSeconds);
        this.potionAmplifier = Math.max(0, Math.min(4, amplifier));
    }

    public double potionRange() {
        return potionRange;
    }

    public void potionRange(double potionRange) {
        this.potionRange = Math.max(1.0D, Math.min(32.0D, potionRange));
    }

    public long potionCooldownMillis() {
        return potionCooldownMillis;
    }

    public void potionCooldownMillis(long potionCooldownMillis) {
        this.potionCooldownMillis = Math.max(1000L, potionCooldownMillis);
    }

    public double potionChance() {
        return potionChance;
    }

    public void potionChance(double potionChance) {
        this.potionChance = Math.max(0.0D, Math.min(1.0D, potionChance));
    }

    public double healThreshold() {
        return healThreshold;
    }

    public void healThreshold(double healThreshold) {
        this.healThreshold = Math.max(0.0D, healThreshold);
    }

    public double healAmount() {
        return healAmount;
    }

    public void healAmount(double healAmount) {
        this.healAmount = Math.max(0.0D, healAmount);
    }

    public long healCooldownMillis() {
        return healCooldownMillis;
    }

    public void healCooldownMillis(long healCooldownMillis) {
        this.healCooldownMillis = Math.max(1000L, healCooldownMillis);
    }

    public Material icon() {
        return icon;
    }

    public short iconData() {
        return iconData;
    }

    public void icon(Material material, short data) {
        this.icon = material == null ? Material.IRON_SWORD : material;
        this.iconData = data;
    }

    public int order() {
        return order;
    }

    public void order(int order) {
        this.order = order;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String permission() {
        return permission;
    }

    public void permission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public boolean allowed(Permissible permissible) {
        return enabled && (permission.isEmpty()
                || (permissible != null && permissible.hasPermission(permission)));
    }

    public ItemStack iconItem() {
        List<String> lore = new ArrayList<String>(description);
        if (lore.isEmpty()) {
            lore.add("&7Difficulty: &f" + difficulty.displayName());
            lore.add("&7Health: &f" + health);
            lore.add("&7Accuracy: &f" + (int) Math.round(accuracy * 100.0D) + "%");
        }
        return Items.item(icon, 1, iconData, displayName, lore);
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("display-name", displayName);
        section.set("description", description);
        section.set("kit", kitId);
        section.set("name", namePattern);
        section.set("skin", skinName);
        section.set("difficulty", difficulty.name());
        section.set("health", health);
        section.set("attack-range", attackRange);
        section.set("preferred-range", preferredRange);
        section.set("reaction-millis", reactionMillis);
        section.set("accuracy", accuracy);
        section.set("aggression", aggression);
        section.set("min-damage", minDamage);
        section.set("max-damage", maxDamage);
        section.set("strafe", strafe);
        section.set("use-potions", usePotions);
        section.set("use-pearls", usePearls);
        section.set("use-rods", useRods);
        section.set("use-apples", useApples);
        section.set("potion.effect", potionEffect);
        section.set("potion.duration-seconds", potionDurationSeconds);
        section.set("potion.amplifier", potionAmplifier);
        section.set("potion.range", potionRange);
        section.set("potion.cooldown-millis", potionCooldownMillis);
        section.set("potion.chance", potionChance);
        section.set("heal.threshold", healThreshold);
        section.set("heal.amount", healAmount);
        section.set("heal.cooldown-millis", healCooldownMillis);
        section.set("icon.material", icon.name());
        section.set("icon.data", (int) iconData);
        section.set("order", order);
        section.set("enabled", enabled);
        section.set("permission", permission);
    }

    public static BotPreset read(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        BotPreset preset = new BotPreset(id);
        BotDifficulty difficulty = BotDifficulty.parse(section.getString("difficulty", "NORMAL"));
        preset.applyDifficulty(difficulty == null ? BotDifficulty.NORMAL : difficulty);
        preset.displayName(section.getString("display-name", Text.capitalize(id)));
        preset.description(section.getStringList("description"));
        preset.kitId(section.getString("kit", ""));
        preset.namePattern(section.getString("name", "{name}"));
        preset.skinName(section.getString("skin", ""));
        preset.health(section.getDouble("health", 20.0D));
        preset.attackRange(section.getDouble("attack-range", 3.2D));
        preset.preferredRange(section.getDouble("preferred-range", 1.6D));
        preset.reactionMillis(section.getLong("reaction-millis", preset.reactionMillis()));
        preset.accuracy(section.getDouble("accuracy", preset.accuracy()));
        preset.aggression(section.getDouble("aggression", preset.aggression()));
        preset.damage(section.getDouble("min-damage", preset.minDamage()),
                section.getDouble("max-damage", preset.maxDamage()));
        preset.strafe(section.getBoolean("strafe", true));
        preset.usePotions(section.getBoolean("use-potions", false));
        preset.usePearls(section.getBoolean("use-pearls", false));
        preset.useRods(section.getBoolean("use-rods", false));
        preset.useApples(section.getBoolean("use-apples", false));
        preset.potion(section.getString("potion.effect", "SLOWNESS"),
                section.getInt("potion.duration-seconds", 5),
                section.getInt("potion.amplifier", 0));
        preset.potionRange(section.getDouble("potion.range", 8.0D));
        preset.potionCooldownMillis(section.getLong("potion.cooldown-millis", 12000L));
        preset.potionChance(section.getDouble("potion.chance", 0.25D));
        preset.healThreshold(section.getDouble("heal.threshold", 8.0D));
        preset.healAmount(section.getDouble("heal.amount", 4.0D));
        preset.healCooldownMillis(section.getLong("heal.cooldown-millis", 15000L));
        preset.icon(Items.material(section.getString("icon.material", "IRON_SWORD"), Material.IRON_SWORD),
                (short) section.getInt("icon.data", 0));
        preset.order(section.getInt("order", 0));
        preset.enabled(section.getBoolean("enabled", true));
        preset.permission(section.getString("permission", ""));
        return preset;
    }

    /** Line describing the preset for the bot menu and tab completion. */
    public String describe() {
        return displayName + " &7(" + difficulty.displayName() + "&7, &f" + kitId + "&7)";
    }

    public List<String> lore() {
        return Collections.unmodifiableList(description);
    }

    @Override
    public String toString() {
        return "BotPreset{" + id + ", kit=" + kitId + ", difficulty=" + difficulty + '}';
    }
}
