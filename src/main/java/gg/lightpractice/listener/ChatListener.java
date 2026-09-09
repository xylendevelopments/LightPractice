package gg.lightpractice.listener;

import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.match.ChatChannel;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.model.Division;
import gg.lightpractice.party.Party;
import gg.lightpractice.party.PartyManager;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.PlayerSettings;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.statistics.StatisticsManager;
import gg.lightpractice.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Chat channels and command gating.
 *
 * <p>The channel of a player decides where the message goes: party chat reaches the members, match chat
 * reaches everybody watching the fight, global chat reaches everyone who has global chat enabled.
 * Spectators get their own line so they never leak callouts into a live match.</p>
 */
public final class ChatListener implements Listener {

    private final PluginCore core;

    public ChatListener(PluginCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player sender = event.getPlayer();
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(sender);
        String message = event.getMessage() == null ? "" : event.getMessage();
        ChatChannel channel = channelOf(session, message);
        event.setCancelled(true);
        if (channel == ChatChannel.PARTY) {
            PartyManager parties = core.optional(PartyManager.class);
            Party party = parties == null ? null : parties.get(sender.getUniqueId());
            if (party != null) {
                parties.chat(sender, stripPrefix(message));
                return;
            }
            core.messages().send(sender, "party.none");
        }
        if (channel == ChatChannel.MATCH) {
            Match match = matchOf(sender, session);
            if (match != null) {
                match.broadcast("chat.match", "{player}", sender.getName(),
                        "{message}", stripPrefix(message));
                return;
            }
            core.messages().send(sender, "chat.not-in-match");
        }
        broadcastGlobal(sender, session, stripPrefix(message), event.getRecipients());
    }

    /** Party and match prefixes win over the stored channel so a quick callout is always possible. */
    private ChatChannel channelOf(LightPlayer session, String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.startsWith("@") || trimmed.startsWith("!p")) {
            return ChatChannel.PARTY;
        }
        if (trimmed.startsWith("!")) {
            return ChatChannel.MATCH;
        }
        if (session == null || session.chatChannel() == null) {
            return ChatChannel.PUBLIC;
        }
        return session.chatChannel();
    }

    private String stripPrefix(String message) {
        String trimmed = message == null ? "" : message.trim();
        while (trimmed.startsWith("@") || trimmed.startsWith("!")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("p ")) {
            trimmed = trimmed.substring(2).trim();
        }
        return trimmed;
    }

    /** The match a player belongs to, or the one they are watching. */
    private Match matchOf(Player player, LightPlayer session) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null) {
            return null;
        }
        Match match = matches.getMatch(player.getUniqueId());
        if (match != null) {
            return match;
        }
        if (session != null && session.spectatedMatchId() != null) {
            return matches.get(session.spectatedMatchId());
        }
        return null;
    }

    /** Sends a global line to everybody who wants to read global chat. */
    private void broadcastGlobal(Player sender, LightPlayer session, String message,
                                 Collection<Player> recipients) {
        String line = format(sender, session, message);
        Collection<Player> targets = recipients == null || recipients.isEmpty()
                ? core.plugin().getServer().getOnlinePlayers()
                : recipients;
        for (Player receiver : targets) {
            if (receiver == null) {
                continue;
            }
            if (receiver.equals(sender)) {
                receiver.sendMessage(line);
                continue;
            }
            Profile profile = profileOf(receiver);
            PlayerSettings settings = profile == null ? null : profile.settings();
            if (settings != null && !settings.globalChat()) {
                continue;
            }
            receiver.sendMessage(line);
        }
    }

    private Profile profileOf(Player player) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || player == null ? null : profiles.getProfile(player);
    }

    private String format(Player sender, LightPlayer session, String message) {
        Profile profile = session == null ? null : session.profile();
        String division = "";
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        if (statistics != null && profile != null) {
            Division ranked = statistics.division(profile.uuid(), null);
            if (ranked != null) {
                division = Text.color(ranked.colored() + " ");
            }
        }
        boolean spectating = session != null && session.spectating();
        boolean inMatch = session != null && session.inMatch();
        return core.messages().raw(inMatch || spectating ? "chat.match-format" : "chat.global-format",
                "{prefix}", prefixOf(profile),
                "{player}", sender.getName(),
                "{division}", division,
                "{level}", profile == null ? "1" : String.valueOf(profile.level()),
                "{coins}", profile == null ? "0" : String.valueOf(profile.coins()),
                "{spectator}", spectating ? Text.color("&7[Spectator] ") : "",
                "{message}", Text.color(message));
    }

    /** The ranked division of a player, shown in front of their name in global chat. */
    private String prefixOf(Profile profile) {
        if (profile == null) {
            return "";
        }
        StatisticsManager statistics = core.optional(StatisticsManager.class);
        if (statistics == null || statistics.divisions() == null) {
            return "";
        }
        int elo = profile.highestElo(0);
        if (elo <= 0) {
            return "";
        }
        Division division = statistics.divisions().byElo(elo);
        if (division == null) {
            return "";
        }
        return Text.color("&7[") + Text.color(division.color()) + division.displayWithTier(elo)
                + Text.color("&7] ");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String line = event.getMessage() == null ? "" : event.getMessage().trim();
        String command = line.startsWith("/") ? line.substring(1) : line;
        int space = command.indexOf(' ');
        if (space > 0) {
            command = command.substring(0, space);
        }
        command = command.toLowerCase(Locale.ROOT);
        if (command.isEmpty()) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null && combat.isTagged(player.getUniqueId())
                && !combat.commandAllowed(command)) {
            event.setCancelled(true);
            core.messages().send(player, "combat.command-blocked", "{command}", command,
                    "{seconds}", String.valueOf(combat.remainingTag(player.getUniqueId()) / 1000L));
            return;
        }
        MatchManager matches = core.optional(MatchManager.class);
        boolean inMatch = matches != null && matches.getMatch(player.getUniqueId()) != null;
        boolean spectating = matches != null && matches.isSpectating(player.getUniqueId());
        if (!inMatch && !spectating) {
            return;
        }
        for (String allowed : allowedCommands()) {
            if (allowed.equalsIgnoreCase(command)) {
                return;
            }
        }
        event.setCancelled(true);
        core.messages().send(player, spectating ? "spectator.command-blocked" : "match.command-blocked",
                "{command}", command);
    }

    private List<String> allowedCommands() {
        List<String> result = core.configs().config().getStringList("match.allowed-commands");
        if (result == null || result.isEmpty()) {
            result = new ArrayList<String>();
            result.add("leave");
            result.add("spectate");
            result.add("msg");
            result.add("r");
            result.add("tell");
            result.add("w");
            result.add("help");
            result.add("kit");
        }
        return result;
    }
}
