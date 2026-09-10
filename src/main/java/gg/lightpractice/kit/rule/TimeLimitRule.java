package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;

/**
 * Ends a match once a time limit passes.
 *
 * <pre>
 * time-limit:
 *   seconds: 600
 *   warn-at: [60, 30, 10]
 * </pre>
 */
public final class TimeLimitRule implements KitRule {

    private long limitSeconds = 600L;
    private long[] warnings = new long[0];
    private int nextWarning;

    @Override
    public String id() {
        return "time-limit";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.limitSeconds = Math.max(1L, values.integer("seconds", 600));
        this.warnings = new long[values.list("warn-at").size()];
        int index = 0;
        for (Object entry : values.list("warn-at")) {
            warnings[index++] = Math.max(0L, asLong(entry));
        }
        java.util.Arrays.sort(warnings);
        this.nextWarning = 0;
    }

    @Override
    public void onMatchStart(Match match) {
        this.nextWarning = 0;
    }

    @Override
    public void onTick(Match match) {
        if (match == null) {
            return;
        }
        long remaining = remainingSeconds(match);
        // warnings count down, so walk the sorted array from the largest value downwards
        while (nextWarning < warnings.length && remaining <= warnings[warnings.length - 1 - nextWarning]) {
            match.notifyTimeWarning(warnings[warnings.length - 1 - nextWarning]);
            nextWarning++;
        }
        if (remaining <= 0L) {
            match.timeUp();
        }
    }

    public long limitSeconds() {
        return limitSeconds;
    }

    /** Seconds left before the limit is reached, never negative. */
    public long remainingSeconds(Match match) {
        long elapsed = match == null ? 0L : match.elapsedSeconds();
        return Math.max(0L, limitSeconds - elapsed);
    }

    private static long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Matches end after " + limitSeconds + " seconds.";
    }
}
