package com.aurorion.personagem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * O que a pessoa digitou. Nada aqui e confiavel.
 *
 * <p>O limite de 64 caracteres por campo esta no <b>codec</b>, e nao so na tela: a tela e do cliente
 * e pode ser substituida, entao o tamanho maximo precisa ser recusado antes de o pacote virar objeto.
 * A regra do nome de verdade (letras, 2 a 24, sobrenome obrigatorio, unicidade) e reconferida no
 * servidor pelo {@code CharacterName}.
 */
public record SubmitNamePayload(String firstName, String lastName) implements CustomPacketPayload {
    public static final Type<SubmitNamePayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_personagem:submit_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SubmitNamePayload::firstName,
            ByteBufCodecs.stringUtf8(64), SubmitNamePayload::lastName,
            SubmitNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
