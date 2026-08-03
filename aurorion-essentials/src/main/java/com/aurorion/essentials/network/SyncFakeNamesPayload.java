package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot completo dos nomes falsos, enviado uma unica vez quando o jogador entra. So inclui
 * quem esta online no momento — O(jogadores online), nao O(historico de quem ja teve nome falso).
 */
public record SyncFakeNamesPayload(Map<UUID, String> names) implements CustomPacketPayload {
    public static final Type<SyncFakeNamesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "sync_fake_names"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFakeNamesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
            SyncFakeNamesPayload::names,
            SyncFakeNamesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
