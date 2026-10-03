package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * Uma escolha na tela do NPC. So indice, token e o texto digitado (nome da etiqueta): preco, estoque
 * e distancia sao reconferidos no servidor, e o texto e filtrado la de novo.
 */
public record NpcActionPayload(UUID token, String kind, int index, String text) implements CustomPacketPayload {
    public static final String SERVICE = "service", TRADE = "trade", TALK = "talk", CLOSE = "close";
    public static final int MAX_TEXT = 64;
    public static final Type<NpcActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "npc_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> { buf.writeUUID(data.token); buf.writeUtf(data.kind, 16); buf.writeVarInt(data.index); buf.writeUtf(data.text, MAX_TEXT); },
            buf -> new NpcActionPayload(buf.readUUID(), buf.readUtf(16), buf.readVarInt(), buf.readUtf(MAX_TEXT)));

    public NpcActionPayload(UUID token, String kind, int index) { this(token, kind, index, ""); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
