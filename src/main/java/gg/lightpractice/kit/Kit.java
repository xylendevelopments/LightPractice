package gg.lightpractice.kit;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.kit.rule.KitRuleSet;
import gg.lightpractice.profile.KitLoadout;
import gg.lightpractice.util.ConfigMaps;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A kit: the loadout plus every rule that governs how a match with it behaves.
 *
 * <p>The loadout (contents, armour, vitals, potion effects) is data, and behaviour lives in the rule
 * set resolved from {@code rules} in {@code kits.yml}. That separation is why new kit types such as
 * Bridges or BedFight are configuration rather than new listener code.</p>
 */
public final class Kit {

    private final String id;
    private final Map<String, Object> ruleOptions = new LinkedHashMap<String, Object>();
    private final Set<String> arenaWhitelist = new LinkedHashSet<String>();
    private final List<StoredPotionEffect> effects = new ArrayList<StoredPotionEffect>();
    private String displayName;
    private List<String> description = new ArrayList<String>();
    private Material iconMaterial = Material.DIAMOND_SWORD;
    private short iconData;
    private int order;
    private boolean enabled = true;
    private boolean ranked = true;
    private boolean unranked = true;
    private boolean duel = true;
    private boolean editable = true;
    private boolean ffa = true;
    private boolean teams = true;
    private String permission = "";
    private ItemStack[] contents = new ItemStack[36];
    private ItemStack[] armor = new ItemStack[4];
    private double health = 20.0D;
    private double maxHealth = 20.0D;
    private int food = 20;
    private float saturation = 5.0F;
    private KitRuleSet ruleSet = new KitRuleSet(null, null);

    public Kit(String id) {
        this.id = id == null ? "kit" : id.trim().toLowerCase(Locale.ROOT);
        this.displayName = Text.capitalize(this.id);
        this.ruleSet = new KitRuleSet(this, null);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName == null || displayName.isEmpty() ? Text.capitalize(id) : displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName == null ? null : Text.color(displayName);
    }

    public List<String> description() {
        return description;
    }

    public void description(List<String> description) {
        this.description = Text.color(description == null ? new ArrayList<String>() : description);
    }

    public Material iconMaterial() {
        return iconMaterial;
    }

    public short iconData() {
        return iconData;
    }

    public void icon(Material material, short data) {
        this.iconMaterial = material == null ? Material.DIAMOND_SWORD : material;
        this.iconData = data;
    }

    /** Menu icon built from the configured material, name and description. */
    public ItemStack icon() {
        return Items.item(iconMaterial, 1, iconData, "&b" + displayName(), description);
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

    public boolean ranked() {
        return ranked;
    }

    public void ranked(boolean ranked) {
        this.ranked = ranked;
    }

    public boolean unranked() {
        return unranked;
    }

    public void unranked(boolean unranked) {
        this.unranked = unranked;
    }

    public boolean duel() {
        return duel;
    }

    public void duel(boolean duel) {
        this.duel = duel;
    }

    public boolean editable() {
        return editable;
    }

    public void editable(boolean editable) {
        this.editable = editable;
    }

    public boolean ffa() {
        return ffa;
    }

    public void ffa(boolean ffa) {
        this.ffa = ffa;
    }

    public boolean teams() {
        return teams;
    }

    public void teams(boolean teams) {
        this.teams = teams;
    }

    /** True when the kit may be used in any queue type at all. */
    public boolean playable() {
        return enabled && (ranked || unranked || duel || ffa);
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

    public ItemStack[] contents() {
        return Items.copy(contents);
    }

    public ItemStack[] rawContents() {
        return contents;
    }

    public void contents(ItemStack[] contents) {
        this.contents = pad(contents, 36);
    }

    public ItemStack[] armor() {
        return Items.copy(armor);
    }

    public ItemStack[] rawArmor() {
        return armor;
    }

    public void armor(ItemStack[] armor) {
        this.armor = pad(armor, 4);
    }

    public List<StoredPotionEffect> effects() {
        return Collections.unmodifiableList(effects);
    }

    public void clearEffects() {
        effects.clear();
    }

    public void addEffect(StoredPotionEffect effect) {
        if (effect != null) {
            effects.add(effect);
        }
    }

    public double health() {
        return health;
    }

    public void health(double health) {
        this.health = Math.max(1.0D, health);
    }

    public double maxHealth() {
        return maxHealth;
    }

    public void maxHealth(double maxHealth) {
        this.maxHealth = Math.max(1.0D, maxHealth);
    }

    public int food() {
        return food;
    }

    public void food(int food) {
        this.food = Math.max(0, Math.min(20, food));
    }

    public float saturation() {
        return saturation;
    }

    public void saturation(float saturation) {
        this.saturation = Math.max(0.0F, saturation);
    }

    public Set<String> arenaWhitelist() {
        return Collections.unmodifiableSet(arenaWhitelist);
    }

    public void allowArena(String arenaName) {
        if (arenaName != null && !arenaName.trim().isEmpty()) {
            arenaWhitelist.add(arenaName.trim().toLowerCase(Locale.ROOT));
        }
    }

    public boolean removeArena(String arenaName) {
        return arenaName != null && arenaWhitelist.remove(arenaName.trim().toLowerCase(Locale.ROOT));
    }

    public void clearArenas() {
        arenaWhitelist.clear();
    }

    /** An empty whitelist means every arena may host this kit. */
    public boolean acceptsArena(Arena arena) {
        if (arena == null) {
            return false;
        }
        return arenaWhitelist.isEmpty() || arenaWhitelist.contains(arena.name().toLowerCase(Locale.ROOT));
    }

    public Map<String, Object> ruleOptions() {
        return ruleOptions;
    }

    public void rule(String id, Object value) {
        if (id == null) {
            return;
        }
        ruleOptions.put(id.trim().toLowerCase(Locale.ROOT), value);
    }

    public Object rule(String id) {
        return id == null ? null : ruleOptions.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public boolean removeRule(String id) {
        return id != null && ruleOptions.remove(id.trim().toLowerCase(Locale.ROOT)) != null;
    }

    public Set<String> ruleIds() {
        return Collections.unmodifiableSet(ruleOptions.keySet());
    }

    public KitRuleSet ruleSet() {
        return ruleSet;
    }

    /** Replaced by {@code KitRuleRegistry} whenever the kit is loaded or edited. */
    public void ruleSet(KitRuleSet ruleSet) {
        this.ruleSet = ruleSet == null ? new KitRuleSet(this, null) : ruleSet;
    }

    /**
     * Prepares a player for a match with this kit.
     *
     * @param custom optional player edited loadout; ignored when it is empty so a broken edit can never
     *               send someone into a match with no items
     */
    public void apply(Player player, KitLoadout custom) {
        if (player == null) {
            return;
        }
        boolean useCustom = custom != null && !custom.isEmpty();
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setContents(pad(useCustom ? custom.contents() : contents, 36));
            player.getInventory().setArmorContents(pad(useCustom ? custom.armor() : armor, 4));
            player.updateInventory();
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.KIT, "Could not apply the inventory of kit " + id, throwable);
        }
        try {
            for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            for (StoredPotionEffect stored : effects) {
                PotionEffect effect = stored.effect();
                if (effect != null) {
                    player.addPotionEffect(effect, true);
                }
            }
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.KIT, "Could not apply the effects of kit " + id, throwable);
        }
        try {
            player.setGameMode(GameMode.SURVIVAL);
            player.setMaxHealth(maxHealth);
            player.setHealth(Math.min(health, player.getMaxHealth()));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setExhaustion(0.0F);
            player.setLevel(0);
            player.setExp(0.0F);
            player.setTotalExperience(0);
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.setSprinting(false);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setWalkSpeed(0.2F);
            player.setFlySpeed(0.1F);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.KIT, "Could not reset the vitals for kit " + id, throwable);
        }
    }

    public void apply(Player player) {
        apply(player, null);
    }

    private static ItemStack[] pad(ItemStack[] source, int size) {
        ItemStack[] result = new ItemStack[size];
        if (source == null) {
            return result;
        }
        System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
        return result;
    }

    /** Replaces every value of this kit with the values of another, keeping the identifier. */
    public void copyFrom(Kit other) {
        if (other == null) {
            return;
        }
        this.displayName = other.displayName;
        this.description = new ArrayList<String>(other.description);
        this.iconMaterial = other.iconMaterial;
        this.iconData = other.iconData;
        this.order = other.order;
        this.enabled = other.enabled;
        this.ranked = other.ranked;
        this.unranked = other.unranked;
        this.duel = other.duel;
        this.editable = other.editable;
        this.ffa = other.ffa;
        this.teams = other.teams;
        this.permission = other.permission;
        this.contents = pad(Items.copy(other.contents), 36);
        this.armor = pad(Items.copy(other.armor), 4);
        this.effects.clear();
        this.effects.addAll(other.effects);
        this.health = other.health;
        this.maxHealth = other.maxHealth;
        this.food = other.food;
        this.saturation = other.saturation;
        this.arenaWhitelist.clear();
        this.arenaWhitelist.addAll(other.arenaWhitelist);
        this.ruleOptions.clear();
        this.ruleOptions.putAll(other.ruleOptions);
    }

    // -------------------------------------------------------------- persistence

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("display-name", displayName);
        section.set("description", description);
        section.set("icon.material", iconMaterial.name());
        section.set("icon.data", (int) iconData);
        section.set("order", order);
        section.set("enabled", enabled);
        section.set("ranked", ranked);
        section.set("unranked", unranked);
        section.set("duel", duel);
        section.set("editable", editable);
        section.set("ffa", ffa);
        section.set("teams", teams);
        section.set("permission", permission);
        section.set("health", health);
        section.set("max-health", maxHealth);
        section.set("food", food);
        section.set("saturation", (double) saturation);
        section.set("arenas", new ArrayList<String>(arenaWhitelist));
        List<String> effectLines = new ArrayList<String>();
        for (StoredPotionEffect effect : effects) {
            effectLines.add(effect.toString());
        }
        section.set("effects", effectLines);

        Map<String, Object> contentsMap = new LinkedHashMap<String, Object>();
        for (int slot = 0; slot < contents.length; slot++) {
            if (!Items.isAir(contents[slot])) {
                contentsMap.put(String.valueOf(slot), contents[slot]);
            }
        }
        section.set("contents", contentsMap);

        Map<String, Object> armorMap = new LinkedHashMap<String, Object>();
        String[] names = {"boots", "leggings", "chestplate", "helmet"};
        for (int slot = 0; slot < armor.length; slot++) {
            if (!Items.isAir(armor[slot])) {
                armorMap.put(names[slot], armor[slot]);
            }
        }
        section.set("armor", armorMap);
        section.set("rules", new LinkedHashMap<String, Object>(ruleOptions));
    }

    public static Kit read(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Kit kit = new Kit(id);
        kit.displayName(section.getString("display-name", Text.capitalize(kit.id())));
        kit.description(section.getStringList("description"));
        String icon = section.getString("icon.material", section.getString("icon", "DIAMOND_SWORD"));
        kit.icon(Items.material(icon, Material.DIAMOND_SWORD), (short) section.getInt("icon.data", 0));
        kit.order(section.getInt("order", 0));
        kit.enabled(section.getBoolean("enabled", true));
        kit.ranked(section.getBoolean("ranked", true));
        kit.unranked(section.getBoolean("unranked", true));
        kit.duel(section.getBoolean("duel", true));
        kit.editable(section.getBoolean("editable", true));
        kit.ffa(section.getBoolean("ffa", true));
        kit.teams(section.getBoolean("teams", true));
        kit.permission(section.getString("permission", ""));
        kit.health(section.getDouble("health", 20.0D));
        kit.maxHealth(section.getDouble("max-health", Math.max(20.0D, kit.health())));
        kit.food(section.getInt("food", 20));
        kit.saturation((float) section.getDouble("saturation", 5.0D));
        for (String arena : section.getStringList("arenas")) {
            kit.allowArena(arena);
        }
        for (String effect : section.getStringList("effects")) {
            StoredPotionEffect parsed = StoredPotionEffect.parse(effect);
            if (parsed != null) {
                kit.effects.add(parsed);
            }
        }
        ConfigurationSection contents = section.getConfigurationSection("contents");
        if (contents != null) {
            for (String key : contents.getKeys(false)) {
                ItemStack item = contents.getItemStack(key);
                int slot = parseSlot(key);
                if (item == null || slot < 0 || slot >= kit.contents.length) {
                    if (item == null) {
                        Debug.log(DebugCategory.KIT, "Kit {} holds an unreadable item in slot '{}'", kit.id(), key);
                    }
                    continue;
                }
                kit.contents[slot] = item;
            }
        }
        ConfigurationSection armor = section.getConfigurationSection("armor");
        if (armor != null) {
            kit.armor[0] = armor.getItemStack("boots");
            kit.armor[1] = armor.getItemStack("leggings");
            kit.armor[2] = armor.getItemStack("chestplate");
            kit.armor[3] = armor.getItemStack("helmet");
        }
        Object rules = section.get("rules");
        if (rules != null) {
            Map<String, Object> resolved = ConfigMaps.toMap(rules);
            for (Map.Entry<String, Object> entry : resolved.entrySet()) {
                kit.rule(entry.getKey(), entry.getValue());
            }
        }
        return kit;
    }

    private static int parseSlot(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "Kit{" + id + ", enabled=" + enabled + ", rules=" + ruleOptions.keySet() + '}';
    }

    /** Items that make up the loadout, used by the kit editor and by {@code /kit info}. */
    public List<ItemStack> loadoutItems() {
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (ItemStack item : contents) {
            if (!Items.isAir(item)) {
                items.add(item.clone());
            }
        }
        for (ItemStack item : armor) {
            if (!Items.isAir(item)) {
                items.add(item.clone());
            }
        }
        return items;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<String>();
        lines.add("items: " + loadoutItems().size());
        lines.add("effects: " + (effects.isEmpty() ? "none" : Arrays.toString(effects.toArray())));
        lines.add("rules: " + (ruleOptions.isEmpty() ? "none" : ruleOptions.keySet()));
        lines.add("health: " + health + "/" + maxHealth + ", food: " + food);
        lines.add("modes: ranked=" + ranked + " unranked=" + unranked + " duel=" + duel + " ffa=" + ffa);
        return lines;
    }
}
