package gg.lightpractice.api;

import gg.lightpractice.profile.Profile;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Access to persistent player profiles.
 *
 * <p>Profiles are loaded asynchronously from MongoDB and cached while a player is online. The
 * {@code requestProfile} methods always deliver their callback on the main thread, which makes them
 * safe to use from commands and menus.</p>
 */
public interface ProfileService {

    /** Cached profile of an online or recently seen player, {@code null} when it is not loaded. */
    Profile getProfile(UUID uuid);

    Profile getProfile(Player player);

    boolean isLoaded(UUID uuid);

    /** Loads a profile if needed and hands it to the callback on the main thread. */
    void requestProfile(Player player, Consumer<Profile> callback);

    /** Loads an offline profile; the callback receives {@code null} when no document exists. */
    void requestProfile(UUID uuid, Consumer<Profile> callback);

    /** Schedules an asynchronous save. */
    void save(Profile profile);

    /** Saves every cached profile, used during shutdown. */
    void saveAll();

    Collection<Profile> cached();

    int cachedCount();

    /** Removes a profile from the cache after it has been saved. */
    void unload(UUID uuid);

    boolean isDatabaseAvailable();
}
