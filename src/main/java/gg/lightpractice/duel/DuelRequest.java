package gg.lightpractice.duel;

import java.util.UUID;

/**
 * A pending one versus one challenge.
 *
 * <p>Requests are immutable apart from the accept flag and expire after a configured time, so a player
 * can never be dragged into a fight by an answer to a challenge sent long ago.</p>
 */
public final class DuelRequest {

    private final UUID sender;
    private final String senderName;
    private final UUID receiver;
    private final String receiverName;
    private final String kitId;
    private final String kitName;
    private final String arenaName;
    private final boolean ranked;
    private final long createdAt = System.currentTimeMillis();
    private final long expiryMillis;
    private boolean accepted;

    public DuelRequest(UUID sender, String senderName, UUID receiver, String receiverName, String kitId,
                       String kitName, String arenaName, boolean ranked, long expiryMillis) {
        this.sender = sender;
        this.senderName = senderName == null ? "unknown" : senderName;
        this.receiver = receiver;
        this.receiverName = receiverName == null ? "unknown" : receiverName;
        this.kitId = kitId;
        this.kitName = kitName == null ? kitId : kitName;
        this.arenaName = arenaName;
        this.ranked = ranked;
        this.expiryMillis = Math.max(1000L, expiryMillis);
    }

    public UUID sender() {
        return sender;
    }

    public String senderName() {
        return senderName;
    }

    public UUID receiver() {
        return receiver;
    }

    public String receiverName() {
        return receiverName;
    }

    public String kitId() {
        return kitId;
    }

    public String kitName() {
        return kitName;
    }

    /** Arena requested by the sender, {@code null} to let the arena manager pick one. */
    public String arenaName() {
        return arenaName;
    }

    public boolean ranked() {
        return ranked;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiryMillis() {
        return expiryMillis;
    }

    public long expiresAt() {
        return createdAt + expiryMillis;
    }

    public boolean isExpired() {
        return isExpired(System.currentTimeMillis());
    }

    public boolean isExpired(long now) {
        return now >= expiresAt();
    }

    public long remainingMillis() {
        return Math.max(0L, expiresAt() - System.currentTimeMillis());
    }

    public boolean accepted() {
        return accepted;
    }

    public void accepted(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean involves(UUID uuid) {
        return uuid != null && (uuid.equals(sender) || uuid.equals(receiver));
    }

    /** The other side of the request. */
    public UUID other(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.equals(sender)) {
            return receiver;
        }
        if (uuid.equals(receiver)) {
            return sender;
        }
        return null;
    }

    @Override
    public String toString() {
        return "DuelRequest{" + senderName + " -> " + receiverName + ", kit=" + kitId + ", ranked=" + ranked
                + ", expires in " + (remainingMillis() / 1000L) + "s}";
    }
}
