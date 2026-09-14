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
 * @param replacement true quando a conta esta comecando outra historia depois de uma morte
 *                    definitiva; muda so o texto de abertura, nunca a regra.
 */
public record OpenCreationPayload(boolean replacement, String title, String intro, String rules)
        implements CustomPacketPayload {
    public static final Type<OpenCreationPayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_personagem:open_creation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.replacement);
                buf.writeUtf(payload.title, 128);
                buf.writeUtf(payload.intro, 512);
                buf.writeUtf(payload.rules, 512);
            },
            buf -> new OpenCreationPayload(buf.readBoolean(), buf.readUtf(128), buf.readUtf(512), buf.readUtf(512)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
