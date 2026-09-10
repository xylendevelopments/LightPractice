package gg.lightpractice.gameevent;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Logic of one event type.
 *
 * <p>A provider decides how an event is played: which match it starts, what happens every second and when
 * the event is over. Everything it needs is reachable through the {@link GameEvent} session, so providers
 * never keep their own state and never schedule their own tasks.</p>
 *
 * <p>Third party providers are registered with
 * {@link gg.lightpractice.api.GameEventService#register(GameEventProvider)}.</p>
 */
public interface GameEventProvider {

    /** Unique id used in configuration and commands. */
    String id();

    /** Coloured name shown in messages and menus. */
    String displayName();

    /** Short explanation of the rules, shown in the event menu. */
    List<String> description();

    /** Menu icon. */
    Material icon();

    /** Minimum entries, {@code 0} to use the value of the event definition. */
    int minPlayers();

    /** Maximum entries, {@code 0} to use the value of the event definition. */
    int maxPlayers();

    /** Seconds entries stay open, {@code 0} to use the configured default. */
    int gatherSeconds();

    /** True when the provider plays the event through the match engine. */
    boolean usesMatch();

    /** Adds reasons why this provider cannot run the given session. */
    void validate(GameEvent event, List<String> problems);

    /** Called when a player entered the event. */
    void onJoin(GameEvent event, Player player);

    /** Called when a player left the event. */
    void onLeave(GameEvent event, Player player);

    /** Called once when the countdown reached zero. */
    void onStart(GameEvent event);

    /** Called every second while the event runs. */
    void onTick(GameEvent event);

    /** Called when the match of the event ended. */
    void onMatchEnd(GameEvent event, Match match);

    /** Called when the event finished with winners. */
    void onFinish(GameEvent event);

    /** Called when the event was stopped early. */
    void onStop(GameEvent event, String reason);
}
