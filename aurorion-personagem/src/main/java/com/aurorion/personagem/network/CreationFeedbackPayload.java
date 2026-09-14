package com.aurorion.personagem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A resposta do servidor a um envio.
 *
 * <p>Existe porque a recusa precisa aparecer <b>dentro</b> da tela. Uma mensagem de chat ficaria
 * atras dela, e a pessoa veria o botao simplesmente nao funcionar.
 *
 * @param accepted true fecha a tela; false mantem aberta com o motivo em vermelho.
 */
public record CreationFeedbackPayload(boolean accepted, Component message) implements CustomPacketPayload {
    public static final Type<CreationFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_personagem:creation_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreationFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CreationFeedbackPayload::accepted,
                    ComponentSerialization.STREAM_CODEC, CreationFeedbackPayload::message,
                    CreationFeedbackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
