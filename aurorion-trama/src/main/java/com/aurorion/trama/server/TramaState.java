package com.aurorion.trama.server;

import com.aurorion.trama.progression.ActivityWindow;
import com.aurorion.trama.skill.Build;
import java.util.Set;

final class TramaState {
    Build build = new Build(Set.of());
    boolean dirty = true, sprint, exitedSprint, pursuitConsumed;
    String dimension = "";
    final ActivityWindow activity = new ActivityWindow();
    long sprintSince, stoppedSince, momentumUntil, openingUntil, openingReady;
    long lastDamage, lastCombat, nextHeal, evasionUntil, evasionReady, magicStepUntil, magicStepReady;
    long magicWardUntil, magicWardReady, adaptationUntil, adaptationReady, shellReady;
    long pursuitReady, tailwindReady;
    String adaptation = "";
    TramaState(long now) { sprintSince = stoppedSince = lastDamage = lastCombat = now; }
    void sprint(boolean running, long now) {
        if (running == sprint) {
            if (running && now-sprintSince >= 60) momentumUntil = now+20;
            return;
        }
        sprint = running;
        if (running) {
            exitedSprint = false; pursuitConsumed = false;
            sprintSince = now;
            if (now >= openingReady) { openingUntil = now+40; openingReady = now+100; }
        } else { stoppedSince = now; exitedSprint = true; }
    }
}
