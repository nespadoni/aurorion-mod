package com.aurorion.vidas.network;

import com.aurorion.vidas.AurorionVidas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * As vidas do proprio jogador, para o HUD desenhar.
 *
 * <p>Dois inteiros, e so para o dono da tela — ninguem precisa saber a vida dos outros para
 * renderizar a propria. O payload e O(1) e nao cresce com a populacao do servidor: nao ha snapshot
 * de login com a tabela inteira, como no {@code aurorion-talk} ou no {@code aurorion-essentials},
 * porque aqui nada de outro jogador aparece na tela (SDD §7.3).
 *
 * <p>{@code max} viaja junto em vez de ser lido da config no cliente: config de servidor nao existe
 * no cliente, e um cliente que adivinhasse o maximo desenharia a quantidade errada de icones.
 */
public record SyncLivesPayload(int lives, int max) implements CustomPacketPayload {
    public static final Type<SyncLivesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionVidas.MOD_ID, "sync_lives"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLivesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncLivesPayload::lives,
            ByteBufCodecs.VAR_INT, SyncLivesPayload::max,
            SyncLivesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
