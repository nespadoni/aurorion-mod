package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * O estado inteiro dos nos, de todas as dimensoes — no maximo tres entradas. Vai no login e quando a
 * staff liga ou desliga um; o cliente filtra pela dimensao em que esta e desenha o resto sozinho. Nenhum
 * vertice, particula ou som trafega.
 */
public record NodePayload(List<Entry> nodes) implements CustomPacketPayload {
    public static final Type<NodePayload> TYPE = new Type<>(AurorionMagia.id("nos"));

    /** @param age ticks desde a liberacao, para a abertura tocar so a quem estava la */
    public record Entry(int index, ResourceLocation dimension, double x, double y, double z, int age) {
    }

    public static final StreamCodec<FriendlyByteBuf, NodePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.nodes.size());
                for (Entry entry : payload.nodes) {
                    buf.writeVarInt(entry.index);
                    buf.writeResourceLocation(entry.dimension);
                    buf.writeDouble(entry.x);
                    buf.writeDouble(entry.y);
                    buf.writeDouble(entry.z);
                    buf.writeVarInt(entry.age);
                }
            },
            buf -> {
                int size = Math.min(buf.readVarInt(), 16);
                Entry[] entries = new Entry[size];
                for (int i = 0; i < size; i++) {
                    entries[i] = new Entry(buf.readVarInt(), buf.readResourceLocation(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
                }
                return new NodePayload(List.of(entries));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
