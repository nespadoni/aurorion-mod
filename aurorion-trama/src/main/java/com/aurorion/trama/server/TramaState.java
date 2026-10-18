package com.aurorion.trama.server;

import com.aurorion.trama.progression.ActivityWindow;
import com.aurorion.trama.progression.ProgressData;
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
    /** Ultimo sync completo com o Pufferfish e o que ele leu; ver TramaRuntime.quietSync. */
    boolean unsynced = true;
    ProgressData.Entry syncedEntry;
    String syncedOrigin = "";
    int syncedEarned;
    long syncedAt;
    // Effects e funcao pura de (build, contexto): o mesmo retrato vale ate um dos dois mudar.
    private Build effectsBuild;
    private Build.Context effectsContext;
    private Build.Effects effects;
    TramaState(long now) { sprintSince = stoppedSince = lastDamage = lastCombat = now; }
    /** Build.effects com cache: um golpe ou BreakSpeed no mesmo estado nao refaz os tres EnumMaps. Somente leitura. */
    Build.Effects effects(Build.Context context) {
        if (effects == null || effectsBuild != build || !context.equals(effectsContext)) {
            effects = build.effects(context);
            effectsBuild = build;
            effectsContext = context;
        }
        return effects;
    }
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
