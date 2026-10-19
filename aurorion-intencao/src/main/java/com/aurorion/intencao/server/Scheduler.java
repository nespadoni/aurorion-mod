package com.aurorion.intencao.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * "Daqui a N ticks." O {@code TickTask} do vanilla nao e atraso: ele roda assim que da. O chamado e
 * o veredito precisam de alguns segundos de tela antes do teleporte, e e para isso que isto existe.
 *
 * <p>Vazio quase sempre: o tick custa uma checagem de lista vazia. Nao e persistido — se o servidor
 * cair no meio, os eventos de login refazem o que faltou (ver {@link Plane#onLogin}).
 */
final class Scheduler {
    private record Task(long due, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {
    }

    static void after(long now, int ticks, Runnable action) {
        TASKS.add(new Task(now + ticks, action));
    }

    static void run(long now) {
        if (TASKS.isEmpty()) return;
        List<Runnable> ready = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.due() <= now) {
                ready.add(task.action());
                it.remove();
            }
        }
        // Fora da iteracao: uma acao pode agendar outra. Uma falha nao segura as outras.
        for (Runnable action : ready) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                com.aurorion.intencao.AurorionIntencao.LOGGER.error("Etapa agendada da batalha de intencao falhou", failure);
            }
        }
    }

    static void clear() {
        TASKS.clear();
    }
}
