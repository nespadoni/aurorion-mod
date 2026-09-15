package com.aurorion.ethereal.house;

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
 * Uma casa de Ethereal. Casa e <em>conteudo</em>, nao comportamento: mora num JSON de datapack
 * ({@code data/<namespace>/aurorion/houses/<nome>.json}), nao num enum Java. Trocar nome, lema, cor,
 * icone, lotacao ou ate a quantidade de casas e editar arquivo e dar {@code /reload} — sem
 * recompilar jar, sem derrubar o servidor (SDD §7, diretriz 4).
 *
 * <p>Essa e a diferenca principal para o mod de referencia, onde as cinco casas eram um {@code enum}
 * com nome, lema e cor escritos em Java: corrigir uma palavra do lema da Nyx era recompilar e
 * reiniciar um servidor de 80 jogadores.
 *
 * <p>O {@code id} nao aparece no JSON: ele vem do caminho do arquivo, igual receita ou loot table.
 *
 * @param motto o que a casa diz de si mesma. Aparece no card do altar e, em destaque, na revelacao.
 */
public record House(
        ResourceLocation id,
        Component name,
        Component motto,
        Component description,
        int color,
        Optional<ResourceLocation> icon,
        int capacity,
        int order,
        RiteStyle ceremony
) {
    /** Branco: cor neutra de quem nao declarou cor no JSON. */
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    /** {@code capacity = 0} significa sem limite de membros. */
    public static final int UNLIMITED = 0;

    /** Aceita "#RRGGBB" ou "RRGGBB" — hex e o que quem edita datapack ja esta acostumado a escrever. */
    static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(
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
     * Escrito campo a campo, e nao com {@code StreamCodec.composite}: o {@code composite} para em
     * seis campos; nome, paleta e trilha viajam juntos.
     *
     * <p>O {@code order} nao vai junto — ele so importa para ordenar a lista no servidor, e o cliente
     * recebe a lista ja ordenada. Por isso ele volta como zero na leitura.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, House> STREAM_CODEC =
            StreamCodec.of(House::write, House::read);

    private static void write(RegistryFriendlyByteBuf buffer, House house) {
        ResourceLocation.STREAM_CODEC.encode(buffer, house.id());
        ComponentSerialization.STREAM_CODEC.encode(buffer, house.name());
        ComponentSerialization.STREAM_CODEC.encode(buffer, house.motto());
        ComponentSerialization.STREAM_CODEC.encode(buffer, house.description());
        buffer.writeInt(house.color());
        ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buffer, house.icon());
        buffer.writeVarInt(house.capacity());
        buffer.writeInt(house.ceremony.secondary());
        buffer.writeInt(house.ceremony.accent());
        ResourceLocation.STREAM_CODEC.encode(buffer, house.ceremony.music());
    }

    private static House read(RegistryFriendlyByteBuf buffer) {
        return new House(
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ComponentSerialization.STREAM_CODEC.decode(buffer),
                ComponentSerialization.STREAM_CODEC.decode(buffer),
                ComponentSerialization.STREAM_CODEC.decode(buffer),
                buffer.readInt(),
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buffer),
                buffer.readVarInt(),
                0, new RiteStyle(buffer.readInt(), buffer.readInt(), ResourceLocation.STREAM_CODEC.decode(buffer)));
    }

    /** Codec do arquivo. Precisa do id porque ele vem de fora do JSON (do caminho do arquivo). */
    public static Codec<House> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(House::name),
                ComponentSerialization.CODEC.optionalFieldOf("motto", CommonComponents.EMPTY).forGetter(House::motto),
                ComponentSerialization.CODEC.optionalFieldOf("description", CommonComponents.EMPTY).forGetter(House::description),
                COLOR_CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(House::color),
                ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(House::icon),
                Codec.intRange(UNLIMITED, 100_000).optionalFieldOf("capacity", UNLIMITED).forGetter(House::capacity),
                Codec.INT.optionalFieldOf("order", 0).forGetter(House::order),
                RiteStyle.CODEC.optionalFieldOf("ceremony", RiteStyle.DEFAULT).forGetter(House::ceremony)
        ).apply(instance, (name, motto, description, color, icon, capacity, order, ceremony) ->
                new House(id, name, motto, description, color, icon, capacity, order, ceremony)));
    }

    public boolean hasCapacity() {
        return capacity > UNLIMITED;
    }

    /** Nome ja tingido com a cor da casa — o que vai para chat e para a GUI. */
    public Component coloredName() {
        return name.copy().withStyle(style -> style.withColor(color));
    }

    /** A cor da casa como ARGB opaco, que e o que todo desenho de GUI e de holograma espera. */
    public int argb() {
        return 0xFF000000 | color;
    }
}
