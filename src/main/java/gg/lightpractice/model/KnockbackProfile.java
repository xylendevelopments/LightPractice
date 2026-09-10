package gg.lightpractice.model;

import org.bukkit.util.Vector;

/**
 * Configurable knockback. 1.8 practice servers tune knockback per kit (sumo needs none, combo needs
 * almost none, no-debuff needs vanilla like values), so profiles are data and are applied by the
 * combat manager after a hit instead of hardcoding numbers into listeners.
 */
public final class KnockbackProfile {

    public static final KnockbackProfile DEFAULT =
            new KnockbackProfile("default", 0.40D, 0.36D, 0.40D, 0.40D, 1.0D, 0.45D, 0.0D);

    private final String name;
    private final double horizontal;
    private final double vertical;
    private final double sprintHorizontal;
    private final double sprintVertical;
    private final double airMultiplier;
    private final double maximumVertical;
    private final double resistance;

    public KnockbackProfile(String name, double horizontal, double vertical, double sprintHorizontal,
                            double sprintVertical, double airMultiplier, double maximumVertical,
                            double resistance) {
        this.name = name;
        this.horizontal = clamp(horizontal, 0.0D, 4.0D);
        this.vertical = clamp(vertical, 0.0D, 4.0D);
        this.sprintHorizontal = clamp(sprintHorizontal, 0.0D, 4.0D);
        this.sprintVertical = clamp(sprintVertical, 0.0D, 4.0D);
        this.airMultiplier = clamp(airMultiplier, 0.0D, 4.0D);
        this.maximumVertical = clamp(maximumVertical, 0.0D, 4.0D);
        this.resistance = clamp(resistance, 0.0D, 1.0D);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public String name() {
        return name;
    }

    public double horizontal() {
        return horizontal;
    }

    public double vertical() {
        return vertical;
    }

    public double resistance() {
        return resistance;
    }

    /**
     * Builds the velocity that should be applied to the victim.
     *
     * @param direction     unit less direction from attacker to victim, may be zero length
     * @param sprinting     whether the attacker was sprinting into the hit
     * @param targetAirborne whether the victim was off the ground when hit
     */
    public Vector calculate(Vector direction, boolean sprinting, boolean targetAirborne) {
        double horizontalValue = sprinting ? sprintHorizontal : horizontal;
        double verticalValue = sprinting ? sprintVertical : vertical;
        if (targetAirborne) {
            verticalValue *= airMultiplier;
        }
        Vector result = new Vector();
        double x = direction == null ? 0.0D : direction.getX();
        double z = direction == null ? 0.0D : direction.getZ();
        double length = Math.sqrt(x * x + z * z);
        if (length > 0.0001D) {
            result.setX((x / length) * horizontalValue);
            result.setZ((z / length) * horizontalValue);
        }
        result.setY(Math.min(verticalValue, maximumVertical));
        if (resistance > 0.0D) {
            result.multiply(1.0D - resistance);
        }
        return result;
    }

    @Override
    public String toString() {
        return "KnockbackProfile{" + name + ", h=" + horizontal + ", v=" + vertical + '}';
    }
}
