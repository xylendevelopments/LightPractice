package gg.lightpractice.api;

import gg.lightpractice.duel.DuelRequest;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Direct duel requests. */
public interface DuelService {

    boolean send(Player sender, Player target, String kitId, String arenaName, boolean ranked);

    boolean accept(Player receiver);

    boolean deny(Player receiver);

    /** Request the player received, {@code null} when there is none or it expired. */
    DuelRequest incoming(UUID uuid);

    /** Request the player sent that has not been answered yet. */
    DuelRequest outgoing(UUID uuid);

    boolean hasIncoming(UUID uuid);

    boolean hasOutgoing(UUID uuid);

    void clear(UUID uuid);

    long expiryMillis();
}
