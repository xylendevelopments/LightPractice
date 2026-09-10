package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.model.ChatChannel;
import gg.lightpractice.profile.PlayerSettings;
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
import java.util.Map;

/**
 * Player preferences.
 *
 * <p>Every toggle writes straight into the settings of the loaded profile and marks it dirty, so a change
 * survives a relog without a separate save path. Clicking a toggle re-renders the menu in place.</p>
 */
public final class SettingsMenu extends Menu {

    public SettingsMenu(PluginCore core) {
        super(core, title(core), 45);
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.settings", "&8Settings");
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        fillBorder(map, Items.item(Material.STAINED_GLASS_PANE, 1, (short) 7, Text.color("&7"),
                lore("&7Settings")));
        Profile profile = profile(viewer);
        if (profile == null || profile.settings() == null) {
            map.put(Integer.valueOf(22), Button.display(Items.item(Material.BARRIER, 1, (short) 0,
                    Text.color("&cUnavailable"), lore("&7Your profile is still loading."))));
            return map;
        }
        PlayerSettings settings = profile.settings();
        map.put(Integer.valueOf(10), toggle(settings.scoreboardVisible(), "&bScoreboard",
                "&7Show the sidebar scoreboard", "scoreboardVisible"));
        map.put(Integer.valueOf(11), toggle(settings.receiveDuelRequests(), "&aDuel Requests",
                "&7Let players send you duels", "receiveDuelRequests"));
        map.put(Integer.valueOf(12), toggle(settings.allowSpectators(), "&eSpectators",
                "&7Allow players to watch your matches", "allowSpectators"));
        map.put(Integer.valueOf(13), toggle(settings.allowPartyInvites(), "&dParty Invites",
                "&7Let players invite you to parties", "allowPartyInvites"));
        map.put(Integer.valueOf(14), toggle(settings.cosmeticEffects(), "&bCosmetic Effects",
                "&7Show particles and sounds of cosmetics", "cosmeticEffects"));
        map.put(Integer.valueOf(15), toggle(settings.globalChat(), "&fGlobal Chat",
                "&7Read the public lobby chat", "globalChat"));
        map.put(Integer.valueOf(16), channel(settings.chatChannel()));
        map.put(Integer.valueOf(31), Button.display(Items.item(Material.BOOK, 1, (short) 0,
                Text.color("&eHow this works"),
                lore("&7Click a toggle to change it",
                        "&7Settings are saved with your profile",
                        "&7Chat channel: &f" + settings.chatChannel().name().toLowerCase(java.util.Locale.ROOT)))));
        map.put(Integer.valueOf(40), backButton(new MainMenu(core)));
        return map;
    }

    private Button toggle(boolean enabled, String name, String description, final String field) {
        ItemStack item = Items.item(enabled ? Material.INK_SACK : Material.INK_SACK, 1,
                enabled ? (short) 10 : (short) 8,
                Text.color(name + " &7[" + (enabled ? "&aOn&7" : "&cOff&7") + "]"),
                lore(description, "&7Click to " + (enabled ? "disable" : "enable")));
        return Button.of(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                Profile profile = profile(player);
                if (profile == null || profile.settings() == null) {
                    return;
                }
                PlayerSettings settings = profile.settings();
                boolean value;
                if ("scoreboardVisible".equals(field)) {
                    value = !settings.scoreboardVisible();
                    settings.scoreboardVisible(value);
                } else if ("receiveDuelRequests".equals(field)) {
                    value = !settings.receiveDuelRequests();
                    settings.receiveDuelRequests(value);
                } else if ("allowSpectators".equals(field)) {
                    value = !settings.allowSpectators();
                    settings.allowSpectators(value);
                } else if ("allowPartyInvites".equals(field)) {
                    value = !settings.allowPartyInvites();
                    settings.allowPartyInvites(value);
                } else if ("cosmeticEffects".equals(field)) {
                    value = !settings.cosmeticEffects();
                    settings.cosmeticEffects(value);
                } else {
                    value = !settings.globalChat();
                    settings.globalChat(value);
                }
                profile.markDirty();
                ProfileManager profiles = core.optional(ProfileManager.class);
                if (profiles != null) {
                    profiles.save(profile);
                }
                core.messages().send(player, "settings.changed", "{setting}", field,
                        "{value}", value ? "enabled" : "disabled");
                refresh(player);
            }
        });
    }

    private Button channel(final ChatChannel current) {
        ItemStack item = Items.item(Material.BOOK_AND_QUILL, 1, (short) 0,
                Text.color("&fChat Channel &7[&b" + current.name().toLowerCase(java.util.Locale.ROOT) + "&7]"),
                lore("&7Where your messages are sent",
                        "&7Public: &f everybody in the lobby",
                        "&7Party: &f only your party",
                        "&7Match: &f only your match",
                        "&7Click to switch"));
        return Button.of(item, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                Profile profile = profile(player);
                if (profile == null || profile.settings() == null) {
                    return;
                }
                ChatChannel[] channels = ChatChannel.values();
                ChatChannel next = channels[(current.ordinal() + 1) % channels.length];
                profile.settings().chatChannel(next);
                profile.markDirty();
                core.messages().send(player, "settings.channel", "{channel}",
                        next.name().toLowerCase(java.util.Locale.ROOT));
                refresh(player);
            }
        });
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }
}
