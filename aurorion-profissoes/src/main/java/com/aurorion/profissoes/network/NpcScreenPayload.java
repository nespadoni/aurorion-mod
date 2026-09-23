package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * A tela de um NPC de oficio, ja decidida pelo servidor: o cliente nao sabe preco, estoque, saldo
 * nem se ha um profissional por perto — recebe cada linha com o motivo pronto e so desenha.
 */
public record NpcScreenPayload(UUID token, String title, String eyebrow, String greeting,
                               String notice, boolean noticeError, int tab, boolean admDialogue,
                               List<ServiceRow> services, List<TradeRow> trades, List<String> dialogue)
        implements CustomPacketPayload {
    public static final int TAB_HOME = 0, TAB_SHOP = 1, TAB_SERVICES = 2, TAB_TALK = 3;
    public static final int MAX_ROWS = 32, MAX_LINES = 16;
    public static final Type<NpcScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "npc_screen"));

    public record ServiceRow(String title, String detail, String cost, boolean enabled, String reason) {}
    /** {@code stock} e o que resta; {@code -1} para infinito. */
    public record TradeRow(ItemStack result, int amount, ItemStack priceIcon, String price, int stock,
                           boolean enabled, String reason) {}

    public NpcScreenPayload {
        services = List.copyOf(services); trades = List.copyOf(trades); dialogue = List.copyOf(dialogue);
        if (services.size() > MAX_ROWS || trades.size() > MAX_ROWS || dialogue.size() > MAX_LINES)
            throw new IllegalArgumentException("Too many NPC rows");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcScreenPayload> STREAM_CODEC = StreamCodec.of((buf, data) -> {
        buf.writeUUID(data.token); buf.writeUtf(data.title, 128); buf.writeUtf(data.eyebrow, 128);
        buf.writeUtf(data.greeting, 512); buf.writeUtf(data.notice, 512); buf.writeBoolean(data.noticeError);
        buf.writeVarInt(data.tab); buf.writeBoolean(data.admDialogue);
        buf.writeVarInt(data.services.size());
        for (var row : data.services) {
            buf.writeUtf(row.title, 128); buf.writeUtf(row.detail, 512); buf.writeUtf(row.cost, 256);
            buf.writeBoolean(row.enabled); buf.writeUtf(row.reason, 512);
        }
        buf.writeVarInt(data.trades.size());
        for (var row : data.trades) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.result); buf.writeVarInt(row.amount);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.priceIcon); buf.writeUtf(row.price, 256);
            buf.writeVarInt(row.stock); buf.writeBoolean(row.enabled); buf.writeUtf(row.reason, 512);
        }
        buf.writeVarInt(data.dialogue.size());
        for (var line : data.dialogue) buf.writeUtf(line, 512);
    }, buf -> {
        UUID token = buf.readUUID(); String title = buf.readUtf(128), eyebrow = buf.readUtf(128);
        String greeting = buf.readUtf(512), notice = buf.readUtf(512); boolean noticeError = buf.readBoolean();
        int tab = buf.readVarInt(); boolean adm = buf.readBoolean();
        int services = count(buf.readVarInt(), MAX_ROWS);
        var serviceRows = new ArrayList<ServiceRow>(services);
        for (int i = 0; i < services; i++)
            serviceRows.add(new ServiceRow(buf.readUtf(128), buf.readUtf(512), buf.readUtf(256), buf.readBoolean(), buf.readUtf(512)));
        int trades = count(buf.readVarInt(), MAX_ROWS);
        var tradeRows = new ArrayList<TradeRow>(trades);
        for (int i = 0; i < trades; i++)
            tradeRows.add(new TradeRow(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(256), buf.readVarInt(), buf.readBoolean(), buf.readUtf(512)));
        int lines = count(buf.readVarInt(), MAX_LINES);
        var dialogue = new ArrayList<String>(lines);
        for (int i = 0; i < lines; i++) dialogue.add(buf.readUtf(512));
        return new NpcScreenPayload(token, title, eyebrow, greeting, notice, noticeError, tab, adm, serviceRows, tradeRows, dialogue);
    });

    private static int count(int size, int max) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid NPC row count");
        return size;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
