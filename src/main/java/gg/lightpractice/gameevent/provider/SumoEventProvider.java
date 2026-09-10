package gg.lightpractice.gameevent.provider;

import gg.lightpractice.gameevent.AbstractGameEventProvider;
import gg.lightpractice.gameevent.GameEvent;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Sumo gauntlet: one champion defends the ring against every challenger in turn.
 *
 * <p>The session keeps the champion and the queue of challengers in its attributes, so the provider is
 * stateless. Each pairing is a normal one versus one match; when the champion loses, the winner takes the
 * ring, and when no challenger is left the champion wins the event.</p>
 */
public final class SumoEventProvider extends AbstractGameEventProvider {

    private static final String CHAMPION = "champion";
    private static final String CHALLENGERS = "challengers";

    public SumoEventProvider() {
        super("sumo", "&6Sumo Gauntlet", Material.SLIME_BALL);
    }

    @Override
    public List<String> description() {
        return Arrays.asList("&7One champion defends the ring.", "&7Beat the champion to take the ring.",
                "&7The last champion wins the event.");
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public void onStart(GameEvent event) {
        List<UUID> entries = new ArrayList<UUID>(event.participantsList());
        Collections.shuffle(entries);
        if (entries.size() < 2) {
            event.manager().stop(event, "not enough players remained");
            return;
        }
        event.attribute(CHAMPION, entries.get(0));
        event.attribute(CHALLENGERS, new ArrayList<UUID>(entries.subList(1, entries.size())));
        event.log("gauntlet started with " + entries.size() + " player(s)");
        announce(event, "event.sumo.begin",
                "{champion}", names(event, Collections.singletonList(entries.get(0))),
                "{challengers}", String.valueOf(entries.size() - 1));
        nextPairing(event);
    }

    @Override
    public void onTick(GameEvent event) {
        // a pairing can fail to start when every arena is busy, retry on the next second
        if (event.matchId() == null || event.manager().match(event) == null) {
            nextPairing(event);
        }
    }

    @Override
    public void onMatchEnd(GameEvent event, Match match) {
        event.matchId(null);
        List<UUID> winners = winnersOf(match);
        UUID champion = event.attributeUuid(CHAMPION);
        if (winners.isEmpty()) {
            Debug.warn(DebugCategory.MATCH, "Sumo pairing of event {} ended without a winner", event.id());
        } else if (champion == null || !winners.contains(champion)) {
            UUID next = winners.get(0);
            event.attribute(CHAMPION, next);
            announce(event, "event.sumo.dethroned",
                    "{champion}", names(event, Collections.singletonList(next)),
                    "{previous}", names(event, Collections.singletonList(champion)));
            event.log(names(event, Collections.singletonList(next)) + " took the ring");
        } else {
            announce(event, "event.sumo.defended",
                    "{champion}", names(event, Collections.singletonList(champion)));
        }
        Object value = event.attribute(CHALLENGERS);
        List<UUID> challengers = challengers(value);
        if (challengers.isEmpty()) {
            UUID winner = event.attributeUuid(CHAMPION);
            event.manager().finish(event, winner == null
                    ? Collections.<UUID>emptyList() : Collections.singletonList(winner),
                    "the gauntlet was cleared");
            return;
        }
        event.attribute(CHALLENGERS, challengers);
        nextPairing(event);
    }

    /** Starts the next champion versus challenger pairing. */
    private void nextPairing(GameEvent event) {
        UUID champion = event.attributeUuid(CHAMPION);
        List<UUID> challengers = challengers(event.attribute(CHALLENGERS));
        if (champion == null) {
            if (challengers.isEmpty()) {
                event.manager().stop(event, "nobody is left in the gauntlet");
            }
            return;
        }
        while (!challengers.isEmpty()) {
            UUID challenger = challengers.remove(0);
            if (challenger.equals(champion) || !event.isParticipant(challenger)
                    || org.bukkit.Bukkit.getPlayer(challenger) == null) {
                continue;
            }
            Kit kit = event.manager().kit(event);
            if (kit == null) {
                event.manager().stop(event, "the event kit disappeared");
                return;
            }
            MatchRequest request = new MatchRequest(kit, MatchType.SOLO, false)
                    .source("event:" + event.id());
            request.countdownSeconds(event.manager().matchCountdown());
            request.resetArena(true);
            request.spectatorsAllowed(event.manager().allowSpectators());
            request.addTeam(Collections.singletonList(champion));
            request.addTeam(Collections.singletonList(challenger));
            event.attribute(CHALLENGERS, challengers);
            if (!event.manager().launch(event, request)) {
                // put the challenger back so the next tick tries again
                challengers.add(0, challenger);
                event.attribute(CHALLENGERS, challengers);
                Debug.log(DebugCategory.MATCH, "Event {} could not start a pairing yet", event.id());
                return;
            }
            announce(event, "event.sumo.pairing",
                    "{champion}", names(event, Collections.singletonList(champion)),
                    "{challenger}", names(event, Collections.singletonList(challenger)),
                    "{remaining}", String.valueOf(challengers.size()));
            return;
        }
        event.attribute(CHALLENGERS, challengers);
        event.manager().finish(event, Collections.singletonList(champion), "the gauntlet was cleared");
    }

    @SuppressWarnings("unchecked")
    private List<UUID> challengers(Object value) {
        if (value instanceof List) {
            return new ArrayList<UUID>((List<UUID>) value);
        }
        return new ArrayList<UUID>();
    }

    @Override
    public void onStop(GameEvent event, String reason) {
        event.attribute(CHAMPION, null);
        event.attribute(CHALLENGERS, null);
    }
}
