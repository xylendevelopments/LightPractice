package gg.lightpractice.api;

import gg.lightpractice.party.Party;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/** Parties, party matches and party versus party duels. */
public interface PartyService {

    /** Party the player belongs to, {@code null} when they are not in one. */
    Party get(UUID uuid);

    Party get(Player player);

    Party byId(String partyId);

    Collection<Party> parties();

    Party create(Player leader);

    boolean invite(Player leader, Player target);

    boolean accept(Player player);

    boolean deny(Player player);

    boolean leave(Player player);

    boolean kick(Player leader, Player target);

    boolean promote(Player leader, Player target);

    boolean disband(Player leader);

    boolean setOpen(Party party, boolean open);

    boolean setFfaAllowed(Party party, boolean allowed);

    /** Everyone against everyone inside the party. */
    boolean startFfa(Party party);

    /** Party split into two balanced teams. */
    boolean startSplit(Party party);

    /** Party versus party duel. */
    boolean startDuel(Party challenger, Party opponent, String kitId);

    /** Called when a member disconnects; the party survives or disbands based on configuration. */
    void handleQuit(UUID uuid);

    boolean isInParty(UUID uuid);
}
