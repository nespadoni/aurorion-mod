package com.aurorion.limbo.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * "Quero abrir a passagem para esta pessoa."
 *
 * <p>Um UUID, e nada mais. O cliente <b>nao</b> manda o custo, nem quantas vidas acha que tem, nem se
 * acha que pode — tudo isso e reconferido no servidor pelo {@code RescueManager}. E a diretriz 5 da
 * §7 no seu caso mais caro: aceitar a palavra do cliente aqui seria deixar qualquer um abrir
 * passagem de graca, ou pior, cobrar vida de quem nao clicou.
 *
 * <p>O servidor tambem nao confia que o Oraculo estava por perto: a validacao de distancia acontece
 * no handler, porque a tela pode ficar aberta enquanto o jogador anda para longe.
 */
public record BeginRescuePayload(UUID target) implements CustomPacketPayload {
    public static final Type<BeginRescuePayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_limbo:iniciar_resgate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeginRescuePayload> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, BeginRescuePayload::target, BeginRescuePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
