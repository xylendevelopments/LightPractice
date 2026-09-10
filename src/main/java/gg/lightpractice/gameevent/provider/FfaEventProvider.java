package gg.lightpractice.gameevent.provider;

import gg.lightpractice.gameevent.AbstractGameEventProvider;
import gg.lightpractice.gameevent.GameEvent;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Free for all event: everybody fights everybody, the last player standing wins.
 *
 * <p>The whole event is one {@link MatchType#FFA} match, so elimination, respawns, kits and arena
 * protection all come from the match engine and the kit rules instead of being reimplemented here.</p>
 */
public final class FfaEventProvider extends AbstractGameEventProvider {

    public FfaEventProvider() {
        super("ffa", "&aFree For All", Material.IRON_SWORD);
    }

    @Override
    public List<String> description() {
        return Arrays.asList("&7Everybody fights everybody.", "&7The last player standing wins.");
    }

    @Override
    public int minPlayers() {
        return 3;
    }

    @Override
    public void onStart(GameEvent event) {
        Kit kit = event.manager().kit(event);
        List<UUID> participants = new ArrayList<UUID>(event.participantsList());
        if (kit == null || participants.size() < 2) {
            event.manager().stop(event, "the event could not start");
            return;
        }
        MatchRequest request = new MatchRequest(kit, MatchType.FFA, false).source("event:" + event.id());
        request.countdownSeconds(event.manager().matchCountdown());
        request.resetArena(true);
        request.spectatorsAllowed(event.manager().allowSpectators());
        for (UUID uuid : participants) {
            request.addTeam(java.util.Collections.singletonList(uuid));
        }
        if (!event.manager().launch(event, request)) {
            event.manager().stop(event, "no arena was available");
        }
    }
}
