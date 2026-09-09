package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Players that can be challenged to a duel.
 *
 * <p>Only idle players who accept duel requests are listed, and a click moves on to the kit choice so a
 * request is never sent without a kit. A shift click asks for a ranked duel instead.</p>
 */
public final class DuelPlayerMenu extends PagedMenu {

    public DuelPlayerMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7Nobody can be challenged right now.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.duel", "&8Duel");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        PlayerManager players = core.optional(PlayerManager.class);
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (players == null || viewer == null) {
            return entries;
        }
        for (LightPlayer session : players.online()) {
            if (session == null) {
                continue;
            }
            final Player target = session.player();
            if (target == null || target.equals(viewer) || !target.isOnline()) {
                continue;
            }
            if (!players.isIdle(target.getUniqueId())) {
                continue;
            }
            Profile profile = profiles == null ? null : profiles.getProfile(target.getUniqueId());
            if (profile != null && profile.settings() != null && !profile.settings().receiveDuelRequests()) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7State: &f" + session.state().name().toLowerCase(java.util.Locale.ROOT)));
            if (profile != null) {
                lore.add(Text.color("&7Level: &a" + profile.level()));
                lore.add(Text.color("&7Wins: &a" + profile.statistics().wins() + " &7Losses: &c"
                        + profile.statistics().losses()));
                lore.add(Text.color("&7Best elo: &f" + profile.highestElo(0)));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aLeft click &7for an unranked duel"));
            lore.add(Text.color("&6Shift click &7for a ranked duel"));
            ItemStack icon = Items.skull(target.getName(), 1, Text.color("&b" + target.getName()), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    boolean ranked = type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online == null) {
                        core.messages().send(player, "duel.target-offline", "{player}", target.getName());
                        refresh(player);
                        return;
                    }
                    open(player, new DuelKitMenu(core, online.getUniqueId(), online.getName(), ranked));
                }
            }));
        }
        return entries;
    }
}
