package com.aurorion.portais.line;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

/**
 * Quando a linha abre, por quanto tempo, e com quanta antecedencia avisa.
 *
 * <p><b>Tempo real, nao tempo do jogo.</b> "Sabado as 20:00" precisa cair no sabado as 20:00 da
 * comunidade — o dia do Minecraft dura 20 minutos e para com {@code doDaylightCycle false}, entao
 * nao serve de relogio. Por isso existe o campo {@code timezone}: sem ele o servidor usaria o fuso
 * da maquina onde esta hospedado, que quase nunca e o fuso dos jogadores.
 *
 * <p>Todo o calculo de calendario ({@link ZonedDateTime}, que aloca) acontece <b>aqui</b>, e este
 * metodo so e chamado nas transicoes — quando uma janela fecha e a proxima precisa ser descoberta.
 * Algumas vezes por semana, portanto. O que roda de segundo em segundo e comparacao de {@code long}
 * em {@link com.aurorion.portais.runtime.LineClock}, sem alocar nada (SDD §2).
 */
public record Schedule(
        ZoneId zone,
        List<Departure> weekly,
        int everyMinutes,
        int openMinutes,
        List<Integer> warnMinutesBefore
) {
    /** Sentinela de "esta linha nao tem proxima partida" — nunca abre sozinha, so por comando. */
    public static final long NEVER = Long.MAX_VALUE;

    public static final int DEFAULT_OPEN_MINUTES = 10;
    private static final List<Integer> DEFAULT_WARNINGS = List.of(60, 15, 5, 1);

    private static final Codec<ZoneId> ZONE_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                try {
                    return DataResult.success(ZoneId.of(raw));
                } catch (DateTimeException exception) {
                    return DataResult.error(() -> "Fuso horario desconhecido: '" + raw
                            + "'. Use um id da IANA, ex.: America/Sao_Paulo.");
                }
            },
            ZoneId::getId);

    public static final Codec<Schedule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ZONE_CODEC.optionalFieldOf("timezone", ZoneId.systemDefault()).forGetter(Schedule::zone),
            Departure.CODEC.listOf().optionalFieldOf("weekly", List.of()).forGetter(Schedule::weekly),
            Codec.intRange(0, 60 * 24 * 365).optionalFieldOf("everyMinutes", 0).forGetter(Schedule::everyMinutes),
            Codec.intRange(1, 60 * 24).optionalFieldOf("openMinutes", DEFAULT_OPEN_MINUTES).forGetter(Schedule::openMinutes),
            Codec.intRange(0, 60 * 24 * 7).listOf().optionalFieldOf("warnMinutesBefore", DEFAULT_WARNINGS).forGetter(Schedule::warnMinutesBefore)
    ).apply(instance, (zone, weekly, everyMinutes, openMinutes, warnings) -> new Schedule(
            zone, List.copyOf(weekly), everyMinutes, openMinutes, sortedDescending(warnings))));

    /**
     * Avisos do mais distante para o mais proximo. A ordem importa: o relogio percorre esta lista com
     * um cursor que so anda para frente, entao um aviso fora de ordem seria pulado.
     */
    private static List<Integer> sortedDescending(List<Integer> warnings) {
        return warnings.stream()
                .filter(minutes -> minutes > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public long openMillis() {
        return openMinutes * 60_000L;
    }

    /**
     * Primeira partida <b>estritamente depois</b> de {@code afterMillis}.
     *
     * <p>Os dois modos convivem: se o datapack declarar {@code weekly} e {@code everyMinutes} ao
     * mesmo tempo, vale a que vier primeiro. Isso deixa escrever "toda hora cheia, e mais uma extra
     * no sabado a noite" sem sintaxe nova.
     *
     * @return epoch millis da proxima partida, ou {@link #NEVER} se a linha nao tem recorrencia.
     */
    public long nextDepartureAfter(long afterMillis) {
        long best = NEVER;

        if (!weekly.isEmpty()) {
            ZonedDateTime base = Instant.ofEpochMilli(afterMillis).atZone(zone);
            for (Departure departure : weekly) {
                for (DayOfWeek day : departure.days()) {
                    // nextOrSame preserva a hora do dia; o .with(at) logo depois e quem fixa o horario.
                    ZonedDateTime candidate = base.with(TemporalAdjusters.nextOrSame(day)).with(departure.at());
                    long millis = candidate.toInstant().toEpochMilli();
                    if (millis <= afterMillis) {
                        // plusWeeks (e nao +7 dias em millis) para o horario continuar as 20:00 na
                        // semana seguinte mesmo se houver mudanca de horario de verao no meio.
                        millis = candidate.plusWeeks(1).toInstant().toEpochMilli();
                    }
                    if (millis < best) {
                        best = millis;
                    }
                }
            }
        }

        if (everyMinutes > 0) {
            long period = everyMinutes * 60_000L;
            // Ancorado na meia-noite local do dia corrente: "de 6 em 6 horas" cai em 00:00, 06:00,
            // 12:00, 18:00 — e nao num horario quebrado herdado da hora em que o servidor subiu.
            long anchor = Instant.ofEpochMilli(afterMillis).atZone(zone)
                    .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
            long next = afterMillis < anchor ? anchor : anchor + ((afterMillis - anchor) / period + 1) * period;
            if (next < best) {
                best = next;
            }
        }

        return best;
    }
}
