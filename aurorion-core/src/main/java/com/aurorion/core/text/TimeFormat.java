package com.aurorion.core.text;

import net.minecraft.network.chat.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Duracao e data viram {@link Component} traduzivel, nunca {@link String} montada no servidor.
 *
 * <p>Isso nao e purismo: o servidor resolve a mensagem uma vez e manda para todo mundo, entao um
 * "sabado" escrito em texto puro aqui chegaria em portugues no cliente de quem joga em ingles. Dia
 * da semana e unidade de tempo saem como chave de idioma e sao montados no cliente.
 */
public final class TimeFormat {
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeFormat() {
    }

    /**
     * Duracao aproximada, com no maximo duas unidades: "2d 4h", "13min 20s", "45s".
     *
     * <p>Duas unidades e o ponto em que a leitura ainda e instantanea. "2d 4h 13min 20s" e mais
     * preciso e menos util — quem le um quadro de horarios quer saber se da tempo de ir buscar
     * comida, nao a hora exata.
     */
    public static Component duration(long millis) {
        if (millis < 0) {
            millis = 0;
        }

        if (millis >= DAY) {
            return join("dias", millis / DAY, "horas", millis % DAY / HOUR);
        }
        if (millis >= HOUR) {
            return join("horas", millis / HOUR, "minutos", millis % HOUR / MINUTE);
        }
        if (millis >= MINUTE) {
            return join("minutos", millis / MINUTE, "segundos", millis % MINUTE / SECOND);
        }
        return unit("segundos", millis / SECOND);
    }

    /** Data como "sabado, 20:00", com o dia traduzido no cliente. */
    public static Component clockTime(long epochMillis, ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(zone);
        return Component.translatable("aurorion_core.horario",
                dayOfWeek(time.getDayOfWeek()),
                String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute()));
    }

    public static Component dayOfWeek(DayOfWeek day) {
        return Component.translatable("aurorion_core.dia." + day.name().toLowerCase(Locale.ROOT));
    }

    private static Component unit(String key, long value) {
        return Component.translatable("aurorion_core.tempo." + key, value);
    }

    /** Esconde a segunda unidade quando ela e zero: "2d" em vez de "2d 0h". */
    private static Component join(String majorKey, long major, String minorKey, long minor) {
        if (minor == 0) {
            return unit(majorKey, major);
        }
        return Component.empty().append(unit(majorKey, major)).append(" ").append(unit(minorKey, minor));
    }
}
