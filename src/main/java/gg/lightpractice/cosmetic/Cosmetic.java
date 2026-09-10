package gg.lightpractice.cosmetic;

import gg.lightpractice.model.CosmeticType;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One purchasable cosmetic.
 *
 * <p>A cosmetic is data: a type, a cost, an icon and the effect, sound or message it plays. Playback goes
 * through {@link gg.lightpractice.util.Visuals} so every effect and sound name is resolved against the
 * 1.8 enums and unknown names are reported instead of throwing.</p>
 */
public final class Cosmetic {

    private final String id;
    private final CosmeticType type;
    private String displayName;
    private List<String> description = new ArrayList<String>();
    private long cost;
    private double vaultCost;
    private Material icon = Material.PAPER;
    private short iconData;
    private int order;
    private boolean enabled = true;
    private boolean free;
    private String permission = "";
    private String effect = "";
    private String sound = "";
    private float volume = 1.0F;
    private float pitch = 1.0F;
    private int particles = 20;
    private double offsetX = 0.35D;
    private double offsetY = 0.35D;
    private double offsetZ = 0.35D;
    private double speed = 0.05D;
    private double radius = 32.0D;
    private int dataId;
    private long intervalMillis = 100L;
    private String message = "";

    public Cosmetic(String id, CosmeticType type) {
        this.id = id == null ? "cosmetic" : id.trim().toLowerCase(Locale.ROOT);
        this.type = type == null ? CosmeticType.KILL_EFFECT : type;
        this.displayName = Text.capitalize(this.id);
    }

    public String id() {
        return id;
    }

    public CosmeticType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = Text.color(displayName == null ? id : displayName);
    }

    public List<String> description() {
        return description;
    }

    public void description(List<String> description) {
        this.description = Text.color(description == null ? new ArrayList<String>() : description);
    }

    public long cost() {
        return cost;
    }

    public void cost(long cost) {
        this.cost = Math.max(0L, cost);
    }

    public double vaultCost() {
        return vaultCost;
    }

    public void vaultCost(double vaultCost) {
        this.vaultCost = Math.max(0.0D, vaultCost);
    }

    public Material icon() {
        return icon;
    }

    public short iconData() {
        return iconData;
    }

    public void icon(Material material, short data) {
        this.icon = material == null ? Material.PAPER : material;
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

    /** Free cosmetics are available to everyone without a purchase. */
    public boolean free() {
        return free;
    }

    public void free(boolean free) {
        this.free = free;
    }

    public String permission() {
        return permission;
    }

    public void permission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public boolean hasPermission(Permissible permissible) {
        return permission.isEmpty() || (permissible != null && permissible.hasPermission(permission));
    }

    public String effect() {
        return effect;
    }

    public void effect(String effect) {
        this.effect = effect == null ? "" : effect.trim();
    }

    public String sound() {
        return sound;
    }

    public void sound(String sound) {
        this.sound = sound == null ? "" : sound.trim();
    }

    public float volume() {
        return volume;
    }

    public void volume(float volume) {
        this.volume = Math.max(0.0F, volume);
    }

    public float pitch() {
        return pitch;
    }

    public void pitch(float pitch) {
        this.pitch = Math.max(0.0F, pitch);
    }

    public int particles() {
        return particles;
    }

    public void particles(int particles) {
        this.particles = Math.max(0, Math.min(500, particles));
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public void offsets(double x, double y, double z) {
        this.offsetX = Math.max(0.0D, x);
        this.offsetY = Math.max(0.0D, y);
        this.offsetZ = Math.max(0.0D, z);
    }

    public double speed() {
        return speed;
    }

    public void speed(double speed) {
        this.speed = Math.max(0.0D, speed);
    }

    public double radius() {
        return radius;
    }

    public void radius(double radius) {
        this.radius = Math.max(1.0D, radius);
    }

    /** Block or item data for effects that need it, such as {@code ICON_CRACK} or {@code STEP_SOUND}. */
    public int dataId() {
        return dataId;
    }

    public void dataId(int dataId) {
        this.dataId = Math.max(0, dataId);
    }

    /** Minimum gap between two playbacks of a repeating cosmetic such as a trail. */
    public long intervalMillis() {
        return intervalMillis;
    }

    public void intervalMillis(long intervalMillis) {
        this.intervalMillis = Math.max(0L, intervalMillis);
    }

    /** Message template of a kill message cosmetic, supports {killer}, {victim} and {kit}. */
    public String message() {
        return message;
    }

    public void message(String message) {
        this.message = message == null ? "" : message;
    }

    /** Whether a profile owns this cosmetic, ignoring permission grants. */
    public boolean owned(Profile profile) {
        return free || (profile != null && profile.hasCosmetic(id));
    }

    /** Whether a player may use this cosmetic: owned for free, purchased or granted by permission. */
    public boolean usable(Profile profile, Permissible permissible) {
        return enabled && (owned(profile) || hasPermission(permissible));
    }

    public boolean purchasable() {
        return enabled && !free && (cost > 0L || vaultCost > 0.0D);
    }

    /** Plays the cosmetic at a location, ignoring empty effect and sound names. */
    public void play(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!effect.isEmpty()) {
            Visuals.effect(location, effect, particles, offsetX, offsetY, offsetZ, speed, (int) radius, dataId);
        }
        if (!sound.isEmpty()) {
            Visuals.sound(location, sound, volume, pitch);
        }
    }

    public void play(Player player) {
        if (player == null) {
            return;
        }
        play(player.getLocation());
        if (!sound.isEmpty()) {
            Visuals.sound(player, sound, volume, pitch);
        }
    }

    /** Menu icon showing ownership, equipment and price for one viewer. */
    public ItemStack iconItem(boolean unlocked, boolean equipped) {
        List<String> lore = new ArrayList<String>(description);
        if (equipped) {
            lore.add("&aEquipped");
        } else if (unlocked) {
            lore.add("&7Click to equip");
        } else if (free) {
            lore.add("&7Free");
        } else if (vaultCost > 0.0D) {
            lore.add("&7Cost: &6" + vaultCost + " money");
        } else {
            lore.add("&7Cost: &6" + cost + " coins");
        }
        return Items.item(icon, 1, iconData, displayName(), lore);
    }

    // -------------------------------------------------------------- persistence

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("type", type.name());
        section.set("display-name", displayName);
        section.set("description", description);
        section.set("cost", cost);
        section.set("vault-cost", vaultCost);
        section.set("icon.material", icon.name());
        section.set("icon.data", (int) iconData);
        section.set("order", order);
        section.set("enabled", enabled);
        section.set("free", free);
        section.set("permission", permission);
        section.set("effect", effect);
        section.set("sound", sound);
        section.set("volume", (double) volume);
        section.set("pitch", (double) pitch);
        section.set("particles", particles);
        section.set("offset-x", offsetX);
        section.set("offset-y", offsetY);
        section.set("offset-z", offsetZ);
        section.set("speed", speed);
        section.set("radius", radius);
        section.set("data-id", dataId);
        section.set("interval-millis", intervalMillis);
        if (!message.isEmpty()) {
            section.set("message", message);
        }
    }

    public static Cosmetic read(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        CosmeticType type = CosmeticType.parse(section.getString("type", "KILL_EFFECT"));
        Cosmetic cosmetic = new Cosmetic(id, type == null ? CosmeticType.KILL_EFFECT : type);
        cosmetic.displayName(section.getString("display-name", Text.capitalize(id)));
        cosmetic.description(section.getStringList("description"));
        cosmetic.cost(section.getLong("cost", 0L));
        cosmetic.vaultCost(section.getDouble("vault-cost", 0.0D));
        cosmetic.icon(Items.material(section.getString("icon.material", section.getString("icon", "PAPER")),
                Material.PAPER), (short) section.getInt("icon.data", 0));
        cosmetic.order(section.getInt("order", 0));
        cosmetic.enabled(section.getBoolean("enabled", true));
        cosmetic.free(section.getBoolean("free", false));
        cosmetic.permission(section.getString("permission", ""));
        cosmetic.effect(section.getString("effect", ""));
        cosmetic.sound(section.getString("sound", ""));
        cosmetic.volume((float) section.getDouble("volume", 1.0D));
        cosmetic.pitch((float) section.getDouble("pitch", 1.0D));
        cosmetic.particles(section.getInt("particles", 20));
        cosmetic.offsets(section.getDouble("offset-x", 0.35D), section.getDouble("offset-y", 0.35D),
                section.getDouble("offset-z", 0.35D));
        cosmetic.speed(section.getDouble("speed", 0.05D));
        cosmetic.radius(section.getDouble("radius", 32.0D));
        cosmetic.dataId(section.getInt("data-id", 0));
        cosmetic.intervalMillis(section.getLong("interval-millis", 100L));
        cosmetic.message(section.getString("message", ""));
        return cosmetic;
    }

    @Override
    public String toString() {
        return "Cosmetic{" + id + ", type=" + type + ", cost=" + cost + '}';
    }
}
