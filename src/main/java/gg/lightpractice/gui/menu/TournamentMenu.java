package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.tournament.Bracket;
import gg.lightpractice.tournament.Tournament;
import gg.lightpractice.tournament.TournamentManager;
import gg.lightpractice.tournament.TournamentState;
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
 * Tournament screen: enter the running bracket or host a new tournament.
 *
 * <p>While a tournament lives, its state, entry count and the pairings of the current round are shown.
 * Otherwise every kit that can be played becomes a host button: a left click starts a solo bracket, a
 * right click a two versus two bracket.</p>
 */
public final class TournamentMenu extends PagedMenu {

    public TournamentMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7No kits are available.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.tournament", "&8Tournament");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final TournamentManager tournaments = core.optional(TournamentManager.class);
        if (tournaments == null || viewer == null) {
            return entries;
        }
        Tournament active = tournaments.active();
        if (active != null) {
            entries.add(infoButton(tournaments, active, viewer));
            for (final Bracket bracket : active.brackets()) {
                if (bracket == null) {
                    continue;
                }
                List<String> lore = new ArrayList<String>();
                lore.add(Text.color("&7Home: " + tournaments.namesOf(bracket.home())));
                if (bracket.bye()) {
                    lore.add(Text.color("&7Away: &fbye"));
                } else {
                    lore.add(Text.color("&7Away: " + tournaments.namesOf(bracket.away())));
                }
                if (bracket.finished()) {
                    lore.add(Text.color("&7Result: " + tournaments.namesOf(bracket.survivors())
                            + " &aadvanced"));
                } else if (bracket.matchId() != null) {
                    lore.add(Text.color("&7Playing in match &f" + bracket.matchId()));
                } else {
                    lore.add(Text.color("&7Waiting for an arena"));
                }
                ItemStack icon = Items.item(bracket.finished() ? Material.INK_SACK : Material.IRON_SWORD, 1,
                        bracket.finished() ? (short) 10 : (short) 0,
                        Text.color("&7Pairing &f#" + (bracket.index() + 1)), lore);
                entries.add(Button.display(icon));
            }
            return entries;
        }
        for (final gg.lightpractice.kit.Kit kit : tournaments.kitsForHosting()) {
            List<String> lore = new ArrayList<String>();
            for (String line : kit.description()) {
                lore.add(Text.color(line));
            }
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aLeft click &7to host a solo bracket"));
            lore.add(Text.color("&eRight click &7to host a " + tournaments.maxTeamSize() + "v"
                    + tournaments.maxTeamSize() + " bracket"));
            lore.add(Text.color("&7Entries: &f" + tournaments.minPlayers() + " &7- &f"
                    + tournaments.maxPlayers()));
            lore.add(Text.color("&7Gathering: &f" + tournaments.joinSeconds() + "s"));
            ItemStack icon = Items.decorate(kit.icon(), kit.displayName(), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    TournamentManager manager = core.optional(TournamentManager.class);
                    if (manager == null) {
                        return;
                    }
                    if (!player.hasPermission("lightpractice.tournament.host")) {
                        core.messages().send(player, "tournament.no-permission");
                        return;
                    }
                    close(player);
                    manager.create(player, kit.id(), type == ClickType.RIGHT ? manager.maxTeamSize() : 1);
                }
            }));
        }
        return entries;
    }

    private Button infoButton(final TournamentManager tournaments, final Tournament active,
                              final Player viewer) {
        boolean entered = active.isParticipant(viewer.getUniqueId());
        List<String> lore = new ArrayList<String>();
        lore.add(Text.color("&7Kit: &f" + tournaments.kitOf(active).displayName()));
        lore.add(Text.color("&7Host: &f" + active.hostName()));
        lore.add(Text.color("&7State: &f" + active.state().configKey()));
        lore.add(Text.color("&7Players: &f" + active.size() + "&7/&f" + active.maxPlayers()));
        lore.add(Text.color("&7Team size: &f" + active.teamSize() + "v" + active.teamSize()));
        if (active.state() == TournamentState.GATHERING) {
            lore.add(Text.color("&7Entries close in: &f" + Text.duration(active.remainingMillis())));
        }
        if (active.state() == TournamentState.RUNNING) {
            lore.add(Text.color("&7Round: &f" + active.round()));
        }
        lore.add(Text.color("&7&m                        "));
        if (entered) {
            lore.add(Text.color("&cClick &7to leave the tournament"));
        } else if (active.state().joinable()) {
            lore.add(Text.color("&aClick &7to enter the tournament"));
        } else {
            lore.add(Text.color("&7Entries are closed"));
        }
        ItemStack icon = Items.item(Material.GOLD_SWORD, 1, (short) 0,
                Text.color("&6Tournament &7- &f" + Text.strip(tournaments.kitOf(active).displayName())), lore);
        return Button.of(icon, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                TournamentManager manager = core.optional(TournamentManager.class);
                if (manager == null) {
                    return;
                }
                if (active.isParticipant(player.getUniqueId())) {
                    manager.leave(player);
                } else {
                    manager.join(player);
                }
                refresh(player);
            }
        });
    }

    @Override
    protected void decorate(Map<Integer, Button> map, final Player viewer) {
        final TournamentManager tournaments = core.optional(TournamentManager.class);
        int lastRow = (rows() - 1) * 9;
        Tournament active = tournaments == null ? null : tournaments.active();
        Tournament completed = tournaments == null ? null : tournaments.completed();
        ItemStack info = active == null
                ? Items.item(Material.BOOK, 1, (short) 0, Text.color("&eTournaments"),
                        completed == null ? lore("&7No tournament is running", "&7Pick a kit to host one")
                                : lore("&7No tournament is running",
                                        "&7Last winner: " + tournaments.namesOf(
                                                java.util.Collections.singletonList(completed.winner())),
                                        "&7Rounds played: &f" + completed.round()))
                : Items.item(Material.BOOK, 1, (short) 0, Text.color("&eRunning Tournament"),
                        lore("&7State: &f" + active.state().configKey(),
                                "&7Round: &f" + active.round(),
                                "&7Pairings: &f" + active.brackets().size(),
                                "&7Entered: &f" + active.size() + "&7/&f" + active.maxPlayers()));
        map.put(Integer.valueOf(lastRow + 3), Button.display(info));
        if (active != null && active.state() == TournamentState.GATHERING
                && (active.host() == null || active.host().equals(viewer == null ? null : viewer.getUniqueId())
                        || (viewer != null && viewer.hasPermission("lightpractice.tournament.admin")))) {
            ItemStack start = Items.item(Material.EMERALD, 1, (short) 0, Text.color("&aStart Now"),
                    lore("&7Close the entries and begin the bracket",
                            "&7Needs: &f" + active.minPlayers() + " &7players"));
            map.put(Integer.valueOf(lastRow + 6), Button.of(start, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    TournamentManager manager = core.optional(TournamentManager.class);
                    if (manager != null) {
                        manager.start(player);
                    }
                    refresh(player);
                }
            }));
        }
        if (active != null && viewer != null && viewer.hasPermission("lightpractice.tournament.admin")) {
            ItemStack stop = Items.item(Material.BARRIER, 1, (short) 0, Text.color("&cStop Tournament"),
                    lore("&7Cancel the tournament"));
            map.put(Integer.valueOf(lastRow + 7), Button.of(stop, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    TournamentManager manager = core.optional(TournamentManager.class);
                    if (manager != null) {
                        manager.stop(player, "stopped from the tournament menu");
                    }
                    refresh(player);
                }
            }));
        }
    }
}
