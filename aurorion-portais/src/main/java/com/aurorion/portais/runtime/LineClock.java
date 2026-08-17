package com.aurorion.portais.runtime;

import com.aurorion.portais.config.TransitConfig;
import com.aurorion.portais.line.Schedule;
import com.aurorion.portais.line.TransitLine;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * O estado vivo de uma linha: quando abre, ate quando fica aberta, e quais avisos ja saiu.
 *
 * <p>Esta e a classe onde mora a garantia de performance do mod. Um tick de espera — que e o estado
 * de 99,99% do tempo — custa <b>duas comparacoes de {@code long}</b> e mais nada: nenhuma alocacao,
 * nenhuma consulta de calendario, nenhum acesso a jogador. Toda a aritmetica de datas
 * ({@link Schedule#nextDepartureAfter}, que aloca {@code ZonedDateTime}) acontece so nas transicoes,
 * algumas vezes por semana.
 *
 * <p>O cursor de avisos so anda para frente dentro de um ciclo e e reposicionado no reagendamento.
 * E o que garante que cada chamada saia exatamente uma vez, mesmo que o servidor trave por um
 * minuto e volte com varios avisos vencidos de uma vez — os vencidos sao pulados, nao disparados em
 * rajada no chat de 90 pessoas.
 */
public final class LineClock {
    private final TransitLine line;

    private long nextDeparture = Schedule.NEVER;
    private long closesAt;
    private int warnCursor;
    private boolean open;
    private long lastCountdownSecond = -1;

    public LineClock(TransitLine line, long now) {
        this.line = line;
        reschedule(now);
    }

    public TransitLine line() {
        return line;
    }

    public boolean isOpen() {
        return open;
    }

    /** Epoch millis em que a janela aberta fecha. So faz sentido quando {@link #isOpen()}. */
    public long closesAt() {
        return closesAt;
    }

    /** Epoch millis da proxima partida, ou {@link Schedule#NEVER} se a linha nao tem recorrencia. */
    public long nextDeparture() {
        return nextDeparture;
    }

    void tick(MinecraftServer server, long now) {
        if (open) {
            if (now >= closesAt) {
                open = false;
                TransitAnnouncer.departed(server, line);
                reschedule(now);
            } else {
                countdown(server, now);
            }
            return;
        }

        if (now >= nextDeparture) {
            openWindow(server, now, line.schedule().openMillis());
            return;
        }

        pendingWarnings(server, now);
    }

    /** Abertura manual por comando: mesma cerimonia da automatica, com duracao propria. */
    public void forceOpen(MinecraftServer server, long now, int minutes) {
        openWindow(server, now, minutes * 60_000L);
    }

    /** @return false se a linha ja estava fechada. */
    public boolean forceClose(MinecraftServer server, long now) {
        if (!open) return false;

        open = false;
        TransitAnnouncer.departed(server, line);
        reschedule(now);
        return true;
    }

    private void openWindow(MinecraftServer server, long now, long durationMillis) {
        open = true;
        // Ancorado em 'now' e nao em nextDeparture: se o servidor engasgou e a abertura chegou
        // atrasada, a janela ainda dura os 10 minutos prometidos em vez de nascer pela metade.
        closesAt = now + durationMillis;
        lastCountdownSecond = -1;
        TransitAnnouncer.boarding(server, line, closesAt, now);
    }

    /**
     * Descobre a proxima partida. A partida guardada so e recalculada quando ja passou — assim um
     * fechamento manual antecipado nao faz a linha pular a partida agendada que ainda vem.
     */
    private void reschedule(long now) {
        if (nextDeparture <= now || nextDeparture == Schedule.NEVER) {
            nextDeparture = line.schedule().nextDepartureAfter(now);
        }
        warnCursor = firstPendingWarning(now);
    }

    private int firstPendingWarning(long now) {
        List<Integer> warnings = line.schedule().warnMinutesBefore();
        int cursor = 0;
        while (cursor < warnings.size() && nextDeparture - warnings.get(cursor) * 60_000L <= now) {
            cursor++;
        }
        return cursor;
    }

    private void pendingWarnings(MinecraftServer server, long now) {
        List<Integer> warnings = line.schedule().warnMinutesBefore();

        while (warnCursor < warnings.size()) {
            int minutes = warnings.get(warnCursor);
            if (now < nextDeparture - minutes * 60_000L) {
                return;
            }
            warnCursor++;
            TransitAnnouncer.warn(server, line, minutes);
        }
    }

    private void countdown(MinecraftServer server, long now) {
        int window = TransitConfig.COUNTDOWN_SECONDS.get();
        if (window <= 0) return;

        long remaining = closesAt - now;
        if (remaining > window * 1000L) return;

        long second = (remaining + 999) / 1000;
        if (second == lastCountdownSecond) return;

        lastCountdownSecond = second;
        TransitAnnouncer.countdown(server, line, (int) second);
    }
}
