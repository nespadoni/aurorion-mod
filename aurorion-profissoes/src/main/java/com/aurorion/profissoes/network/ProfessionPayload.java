package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProfessionPayload(String profession) implements CustomPacketPayload {
    public static final Type<ProfessionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "profession"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfessionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeUtf(data.profession, 32), buf -> new ProfessionPayload(buf.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
