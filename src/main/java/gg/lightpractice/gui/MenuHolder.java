package gg.lightpractice.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder that carries the menu it belongs to.
 *
 * <p>Listeners recognise our inventories through this holder instead of comparing titles, which keeps
 * menus working when two of them share a name and when other plugins open inventories with similar
 * titles.</p>
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    public MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String toString() {
        return "MenuHolder{" + (menu == null ? "none" : menu.title()) + '}';
    }
}
