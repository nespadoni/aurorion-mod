package com.aurorion.ato2.network;

import com.aurorion.ato2.AurorionAto2;
import com.aurorion.ato2.house.HouseOption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Servidor -> cliente: abre a tela de escolha com o catalogo inteiro dentro.
 *
 * <p>Manda a lista completa em vez de um indice de catalogo pre-sincronizado porque isso e o que
 * permite as casas serem datapack: o cliente nao precisa ter os JSONs, nao precisa dar reload junto
 * e nao pode ficar dessincronizado do servidor. O custo e O(casas) — e trafega no clique no altar,
 * nao no login e muito menos por tick, entao nao entra na conta de nenhum caminho quente (SDD §7.3).
 *
 * @param options casas disponiveis, ja ordenadas pelo servidor, com a lotacao do momento
 * @param current casa atual do jogador, se ele ja tiver uma
 * @param locked  true quando ja escolheu e o servidor nao permite trocar: a tela abre so de leitura
 */
public record OpenHouseSelectionPayload(
        List<HouseOption> options,
        Optional<ResourceLocation> current,
        boolean locked
) implements CustomPacketPayload {
    public static final Type<OpenHouseSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionAto2.MOD_ID, "open_house_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHouseSelectionPayload> STREAM_CODEC = StreamCodec.composite(
            HouseOption.STREAM_CODEC.apply(ByteBufCodecs.list()),
            OpenHouseSelectionPayload::options,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            OpenHouseSelectionPayload::current,
            ByteBufCodecs.BOOL,
            OpenHouseSelectionPayload::locked,
            OpenHouseSelectionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
