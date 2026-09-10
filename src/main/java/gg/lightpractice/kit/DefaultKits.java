package gg.lightpractice.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kits shipped with LightPractice.
 *
 * <p>They are only installed when {@code kits.yml} holds no kits at all, so an existing server never
 * has its tuning overwritten. Each definition is plain data: a loadout plus a rule map, which is the
 * same shape an administrator writes by hand.</p>
 */
public final class DefaultKits {

    private DefaultKits() {
    }

    /** Builds every default kit without registering it anywhere. */
    public static List<Kit> build() {
        List<Kit> kits = new ArrayList<Kit>();
        kits.add(noDebuff());
        kits.add(debuff());
        kits.add(sumo());
        kits.add(boxing());
        kits.add(combo());
        kits.add(bridges());
        kits.add(bedFight());
        kits.add(uhc());
        kits.add(axe());
        kits.add(spleef());
        kits.add(pearlFight());
        kits.add(fireballFight());
        kits.add(tntSumo());
        kits.add(classic());
        return kits;
    }

    /** Installs the defaults into a manager, returning how many were added. */
    public static int install(KitManager manager) {
        return manager == null ? 0 : manager.install(build());
    }

    /** Option map builder so rule configuration reads like the YAML it produces. */
    public static Map<String, Object> options(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return values;
    }

    // ------------------------------------------------------------------- helpers

    private static Kit base(String id, String display, Material icon, int order, String... description) {
        Kit kit = new Kit(id);
        kit.displayName(display);
        kit.description(Arrays.asList(description));
        kit.icon(icon, (short) 0);
        kit.order(order);
        return kit;
    }

    private static ItemStack item(Material material, int amount) {
        return new ItemStack(material, Math.max(1, amount));
    }

    private static ItemStack sword(Material material, int sharpness) {
        ItemStack item = new ItemStack(material, 1);
        if (sharpness > 0) {
            gg.lightpractice.util.Items.enchant(item, "DAMAGE_ALL", sharpness);
        }
        return item;
    }

    private static ItemStack tool(Material material, int efficiency, int unbreaking) {
        ItemStack item = new ItemStack(material, 1);
        gg.lightpractice.util.Items.enchant(item, "DIG_SPEED", efficiency);
        gg.lightpractice.util.Items.enchant(item, "DURABILITY", unbreaking);
        return item;
    }

    private static ItemStack bow(int power, int infinity) {
        ItemStack item = new ItemStack(Material.BOW, 1);
        gg.lightpractice.util.Items.enchant(item, "ARROW_DAMAGE", power);
        gg.lightpractice.util.Items.enchant(item, "ARROW_INFINITE", infinity);
        return item;
    }

    /** Armour array in Bukkit order: boots, leggings, chestplate, helmet. */
    private static ItemStack[] armor(Material material, int protection) {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = protect(new ItemStack(Material.valueOf(material.name() + "_BOOTS")), protection);
        armor[1] = protect(new ItemStack(Material.valueOf(material.name() + "_LEGGINGS")), protection);
        armor[2] = protect(new ItemStack(Material.valueOf(material.name() + "_CHESTPLATE")), protection);
        armor[3] = protect(new ItemStack(Material.valueOf(material.name() + "_HELMET")), protection);
        return armor;
    }

    private static ItemStack protect(ItemStack item, int protection) {
        if (protection > 0) {
            gg.lightpractice.util.Items.enchant(item, "PROTECTION_ENVIRONMENTAL", protection);
        }
        return item;
    }

    private static ItemStack potion(PotionType type, int level, boolean splash, int amount) {
        return gg.lightpractice.util.Items.potion(type, level, splash, false, amount);
    }

    private static void fill(ItemStack[] contents, int from, int to, ItemStack item) {
        for (int slot = from; slot <= to && slot < contents.length; slot++) {
            contents[slot] = item == null ? null : item.clone();
        }
    }

    /** Shared "clean fight" rules: no fall damage, no hunger, no regeneration, capped duration. */
    private static void standardRules(Kit kit, int seconds) {
        kit.rule("no-fall-damage", Boolean.TRUE);
        kit.rule("no-hunger", Boolean.TRUE);
        kit.rule("no-regen", Boolean.TRUE);
        kit.rule("time-limit", options("seconds", seconds, "warn-at", Arrays.asList(60, 30, 10)));
    }

    // --------------------------------------------------------------------- kits

    private static Kit noDebuff() {
        Kit kit = base("nodebuff", "&bNo Debuff", Material.POTION, 1,
                "&7Diamond armour, a sharp sword", "&7and splash potions of healing.");
        kit.contents(buildNoDebuffContents());
        kit.armor(armor(Material.DIAMOND, 1));
        kit.addEffect(new StoredPotionEffect("SPEED", 1, -1, false, true));
        kit.addEffect(new StoredPotionEffect("FIRE_RESISTANCE", 0, -1, false, true));
        standardRules(kit, 900);
        kit.rule("potions", options("drinking", Boolean.FALSE, "splashing", Boolean.TRUE));
        kit.rule("golden-apples", options("cooldown", 0, "enchanted", Boolean.FALSE));
        kit.rule("pearls", options("enabled", Boolean.FALSE));
        kit.rule("rod", options("pull-strength", 0.70D));
        kit.rule("knockback", options("name", "nodebuff", "horizontal", 0.40D, "vertical", 0.36D,
                "sprint-horizontal", 0.44D, "sprint-vertical", 0.40D, "air-multiplier", 1.0D,
                "max-vertical", 0.45D, "resistance", 0.0D));
        return kit;
    }

    private static ItemStack[] buildNoDebuffContents() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.DIAMOND_SWORD, 1);
        fill(contents, 1, 8, potion(PotionType.INSTANT_HEAL, 2, true, 1));
        contents[9] = item(Material.GOLDEN_APPLE, 16);
        contents[10] = item(Material.GOLDEN_APPLE, 16);
        fill(contents, 11, 16, potion(PotionType.INSTANT_HEAL, 2, true, 1));
        contents[17] = item(Material.MILK_BUCKET, 1);
        return contents;
    }

    private static Kit debuff() {
        Kit kit = base("debuff", "&5Debuff", Material.POTION, 2,
                "&7Splash potions of poison, slowness", "&7and weakness. Land them to win.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.DIAMOND_SWORD, 1);
        fill(contents, 1, 2, potion(PotionType.POISON, 2, true, 1));
        fill(contents, 3, 4, potion(PotionType.SLOWNESS, 2, true, 1));
        contents[5] = potion(PotionType.WEAKNESS, 2, true, 1);
        contents[6] = potion(PotionType.INSTANT_DAMAGE, 2, true, 1);
        fill(contents, 7, 8, potion(PotionType.INSTANT_HEAL, 2, true, 1));
        contents[9] = item(Material.GOLDEN_APPLE, 16);
        contents[10] = item(Material.MILK_BUCKET, 1);
        kit.contents(contents);
        kit.armor(armor(Material.DIAMOND, 1));
        standardRules(kit, 900);
        kit.rule("potions", options("drinking", Boolean.FALSE, "splashing", Boolean.TRUE));
        kit.rule("golden-apples", options("cooldown", 2000));
        kit.rule("pearls", options("enabled", Boolean.FALSE));
        return kit;
    }

    private static Kit sumo() {
        Kit kit = base("sumo", "&eSumo", Material.SLIME_BALL, 3,
                "&7No items, no damage.", "&7Push your opponent off the platform.");
        kit.contents(new ItemStack[36]);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("sumo", options("out-of-bounds", Boolean.TRUE, "border-warning", Boolean.TRUE));
        kit.rule("void-loss", options("outside-bounds", Boolean.TRUE));
        kit.rule("knockback", options("name", "sumo", "horizontal", 0.42D, "vertical", 0.38D,
                "sprint-horizontal", 0.48D, "sprint-vertical", 0.40D, "air-multiplier", 1.0D,
                "max-vertical", 0.45D, "resistance", 0.0D));
        kit.teams(false);
        kit.ffa(false);
        kit.editable(false);
        return kit;
    }

    private static Kit boxing() {
        Kit kit = base("boxing", "&6Boxing", Material.LEATHER_CHESTPLATE, 4,
                "&7First to land 100 hits wins.", "&7Damage does not matter, accuracy does.");
        kit.contents(new ItemStack[36]);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("boxing", options("hits", 100));
        kit.rule("knockback", options("name", "boxing", "horizontal", 0.40D, "vertical", 0.36D));
        kit.teams(false);
        kit.ffa(false);
        kit.editable(false);
        return kit;
    }

    private static Kit combo() {
        Kit kit = base("combo", "&dCombo", Material.DIAMOND_SWORD, 5,
                "&7Reduced knockback keeps hits chaining.", "&7Stay aggressive and never stop clicking.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.DIAMOND_SWORD, 1);
        kit.contents(contents);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("combo", options("horizontal", 0.32D, "vertical", 0.01D));
        kit.teams(false);
        kit.ffa(false);
        kit.editable(false);
        return kit;
    }

    private static Kit bridges() {
        Kit kit = base("bridges", "&3Bridges", Material.WOOL, 6,
                "&7Bridge to the enemy platform and score.", "&7Three lives each, falling costs one.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.IRON_SWORD, 1);
        fill(contents, 1, 3, item(Material.WOOL, 64));
        contents[4] = item(Material.COBBLESTONE, 64);
        contents[5] = item(Material.GOLDEN_APPLE, 16);
        kit.contents(contents);
        kit.armor(armor(Material.IRON, 1));
        standardRules(kit, 900);
        kit.rule("build", options("allowed-blocks", Arrays.asList("WOOL", "GLASS", "COBBLESTONE", "SAND",
                "GRAVEL", "WOOD", "STONE")));
        kit.rule("respawn", options("lives", 3, "delay-ticks", 60, "reapply-kit", Boolean.TRUE));
        kit.rule("void-loss", options("y-threshold", 0.0D, "outside-bounds", Boolean.FALSE));
        kit.rule("pearls", options("enabled", Boolean.TRUE, "cooldown", 10000, "damage", 0.0D));
        kit.rule("knockback", options("name", "bridges", "horizontal", 0.44D, "vertical", 0.38D,
                "sprint-horizontal", 0.48D, "sprint-vertical", 0.40D));
        return kit;
    }

    private static Kit bedFight() {
        Kit kit = base("bedfight", "&cBed Fight", Material.BED, 7,
                "&7Protect your bed, break theirs.", "&7No bed means no respawns.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.DIAMOND_SWORD, 1);
        contents[1] = item(Material.WOOL, 64);
        contents[2] = item(Material.WOOD, 64);
        contents[3] = item(Material.COBBLESTONE, 64);
        contents[4] = tool(Material.IRON_PICKAXE, 2, 1);
        contents[5] = bow(1, 0);
        contents[6] = item(Material.ARROW, 32);
        contents[7] = item(Material.BED, 1);
        contents[8] = item(Material.GOLDEN_APPLE, 16);
        kit.contents(contents);
        kit.armor(armor(Material.IRON, 2));
        kit.rule("beds", options("enabled", Boolean.TRUE, "protection-radius", 25.0D,
                "message", "match.bed-destroyed"));
        kit.rule("build", options("allowed-blocks", Arrays.asList("WOOL", "WOOD", "COBBLESTONE", "SAND",
                "GRAVEL", "GLASS", "STONE", "LADDER")));
        kit.rule("break", options("allowed-blocks", Arrays.asList("BED_BLOCK", "WOOL", "WOOD", "LEAVES",
                "LEAVES_2", "LONG_GRASS", "COBBLESTONE")));
        kit.rule("respawn", options("lives", -1, "delay-ticks", 60, "reapply-kit", Boolean.TRUE));
        kit.rule("no-regen", Boolean.TRUE);
        kit.rule("drop-items", Boolean.TRUE);
        kit.rule("time-limit", options("seconds", 1800, "warn-at", Arrays.asList(120, 60, 30)));
        kit.rule("pearls", options("enabled", Boolean.FALSE));
        kit.rule("golden-apples", options("cooldown", 0));
        kit.ranked(false);
        kit.duel(false);
        return kit;
    }

    private static Kit uhc() {
        Kit kit = base("uhc", "&4UHC", Material.GOLDEN_APPLE, 8,
                "&7Full survival loadout.", "&7Fall damage and hunger are on, no natural regeneration.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.DIAMOND_SWORD, 2);
        contents[1] = sword(Material.DIAMOND_AXE, 1);
        contents[2] = bow(1, 0);
        contents[3] = item(Material.ARROW, 64);
        contents[4] = item(Material.LAVA_BUCKET, 1);
        contents[5] = item(Material.WATER_BUCKET, 1);
        contents[6] = item(Material.GOLDEN_APPLE, 6);
        contents[7] = item(Material.COBBLESTONE, 64);
        contents[8] = item(Material.WOOD, 64);
        fill(contents, 9, 11, potion(PotionType.INSTANT_HEAL, 2, true, 1));
        contents[12] = item(Material.MILK_BUCKET, 1);
        contents[13] = item(Material.COOKED_BEEF, 64);
        contents[14] = item(Material.ENDER_PEARL, 16);
        contents[15] = tool(Material.IRON_PICKAXE, 2, 1);
        kit.contents(contents);
        kit.armor(armor(Material.DIAMOND, 2));
        kit.rule("build", options("allowed-blocks", Arrays.asList()));
        kit.rule("break", options("allowed-blocks", Arrays.asList()));
        kit.rule("no-regen", Boolean.TRUE);
        kit.rule("drop-items", Boolean.TRUE);
        kit.rule("pearls", options("enabled", Boolean.TRUE, "cooldown", 10000, "damage", 2.0D));
        kit.rule("potions", options("drinking", Boolean.TRUE, "splashing", Boolean.TRUE));
        kit.rule("golden-apples", options("cooldown", 0, "heal", 4.0D));
        kit.rule("rod", options("pull-strength", 0.80D));
        kit.rule("time-limit", options("seconds", 1200, "warn-at", Arrays.asList(120, 60, 30)));
        return kit;
    }

    private static Kit axe() {
        Kit kit = base("axe", "&8Axe", Material.IRON_AXE, 9,
                "&7Iron armour and a sharp axe.", "&7Heavier knockback, short fights.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.IRON_AXE, 1);
        contents[1] = item(Material.GOLDEN_APPLE, 16);
        contents[2] = item(Material.COOKED_BEEF, 32);
        kit.contents(contents);
        kit.armor(armor(Material.IRON, 1));
        standardRules(kit, 600);
        kit.rule("knockback", options("name", "axe", "horizontal", 0.46D, "vertical", 0.40D,
                "sprint-horizontal", 0.52D, "sprint-vertical", 0.42D, "air-multiplier", 1.0D,
                "max-vertical", 0.46D, "resistance", 0.0D));
        return kit;
    }

    private static Kit spleef() {
        Kit kit = base("spleef", "&fSpleef", Material.DIAMOND_SPADE, 10,
                "&7Break the blocks below your opponent.", "&7Whoever falls into the void loses.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = tool(Material.DIAMOND_SPADE, 3, 3);
        kit.contents(contents);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("spleef", options("allowed-blocks", Arrays.asList("SNOW_BLOCK", "ICE", "PACKED_ICE")));
        kit.rule("void-loss", options("y-threshold", 0.0D, "outside-bounds", Boolean.TRUE));
        kit.teams(false);
        kit.editable(false);
        return kit;
    }

    private static Kit pearlFight() {
        Kit kit = base("pearlfight", "&dPearl Fight", Material.ENDER_PEARL, 11,
                "&7Pearls only.", "&7Knock opponents off the island with the blast.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = item(Material.ENDER_PEARL, 16);
        contents[1] = item(Material.ENDER_PEARL, 16);
        kit.contents(contents);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("no-damage", options("allowed-causes", Arrays.asList("VOID")));
        kit.rule("void-loss", options("y-threshold", 0.0D, "outside-bounds", Boolean.TRUE));
        kit.rule("pearls", options("enabled", Boolean.TRUE, "cooldown", 3000, "damage", 0.0D));
        kit.teams(false);
        kit.editable(false);
        return kit;
    }

    private static Kit fireballFight() {
        Kit kit = base("fireballfight", "&6Fireball Fight", Material.FIREBALL, 12,
                "&7Launch fireballs to blast players off.", "&7Explosions only knock back, they do no damage.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = item(Material.FIREBALL, 16);
        contents[1] = item(Material.FIREBALL, 16);
        contents[2] = item(Material.GOLDEN_APPLE, 8);
        kit.contents(contents);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 600);
        kit.rule("fireball", options("enabled", Boolean.TRUE, "cooldown", 1500, "power", 1.8D,
                "recoil", 1.0D, "block-damage", Boolean.FALSE, "damage-scale", 0.0D,
                "consume-item", Boolean.FALSE));
        kit.rule("no-damage", options("allowed-causes", Arrays.asList("BLOCK_EXPLOSION", "VOID")));
        kit.rule("void-loss", options("y-threshold", 0.0D, "outside-bounds", Boolean.TRUE));
        kit.teams(false);
        kit.editable(false);
        return kit;
    }

    private static Kit tntSumo() {
        Kit kit = base("tntsumo", "&cTNT Sumo", Material.TNT, 13,
                "&7Place TNT and blow opponents off.", "&7Explosions knock back without damaging blocks.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = item(Material.FLINT_AND_STEEL, 1);
        contents[1] = item(Material.TNT, 16);
        kit.contents(contents);
        kit.armor(new ItemStack[4]);
        standardRules(kit, 300);
        kit.rule("tnt", options("enabled", Boolean.TRUE, "instant", Boolean.TRUE, "fuse-ticks", 20,
                "block-damage", Boolean.FALSE, "damage-scale", 0.0D));
        kit.rule("build", options("allowed-blocks", Arrays.asList("TNT")));
        kit.rule("sumo", options("out-of-bounds", Boolean.TRUE));
        kit.rule("void-loss", options("outside-bounds", Boolean.TRUE));
        kit.rule("no-damage", options("allowed-causes", Arrays.asList("BLOCK_EXPLOSION", "VOID")));
        kit.teams(false);
        kit.ffa(false);
        kit.editable(false);
        return kit;
    }

    private static Kit classic() {
        Kit kit = base("classic", "&aClassic", Material.IRON_SWORD, 14,
                "&7Balanced vanilla style PvP.", "&7Fall damage and hunger are on.");
        ItemStack[] contents = new ItemStack[36];
        contents[0] = sword(Material.IRON_SWORD, 1);
        contents[1] = bow(1, 0);
        contents[2] = item(Material.ARROW, 32);
        contents[3] = item(Material.GOLDEN_APPLE, 8);
        contents[4] = item(Material.COOKED_BEEF, 32);
        kit.contents(contents);
        kit.armor(armor(Material.CHAINMAIL, 1));
        kit.rule("no-regen", Boolean.FALSE);
        kit.rule("drop-items", Boolean.TRUE);
        kit.rule("time-limit", options("seconds", 600, "warn-at", Arrays.asList(60, 30, 10)));
        kit.rule("pearls", options("enabled", Boolean.FALSE));
        kit.rule("potions", options("enabled", Boolean.FALSE));
        kit.rule("golden-apples", options("cooldown", 0));
        return kit;
    }
}
