package gg.lightpractice.match;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Everything needed to start a match: the kit, the format, the sides and optionally a fixed arena.
 *
 * <p>Queues, duels, parties, tournaments, events and admin commands all build the same request object,
 * so {@link MatchManager} has exactly one start path to validate.</p>
 */
public final class MatchRequest {

    /** Colours handed to teams that do not configure their own, in team order. */
    private static final String[] PALETTE = {"&c", "&9", "&a", "&e", "&d", "&b", "&6", "&5"};
    private static final String[] NAMES = {"red", "blue", "green", "yellow", "pink", "aqua", "gold", "purple"};

    private final Kit kit;
    private final MatchType type;
    private final boolean ranked;
    private final List<TeamSpec> teams = new ArrayList<TeamSpec>();
    private Arena arena;
    private String source = "command";
    private int countdownSeconds = -1;
    private boolean spectatorsAllowed = true;
    private boolean resetArena = true;

    public MatchRequest(Kit kit, MatchType type, boolean ranked) {
        this.kit = kit;
        this.type = type == null ? MatchType.SOLO : type;
        this.ranked = ranked;
    }

    public Kit kit() {
        return kit;
    }

    public MatchType type() {
        return type;
    }

    public boolean ranked() {
        return ranked;
    }

    public Arena arena() {
        return arena;
    }

    public MatchRequest arena(Arena arena) {
        this.arena = arena;
        return this;
    }

    public String source() {
        return source;
    }

    public MatchRequest source(String source) {
        this.source = source == null || source.trim().isEmpty() ? "command" : source.trim().toLowerCase(Locale.ROOT);
        return this;
    }

    /** Countdown override in seconds, {@code -1} keeps the configured value. */
    public int countdownSeconds() {
        return countdownSeconds;
    }

    public MatchRequest countdownSeconds(int countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
        return this;
    }

    public boolean spectatorsAllowed() {
        return spectatorsAllowed;
    }

    public MatchRequest spectatorsAllowed(boolean spectatorsAllowed) {
        this.spectatorsAllowed = spectatorsAllowed;
        return this;
    }

    public boolean resetArena() {
        return resetArena;
    }

    public MatchRequest resetArena(boolean resetArena) {
        this.resetArena = resetArena;
        return this;
    }

    public List<TeamSpec> teams() {
        return Collections.unmodifiableList(teams);
    }

    public MatchRequest addTeam(TeamSpec team) {
        if (team != null) {
            teams.add(team);
        }
        return this;
    }

    /** Adds a team with a colour and name from the default palette. */
    public TeamSpec addTeam(Collection<UUID> members) {
        return addTeam(null, null, members);
    }

    public TeamSpec addTeam(String name, Collection<UUID> members) {
        return addTeam(name, null, members);
    }

    public TeamSpec addTeam(String name, String displayName, Collection<UUID> members) {
        int index = teams.size();
        String resolvedName = name == null || name.trim().isEmpty() ? NAMES[index % NAMES.length] : name.trim();
        String color = PALETTE[index % PALETTE.length];
        String resolvedDisplay = displayName == null || displayName.trim().isEmpty()
                ? Text.capitalize(resolvedName) : displayName;
        TeamSpec spec = new TeamSpec(resolvedName.toLowerCase(Locale.ROOT), resolvedDisplay, color, members);
        teams.add(spec);
        return spec;
    }

    /** Splits every team into one team per member, used for free for all formats. */
    public MatchRequest splitPerPlayer() {
        List<UUID> everyone = participants();
        teams.clear();
        for (UUID uuid : everyone) {
            addTeam(Collections.singletonList(uuid));
        }
        return this;
    }

    public List<UUID> participants() {
        List<UUID> result = new ArrayList<UUID>();
        for (TeamSpec team : teams) {
            result.addAll(team.members());
        }
        return result;
    }

    public int participantCount() {
        int count = 0;
        for (TeamSpec team : teams) {
            count += team.members().size();
        }
        return count;
    }

    /** Reasons this request cannot be started, empty when it is valid. */
    public List<String> problems() {
        List<String> problems = new ArrayList<String>();
        if (kit == null) {
            problems.add("no kit");
        } else if (!kit.enabled()) {
            problems.add("kit " + kit.id() + " is disabled");
        } else if (!kit.playable()) {
            problems.add("kit " + kit.id() + " cannot be played in any mode");
        }
        if (teams.isEmpty()) {
            problems.add("no teams");
        }
        if (participantCount() < 2) {
            problems.add("at least two participants are required");
        }
        Set<UUID> seen = new LinkedHashSet<UUID>();
        for (TeamSpec team : teams) {
            if (team.members().isEmpty()) {
                problems.add("team " + team.name() + " has no members");
            }
            for (UUID uuid : team.members()) {
                if (uuid == null) {
                    problems.add("team " + team.name() + " holds a null member");
                } else if (!seen.add(uuid)) {
                    problems.add("a participant is listed twice");
                }
            }
        }
        if (ranked && !type.rankedCapable()) {
            problems.add(type.name() + " matches cannot be ranked");
        }
        if (type == MatchType.SOLO && (teams.size() != 2 || participantCount() != 2)) {
            problems.add("a solo match needs exactly two sides of one player");
        }
        if (type.isTeamBased() && teams.size() > 1) {
            int size = teams.get(0).members().size();
            for (TeamSpec team : teams) {
                if (team.members().size() != size) {
                    problems.add("team sizes must be equal");
                    break;
                }
            }
        }
        if (arena != null && kit != null && !kit.acceptsArena(arena)) {
            problems.add("arena " + arena.name() + " does not allow kit " + kit.id());
        }
        return problems;
    }

    public boolean isValid() {
        return problems().isEmpty();
    }

    // ------------------------------------------------------------- convenience

    public static MatchRequest duel(Player first, Player second, Kit kit, boolean ranked) {
        MatchRequest request = new MatchRequest(kit, MatchType.SOLO, ranked).source("duel");
        if (first != null) {
            request.addTeam(first.getName(), Collections.singletonList(first.getUniqueId()));
        }
        if (second != null) {
            request.addTeam(second.getName(), Collections.singletonList(second.getUniqueId()));
        }
        return request;
    }

    public static MatchRequest solo(UUID first, UUID second, Kit kit, boolean ranked, String source) {
        MatchRequest request = new MatchRequest(kit, MatchType.SOLO, ranked).source(source);
        request.addTeam(Collections.singletonList(first));
        request.addTeam(Collections.singletonList(second));
        return request;
    }

    public static MatchRequest ffa(Kit kit, Collection<UUID> players, String source) {
        MatchRequest request = new MatchRequest(kit, MatchType.FFA, false).source(source);
        if (players != null) {
            for (UUID uuid : players) {
                request.addTeam(Collections.singletonList(uuid));
            }
        }
        return request;
    }

    public static MatchRequest teams(Kit kit, boolean ranked, List<List<UUID>> sides, String source) {
        MatchRequest request = new MatchRequest(kit, MatchType.TEAMS, ranked).source(source);
        if (sides != null) {
            for (List<UUID> side : sides) {
                request.addTeam(side);
            }
        }
        return request;
    }

    @Override
    public String toString() {
        return "MatchRequest{" + (kit == null ? "no-kit" : kit.id()) + ", " + type + ", ranked=" + ranked
                + ", teams=" + teams.size() + ", players=" + participantCount() + ", source=" + source + '}';
    }

    /** One side of the requested match. */
    public static final class TeamSpec {

        private final String name;
        private final String displayName;
        private final String color;
        private final List<UUID> members;
        private String spawnKey;

        public TeamSpec(String name, String displayName, String color, Collection<UUID> members) {
            this.name = name == null ? "team" : name.toLowerCase(Locale.ROOT);
            this.displayName = displayName == null ? this.name : displayName;
            this.color = color == null ? "&7" : color;
            this.members = members == null ? new ArrayList<UUID>() : new ArrayList<UUID>(members);
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

        public List<UUID> members() {
            return Collections.unmodifiableList(members);
        }

        /** Arena spawn key such as {@code red}; when unset the arena assigns spawns in order. */
        public String spawnKey() {
            return spawnKey;
        }

        public TeamSpec spawnKey(String spawnKey) {
            this.spawnKey = spawnKey == null || spawnKey.trim().isEmpty() ? null : spawnKey.trim();
            return this;
        }

        @Override
        public String toString() {
            return name + "(" + members.size() + ')';
        }
    }
}
