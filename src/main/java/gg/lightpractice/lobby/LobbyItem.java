package gg.lightpractice.lobby;

import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One item handed out in the lobby that opens a menu.
 *
 * <p>The item is pure configuration: an action id, a slot, a material and its name. The GUI layer maps
 * the action id to a menu, so adding a lobby shortcut never needs a new listener.</p>
 */
public final class LobbyItem {

    private final String action;
    private int slot;
    private Material material = Material.PAPER;
    private short data;
    private String displayName = "&bMenu";
    private List<String> lore = new ArrayList<String>();
    private boolean enabled = true;
    private String permission = "";

    public LobbyItem(String action, int slot, Material material, String displayName) {
        this.action = action == null ? "none" : action.trim().toLowerCase(Locale.ROOT);
        this.slot = Math.max(0, Math.min(35, slot));
        this.material = material == null ? Material.PAPER : material;
        this.displayName = Text.color(displayName);
    }

    public String action() {
        return action;
    }

    public int slot() {
        return slot;
    }

    public void slot(int slot) {
        this.slot = Math.max(0, Math.min(35, slot));
    }

    public Material material() {
        return material;
    }

    public short data() {
        return data;
    }

    public void icon(Material material, short data) {
        this.material = material == null ? Material.PAPER : material;
        this.data = data;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = Text.color(displayName == null ? action : displayName);
    }

    public List<String> lore() {
        return lore;
    }

    public void lore(List<String> lore) {
        this.lore = Text.color(lore == null ? new ArrayList<String>() : lore);
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

    public boolean visibleTo(org.bukkit.permissions.Permissible permissible) {
        return enabled && (permission.isEmpty() || (permissible != null && permissible.hasPermission(permission)));
    }

    public ItemStack item() {
        return Items.item(material, 1, data, displayName, lore);
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("slot", slot);
        section.set("material", material.name());
        section.set("data", (int) data);
        section.set("name", displayName);
        section.set("lore", lore);
        section.set("enabled", enabled);
        section.set("permission", permission);
    }

    public static LobbyItem read(String action, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        LobbyItem item = new LobbyItem(action, section.getInt("slot", 0),
                Items.material(section.getString("material", "PAPER"), Material.PAPER),
                section.getString("name", Text.capitalize(action)));
        item.icon(item.material(), (short) section.getInt("data", 0));
        item.lore(section.getStringList("lore"));
        item.enabled(section.getBoolean("enabled", true));
        item.permission(section.getString("permission", ""));
        return item;
    }

    @Override
    public String toString() {
        return "LobbyItem{" + action + ", slot=" + slot + ", material=" + material + '}';
    }
}
