package com.aurorion.limbo.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * A lista do Oraculo, ja pronta para desenhar.
 *
 * <h2>Por que o servidor manda texto mastigado</h2>
 *
 * <p>O cliente nao tem como saber quem esta no Limbo — esse dado so existe no servidor. Entao ele
 * recebe a lista fechada e nao calcula nada: nem prazo, nem ordem, nem se pode pagar. E a mesma
 * disciplina do Projetor Aeonico (SDD §10.2): snapshot pronto, cliente burro.
 *
 * <h2>Tamanho</h2>
 *
 * <p>E O(exilados), nao O(jogadores), e so viaja quando alguem <b>clica</b> no Oraculo. Num servidor
 * com 80 pessoas e cinco exilados, sao cinco linhas de vez em quando — nao e caminho quente.
 * Ainda assim tem teto: {@link #MAX_ENTRIES}, porque payload sem limite e vetor de abuso mesmo
 * partindo do servidor (uma lista gigante trava o cliente de quem clicou).
 */
public record OpenOraclePayload(List<Entry> exiles, int viewerLives, int cost, int minLives)
        implements CustomPacketPayload {

    /** Teto de linhas. Acima disso o Oraculo mostra as mais urgentes e nada mais. */
    public static final int MAX_ENTRIES = 64;

    public record Entry(UUID id, String name, long remainingMillis, boolean attempted, boolean online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.stringUtf8(16), Entry::name,
                ByteBufCodecs.VAR_LONG, Entry::remainingMillis,
                ByteBufCodecs.BOOL, Entry::attempted,
                ByteBufCodecs.BOOL, Entry::online,
                Entry::new);
    }

    public static final Type<OpenOraclePayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_limbo:oraculo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOraclePayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), OpenOraclePayload::exiles,
                    ByteBufCodecs.VAR_INT, OpenOraclePayload::viewerLives,
                    ByteBufCodecs.VAR_INT, OpenOraclePayload::cost,
                    ByteBufCodecs.VAR_INT, OpenOraclePayload::minLives,
                    OpenOraclePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
