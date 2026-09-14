package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.HouseOption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Servidor -> cliente: abre a grade de casas com o catalogo inteiro dentro.
 *
 * <p>Manda a lista completa em vez de um indice pre-sincronizado porque isso e o que permite as
 * casas serem datapack: o cliente nao precisa ter os JSONs, nao precisa dar reload junto e nao pode
 * ficar dessincronizado do servidor. O custo e O(casas) — e trafega no clique no altar, nao no login
 * e muito menos por tick (SDD §7.3).
 *
 * @param options casas disponiveis, ja ordenadas pelo servidor, com a lotacao do momento
 * @param current casa atual do jogador, se ele ja tiver uma
 * @param locked  true quando a tela e so de leitura: ja escolheu, ou quem decide e a cerimonia
 */
public record OpenHouseSelectionPayload(
        List<HouseOption> options,
        Optional<ResourceLocation> current,
        boolean locked
) implements CustomPacketPayload {
    public static final Type<OpenHouseSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "open_house_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHouseSelectionPayload> STREAM_CODEC = StreamCodec.composite(
            HouseOption.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenHouseSelectionPayload::options,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), OpenHouseSelectionPayload::current,
            ByteBufCodecs.BOOL, OpenHouseSelectionPayload::locked,
            OpenHouseSelectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
