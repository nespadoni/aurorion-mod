package com.aurorion.talk.network;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Enviado pelo cliente quando o jogador confirma um estilo na tela de personalizacao.
 *
 * <p>O servidor <b>nunca</b> confia neste payload cegamente: valida com
 * {@link BalloonStyle#isWellFormed()} antes de persistir e propagar, porque um cliente modificado
 * poderia mandar qualquer caminho de textura ou qualquer cor.</p>
 */
public record SetStylePayload(BalloonStyle style) implements CustomPacketPayload {
    public static final Type<SetStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionTalk.MOD_ID, "set_style"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetStylePayload> STREAM_CODEC =
            BalloonStyle.STREAM_CODEC.map(SetStylePayload::new, SetStylePayload::style)
                    .cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
