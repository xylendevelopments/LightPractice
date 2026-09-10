package gg.lightpractice.queue;

import gg.lightpractice.match.MatchType;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A waiting list for one kit and format.
 *
 * <p>Queues are configuration: kit, ranked flag, format, team size and the player bounds. The
 * matchmaker only reads those numbers, so adding a 2v2 or an FFA queue never needs code.</p>
 */
public final class Queue {

    private final String id;
    private final String kitId;
    private final boolean ranked;
    private final List<QueueEntry> entries = new CopyOnWriteArrayList<QueueEntry>();
    private MatchType type = MatchType.SOLO;
    private int teamSize = 1;
    private int minimumPlayers = 2;
    private int maximumPlayers = 2;
    private String displayName;
    private boolean enabled = true;
    private int order;
    private Material icon = Material.IRON_SWORD;
    private short iconData;
    private List<String> lore = new ArrayList<String>();
    private String permission = "";
    private String arenaName;
    private int startedMatches;
    private long lastMatchAt;
    private long lastAttemptAt;

    public Queue(String id, String kitId, boolean ranked) {
        this.id = id == null ? "queue" : id.trim().toLowerCase(Locale.ROOT);
        this.kitId = kitId == null ? "" : kitId.trim().toLowerCase(Locale.ROOT);
        this.ranked = ranked;
        this.displayName = Text.capitalize(this.kitId) + (ranked ? " Ranked" : " Unranked");
    }

    /** Identifier of a queue, matching the {@code <kit>-<ranked|unranked>} naming convention. */
    public static String idOf(String kitId, boolean ranked) {
        String kit = kitId == null ? "" : kitId.trim().toLowerCase(Locale.ROOT);
        return kit + "-" + (ranked ? "ranked" : "unranked");
    }

    public String id() {
        return id;
    }

    public String kitId() {
        return kitId;
    }

    public boolean ranked() {
        return ranked;
    }

    public MatchType type() {
        return type;
    }

    public void type(MatchType type) {
        this.type = type == null ? MatchType.SOLO : type;
    }

    /** Players per side; {@code 1} for duels and free for all. */
    public int teamSize() {
        return teamSize;
    }

    public void teamSize(int teamSize) {
        this.teamSize = Math.max(1, teamSize);
    }

    public int minimumPlayers() {
        return minimumPlayers;
    }

    public void minimumPlayers(int minimumPlayers) {
        this.minimumPlayers = Math.max(1, minimumPlayers);
    }

    public int maximumPlayers() {
        return maximumPlayers;
    }

    public void maximumPlayers(int maximumPlayers) {
        this.maximumPlayers = Math.max(1, maximumPlayers);
    }

    public String displayName() {
        return displayName == null || displayName.isEmpty() ? id : displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName == null ? null : Text.color(displayName);
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int order() {
        return order;
    }

    public void order(int order) {
        this.order = order;
    }

    public Material icon() {
        return icon;
    }

    public short iconData() {
        return iconData;
    }

    public void icon(Material material, short data) {
        this.icon = material == null ? Material.IRON_SWORD : material;
        this.iconData = data;
    }

    public List<String> lore() {
        return lore;
    }

    public void lore(List<String> lore) {
        this.lore = Text.color(lore == null ? new ArrayList<String>() : lore);
    }

    public String permission() {
        return permission;
    }

    public void permission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public boolean hasPermission(Permissible permissible) {
        return permission.isEmpty() || (permissible != null && permissible.hasPermission(permission));
    }

    /** Arena this queue must use, {@code null} to let the arena manager choose. */
    public String arenaName() {
        return arenaName;
    }

    public void arenaName(String arenaName) {
        this.arenaName = arenaName == null || arenaName.trim().isEmpty() ? null : arenaName.trim();
    }

    public int startedMatches() {
        return startedMatches;
    }

    public long lastMatchAt() {
        return lastMatchAt;
    }

    public long lastAttemptAt() {
        return lastAttemptAt;
    }

    public void attemptMade() {
        this.lastAttemptAt = System.currentTimeMillis();
    }

    public void matchStarted() {
        this.startedMatches++;
        this.lastMatchAt = System.currentTimeMillis();
        this.lastAttemptAt = System.currentTimeMillis();
    }

    // ----------------------------------------------------------------- contents

    public List<QueueEntry> entries() {
        List<QueueEntry> sorted = new ArrayList<QueueEntry>(entries);
        Collections.sort(sorted, new Comparator<QueueEntry>() {
            @Override
            public int compare(QueueEntry left, QueueEntry right) {
                return Long.compare(left.joinedAt(), right.joinedAt());
            }
        });
        return sorted;
    }

    /** Waiting entries of a single player only, used by the matchmaker for solo and team queues. */
    public List<QueueEntry> soloEntries() {
        List<QueueEntry> result = new ArrayList<QueueEntry>();
        for (QueueEntry entry : entries()) {
            if (!entry.isGroup()) {
                result.add(entry);
            }
        }
        return result;
    }

    public int entryCount() {
        return entries.size();
    }

    /** Total number of queued players, counting party members. */
    public int size() {
        int count = 0;
        for (QueueEntry entry : entries) {
            count += entry.groupSize();
        }
        return count;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean add(QueueEntry entry) {
        return entry != null && entry.uuid() != null && !entries.contains(entry) && entries.add(entry);
    }

    public QueueEntry remove(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        QueueEntry found = entry(uuid);
        if (found != null) {
            entries.remove(found);
        }
        return found;
    }

    public QueueEntry entry(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (QueueEntry entry : entries) {
            if (entry.contains(uuid)) {
                return entry;
            }
        }
        return null;
    }

    public boolean contains(UUID uuid) {
        return entry(uuid) != null;
    }

    /** One based position in the queue, {@code -1} when the player is not queued here. */
    public int position(UUID uuid) {
        List<QueueEntry> sorted = entries();
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).contains(uuid)) {
                return index + 1;
            }
        }
        return -1;
    }

    /** Players the queue needs before a match can start. */
    public int playersRequired() {
        if (type.everyoneAlone()) {
            return Math.max(2, minimumPlayers);
        }
        return Math.max(2, teamSize * 2);
    }

    public boolean hasEnoughPlayers() {
        return size() >= playersRequired();
    }

    /** Whether the player may join this queue right now. */
    public boolean canJoin(Player player) {
        if (!enabled) {
            return false;
        }
        if (player == null) {
            return false;
        }
        if (!hasPermission(player)) {
            return false;
        }
        return maximumPlayers <= 0 || size() < maximumPlayers;
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<String>();
        if (kitId.isEmpty()) {
            problems.add("no kit configured");
        }
        if (teamSize < 1) {
            problems.add("team size must be at least one");
        }
        if (type.isTeamBased() && teamSize < 2) {
            problems.add("team queues need a team size of two or more");
        }
        if (maximumPlayers > 0 && maximumPlayers < playersRequired()) {
            problems.add("maximum players is below the required amount");
        }
        if (ranked && !type.rankedCapable()) {
            problems.add(type.name() + " queues cannot be ranked");
        }
        return problems;
    }

    /** Menu icon showing the queue and how many players are waiting. */
    public ItemStack iconItem() {
        List<String> lines = new ArrayList<String>(lore);
        lines.add("&7Queued: &f" + size());
        lines.add("&7Format: &f" + describeFormat());
        lines.add(ranked ? "&7Rating: &fcounted" : "&7Rating: &fnot counted");
        return Items.item(icon, 1, iconData, displayName(), lines);
    }

    public String describeFormat() {
        if (type.everyoneAlone()) {
            return "FFA (" + minimumPlayers + "-" + maximumPlayers + ")";
        }
        return teamSize + "v" + teamSize;
    }

    // -------------------------------------------------------------- persistence

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("kit", kitId);
        section.set("ranked", ranked);
        section.set("display-name", displayName);
        section.set("type", type.name());
        section.set("team-size", teamSize);
        section.set("minimum-players", minimumPlayers);
        section.set("maximum-players", maximumPlayers);
        section.set("enabled", enabled);
        section.set("order", order);
        section.set("icon.material", icon.name());
        section.set("icon.data", (int) iconData);
        section.set("icon.lore", lore);
        section.set("permission", permission);
        if (arenaName != null) {
            section.set("arena", arenaName);
        }
    }

    public static Queue read(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String kitId = section.getString("kit", id.split("-")[0]);
        boolean ranked = section.getBoolean("ranked", id.endsWith("-ranked"));
        Queue queue = new Queue(id, kitId, ranked);
        queue.displayName(section.getString("display-name", queue.displayName()));
        MatchType type = MatchType.parse(section.getString("type", "SOLO"));
        queue.type(type == null ? MatchType.SOLO : type);
        queue.teamSize(section.getInt("team-size", 1));
        queue.minimumPlayers(section.getInt("minimum-players", queue.type().everyoneAlone() ? 4 : 2));
        queue.maximumPlayers(section.getInt("maximum-players",
                queue.type().everyoneAlone() ? 8 : queue.teamSize() * 2));
        queue.enabled(section.getBoolean("enabled", true));
        queue.order(section.getInt("order", 0));
        queue.icon(Items.material(section.getString("icon.material", section.getString("icon", "IRON_SWORD")),
                Material.IRON_SWORD), (short) section.getInt("icon.data", 0));
        queue.lore(section.getStringList("icon.lore"));
        queue.permission(section.getString("permission", ""));
        queue.arenaName(section.getString("arena", null));
        return queue;
    }

    @Override
    public String toString() {
        return "Queue{" + id + ", players=" + size() + ", type=" + type + '}';
    }

    /** Queues of a list of kits, used to generate the default configuration. */
    public static List<String> defaultIds(List<String> kitIds) {
        List<String> ids = new ArrayList<String>();
        for (String kitId : kitIds == null ? Arrays.<String>asList() : kitIds) {
            ids.add(idOf(kitId, false));
            ids.add(idOf(kitId, true));
        }
        return ids;
    }

    /** Entries that waited longer than the given milliseconds. */
    public List<QueueEntry> waitingLongerThan(long millis) {
        List<QueueEntry> result = new ArrayList<QueueEntry>();
        for (QueueEntry entry : entries) {
            if (entry.ageMillis() >= millis) {
                result.add(entry);
            }
        }
        return result;
    }

    public void clear() {
        entries.clear();
    }

    public List<UUID> queuedPlayers() {
        List<UUID> players = new ArrayList<UUID>();
        for (QueueEntry entry : entries) {
            players.addAll(entry.group());
        }
        return players;
    }
}
