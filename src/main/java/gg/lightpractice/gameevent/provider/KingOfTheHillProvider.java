package gg.lightpractice.gameevent.provider;

import gg.lightpractice.gameevent.AbstractGameEventProvider;
import gg.lightpractice.gameevent.GameEvent;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Locations;
import gg.lightpractice.util.Visuals;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * King of the hill: hold the point longer than everybody else.
 *
 * <p>The fight itself is a normal event match with respawns, while this provider measures who stands on
 * the point. Capture time is kept per player inside the session, progress is announced at fixed steps and
 * the match is ended through its own score rule once somebody reached the configured hold time.</p>
 */
public final class KingOfTheHillProvider extends AbstractGameEventProvider {

    private static final String PROGRESS = "progress:";
    private static final String CONTROLLING = "controlling";
    private static final String LAST_ANNOUNCEMENT = "announced";

    public KingOfTheHillProvider() {
        super("koth", "&bKing Of The Hill", Material.BEACON);
    }

    @Override
    public List<String> description() {
        return Arrays.asList("&7Stand on the point to capture it.",
                "&7Hold it for the full time to win.", "&7Dying resets your progress.");
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public void validate(GameEvent event, List<String> problems) {
        super.validate(event, problems);
        if (event == null) {
            return;
        }
        Location objective = event.objective();
        if (objective == null || objective.getWorld() == null) {
            problems.add("no capture point is configured for this event");
        }
        Kit kit = event.manager() == null ? null : event.manager().kit(event);
        if (kit != null && kit.ruleSet() != null && !kit.ruleSet().allowRespawn()) {
            problems.add("kit " + kit.id() + " does not allow respawns, king of the hill needs them");
        }
    }

    @Override
    public void onStart(GameEvent event) {
        Kit kit = event.manager().kit(event);
        List<UUID> participants = event.participantsList();
        if (kit == null || participants.size() < 2 || event.objective() == null) {
            event.manager().stop(event, "the event could not start");
            return;
        }
        MatchRequest request = new MatchRequest(kit, MatchType.EVENT, false).source("event:" + event.id());
        request.countdownSeconds(event.manager().matchCountdown());
        request.resetArena(true);
        request.spectatorsAllowed(event.manager().allowSpectators());
        for (UUID uuid : participants) {
            request.addTeam(Collections.singletonList(uuid));
        }
        if (!event.manager().launch(event, request)) {
            event.manager().stop(event, "no arena was available");
            return;
        }
        event.attribute(LAST_ANNOUNCEMENT, Integer.valueOf(0));
        event.log("capture point at " + Locations.format(event.objective()) + ", hold "
                + event.objectiveSeconds() + "s");
        announce(event, "event.koth.begin",
                "{seconds}", String.valueOf(event.objectiveSeconds()),
                "{radius}", String.valueOf((int) event.objectiveRadius()));
    }

    @Override
    public void onTick(GameEvent event) {
        Match match = event.manager().match(event);
        Location point = event.objective();
        if (match == null || !match.isLive() || point == null || point.getWorld() == null) {
            return;
        }
        double radiusSquared = event.objectiveRadius() * event.objectiveRadius();
        UUID controlling = null;
        for (Player player : match.alivePlayers()) {
            Location location = player.getLocation();
            if (location == null || location.getWorld() == null
                    || !location.getWorld().equals(point.getWorld())) {
                continue;
            }
            if (location.distanceSquared(point) <= radiusSquared) {
                if (controlling != null) {
                    // two players on the point neutralise each other
                    controlling = null;
                    break;
                }
                controlling = player.getUniqueId();
            }
        }
        Visuals.effect(point.clone().add(0.0D, 1.0D, 0.0D), controlling == null ? "COLOURED_DUST" : "HAPPY_VILLAGER",
                controlling == null ? 4 : 12, 0.6D, 0.4D, 0.6D, 0.0D, 48, 0);
        event.attribute(CONTROLLING, controlling);
        if (controlling == null) {
            return;
        }
        String key = PROGRESS + controlling;
        int held = event.attributeInt(key, 0) + 1;
        event.attribute(key, Integer.valueOf(held));
        int target = Math.max(1, event.objectiveSeconds());
        int announced = event.attributeInt(LAST_ANNOUNCEMENT, 0);
        int step = Math.max(1, target / 4);
        if (held % step == 0 && held / step > announced && held < target) {
            event.attribute(LAST_ANNOUNCEMENT, Integer.valueOf(held / step));
            announce(event, "event.koth.progress",
                    "{player}", names(event, Collections.singletonList(controlling)),
                    "{held}", String.valueOf(held),
                    "{seconds}", String.valueOf(target));
        }
        if (held >= target) {
            Player winner = org.bukkit.Bukkit.getPlayer(controlling);
            if (winner == null) {
                event.attribute(key, Integer.valueOf(0));
                return;
            }
            event.log(names(event, Collections.singletonList(controlling)) + " held the point for "
                    + target + "s");
            match.endByScore(winner, "held the point");
            Debug.log(DebugCategory.MATCH, "Event {} finished, {} held the hill", event.id(), winner.getName());
        }
    }

    @Override
    public void onMatchEnd(GameEvent event, Match match) {
        event.attribute(CONTROLLING, null);
        for (UUID uuid : event.participantsList()) {
            event.attribute(PROGRESS + uuid, null);
        }
        event.manager().finish(event, winnersOf(match), "the hill was decided");
    }

    @Override
    public void onStop(GameEvent event, String reason) {
        event.attribute(CONTROLLING, null);
        event.attribute(LAST_ANNOUNCEMENT, null);
        for (UUID uuid : event.participantsList()) {
            event.attribute(PROGRESS + uuid, null);
        }
    }
}
