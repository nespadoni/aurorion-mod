package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Uma escolha na tela do NPC. So indice e token: preco, estoque e distancia sao reconferidos no servidor. */
public record NpcActionPayload(UUID token, String kind, int index) implements CustomPacketPayload {
    public static final String SERVICE = "service", TRADE = "trade", TALK = "talk", CLOSE = "close";
    public static final Type<NpcActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "npc_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> { buf.writeUUID(data.token); buf.writeUtf(data.kind, 16); buf.writeVarInt(data.index); },
            buf -> new NpcActionPayload(buf.readUUID(), buf.readUtf(16), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
