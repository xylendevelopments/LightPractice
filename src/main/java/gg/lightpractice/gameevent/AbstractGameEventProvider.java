package gg.lightpractice.gameevent;

import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for the bundled event types.
 *
 * <p>It implements the parts every provider shares: menu metadata, the winner extraction of a finished
 * match and empty hooks for the moments a provider does not care about. Concrete providers only override
 * what makes their event type different.</p>
 */
public abstract class AbstractGameEventProvider implements GameEventProvider {

    private final String id;
    private final String displayName;
    private final Material icon;

    protected AbstractGameEventProvider(String id, String displayName, Material icon) {
        this.id = id == null ? "event" : id.trim().toLowerCase(java.util.Locale.ROOT);
        this.displayName = displayName == null ? gg.lightpractice.util.Text.capitalize(id) : displayName;
        this.icon = icon == null ? Material.PAPER : icon;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<String> description() {
        return Collections.emptyList();
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public int minPlayers() {
        return 0;
    }

    @Override
    public int maxPlayers() {
        return 0;
    }

    @Override
    public int gatherSeconds() {
        return 0;
    }

    @Override
    public boolean usesMatch() {
        return true;
    }

    @Override
    public void validate(GameEvent event, List<String> problems) {
        if (event != null && event.manager() != null && event.manager().kit(event) == null) {
            problems.add("kit " + event.kitId() + " is unknown or disabled");
        }
    }

    @Override
    public void onJoin(GameEvent event, Player player) {
        // nothing shared to do
    }

    @Override
    public void onLeave(GameEvent event, Player player) {
        // nothing shared to do
    }

    @Override
    public void onTick(GameEvent event) {
        // providers without a per second routine keep the default
    }

    @Override
    public void onFinish(GameEvent event) {
        // nothing shared to do
    }

    @Override
    public void onStop(GameEvent event, String reason) {
        // nothing shared to do
    }

    /** Winners of a finished match: the winning side, or everybody still alive in a draw. */
    protected List<UUID> winnersOf(Match match) {
        List<UUID> winners = new ArrayList<UUID>();
        if (match == null) {
            return winners;
        }
        MatchTeam team = match.winningTeam();
        if (team != null) {
            for (MatchPlayer member : team.members()) {
                winners.add(member.uuid());
            }
            return winners;
        }
        for (MatchPlayer member : match.participants()) {
            if (!member.eliminated()) {
                winners.add(member.uuid());
            }
        }
        return winners;
    }

    @Override
    public void onMatchEnd(GameEvent event, Match match) {
        if (event == null || event.manager() == null) {
            return;
        }
        event.manager().finish(event, winnersOf(match), "the match ended");
    }

    /** Online participants of a session. */
    protected List<Player> online(GameEvent event) {
        return event == null || event.manager() == null
                ? new ArrayList<Player>() : event.manager().online(event);
    }

    /** Announces to the participants of a session. */
    protected void announce(GameEvent event, String key, String... replacements) {
        if (event != null && event.manager() != null) {
            event.manager().announce(event, key, replacements);
        }
    }

    /** Names of a group of players, coloured and comma separated. */
    protected String names(GameEvent event, Collection<UUID> uuids) {
        return event == null || event.manager() == null ? "" : event.manager().names(uuids);
    }
}
