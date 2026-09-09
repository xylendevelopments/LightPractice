package gg.lightpractice.gui.menu;

import gg.lightpractice.api.SpectatorService;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * List of live matches a player may spectate.
 *
 * <p>The menu re-renders every second because matches start and end constantly, and clicking an entry
 * hands the player to the spectator system which applies the spectator state, the tools and the
 * visibility rules.</p>
 */
public final class SpectateMenu extends PagedMenu {

    public SpectateMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7No matches are running.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.spectate", "&8Spectate");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null || viewer == null) {
            return entries;
        }
        for (final Match match : matches.spectatable()) {
            if (match == null) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Kit: &f" + (match.kit() == null ? "unknown" : match.kit().displayName())));
            lore.add(Text.color("&7Arena: &f" + (match.arena() == null ? "none" : match.arena().name())));
            lore.add(Text.color("&7Type: &f" + match.type().name().toLowerCase(java.util.Locale.ROOT)
                    + (match.ranked() ? " &6(ranked)" : "")));
            lore.add(Text.color("&7Running for: &f" + Text.clock(match.elapsedSeconds())));
            lore.add(Text.color("&7Alive: &f" + match.aliveCount() + "&7/&f" + match.participants().size()));
            lore.add(Text.color("&7Spectators: &f" + match.spectators().size()));
            lore.add(Text.color("&7&m                        "));
            StringBuilder names = new StringBuilder();
            for (MatchPlayer participant : match.participants()) {
                if (names.length() > 0) {
                    names.append(Text.color("&7, &f"));
                }
                names.append(Text.color("&f" + participant.name()));
                if (names.length() > 60) {
                    names.append(Text.color("&7..."));
                    break;
                }
            }
            lore.add(Text.color("&7Players: " + names));
            lore.add(Text.color("&aClick &7to spectate"));
            String first = match.participants().isEmpty() ? null : match.participants().iterator().next().name();
            ItemStack icon = first == null
                    ? Items.item(Material.EYE_OF_ENDER, 1, (short) 0, Text.color("&7Match " + match.identifier()), lore)
                    : Items.skull(first, 1, Text.color("&b" + first + " &7vs &b..."), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    SpectatorService spectators = core.optional(SpectatorService.class);
                    if (spectators == null) {
                        core.messages().send(player, "spectate.unavailable");
                        return;
                    }
                    close(player);
                    spectators.spectate(player, match);
                }
            }));
        }
        return entries;
    }
}
