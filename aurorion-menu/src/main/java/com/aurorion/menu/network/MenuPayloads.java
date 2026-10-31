package com.aurorion.menu.network;

import com.aurorion.menu.AurorionMenu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Os dois pacotes do menu: o que a pessoa pede, e o que os outros veem dela. */
public final class MenuPayloads {
    /** Bits de presença: menu aberto. */
    public static final int MENU = 1;
    /** Bits de presença: meditando. */
    public static final int MEDITATING = 2;

    private MenuPayloads() {
    }

    /**
     * Do cliente: {@code kind} 0 = menu aberto/fechado ({@code value} 1/0); 1 = meditar ({@code value} 1
     * começa, 0 para).
     */
    public record Request(int kind, int value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(AurorionMenu.id("pedido"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeVarInt(p.value);
                }, buf -> new Request(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Para quem rastreia a pessoa (e ela mesma): os bits {@link #MENU}/{@link #MEDITATING} dela agora. */
    public record Presence(UUID player, int flags) implements CustomPacketPayload {
        public static final Type<Presence> TYPE = new Type<>(AurorionMenu.id("presenca"));
        public static final StreamCodec<FriendlyByteBuf, Presence> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, p.player);
                    buf.writeVarInt(p.flags);
                }, buf -> new Presence(UUIDUtil.STREAM_CODEC.decode(buf), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
