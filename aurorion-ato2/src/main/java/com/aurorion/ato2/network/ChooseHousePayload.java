package com.aurorion.ato2.network;

import com.aurorion.ato2.AurorionAto2;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor: "escolhi esta casa".
 *
 * <p>Deliberadamente so o id — nada de nome, cor ou lotacao vindos do cliente. Tudo que o servidor
 * precisa saber ele ja tem; o cliente so aponta. E o id ainda passa por tres validacoes do outro
 * lado (existe no catalogo? o jogador estava mesmo num altar? a casa cabe mais gente?).
 */
public record ChooseHousePayload(ResourceLocation house) implements CustomPacketPayload {
    public static final Type<ChooseHousePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionAto2.MOD_ID, "choose_house"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseHousePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            ChooseHousePayload::house,
            ChooseHousePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
