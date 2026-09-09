package gg.lightpractice.player;

import gg.lightpractice.model.ChatChannel;
import gg.lightpractice.profile.Profile;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Runtime session of an online player.
 *
 * <p>This is deliberately not a profile: it holds only data that is meaningless once the player
 * disconnects (current match, queue entry, party id, combat tag, snapshot of their inventory). Nothing
 * here is persisted, which keeps the profile the only source of truth for stored data.</p>
 */
public final class LightPlayer {

    private final UUID uuid;
    private final long joinedAt = System.currentTimeMillis();
    private String name;
    private Player player;
    private Profile profile;
    private PlayerState state = PlayerState.LOADING;
    private InventorySnapshot snapshot;
    private String matchId;
    private String queueId;
    private String partyId;
    private String spectatedMatchId;
    private UUID spectatedTarget;
    private String editingKit;
    private String botId;
    private String tournamentId;
    private String eventId;
    private UUID lastAttacker;
    private long lastDamageAt;
    private long lastAttackAt;
    private int combo;
    private long comboEndsAt;
    private ChatChannel chatChannel = ChatChannel.PUBLIC;
    private long stateChangedAt = System.currentTimeMillis();

    public LightPlayer(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    /** Live Bukkit player, {@code null} once the session is stale (during quit handling). */
    public Player player() {
        return player;
    }

    public void player(Player player) {
        this.player = player;
    }

    public boolean isOnline() {
        return player != null && player.isOnline();
    }

    public Profile profile() {
        return profile;
    }

    public void profile(Profile profile) {
        this.profile = profile;
    }

    public boolean hasProfile() {
        return profile != null;
    }

    public PlayerState state() {
        return state;
    }

    /**
     * Raw state assignment. Callers must go through {@code PlayerManager#setState} so transitions stay
     * validated; this setter exists for the manager and for restoration during shutdown.
     */
    public void state(PlayerState state) {
        this.state = state == null ? PlayerState.LOBBY : state;
        this.stateChangedAt = System.currentTimeMillis();
    }

    public long stateChangedAt() {
        return stateChangedAt;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public InventorySnapshot snapshot() {
        return snapshot;
    }

    public void snapshot(InventorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void clearSnapshot() {
        this.snapshot = null;
    }

    public String matchId() {
        return matchId;
    }

    public void matchId(String matchId) {
        this.matchId = matchId;
    }

    public String queueId() {
        return queueId;
    }

    public void queueId(String queueId) {
        this.queueId = queueId;
    }

    public String partyId() {
        return partyId;
    }

    public void partyId(String partyId) {
        this.partyId = partyId;
    }

    public String spectatedMatchId() {
        return spectatedMatchId;
    }

    public void spectatedMatchId(String spectatedMatchId) {
        this.spectatedMatchId = spectatedMatchId;
    }

    public UUID spectatedTarget() {
        return spectatedTarget;
    }

    public void spectatedTarget(UUID spectatedTarget) {
        this.spectatedTarget = spectatedTarget;
    }

    public String editingKit() {
        return editingKit;
    }

    public void editingKit(String editingKit) {
        this.editingKit = editingKit;
    }

    public String botId() {
        return botId;
    }

    public void botId(String botId) {
        this.botId = botId;
    }

    /** Tournament the player is entered in, set while a bracket runs and cleared when it ends. */
    public String tournamentId() {
        return tournamentId;
    }

    public void tournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /** Event session the player joined, cleared when the event ends. */
    public String eventId() {
        return eventId;
    }

    public void eventId(String eventId) {
        this.eventId = eventId;
    }

    /** Records damage taken, which drives combat tagging and the "last attacker" death credit. */
    public void damageFrom(UUID attacker) {
        this.lastAttacker = attacker;
        this.lastDamageAt = System.currentTimeMillis();
    }

    public UUID lastAttacker() {
        return lastAttacker;
    }

    public long lastDamageAt() {
        return lastDamageAt;
    }

    public boolean inCombat(long combatMillis) {
        return lastDamageAt > 0L && System.currentTimeMillis() - lastDamageAt <= combatMillis;
    }

    public void clearCombat() {
        this.lastAttacker = null;
        this.lastDamageAt = 0L;
    }

    public long lastAttackAt() {
        return lastAttackAt;
    }

    public void attacked() {
        this.lastAttackAt = System.currentTimeMillis();
    }

    public int combo() {
        return combo;
    }

    /** Registers a landed hit; a combo expires after the configured window without another hit. */
    public void registerHit(long comboWindowMillis) {
        long now = System.currentTimeMillis();
        if (now > comboEndsAt) {
            combo = 0;
        }
        combo++;
        comboEndsAt = now + comboWindowMillis;
    }

    public void resetCombo() {
        combo = 0;
        comboEndsAt = 0L;
    }

    public ChatChannel chatChannel() {
        if (profile != null && profile.settings() != null) {
            return profile.settings().chatChannel();
        }
        return chatChannel;
    }

    public void chatChannel(ChatChannel channel) {
        this.chatChannel = channel == null ? ChatChannel.PUBLIC : channel;
        if (profile != null && profile.settings() != null) {
            profile.settings().chatChannel(this.chatChannel);
        }
    }

    /** True when the player is inside a match, a tournament match or waiting for one to start. */
    public boolean inMatch() {
        return matchId != null;
    }

    public boolean inQueue() {
        return queueId != null;
    }

    public boolean inParty() {
        return partyId != null;
    }

    public boolean spectating() {
        return spectatedMatchId != null;
    }

    public boolean editing() {
        return editingKit != null;
    }

    public boolean inTournament() {
        return tournamentId != null;
    }

    public boolean inEvent() {
        return eventId != null;
    }

    @Override
    public String toString() {
        return "LightPlayer{" + name + ", state=" + state + '}';
    }
}
