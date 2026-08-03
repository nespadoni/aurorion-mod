package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Broadcast do servidor: um unico jogador definiu ou limpou o nome falso.
 *
 * @param name {@link Optional#empty()} significa "voltou ao nome real".
 */
public record UpdateFakeNamePayload(UUID player, Optional<String> name) implements CustomPacketPayload {
    public static final Type<UpdateFakeNamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "update_fake_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFakeNamePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, UpdateFakeNamePayload::player,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), UpdateFakeNamePayload::name,
            UpdateFakeNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
