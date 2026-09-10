package gg.lightpractice.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * One clickable slot of a menu.
 *
 * <p>A button is an item plus what a click does. Buttons without an action are pure decoration and are
 * still added to the holder map so a click on them is cancelled instead of letting the player take the
 * item out of the menu.</p>
 */
public final class Button {

    private final ItemStack item;
    private final ClickAction action;

    private Button(ItemStack item, ClickAction action) {
        this.item = item;
        this.action = action;
    }

    public static Button of(ItemStack item, ClickAction action) {
        return new Button(item, action);
    }

    /** Button that only shows something. */
    public static Button display(ItemStack item) {
        return new Button(item, null);
    }

    /** Button that runs one action no matter how it was clicked. */
    public static Button action(ItemStack item, final Runnable runnable) {
        return new Button(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                if (runnable != null) {
                    runnable.run();
                }
            }
        });
    }

    public ItemStack item() {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    public ClickAction action() {
        return action;
    }

    public boolean clickable() {
        return action != null;
    }

    public void click(Player player, ClickType type) {
        if (action != null && player != null) {
            action.click(player, type);
        }
    }

    @Override
    public String toString() {
        return "Button{" + item.getType() + (clickable() ? ", clickable" : ", display") + '}';
    }
}
