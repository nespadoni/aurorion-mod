package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ActionPayload(UUID token, String action) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> { buf.writeUUID(data.token); buf.writeUtf(data.action, 64); }, buf -> new ActionPayload(buf.readUUID(), buf.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
