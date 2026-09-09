package gg.lightpractice.model;

import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;

/**
 * A configured rating bracket such as Bronze or Grandmaster. Divisions are pure data: they are read
 * from {@code divisions.yml} and resolved by {@code DivisionManager} whenever a rating changes.
 */
public final class Division {

    private final String id;
    private final String displayName;
    private final String color;
    private final int minimumElo;
    private final int maximumElo;
    private final Material icon;
    private final short iconData;
    private final String promoteMessage;
    private final String demoteMessage;
    private final int tierCount;

    public Division(String id, String displayName, String color, int minimumElo, int maximumElo,
                    String icon, short iconData, int tierCount, String promoteMessage, String demoteMessage) {
        this.id = id;
        this.displayName = Text.color(displayName);
        this.color = Text.color(color);
        this.minimumElo = minimumElo;
        this.maximumElo = maximumElo;
        this.icon = Items.material(icon, Material.IRON_INGOT);
        this.iconData = iconData;
        this.tierCount = Math.max(1, tierCount);
        this.promoteMessage = promoteMessage;
        this.demoteMessage = demoteMessage;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    /** Division name wrapped in its configured colour, used by tab, scoreboards and placeholders. */
    public String colored() {
        return color + displayName;
    }

    public int minimumElo() {
        return minimumElo;
    }

    public int maximumElo() {
        return maximumElo;
    }

    public int tierCount() {
        return tierCount;
    }

    public Material icon() {
        return icon;
    }

    public short iconData() {
        return iconData;
    }

    public String promoteMessage() {
        return promoteMessage;
    }

    public String demoteMessage() {
        return demoteMessage;
    }

    public boolean contains(int elo) {
        return elo >= minimumElo && (maximumElo < 0 || elo <= maximumElo);
    }

    /**
     * Computes the tier inside this division, where tier 1 is the lowest. Tiers are derived from the
     * position of the rating inside the division range so no extra storage is required.
     */
    public int tier(int elo) {
        if (tierCount <= 1) {
            return 1;
        }
        int ceiling = maximumElo < 0 ? minimumElo + (tierCount * 100) : maximumElo;
        int span = Math.max(1, ceiling - minimumElo);
        int position = Math.max(0, Math.min(span, elo - minimumElo));
        int tier = 1 + (int) Math.floor((double) position / span * tierCount);
        return Math.max(1, Math.min(tierCount, tier));
    }

    public String displayWithTier(int elo) {
        return tierCount <= 1 ? displayName : displayName + " " + toRoman(tier(elo));
    }

    private static String toRoman(int value) {
        switch (value) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            default:
                return String.valueOf(value);
        }
    }

    @Override
    public String toString() {
        return "Division{id=" + id + ", range=" + minimumElo + "-" + maximumElo + '}';
    }
}
