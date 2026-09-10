package gg.lightpractice.scoreboard;

import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sidebar of one player, rendered with teams.
 *
 * <p>Minecraft 1.8 limits a score name to sixteen characters, so every line becomes a team whose entry is
 * an invisible colour code and whose prefix and suffix carry the text. That allows up to thirty two
 * characters per line without ever creating a second scoreboard, and it lets the board be updated in
 * place: only the teams whose text really changed are touched, which keeps the packet count low enough to
 * refresh every player several times a second.</p>
 */
public final class PlayerBoard {

    /** Sixteen unique invisible entries, one per line, which is more than any sidebar needs. */
    private static final int MAX_LINES = 16;
    private static final char CODE = '\u00A7';
    private static final String DIGITS = "0123456789abcdef";

    private final UUID uuid;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Objective health;
    private final Map<Integer, Team> teams = new HashMap<Integer, Team>();
    private String title = "";
    private int lineCount;
    private boolean attached;
    private boolean visible = true;

    /** Builds a fresh board; the caller attaches it to the player. */
    PlayerBoard(Player player, String title, boolean healthObjective) {
        this.uuid = player.getUniqueId();
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("lpboard", "dummy");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.title = title == null ? "" : title;
        this.objective.setDisplayName(this.title);
        if (healthObjective) {
            Objective below = scoreboard.registerNewObjective("lphealth", "health");
            below.setDisplaySlot(DisplaySlot.BELOW_NAME);
            below.setDisplayName(Text.color("&c\u2764"));
            this.health = below;
        } else {
            this.health = null;
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean attached() {
        return attached;
    }

    public boolean visible() {
        return visible;
    }

    public void visible(boolean visible) {
        this.visible = visible;
    }

    public int lineCount() {
        return lineCount;
    }

    /** Hands the board to the player, once. */
    void attach(Player player) {
        if (player == null || attached) {
            return;
        }
        player.setScoreboard(scoreboard);
        attached = true;
    }

    /** Gives the vanilla board back, used on quit, disable and when the sidebar is switched off. */
    void detach(Player player) {
        if (player == null || !attached) {
            return;
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        attached = false;
    }

    /**
     * Renders a title and its lines.
     *
     * <p>Lines beyond {@value #MAX_LINES} are dropped because 1.8 has no unique invisible entry left for
     * them. Empty strings are kept, they render as spacers.</p>
     */
    public void render(String title, List<String> lines) {
        if (!visible) {
            clear();
            return;
        }
        String wanted = title == null ? "" : title;
        if (!wanted.equals(this.title)) {
            this.title = wanted;
            objective.setDisplayName(wanted);
        }
        int count = Math.min(lines == null ? 0 : lines.size(), MAX_LINES);
        for (int index = count; index < lineCount; index++) {
            Team team = teams.remove(Integer.valueOf(index));
            String entry = entry(index);
            scoreboard.resetScores(entry);
            if (team != null) {
                team.unregister();
            }
        }
        for (int index = 0; index < count; index++) {
            String entry = entry(index);
            Team team = teams.get(Integer.valueOf(index));
            if (team == null) {
                team = scoreboard.registerNewTeam("lp" + index);
                team.addEntry(entry);
                teams.put(Integer.valueOf(index), team);
            }
            apply(team, lines.get(index));
            Score score = objective.getScore(entry);
            int value = count - index;
            if (score.getScore() != value) {
                score.setScore(value);
            }
        }
        lineCount = count;
    }

    /** Empties the sidebar but keeps the board attached. */
    public void clear() {
        for (int index = 0; index < lineCount; index++) {
            Team team = teams.remove(Integer.valueOf(index));
            scoreboard.resetScores(entry(index));
            if (team != null) {
                team.unregister();
            }
        }
        lineCount = 0;
    }

    /** Splits a coloured line into the sixteen character prefix and suffix a 1.8 team can hold. */
    private static void apply(Team team, String line) {
        String text = line == null ? "" : Text.color(line);
        if (text.length() <= 16) {
            set(team, text, "");
            return;
        }
        String prefix = text.substring(0, 16);
        String rest = text.substring(16);
        if (prefix.endsWith(String.valueOf(CODE))) {
            // a colour code split in half would escape the entry, so it moves to the suffix
            prefix = prefix.substring(0, prefix.length() - 1);
            rest = CODE + rest;
        }
        String carry = ChatColor.getLastColors(prefix);
        int room = Math.max(0, 16 - carry.length());
        if (rest.length() > room) {
            rest = rest.substring(0, room);
        }
        set(team, prefix, carry + rest);
    }

    private static void set(Team team, String prefix, String suffix) {
        if (!prefix.equals(team.getPrefix())) {
            team.setPrefix(prefix);
        }
        if (!suffix.equals(team.getSuffix())) {
            team.setSuffix(suffix);
        }
    }

    /** Invisible and unique per line, built from colour codes only. */
    private static String entry(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index;
        do {
            builder.append(CODE).append(DIGITS.charAt(value % 16));
            value /= 16;
        } while (value > 0 && builder.length() <= 14);
        return builder.toString();
    }

    public String toString() {
        return "PlayerBoard{" + uuid + ", lines=" + lineCount + ", attached=" + attached + '}';
    }
}
