package com.aurorion.ato2.network;

import com.aurorion.ato2.AurorionAto2;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: veredito da escolha.
 *
 * <p>Existe para a tela nao ter que adivinhar. Fechar a tela no clique e torcer para ter dado certo
 * funciona ate duas pessoas disputarem a ultima vaga de uma casa lotada — dai uma delas ficaria
 * achando que entrou. Com o veredito, quem perdeu a corrida continua na tela e escolhe outra.
 */
public record HouseChoiceResultPayload(boolean success, Component message) implements CustomPacketPayload {
    public static final Type<HouseChoiceResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionAto2.MOD_ID, "house_choice_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HouseChoiceResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            HouseChoiceResultPayload::success,
            ComponentSerialization.STREAM_CODEC,
            HouseChoiceResultPayload::message,
            HouseChoiceResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
