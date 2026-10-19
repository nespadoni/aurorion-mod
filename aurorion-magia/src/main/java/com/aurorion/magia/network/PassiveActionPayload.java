package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Apertei a tecla da passiva." Um byte, so quando a pessoa aperta. O servidor decide tudo: qual
 * passiva responde, se ha energia, para onde se olha — o cliente nao manda alvo nem posicao, entao
 * nao ha o que forjar.
 *
 * @param alternate a tecla com Shift: a segunda acao da passiva (marcar alguem, abrir as pedras)
 */
public record PassiveActionPayload(boolean alternate) implements CustomPacketPayload {
    public static final Type<PassiveActionPayload> TYPE = new Type<>(AurorionMagia.id("passive_action"));

    public static final StreamCodec<ByteBuf, PassiveActionPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(PassiveActionPayload::new, PassiveActionPayload::alternate);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
