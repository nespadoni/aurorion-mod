package com.aurorion.trama.progression;

import java.util.HashSet;
import java.util.Set;

/** Sixty one-second samples. Movement alone (AFK rails/water) or stationary clicks are insufficient. */
public final class ActivityWindow {
    private final Set<String> cells = new HashSet<>();
    private final Set<Integer> looks = new HashSet<>();
    private int samples, interactions, pendingInteractions, actionSamples, movingSamples;
    private double lastX, lastY, lastZ;
    private boolean hasPosition;
    public void interact(int kind) { pendingInteractions |= kind; }
    public void clear() {
        cells.clear(); looks.clear(); samples = interactions = pendingInteractions = actionSamples = movingSamples = 0;
        hasPosition = false;
    }
    public boolean sample(double x, double y, double z, float yaw, float pitch) {
        // Teleporting does not fill an activity window. Use two-block cells for builders.
        if (hasPosition && squared(x-lastX, y-lastY, z-lastZ) > 64*64) clear();
        if (hasPosition && squared(x-lastX, y-lastY, z-lastZ) > .04) movingSamples++;
        if (pendingInteractions != 0) { actionSamples++; interactions |= pendingInteractions; pendingInteractions = 0; }
        lastX = x; lastY = y; lastZ = z; hasPosition = true;
        cells.add((int)Math.floor(x/2) + ":" + (int)Math.floor(y/2) + ":" + (int)Math.floor(z/2));
        looks.add(Math.floorMod((int)Math.floor(yaw/30), 12)*7 + (int)Math.floor((pitch+90)/30));
        if (++samples < 60) return false;
        boolean active = looks.size() >= 2 && (cells.size() >= 4 && movingSamples >= 10
                || cells.size() >= 2 && Integer.bitCount(interactions) >= 2 && actionSamples >= 8);
        clear(); return active;
    }
    private static double squared(double x, double y, double z) { return x*x+y*y+z*z; }
}
