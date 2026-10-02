package com.aurorion.trama.progression;

/** Pure cumulative curves: banking discoveries cannot bypass the active-time gate. */
public final class ProgressRules {
    public static final int MAX_POINTS = 45;
    public static final int INITIAL_POINTS = 5;
    private ProgressRules() {}

    public static long experienceRequired(int points, int firstCost, int increment) {
        long steps = Math.max(0, Math.min(MAX_POINTS, points) - INITIAL_POINTS);
        return steps * firstCost + steps * (steps - 1) / 2 * increment;
    }
    public static long secondsRequired(int points, int firstMinutes, int increment) {
        long steps = Math.max(0, Math.min(MAX_POINTS, points) - INITIAL_POINTS);
        return (steps * firstMinutes + steps * (steps - 1) / 2 * increment) * 60;
    }
    public static int earned(long experience, long activeSeconds, int firstCost, int xpIncrement,
                             int firstMinutes, int minuteIncrement) {
        int points = INITIAL_POINTS;
        while (points < MAX_POINTS
                && experience >= experienceRequired(points + 1, firstCost, xpIncrement)
                && activeSeconds >= secondsRequired(points + 1, firstMinutes, minuteIncrement)) points++;
        return points;
    }
}
