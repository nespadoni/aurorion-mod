package com.aurorion.servicos.network;

import com.aurorion.servicos.AurorionServicos;
import com.aurorion.servicos.data.ServicosRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma acao no app. O servidor refaz toda checagem e responde com a aba {@code tab}/{@code filter}
 * atualizada ({@link ServicosPagePayload}), com a mensagem do resultado e, quando for o caso, a
 * conversa que o celular deve abrir.
 *
 * @param action o que fazer ({@code anunciar}, {@code pedir}, {@code aceitar}...; ver {@code ServicosManager})
 * @param id     anuncio, pedido ou vaga alvo; 0 para criar
 * @param text   descricao do anuncio/vaga ou texto do pedido; "1"/"0" no {@code trabalhando}
 * @param price  preco do anuncio ou salario da vaga
 */
public record ServicosActionPayload(String action, long id, String category, String title, String text,
                                    String price, String tab, String filter) implements CustomPacketPayload {
    public static final Type<ServicosActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionServicos.MOD_ID, "action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServicosActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeUtf(data.action, 24);
                buf.writeVarLong(data.id);
                buf.writeUtf(data.category, 32);
                buf.writeUtf(data.title, ServicosRules.TITLE * 2);
                buf.writeUtf(data.text, ServicosRules.DESCRIPTION * 2);
                buf.writeUtf(data.price, ServicosRules.PRICE * 2);
                buf.writeUtf(data.tab, 16);
                buf.writeUtf(data.filter, 32);
            },
            buf -> new ServicosActionPayload(buf.readUtf(24), buf.readVarLong(), buf.readUtf(32),
                    buf.readUtf(ServicosRules.TITLE * 2), buf.readUtf(ServicosRules.DESCRIPTION * 2),
                    buf.readUtf(ServicosRules.PRICE * 2), buf.readUtf(16), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
