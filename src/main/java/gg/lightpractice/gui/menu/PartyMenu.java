package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.party.Party;
import gg.lightpractice.party.PartyManager;
import gg.lightpractice.party.PartyMember;
import gg.lightpractice.party.PartyRank;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Party screen.
 *
 * <p>Members see their party with every action a leader may take, players without a party see the open
 * parties they can join plus their pending invite. All actions go through {@link PartyManager} so the
 * permission checks, announcements and match starts behave exactly like the party commands.</p>
 */
public final class PartyMenu extends PagedMenu {

    public PartyMenu(PluginCore core) {
        super(core, title(core), 5, null, "&7Nothing to show here.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.party", "&8Party");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final PartyManager parties = core.optional(PartyManager.class);
        if (parties == null || viewer == null) {
            return entries;
        }
        final Party own = parties.get(viewer);
        if (own != null) {
            for (final PartyMember member : own.members()) {
                if (member == null) {
                    continue;
                }
                boolean leader = viewer.getUniqueId().equals(own.leader());
                boolean self = viewer.getUniqueId().equals(member.uuid());
                List<String> lore = new ArrayList<String>();
                lore.add(Text.color("&7Rank: &f" + member.rank().displayName()));
                lore.add(Text.color("&7Online: " + (member.isOnline() ? "&ayes" : "&cno")));
                lore.add(Text.color("&7Joined: &f" + Text.duration(System.currentTimeMillis()
                        - member.joinedAt()) + " ago"));
                if (leader && !self) {
                    lore.add(Text.color("&7&m                        "));
                    lore.add(Text.color("&eRight click &7to promote"));
                    lore.add(Text.color("&cLeft click &7to kick"));
                } else if (!self) {
                    lore.add(Text.color("&7&m                        "));
                    lore.add(Text.color("&aClick &7to view their stats"));
                }
                ItemStack icon = Items.skull(member.name(), 1,
                        Text.color((member.rank() == PartyRank.LEADER ? "&6" : "&7") + member.name()), lore);
                entries.add(Button.of(icon, new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        PartyManager manager = core.optional(PartyManager.class);
                        Party party = manager == null ? null : manager.get(player);
                        if (manager == null || party == null) {
                            return;
                        }
                        Player target = Bukkit.getPlayer(member.uuid());
                        if (viewer.getUniqueId().equals(member.uuid())) {
                            open(player, new StatsMenu(core, player.getUniqueId(), player.getName()));
                            return;
                        }
                        if (!party.isLeader(player.getUniqueId())) {
                            open(player, new StatsMenu(core, member.uuid(), member.name()));
                            return;
                        }
                        if (type == ClickType.RIGHT) {
                            if (target == null) {
                                core.messages().send(player, "party.target-offline",
                                        "{player}", member.name());
                                refresh(player);
                                return;
                            }
                            manager.promote(player, target);
                        } else {
                            if (target == null) {
                                core.messages().send(player, "party.target-offline",
                                        "{player}", member.name());
                                refresh(player);
                                return;
                            }
                            manager.kick(player, target);
                        }
                        refresh(player);
                    }
                }));
            }
            return entries;
        }
        for (final Party party : parties.openParties()) {
            if (party == null) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.add(Text.color("&7Leader: &f" + nameOf(parties, party.leader())));
            lore.add(Text.color("&7Members: &f" + party.size() + "&7/&f" + party.maxSize()));
            lore.add(Text.color("&7Online: &f" + party.onlineCount()));
            lore.add(Text.color("&7Kit: &f" + (party.kitId().isEmpty() ? "any" : party.kitId())));
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aClick &7to join this party"));
            ItemStack icon = Items.skull(nameOf(parties, party.leader()), 1,
                    Text.color("&dParty of &f" + nameOf(parties, party.leader())), lore);
            entries.add(Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    PartyManager manager = core.optional(PartyManager.class);
                    if (manager == null) {
                        return;
                    }
                    if (manager.joinOpen(player, party)) {
                        close(player);
                    } else {
                        refresh(player);
                    }
                }
            }));
        }
        Party invited = parties.invitedParty(viewer.getUniqueId());
        if (invited != null) {
            List<String> lore = lore("&7Leader: &f" + nameOf(parties, invited.leader()),
                    "&7Members: &f" + invited.size(),
                    "&7&m                        ",
                    "&aLeft click &7to accept", "&cRight click &7to decline");
            ItemStack icon = Items.item(Material.BOOK_AND_QUILL, 1, (short) 0,
                    Text.color("&6Pending Invite"), lore);
            entries.add(0, Button.of(icon, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    PartyManager manager = core.optional(PartyManager.class);
                    if (manager == null) {
                        return;
                    }
                    if (type == ClickType.RIGHT) {
                        manager.deny(player);
                    } else if (manager.accept(player)) {
                        close(player);
                        return;
                    }
                    refresh(player);
                }
            }));
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, final Player viewer) {
        final PartyManager parties = core.optional(PartyManager.class);
        int lastRow = (rows() - 1) * 9;
        if (parties == null || viewer == null) {
            map.put(Integer.valueOf(lastRow + 4), closeButton());
            return;
        }
        final Party party = parties.get(viewer);
        if (party == null) {
            ItemStack create = Items.item(Material.NAME_TAG, 1, (short) 0, Text.color("&aCreate Party"),
                    lore("&7Start your own party", "&7Maximum size: &f" + parties.maxSize()));
            map.put(Integer.valueOf(lastRow + 3), Button.of(create, new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    PartyManager manager = core.optional(PartyManager.class);
                    if (manager != null && manager.create(player) != null) {
                        refresh(player);
                    }
                }
            }));
            map.put(Integer.valueOf(lastRow + 4), closeButton());
            return;
        }
        boolean leader = party.isLeader(viewer.getUniqueId());
        map.put(Integer.valueOf(lastRow + 1), toggle(leader, party.open(), "&dOpen Party",
                "&7Let players join without an invite", new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        PartyManager manager = core.optional(PartyManager.class);
                        Party current = manager == null ? null : manager.get(player);
                        if (manager != null && current != null) {
                            manager.setOpen(current, !current.open());
                        }
                        refresh(player);
                    }
                }));
        map.put(Integer.valueOf(lastRow + 2), toggle(leader, party.ffaAllowed(), "&cAllow FFA",
                "&7Let the party fight a free for all", new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        PartyManager manager = core.optional(PartyManager.class);
                        Party current = manager == null ? null : manager.get(player);
                        if (manager != null && current != null) {
                            manager.setFfaAllowed(current, !current.ffaAllowed());
                        }
                        refresh(player);
                    }
                }));
        ItemStack kit = Items.item(Material.CHEST, 1, (short) 0, Text.color("&bParty Kit"),
                lore("&7Current: &f" + (party.kitId().isEmpty() ? "none" : party.kitId()),
                        leader ? "&7Click to choose a kit" : "&7Only the leader may change this"));
        map.put(Integer.valueOf(lastRow + 3), leader ? Button.of(kit, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                open(player, new PartyKitMenu(core));
            }
        }) : Button.display(kit));
        ItemStack ffa = Items.item(Material.IRON_SWORD, 1, (short) 0, Text.color("&cStart FFA"),
                lore("&7Everybody against everybody",
                        "&7Online members: &f" + party.onlineCount(),
                        "&7Needs: &f3 players and FFA allowed"));
        map.put(Integer.valueOf(lastRow + 5), leader ? Button.of(ffa, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                PartyManager manager = core.optional(PartyManager.class);
                Party current = manager == null ? null : manager.get(player);
                if (manager != null && current != null) {
                    if (manager.startFfa(current)) {
                        close(player);
                    }
                }
            }
        }) : Button.display(ffa));
        ItemStack split = Items.item(Material.DIAMOND_SWORD, 1, (short) 0, Text.color("&aStart Split"),
                lore("&7The party is split into even teams",
                        "&7Online members: &f" + party.onlineCount(),
                        "&7Needs: &f4 players"));
        map.put(Integer.valueOf(lastRow + 6), leader ? Button.of(split, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                PartyManager manager = core.optional(PartyManager.class);
                Party current = manager == null ? null : manager.get(player);
                if (manager != null && current != null) {
                    if (manager.startSplit(current)) {
                        close(player);
                    }
                }
            }
        }) : Button.display(split));
        ItemStack leave = leader
                ? Items.item(Material.BARRIER, 1, (short) 0, Text.color("&4Disband Party"),
                        lore("&7Kick everybody and close the party"))
                : Items.item(Material.INK_SACK, 1, (short) 1, Text.color("&cLeave Party"),
                        lore("&7Leave this party"));
        map.put(Integer.valueOf(lastRow + 7), Button.of(leave, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                PartyManager manager = core.optional(PartyManager.class);
                Party current = manager == null ? null : manager.get(player);
                if (manager == null || current == null) {
                    return;
                }
                if (current.isLeader(player.getUniqueId())) {
                    manager.disband(player);
                } else {
                    manager.leave(player);
                }
                close(player);
            }
        }));
        map.put(Integer.valueOf(lastRow + 4), closeButton());
    }

    private Button toggle(boolean mayUse, boolean enabled, String name, String description,
                          ClickAction action) {
        ItemStack item = Items.item(Material.LEVER, 1, (short) 0,
                Text.color(name + " &7[" + (enabled ? "&aOn&7" : "&cOff&7") + "]"),
                lore(description, mayUse ? "&7Click to toggle" : "&7Only the leader may change this"));
        return mayUse ? Button.of(item, action) : Button.display(item);
    }

    /** Name of a member, falling back to the stored profile name so no uuid is ever shown. */
    private String nameOf(PartyManager parties, UUID uuid) {
        Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        if (parties != null && uuid != null) {
            for (Party party : parties.parties()) {
                PartyMember member = party == null ? null : party.member(uuid);
                if (member != null && member.name() != null) {
                    return member.name();
                }
            }
        }
        ProfileManager profiles = core.optional(ProfileManager.class);
        Profile profile = profiles == null || uuid == null ? null : profiles.getProfile(uuid);
        if (profile != null && profile.name() != null) {
            return profile.name();
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null || uuid == null ? null : players.get(uuid);
        return session != null ? session.name() : "unknown";
    }
}
