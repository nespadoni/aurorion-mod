package com.aurorion.personagem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tudo que a tela precisa desenhar, num pacote so.
 *
 * <p>O texto viaja junto de proposito: a tela nao guarda copia da config nem le chave de traducao do
 * cliente. Editar o TOML do servidor muda o que a proxima pessoa le, sem que ninguem atualize nada.
 *
 * @param reserved nome ja reservado por uma criacao que ficou pela metade — string vazia no caso
 *                 normal. A tela mostra esse nome travado: a identidade ja foi escrita no diario,
 *                 e escolher outro nome agora deixaria a reserva antiga orfa.
 */
public record OpenCreationPayload(boolean replacement, String title, String intro, String rules, String reserved)
        implements CustomPacketPayload {
    public static final Type<OpenCreationPayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_personagem:open_creation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.replacement);
                buf.writeUtf(payload.title, 128);
                buf.writeUtf(payload.intro, 512);
                buf.writeUtf(payload.rules, 512);
                buf.writeUtf(payload.reserved, 64);
            },
            buf -> new OpenCreationPayload(buf.readBoolean(), buf.readUtf(128), buf.readUtf(512),
                    buf.readUtf(512), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
