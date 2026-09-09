package gg.lightpractice.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Item helpers for Minecraft 1.8.9.
 *
 * <p>1.8 identifies potions, wool colours and skulls through damage values instead of separate
 * materials, and there is no off-hand slot, so every inventory model in LightPractice is built on
 * top of these helpers to keep the version specific details in one place.</p>
 */
public final class Items {

    private Items() {
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /** Resolves a material name from configuration, falling back when the name is unknown. */
    public static Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        Material material = Material.matchMaterial(key);
        if (material == null) {
            try {
                material = Material.getMaterial(key);
            } catch (IllegalArgumentException ignored) {
                material = null;
            }
        }
        if (material == null) {
            material = legacy(key);
        }
        if (material == null) {
            Debug.log(DebugCategory.GUI, "Unknown material '{}' in configuration, using {}", name, fallback);
            return fallback;
        }
        return material;
    }

    /** Maps common modern material names onto their 1.8 equivalents. */
    private static Material legacy(String key) {
        if ("PLAYER_HEAD".equals(key)) {
            return Material.SKULL_ITEM;
        }
        if ("CLOCK".equals(key)) {
            return Material.WATCH;
        }
        if ("CRAFTING_TABLE".equals(key)) {
            return Material.WORKBENCH;
        }
        if ("OAK_SIGN".equals(key) || "SIGN".equals(key)) {
            return Material.SIGN;
        }
        if ("GOLDEN_CARROT".equals(key)) {
            return Material.GOLDEN_CARROT;
        }
        if ("FIRE_CHARGE".equals(key)) {
            return Material.FIREBALL;
        }
        if ("GLASS_PANE".equals(key)) {
            return Material.THIN_GLASS;
        }
        if ("IRON_BARS".equals(key)) {
            return Material.IRON_FENCE;
        }
        if ("GREEN_DYE".equals(key) || "LIME_DYE".equals(key) || "INK_SAC".equals(key)) {
            return Material.INK_SACK;
        }
        if ("OAK_FENCE_GATE".equals(key)) {
            return Material.FENCE_GATE;
        }
        if ("SNOW_BLOCK".equals(key)) {
            return Material.SNOW_BLOCK;
        }
        return null;
    }

    public static ItemStack item(Material material, int amount) {
        return new ItemStack(material == null ? Material.STONE : material, Math.max(1, amount));
    }

    public static ItemStack item(Material material, int amount, short data) {
        return new ItemStack(material == null ? Material.STONE : material, Math.max(1, amount), data);
    }

    public static ItemStack item(Material material, int amount, String displayName, List<String> lore) {
        return decorate(item(material, amount), displayName, lore);
    }

    public static ItemStack item(Material material, int amount, short data, String displayName, List<String> lore) {
        return decorate(item(material, amount, data), displayName, lore);
    }

    public static ItemStack decorate(ItemStack item, String displayName, List<String> lore) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (displayName != null) {
            meta.setDisplayName(Text.color(displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(Text.color(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack skull(String owner, int amount, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, Math.max(1, amount), (short) 3);
        if (owner != null && !owner.trim().isEmpty()) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setOwner(Text.trimToFit(owner.trim(), 16));
                if (displayName != null) {
                    meta.setDisplayName(Text.color(displayName));
                }
                if (lore != null && !lore.isEmpty()) {
                    meta.setLore(Text.color(lore));
                }
                item.setItemMeta(meta);
                return item;
            }
        }
        return decorate(item, displayName, lore);
    }

    /**
     * Builds a 1.8 potion item. 1.8 stores the potion type, tier and splash flag inside the damage
     * value, which {@link Potion} computes for us.
     */
    public static ItemStack potion(PotionType type, int level, boolean splash, boolean extended, int amount) {
        if (type == null) {
            return new ItemStack(Material.POTION, Math.max(1, amount));
        }
        try {
            Potion potion = new Potion(type, Math.max(1, level));
            if (splash) {
                potion.splash();
            }
            if (extended && potion.getType() != PotionType.INSTANT_HEAL && potion.getType() != PotionType.INSTANT_DAMAGE) {
                potion.extend();
            }
            return potion.toItemStack(Math.max(1, amount));
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.KIT, "Could not build potion {}: {}", type, throwable.getMessage());
            return new ItemStack(Material.POTION, Math.max(1, amount));
        }
    }

    public static PotionType potionType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return PotionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ItemStack enchant(ItemStack item, String enchantment, int level) {
        if (item == null || enchantment == null || level <= 0) {
            return item;
        }
        Enchantment resolved = Enchantment.getByName(enchantment.trim().toUpperCase(Locale.ROOT));
        if (resolved == null) {
            Debug.log(DebugCategory.KIT, "Unknown enchantment '{}' requested", enchantment);
            return item;
        }
        item.addUnsafeEnchantment(resolved, level);
        return item;
    }

    public static ItemStack[] copy(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    public static List<ItemStack> copy(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        if (source == null) {
            return copy;
        }
        for (ItemStack item : source) {
            copy.add(item == null ? null : item.clone());
        }
        return copy;
    }

    public static ItemStack cloneOrNull(ItemStack source) {
        return source == null ? null : source.clone();
    }

    /** Counts how many items of the given material the array holds. */
    public static int count(ItemStack[] contents, Material material) {
        if (contents == null || material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static int firstEmpty(ItemStack[] contents) {
        if (contents == null) {
            return -1;
        }
        for (int index = 0; index < contents.length; index++) {
            if (isAir(contents[index])) {
                return index;
            }
        }
        return -1;
    }

    public static List<String> loreOf(String... lines) {
        return new ArrayList<String>(Arrays.asList(lines));
    }

    /** Compares two stacks the way 1.8 does: type, amount, durability and meta. */
    public static boolean similar(ItemStack left, ItemStack right) {
        if (left == right) {
            return true;
        }
        if (isAir(left) || isAir(right)) {
            return isAir(left) && isAir(right);
        }
        return left.isSimilar(right);
    }
}
