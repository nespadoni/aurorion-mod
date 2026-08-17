package com.aurorion.ato2.house;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Uma casa do Ato 2. Casa e <em>conteudo</em>, nao comportamento: mora num JSON de datapack
 * ({@code data/<namespace>/aurorion/houses/<nome>.json}), nao num enum Java. Trocar nome, cor,
 * icone, lotacao ou ate a quantidade de casas e editar arquivo e dar {@code /reload} — sem
 * recompilar jar, sem derrubar o servidor (SDD §7, diretriz 4).
 *
 * <p>O {@code id} nao aparece no JSON: ele vem do caminho do arquivo, igual receita ou loot table.
 */
public record House(
        ResourceLocation id,
        Component name,
        Component description,
        int color,
        Optional<ResourceLocation> icon,
        int capacity,
        int order
) {
    /** Branco: cor neutra de quem nao declarou cor no JSON. */
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    /** {@code capacity = 0} significa sem limite de membros. */
    public static final int UNLIMITED = 0;

    /** Aceita "#RRGGBB" ou "RRGGBB" — hex e o que quem edita datapack ja esta acostumado a escrever. */
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

    /**
     * A ordem de campos e a mesma do construtor menos o {@code order}, que so importa para ordenar a
     * lista no servidor — o cliente recebe a lista ja ordenada e nao precisa saber por que.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, House> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, House::id,
            ComponentSerialization.STREAM_CODEC, House::name,
            ComponentSerialization.STREAM_CODEC, House::description,
            ByteBufCodecs.INT, House::color,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), House::icon,
            ByteBufCodecs.VAR_INT, House::capacity,
            (id, name, description, color, icon, capacity) ->
                    new House(id, name, description, color, icon, capacity, 0));

    /** Codec do arquivo. Precisa do id porque ele vem de fora do JSON (do caminho do arquivo). */
    public static Codec<House> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(House::name),
                ComponentSerialization.CODEC.optionalFieldOf("description", CommonComponents.EMPTY).forGetter(House::description),
                COLOR_CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(House::color),
                ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(House::icon),
                Codec.intRange(UNLIMITED, 100_000).optionalFieldOf("capacity", UNLIMITED).forGetter(House::capacity),
                Codec.INT.optionalFieldOf("order", 0).forGetter(House::order)
        ).apply(instance, (name, description, color, icon, capacity, order) ->
                new House(id, name, description, color, icon, capacity, order)));
    }

    public boolean hasCapacity() {
        return capacity > UNLIMITED;
    }

    /** Nome ja tingido com a cor da casa — o que vai para chat e para a GUI. */
    public Component coloredName() {
        return name.copy().withStyle(style -> style.withColor(color));
    }
}
