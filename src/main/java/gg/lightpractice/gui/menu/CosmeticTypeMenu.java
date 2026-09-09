package gg.lightpractice.gui.menu;

import gg.lightpractice.cosmetic.Cosmetic;
import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.model.CosmeticType;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics of one category: buy, equip and unequip.
 *
 * <p>A left click equips an owned cosmetic or buys it when it is not owned yet, a right click always buys
 * and a shift right click unequips, which keeps the two actions that cost something separate from the one
 * that does not.</p>
 */
public final class CosmeticTypeMenu extends PagedMenu {

    private final CosmeticType type;

    public CosmeticTypeMenu(PluginCore core, CosmeticType type) {
        super(core, title(core, type), 5, new CosmeticsMenu(core), "&7Nothing to show in this category.");
        this.type = type;
    }

    private static String title(PluginCore core, CosmeticType type) {
        String base = core.configs().gui().getString("titles.cosmetic-type", "&8Cosmetics");
        return base + " &7- &f" + Text.capitalize(type == null ? "unknown" : type.configKey().replace('-', ' '));
    }

    public CosmeticType type() {
        return type;
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics == null || viewer == null || type == null) {
            return entries;
        }
        Profile profile = profile(viewer);
        String equipped = profile == null ? null : profile.equipped(type);
        for (final Cosmetic cosmetic : cosmetics.byType(type)) {
            boolean unlocked = cosmetic.owned(profile) || cosmetic.hasPermission(viewer);
            boolean isEquipped = cosmetic.id().equals(equipped);
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Owned: " + (unlocked ? "&ayes" : "&cno")));
            if (isEquipped) {
                lore.add(Text.color("&aEquipped"));
                lore.add(Text.color("&7Shift right click to unequip"));
            } else if (unlocked) {
                lore.add(Text.color("&aClick &7to equip"));
            } else {
                lore.add(Text.color("&7Cost: &6" + (cosmetics.useVault() && cosmetic.vaultCost() > 0.0D
                        ? cosmetic.vaultCost() + " money" : cosmetic.cost() + " coins")));
                lore.add(Text.color("&6Click &7to buy"));
            }
            ItemStack icon = Items.decorate(cosmetic.iconItem(unlocked, isEquipped),
                    cosmetic.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType click) {
                    handle(player, cosmetic, click);
                }
            }));
        }
        return entries;
    }

    private void handle(Player player, Cosmetic cosmetic, ClickType click) {
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        if (cosmetics == null) {
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            cosmetics.unequip(player, type);
            refresh(player);
            return;
        }
        boolean unlocked = cosmetics.isUnlocked(player.getUniqueId(), cosmetic.id());
        if (unlocked) {
            cosmetics.equip(player, cosmetic);
        } else {
            cosmetics.purchase(player, cosmetic);
        }
        refresh(player);
    }

    @Override
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        Profile profile = profile(viewer);
        int lastRow = (rows() - 1) * 9;
        ItemStack unequip = Items.item(Material.INK_SACK, 1, (short) 1, Text.color("&cUnequip"),
                profile == null || profile.equipped(type) == null
                        ? lore("&7Nothing is equipped in this category")
                        : lore("&7Equipped: &f" + profile.equipped(type)));
        map.put(Integer.valueOf(lastRow + 3), cosmetics == null ? Button.display(unequip)
                : Button.of(unequip, new ClickAction() {
                    @Override
                    public void click(Player player, ClickType click) {
                        CosmeticManager manager = core.optional(CosmeticManager.class);
                        if (manager != null) {
                            manager.unequip(player, type);
                        }
                        refresh(player);
                    }
                }));
        ItemStack balance = Items.item(Material.GOLD_NUGGET, 1, (short) 0, Text.color("&6Balance"),
                lore("&7Coins: &6" + (profile == null ? 0 : profile.coins()),
                        "&7Unlocked: &f" + (profile == null ? 0 : profile.unlockedCosmetics().size())));
        map.put(Integer.valueOf(lastRow + 6), Button.display(balance));
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
