package com.aurorion.servicos.network;

import com.aurorion.servicos.AurorionServicos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * O cliente pede uma aba do app.
 *
 * @param tab    {@code buscar}, {@code pedidos}, {@code vagas} ou {@code perfil}
 * @param filter categoria (vazio = todas) em buscar/vagas; {@code meus} ou {@code recebidos} em pedidos
 */
public record ServicosQueryPayload(String tab, String filter) implements CustomPacketPayload {
    public static final Type<ServicosQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionServicos.MOD_ID, "query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServicosQueryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeUtf(data.tab, 16);
                buf.writeUtf(data.filter, 32);
            },
            buf -> new ServicosQueryPayload(buf.readUtf(16), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
