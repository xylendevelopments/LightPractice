package gg.lightpractice.api.event;

import org.bukkit.event.Event;

/**
 * Base type for every developer event published by LightPractice.
 *
 * <p>Subclasses declare their own {@code HandlerList} as Bukkit requires, and use this class to carry
 * the thread the event was fired on so listeners can tell a database callback from a gameplay event.</p>
 */
public abstract class LightPracticeEvent extends Event {

    private final String pluginVersion;

    protected LightPracticeEvent(boolean async, String pluginVersion) {
        super(async);
        this.pluginVersion = pluginVersion;
    }

    /** Version of the plugin that fired the event, useful for compatibility checks. */
    public String getPluginVersion() {
        return pluginVersion;
    }
}
