package gg.lightpractice.match;

import gg.lightpractice.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One side of a match.
 *
 * <p>Solo matches use two teams of one, free for all matches use one team per participant, so the rest
 * of the engine never needs a special case: it asks the teams who is alive and who won.</p>
 */
public final class MatchTeam {

    private final String name;
    private final String displayName;
    private final String color;
    private final List<MatchPlayer> members = new CopyOnWriteArrayList<MatchPlayer>();
    private Location spawn;
    private boolean eliminated;
    private boolean respawnEnabled = true;
    private int score;

    public MatchTeam(String name, String displayName, String color, Location spawn) {
        this.name = name == null ? "team" : name;
        this.displayName = displayName == null ? this.name : Text.color(displayName);
        this.color = color == null ? "&7" : Text.color(color);
        this.spawn = spawn;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    /** Display name wrapped in the team colour, used by scoreboards and messages. */
    public String coloredName() {
        return color + Text.strip(displayName);
    }

    public Location spawn() {
        return spawn;
    }

    public void spawn(Location spawn) {
        this.spawn = spawn;
    }

    public void add(MatchPlayer player) {
        if (player != null && !members.contains(player)) {
            members.add(player);
            player.team(this);
        }
    }

    public boolean remove(MatchPlayer player) {
        return player != null && members.remove(player);
    }

    public List<MatchPlayer> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    /** Online players of this team, used for teleporting and broadcasting. */
    public List<Player> players() {
        List<Player> players = new ArrayList<Player>();
        for (MatchPlayer member : members) {
            Player player = member.player();
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** Members that are still in the fight. */
    public List<MatchPlayer> alive() {
        List<MatchPlayer> alive = new ArrayList<MatchPlayer>();
        for (MatchPlayer member : members) {
            if (!member.eliminated()) {
                alive.add(member);
            }
        }
        return alive;
    }

    public List<Player> alivePlayers() {
        List<Player> players = new ArrayList<Player>();
        for (MatchPlayer member : alive()) {
            Player player = member.player();
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public int aliveCount() {
        return alive().size();
    }

    public boolean eliminated() {
        return eliminated;
    }

    public void eliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    /** False once the team lost its respawn right, for example when its bed was destroyed. */
    public boolean respawnEnabled() {
        return respawnEnabled;
    }

    public void respawnEnabled(boolean respawnEnabled) {
        this.respawnEnabled = respawnEnabled;
    }

    public int score() {
        return score;
    }

    public void score(int score) {
        this.score = Math.max(0, score);
    }

    public void addScore(int amount) {
        this.score = Math.max(0, this.score + amount);
    }

    public boolean contains(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        for (MatchPlayer member : members) {
            if (uuid.equals(member.uuid())) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(Player player) {
        return player != null && contains(player.getUniqueId());
    }

    public MatchPlayer member(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (MatchPlayer member : members) {
            if (uuid.equals(member.uuid())) {
                return member;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "MatchTeam{" + name + ", size=" + members.size() + ", eliminated=" + eliminated + '}';
    }
}
