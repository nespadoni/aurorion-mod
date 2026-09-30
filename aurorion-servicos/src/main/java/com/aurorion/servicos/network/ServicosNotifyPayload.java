package com.aurorion.servicos.network;

import com.aurorion.servicos.AurorionServicos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Aviso do app: pedido novo, pedido aceito, candidato novo. O cliente decide como mostrar — no
 * celular (notificacao, som e aviso na tela) so se a pessoa estiver com o celular.
 *
 * @param badge pedidos esperando resposta, para o numero no icone; -1 quando nao mudou
 */
public record ServicosNotifyPayload(String title, String text, int badge) implements CustomPacketPayload {
    public static final Type<ServicosNotifyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionServicos.MOD_ID, "notify"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServicosNotifyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeUtf(data.title, 96);
                buf.writeUtf(data.text, 256);
                buf.writeVarInt(data.badge + 1);
            },
            buf -> new ServicosNotifyPayload(buf.readUtf(96), buf.readUtf(256), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
