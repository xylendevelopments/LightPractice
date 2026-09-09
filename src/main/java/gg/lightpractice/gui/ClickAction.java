package gg.lightpractice.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** What happens when a menu button is clicked. */
public interface ClickAction {

    void click(Player player, ClickType type);
}
