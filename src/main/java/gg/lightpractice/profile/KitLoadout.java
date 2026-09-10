package gg.lightpractice.profile;

import gg.lightpractice.util.Items;
import org.bukkit.inventory.ItemStack;

/**
 * A player edited kit loadout. The kit editor writes one of these per kit and it is persisted with the
 * profile so a player always starts a match with their own layout while the server keeps the
 * authoritative item set from {@code kits.yml}.
 */
public final class KitLoadout {

    private final ItemStack[] contents;
    private final ItemStack[] armor;

    public KitLoadout(ItemStack[] contents, ItemStack[] armor) {
        this.contents = Items.copy(contents);
        this.armor = Items.copy(armor == null ? new ItemStack[4] : armor);
    }

    public ItemStack[] contents() {
        return Items.copy(contents);
    }

    public ItemStack[] armor() {
        return Items.copy(armor);
    }

    public ItemStack[] rawContents() {
        return contents;
    }

    public ItemStack[] rawArmor() {
        return armor;
    }

    public boolean isEmpty() {
        for (ItemStack item : contents) {
            if (!Items.isAir(item)) {
                return false;
            }
        }
        for (ItemStack item : armor) {
            if (!Items.isAir(item)) {
                return false;
            }
        }
        return true;
    }
}
