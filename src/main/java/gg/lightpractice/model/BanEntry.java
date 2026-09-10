package gg.lightpractice.model;

import java.util.UUID;

/**
 * A practice ban. Bans are stored separately from profiles so an expired or lifted ban never leaves
 * residue on a player document, and they gate queueing, duels, tournaments and events.
 */
public final class BanEntry {

    private final UUID uuid;
    private final String name;
    private final String reason;
    private final UUID issuer;
    private final String issuerName;
    private final long issuedAt;
    private final long expiresAt;

    public BanEntry(UUID uuid, String name, String reason, UUID issuer, String issuerName,
                    long issuedAt, long expiresAt) {
        this.uuid = uuid;
        this.name = name;
        this.reason = reason == null ? "No reason provided" : reason;
        this.issuer = issuer;
        this.issuerName = issuerName == null ? "Console" : issuerName;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static BanEntry permanent(UUID uuid, String name, String reason, UUID issuer, String issuerName) {
        return new BanEntry(uuid, name, reason, issuer, issuerName, System.currentTimeMillis(), -1L);
    }

    public static BanEntry temporary(UUID uuid, String name, String reason, UUID issuer, String issuerName,
                                     long durationMillis) {
        long now = System.currentTimeMillis();
        return new BanEntry(uuid, name, reason, issuer, issuerName, now, now + Math.max(1000L, durationMillis));
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String reason() {
        return reason;
    }

    public UUID issuer() {
        return issuer;
    }

    public String issuerName() {
        return issuerName;
    }

    public long issuedAt() {
        return issuedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean permanent() {
        return expiresAt < 0L;
    }

    public boolean expired() {
        return expiresAt >= 0L && System.currentTimeMillis() > expiresAt;
    }

    public boolean active() {
        return !expired();
    }

    public long remainingMillis() {
        if (permanent()) {
            return -1L;
        }
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
