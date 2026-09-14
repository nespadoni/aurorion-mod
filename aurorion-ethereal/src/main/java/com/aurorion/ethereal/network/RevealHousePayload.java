package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: a revelacao.
 *
 * <p>Leva nome, lema e cor prontos em vez do id da casa porque o cliente nao tem os datapacks — para
 * ele, casa nao existe como conceito. Isso tambem faz a tela de revelacao funcionar identica para
 * uma casa que foi criada no datapack cinco minutos antes.
 */
public record RevealHousePayload(Component houseName, Component motto, int color) implements CustomPacketPayload {
    public static final Type<RevealHousePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "reveal_house"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RevealHousePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, RevealHousePayload::houseName,
            ComponentSerialization.STREAM_CODEC, RevealHousePayload::motto,
            ByteBufCodecs.INT, RevealHousePayload::color,
            RevealHousePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
