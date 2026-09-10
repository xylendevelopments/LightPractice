package gg.lightpractice.service;

/**
 * Lifecycle contract for every manager in the plugin. The service registry walks the registered
 * services in construction order when enabling and in reverse order when disabling, which is what
 * makes shutdown deterministic (matchmaking stops before matches are ended, matches end before
 * profiles are saved).
 */
public interface LightService {

    /** Human readable name used in startup, reload and shutdown logging. */
    String name();

    /**
     * Load and enable order, lower numbers first.
     *
     * <p>Configuration and the database come first, then the data owners (profiles, kits, arenas), then
     * the systems that play with them (queues, matches, parties) and finally the presentation layer
     * (scoreboards, tab lists, GUIs). The order is what makes startup deterministic.</p>
     */
    default int startupOrder() {
        return 100;
    }

    /**
     * Called once after every service has been constructed and the configuration files are loaded,
     * before {@link #onEnable()}. Managers read their own settings here.
     */
    default void onLoad() {
    }

    /** Called while the plugin enables, after every dependency has been constructed. */
    default void onEnable() {
    }

    /** Called while the plugin disables, in reverse registration order. */
    default void onDisable() {
    }

    /** Called by {@code /lightpractice reload}; must not rebuild listeners or tasks. */
    default void onReload() {
    }
}
