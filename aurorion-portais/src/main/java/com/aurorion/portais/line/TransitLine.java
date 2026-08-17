package com.aurorion.portais.line;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Uma linha: o conjunto de dimensoes que ela destranca, mais o horario em que faz isso.
 *
 * <p>Mora em {@code data/<namespace>/aurorion/linhas/<nome>.json}, igual receita ou loot table — o
 * {@code id} vem do caminho do arquivo, nao do conteudo. Mudar o dia da partida, o tempo de janela
 * ou ate criar uma linha nova e editar arquivo e dar {@code /reload}: sem recompilar jar, sem
 * derrubar o servidor (SDD §7, diretriz 4).
 *
 * <p>{@code dimensions} e lista, e nao um campo unico, porque uma linha pode servir a um conjunto —
 * um mod de exploracao que adiciona tres dimensoes irmas cabe numa linha so, com um aviso so no
 * chat, em vez de tres linhas disparando avisos no mesmo minuto.
 */
public record TransitLine(
        ResourceLocation id,
        Component name,
        Component description,
        int color,
        List<ResourceKey<Level>> dimensions,
        Optional<Station> station,
        Optional<Station> arrival,
        Schedule schedule,
        int order
) {
    public static final int DEFAULT_COLOR = 0xFFAA00;

    /** Aceita "#RRGGBB" ou "RRGGBB" — mesmo formato do datapack de casas do aurorion-ato2. */
    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                String hex = raw.startsWith("#") ? raw.substring(1) : raw;
                if (hex.length() != 6) {
                    return DataResult.error(() -> "Cor precisa ter 6 digitos hexadecimais (RRGGBB): " + raw);
                }
                try {
                    return DataResult.success(Integer.parseInt(hex, 16));
                } catch (NumberFormatException exception) {
                    return DataResult.error(() -> "Cor hexadecimal invalida: " + raw);
                }
            },
            value -> String.format(Locale.ROOT, "#%06X", value));

    public static Codec<TransitLine> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(TransitLine::name),
                ComponentSerialization.CODEC.optionalFieldOf("description", CommonComponents.EMPTY).forGetter(TransitLine::description),
                COLOR_CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(TransitLine::color),
                ResourceKey.codec(Registries.DIMENSION).listOf().fieldOf("dimensions").forGetter(TransitLine::dimensions),
                Station.CODEC.optionalFieldOf("station").forGetter(TransitLine::station),
                Station.CODEC.optionalFieldOf("arrival").forGetter(TransitLine::arrival),
                Schedule.CODEC.fieldOf("schedule").forGetter(TransitLine::schedule),
                Codec.INT.optionalFieldOf("order", 0).forGetter(TransitLine::order)
        ).apply(instance, (name, description, color, dimensions, station, arrival, schedule, order) ->
                new TransitLine(id, name, description, color, List.copyOf(dimensions), station, arrival, schedule, order)));
    }

    public Component coloredName() {
        return name.copy().withStyle(style -> style.withColor(color));
    }

    /** A estacao de desembarque desta dimensao especifica, se o datapack declarou uma para ela. */
    public Optional<Station> arrivalIn(ResourceKey<Level> dimension) {
        return arrival.filter(station -> station.dimension() == dimension);
    }

    public boolean serves(ResourceKey<Level> dimension) {
        // Lista pequena (quase sempre 1 elemento) e percorrida com indice: sem alocar iterador num
        // metodo que roda em toda tentativa de viagem.
        for (int i = 0; i < dimensions.size(); i++) {
            if (dimensions.get(i) == dimension) {
                return true;
            }
        }
        return false;
    }
}
