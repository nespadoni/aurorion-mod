package com.aurorion.portais.line;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Uma partida recorrente: "sabado e domingo, as 20:00".
 *
 * <p>Os dias sao escritos por nome em ingles ({@code "saturday"}), com o mesmo vocabulario de
 * {@link DayOfWeek} — sem inventar um dicionario proprio que quem edita datapack teria que decorar.
 * A comparacao ignora maiusculas.
 */
public record Departure(Set<DayOfWeek> days, LocalTime at) {
    /** Padrao de {@code days} ausente: partida diaria. */
    public static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);

    private static final Codec<DayOfWeek> DAY_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                try {
                    return DataResult.success(DayOfWeek.valueOf(raw.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Dia da semana desconhecido: '" + raw
                            + "'. Use monday, tuesday, wednesday, thursday, friday, saturday ou sunday.");
                }
            },
            day -> day.name().toLowerCase(Locale.ROOT));

    /** Aceita "HH:mm" e "HH:mm:ss" — os dois formatos que {@link LocalTime#parse} entende de graca. */
    private static final Codec<LocalTime> TIME_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                try {
                    return DataResult.success(LocalTime.parse(raw));
                } catch (DateTimeParseException exception) {
                    return DataResult.error(() -> "Horario invalido: '" + raw + "'. Use HH:mm (ex.: 20:00).");
                }
            },
            LocalTime::toString);

    public static final Codec<Departure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DAY_CODEC.listOf().optionalFieldOf("days", List.copyOf(EVERY_DAY)).forGetter(departure -> List.copyOf(departure.days())),
            TIME_CODEC.fieldOf("at").forGetter(Departure::at)
    ).apply(instance, (days, at) -> new Departure(
            days.isEmpty() ? EVERY_DAY : EnumSet.copyOf(days), at)));
}
