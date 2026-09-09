package gg.lightpractice.gui.menu;

import gg.lightpractice.cosmetic.Cosmetic;
import gg.lightpractice.cosmetic.CosmeticManager;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetic categories with what the viewer owns and has equipped.
 *
 * <p>Each category opens {@link CosmeticTypeMenu}, which does the purchasing and equipping. This menu only
 * summarises progress so a player can see what is left to unlock.</p>
 */
public final class CosmeticsMenu extends Menu {

    public CosmeticsMenu(PluginCore core) {
        super(core, title(core), 27);
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.cosmetics", "&8Cosmetics");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        fillBorder(map, Items.item(Material.STAINED_GLASS_PANE, 1, (short) 11, Text.color("&b"),
                lore("&7Cosmetics")));
        CosmeticManager cosmetics = core.optional(CosmeticManager.class);
        Profile profile = profile(viewer);
        int slot = 10;
        if (cosmetics == null) {
            map.put(Integer.valueOf(13), Button.display(Items.item(Material.BARRIER, 1, (short) 0,
                    Text.color("&cUnavailable"), lore("&7The cosmetic system is not running."))));
            map.put(Integer.valueOf(22), closeButton());
            return map;
        }
        for (final CosmeticType type : CosmeticType.values()) {
            List<Cosmetic> available = cosmetics.byType(type);
            if (available.isEmpty()) {
                continue;
            }
            int owned = 0;
            String equippedId = profile == null ? null : profile.equipped(type);
            for (Cosmetic cosmetic : available) {
                if (cosmetic.owned(profile) || cosmetic.hasPermission(viewer)) {
                    owned++;
                }
            }
            List<String> lines = lore("&7Owned: &f" + owned + "&7/&f" + available.size());
            if (equippedId != null) {
                Cosmetic equipped = cosmetics.get(equippedId);
                lines.add(Text.color("&7Equipped: &f"
                        + (equipped == null ? equippedId : Text.strip(equipped.displayName()))));
            } else {
                lines.add(Text.color("&7Nothing equipped"));
            }
            lines.add(Text.color("&7&m                        "));
            lines.add(Text.color("&aClick &7to browse"));
            ItemStack icon = Items.item(iconOf(type), 1, (short) 0,
                    Text.color("&b" + Text.capitalize(type.configKey().replace('-', ' '))), lines);
            map.put(Integer.valueOf(slot), Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType click) {
                    open(player, new CosmeticTypeMenu(core, type));
                }
            }));
            slot++;
            if (slot > 16) {
                break;
            }
        }
        map.put(Integer.valueOf(22), Button.display(Items.item(Material.GOLD_NUGGET, 1, (short) 0,
                Text.color("&6Your Coins"), lore("&7Balance: &6" + (profile == null ? 0 : profile.coins()),
                        "&7Currency: &f" + (cosmetics.useVault() ? "money" : "coins")))));
        map.put(Integer.valueOf(26), closeButton());
        return map;
    }

    private Material iconOf(CosmeticType type) {
        switch (type) {
            case KILL_EFFECT:
                return Material.BLAZE_POWDER;
            case KILL_MESSAGE:
                return Material.BOOK_AND_QUILL;
            case TRAIL:
                return Material.REDSTONE;
            case PROJECTILE_EFFECT:
                return Material.ARROW;
            default:
                return Material.FIREWORK;
        }
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
