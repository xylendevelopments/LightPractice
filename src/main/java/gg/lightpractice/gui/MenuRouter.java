package gg.lightpractice.gui;

import gg.lightpractice.gui.menu.BotMenu;
import gg.lightpractice.gui.menu.CosmeticsMenu;
import gg.lightpractice.gui.menu.DailyRewardMenu;
import gg.lightpractice.gui.menu.DuelPlayerMenu;
import gg.lightpractice.gui.menu.EventMenu;
import gg.lightpractice.gui.menu.HistoryMenu;
import gg.lightpractice.gui.menu.KitSelectMenu;
import gg.lightpractice.gui.menu.LeaderboardMenu;
import gg.lightpractice.gui.menu.MainMenu;
import gg.lightpractice.gui.menu.PartyMenu;
import gg.lightpractice.gui.menu.QueueMenu;
import gg.lightpractice.gui.menu.SettingsMenu;
import gg.lightpractice.gui.menu.SpectateMenu;
import gg.lightpractice.gui.menu.StatsMenu;
import gg.lightpractice.gui.menu.TournamentMenu;
import gg.lightpractice.lobby.LobbyManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Turns a configured action name into a menu.
 *
 * <p>Lobby items, the {@code /menu} command and NPC integrations all describe their target with a single
 * word, so the mapping lives here instead of being repeated in every caller. Two generic forms are
 * supported as well: {@code command:<line>} runs a command as the player and {@code message:<key>} sends a
 * message, which makes custom lobby items possible without touching Java.</p>
 */
public final class MenuRouter {

    private static final List<String> KNOWN = Collections.unmodifiableList(Arrays.asList(
            "menu", "kits", "queue", "spectate", "party", "duel", "stats", "leaderboard",
            "cosmetics", "settings", "history", "daily", "bots", "events", "tournaments",
            "editor", "hub"));

    private MenuRouter() {
    }

    /** Every action word this router understands, used for tab completion and validation. */
    public static List<String> actions() {
        return KNOWN;
    }

    /** Whether an action word is known, used to validate configuration on load. */
    public static boolean known(String action) {
        return menuKey(action) != null || generic(action) != null;
    }

    /**
     * Runs an action for a player.
     *
     * @return true when the action was understood and carried out
     */
    public static boolean run(PluginCore core, Player player, String action) {
        if (core == null || player == null) {
            return false;
        }
        String key = normalize(action);
        if (key.isEmpty()) {
            return false;
        }
        String generic = generic(key);
        if (generic != null) {
            return runGeneric(core, player, key, generic);
        }
        if ("hub".equals(menuKey(key))) {
            LobbyManager lobby = core.optional(LobbyManager.class);
            if (lobby == null) {
                return false;
            }
            return lobby.sendToLobby(player);
        }
        Menu menu = resolve(core, player, key);
        if (menu == null) {
            Debug.log(DebugCategory.GUI, "Unknown menu action {} of {}", action, player.getName());
            core.messages().send(player, "gui.unknown-action", "{action}", key);
            return false;
        }
        GuiManager gui = core.optional(GuiManager.class);
        if (gui == null) {
            return false;
        }
        return gui.open(player, menu);
    }

    /** Builds the menu an action word stands for, or {@code null} when the word is unknown. */
    public static Menu resolve(PluginCore core, Player player, String action) {
        String key = menuKey(normalize(action));
        if (key == null) {
            return null;
        }
        if ("menu".equals(key)) {
            return new MainMenu(core);
        }
        if ("kits".equals(key) || "editor".equals(key)) {
            return new KitSelectMenu(core);
        }
        if ("queue".equals(key)) {
            return new QueueMenu(core);
        }
        if ("spectate".equals(key)) {
            return new SpectateMenu(core);
        }
        if ("party".equals(key)) {
            return new PartyMenu(core);
        }
        if ("duel".equals(key)) {
            return new DuelPlayerMenu(core);
        }
        if ("stats".equals(key)) {
            return new StatsMenu(core, player.getUniqueId(), player.getName());
        }
        if ("leaderboard".equals(key)) {
            return new LeaderboardMenu(core);
        }
        if ("cosmetics".equals(key)) {
            return new CosmeticsMenu(core);
        }
        if ("settings".equals(key)) {
            return new SettingsMenu(core);
        }
        if ("history".equals(key)) {
            return new HistoryMenu(core, player.getUniqueId(), 0);
        }
        if ("daily".equals(key)) {
            return new DailyRewardMenu(core);
        }
        if ("bots".equals(key)) {
            return new BotMenu(core);
        }
        if ("events".equals(key)) {
            return new EventMenu(core);
        }
        if ("tournaments".equals(key)) {
            return new TournamentMenu(core);
        }
        // the hub action is carried out by run(), it does not open a menu
        return null;
    }

    /** Maps every accepted spelling onto one internal key. */
    private static String menuKey(String key) {
        if ("menu".equals(key) || "main".equals(key) || "home".equals(key)) {
            return "menu";
        }
        if ("kits".equals(key) || "kit".equals(key) || "kitselect".equals(key)) {
            return "kits";
        }
        if ("editor".equals(key) || "kiteditor".equals(key) || "edit".equals(key)) {
            return "editor";
        }
        if ("queue".equals(key) || "queues".equals(key) || "play".equals(key) || "unranked".equals(key)
                || "ranked".equals(key)) {
            return "queue";
        }
        if ("spectate".equals(key) || "spectator".equals(key) || "spectators".equals(key)
                || "watch".equals(key)) {
            return "spectate";
        }
        if ("party".equals(key) || "parties".equals(key)) {
            return "party";
        }
        if ("duel".equals(key) || "duels".equals(key)) {
            return "duel";
        }
        if ("stats".equals(key) || "statistics".equals(key) || "stat".equals(key)) {
            return "stats";
        }
        if ("leaderboard".equals(key) || "leaderboards".equals(key) || "top".equals(key)
                || "ranking".equals(key)) {
            return "leaderboard";
        }
        if ("cosmetics".equals(key) || "cosmetic".equals(key) || "store".equals(key)
                || "shop".equals(key)) {
            return "cosmetics";
        }
        if ("settings".equals(key) || "options".equals(key) || "preferences".equals(key)) {
            return "settings";
        }
        if ("history".equals(key) || "matches".equals(key) || "matchhistory".equals(key)) {
            return "history";
        }
        if ("daily".equals(key) || "reward".equals(key) || "rewards".equals(key)
                || "dailyreward".equals(key)) {
            return "daily";
        }
        if ("bots".equals(key) || "bot".equals(key) || "practicebot".equals(key)) {
            return "bots";
        }
        if ("events".equals(key) || "event".equals(key)) {
            return "events";
        }
        if ("tournaments".equals(key) || "tournament".equals(key) || "tourneys".equals(key)) {
            return "tournaments";
        }
        if ("hub".equals(key) || "lobby".equals(key) || "spawn".equals(key)) {
            return "hub";
        }
        return null;
    }

    /** Payload of a {@code command:} or {@code message:} action, {@code null} for plain menu words. */
    private static String generic(String key) {
        if (key.startsWith("command:") || key.startsWith("cmd:")) {
            return "command:" + key.substring(key.indexOf(':') + 1);
        }
        if (key.startsWith("message:") || key.startsWith("msg:")) {
            return "message:" + key.substring(key.indexOf(':') + 1);
        }
        return null;
    }

    private static boolean runGeneric(PluginCore core, Player player, String key, String generic) {
        if (generic.startsWith("command:")) {
            String line = generic.substring("command:".length()).trim();
            if (line.isEmpty()) {
                return false;
            }
            Debug.log(DebugCategory.GUI, "{} ran the lobby command {}", player.getName(), line);
            player.chat(line.startsWith("/") ? line : "/" + line);
            return true;
        }
        String message = generic.substring("message:".length()).trim();
        if (message.isEmpty()) {
            return false;
        }
        player.sendMessage(Text.color(message));
        return true;
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT).replace("-", "")
                .replace(" ", "").replace("_", "");
    }

    /** Every action word plus the two generic forms, used by the configuration validator. */
    public static List<String> describe() {
        List<String> result = new ArrayList<String>(KNOWN);
        result.add("command:<line>");
        result.add("message:<text>");
        return result;
    }
}
