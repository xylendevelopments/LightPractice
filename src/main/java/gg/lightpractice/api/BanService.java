package gg.lightpractice.api;

import gg.lightpractice.model.BanEntry;

import java.util.Collection;
import java.util.UUID;

/** Practice bans that gate queues, duels, parties, tournaments and events. */
public interface BanService {

    boolean isBanned(UUID uuid);

    BanEntry ban(UUID uuid);

    boolean ban(UUID uuid, String name, String reason, UUID issuer, String issuerName, long durationMillis);

    boolean unban(UUID uuid);

    Collection<BanEntry> bans();

    /** Loads bans for online players after the database becomes available. */
    void refresh();
}
