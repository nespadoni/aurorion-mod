package com.aurorion.economia.network;

import com.aurorion.economia.AurorionEconomia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Pacotes pequenos, enviados somente ao abrir ou agir na tela de cobranca. */
public final class EconomyPayloads {
    private EconomyPayloads() { }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(AurorionEconomia.MOD_ID, path));
    }

    public record OpenRequest(UUID target) implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = type("charge_open_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequest> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.target),
                buf -> new OpenRequest(buf.readUUID()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenMenu(UUID target, String targetName, List<Option> options) implements CustomPacketPayload {
        public record Option(String action, String title, String detail, boolean enabled) { }

        public OpenMenu {
            options = List.copyOf(options);
            if (options.size() > 8) throw new IllegalArgumentException("Too many interaction options");
        }

        public static final Type<OpenMenu> TYPE = type("interaction_open_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenu> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.target);
                    buf.writeUtf(value.targetName, 80);
                    buf.writeVarInt(value.options.size());
                    for (Option option : value.options) {
                        buf.writeUtf(option.action, 48);
                        buf.writeUtf(option.title, 96);
                        buf.writeUtf(option.detail, 256);
                        buf.writeBoolean(option.enabled);
                    }
                },
                buf -> {
                    UUID target = buf.readUUID();
                    String targetName = buf.readUtf(80);
                    int size = buf.readVarInt();
                    if (size < 0 || size > 8) throw new IllegalArgumentException("Invalid interaction option count");
                    List<Option> options = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) options.add(new Option(
                            buf.readUtf(48), buf.readUtf(96), buf.readUtf(256), buf.readBoolean()));
                    return new OpenMenu(target, targetName, options);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MenuAction(UUID target, String action) implements CustomPacketPayload {
        public static final Type<MenuAction> TYPE = type("interaction_menu_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, MenuAction> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.target); buf.writeUtf(value.action, 48); },
                buf -> new MenuAction(buf.readUUID(), buf.readUtf(48)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenComposer(UUID target, String targetName) implements CustomPacketPayload {
        public static final Type<OpenComposer> TYPE = type("charge_open_composer");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenComposer> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.target); buf.writeUtf(value.targetName, 80); },
                buf -> new OpenComposer(buf.readUUID(), buf.readUtf(80)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Submit(UUID target, String amount) implements CustomPacketPayload {
        public static final Type<Submit> TYPE = type("charge_submit");
        public static final StreamCodec<RegistryFriendlyByteBuf, Submit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.target); buf.writeUtf(value.amount, 32); },
                buf -> new Submit(buf.readUUID(), buf.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenApproval(UUID token, String chargerName, long amount, long balance)
            implements CustomPacketPayload {
        public static final Type<OpenApproval> TYPE = type("charge_open_approval");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenApproval> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.token);
                    buf.writeUtf(value.chargerName, 80);
                    buf.writeVarLong(value.amount);
                    buf.writeVarLong(value.balance);
                },
                buf -> new OpenApproval(buf.readUUID(), buf.readUtf(80), buf.readVarLong(), buf.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Respond(UUID token, boolean accepted) implements CustomPacketPayload {
        public static final Type<Respond> TYPE = type("charge_respond");
        public static final StreamCodec<RegistryFriendlyByteBuf, Respond> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.token); buf.writeBoolean(value.accepted); },
                buf -> new Respond(buf.readUUID(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Status(String title, String message, boolean success) implements CustomPacketPayload {
        public static final Type<Status> TYPE = type("charge_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.title, 96);
                    buf.writeUtf(value.message, 512);
                    buf.writeBoolean(value.success);
                },
                buf -> new Status(buf.readUtf(96), buf.readUtf(512), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
