package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record PanelPayload(UUID token, int mode, String title, String subtitle, List<Row> rows) implements CustomPacketPayload {
    public static final Type<PanelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "panel"));
    public record Row(String action, String title, String detail, boolean enabled) {}
    public PanelPayload { rows = List.copyOf(rows); if (rows.size() > 12) throw new IllegalArgumentException("Too many services"); }
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelPayload> STREAM_CODEC = StreamCodec.of((buf, data) -> {
        buf.writeUUID(data.token); buf.writeVarInt(data.mode); buf.writeUtf(data.title, 128); buf.writeUtf(data.subtitle, 512);
        buf.writeVarInt(data.rows.size());
        for (var row : data.rows) {
            buf.writeUtf(row.action, 64); buf.writeUtf(row.title, 128); buf.writeUtf(row.detail, 512); buf.writeBoolean(row.enabled);
        }
    }, buf -> {
        UUID token = buf.readUUID(); int mode = buf.readVarInt(); String title = buf.readUtf(128), subtitle = buf.readUtf(512);
        int size = buf.readVarInt();
        if (size < 0 || size > 12) throw new IllegalArgumentException("Invalid service count");
        var rows = new ArrayList<Row>(size);
        for (int i = 0; i < size; i++) rows.add(new Row(buf.readUtf(64), buf.readUtf(128), buf.readUtf(512), buf.readBoolean()));
        return new PanelPayload(token, mode, title, subtitle, rows);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
