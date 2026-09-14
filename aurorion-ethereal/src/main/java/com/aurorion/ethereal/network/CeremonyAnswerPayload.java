package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor: "respondi a alternativa X da pergunta N".
 *
 * <p>O indice da pergunta vai junto de proposito. Sem ele, o servidor teria de confiar que o cliente
 * esta na pergunta que ele acha que esta, e um cliente adulterado responderia a mesma pergunta oito
 * vezes para empilhar contagem numa casa so.
 */
public record CeremonyAnswerPayload(int question, int option) implements CustomPacketPayload {
    public static final Type<CeremonyAnswerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "ceremony_answer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CeremonyAnswerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CeremonyAnswerPayload::question,
            ByteBufCodecs.VAR_INT, CeremonyAnswerPayload::option,
            CeremonyAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
