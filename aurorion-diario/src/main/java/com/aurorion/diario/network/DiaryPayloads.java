package com.aurorion.diario.network;

import com.aurorion.diario.AurorionDiario;
import com.aurorion.diario.markup.DiaryMarkup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Os pacotes do diário. Todo texto que vem do cliente tem teto no próprio codec: um cliente
 * adulterado não consegue alocar além disso no servidor, e o servidor ainda revalida tudo.
 */
public final class DiaryPayloads {
    public static final int TITLE = 200;
    public static final int LORE_DATE = 120;
    /** Texto que o servidor manda para leitura (pacote para o cliente aceita até 1 MiB). */
    public static final int MARKUP = DiaryMarkup.MAX_MARKUP_CHARS;
    /**
     * Texto que o cliente manda ao salvar. O pacote cliente → servidor do 1.21.1 tem teto de 32767
     * bytes; 10 mil caracteres cabem mesmo no pior caso de UTF-8 (3 bytes cada). Entrada maior que
     * isso abre no jogo só para leitura — nunca cortada, para não perder texto ao salvar.
     */
    public static final int MARKUP_EDIT = 10_000;
    public static final int KEY = 40;
    public static final int MESSAGE = 300;
    public static final int MAX_ENTRIES = 200;

    /** Bits de situação de uma entrada. */
    public static final int PUBLISHED = 1;
    public static final int CHANGES = 2;
    public static final int HIDDEN = 4;
    public static final int LOCAL = 8;
    /** Longa demais para editar no jogo: abre só para leitura. */
    public static final int READ_ONLY = 16;

    private DiaryPayloads() {
    }

    private static ResourceLocation channel(String path) {
        return ResourceLocation.fromNamespaceAndPath(AurorionDiario.MOD_ID, path);
    }

    // ── Servidor → cliente ──────────────────────────────────────────────────────

    public record Summary(long id, String title, int flags) {
    }

    /** Abre a tela com as entradas do personagem atual. */
    public record Open(String character, List<Summary> entries, String siteUrl) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(channel("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.character, 160);
                    int count = Math.min(data.entries.size(), MAX_ENTRIES);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        Summary s = data.entries.get(i);
                        buf.writeVarLong(s.id());
                        buf.writeUtf(s.title(), TITLE);
                        buf.writeVarInt(s.flags());
                    }
                    buf.writeUtf(data.siteUrl, 200);
                },
                buf -> {
                    String character = buf.readUtf(160);
                    int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
                    List<Summary> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(new Summary(buf.readVarLong(), buf.readUtf(TITLE), buf.readVarInt()));
                    return new Open(character, entries, buf.readUtf(200));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma entrada aberta para edição, já convertida para a marcação do jogo. */
    public record Entry(long id, int version, String title, String loreDate, String markup, int flags) implements CustomPacketPayload {
        public static final Type<Entry> TYPE = new Type<>(channel("entry"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeVarLong(data.id);
                    buf.writeVarInt(data.version);
                    buf.writeUtf(data.title, TITLE);
                    buf.writeUtf(data.loreDate, LORE_DATE);
                    buf.writeUtf(data.markup, MARKUP);
                    buf.writeVarInt(data.flags);
                },
                buf -> new Entry(buf.readVarLong(), buf.readVarInt(), buf.readUtf(TITLE), buf.readUtf(LORE_DATE), buf.readUtf(MARKUP), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Resultado de salvar, publicar ou retirar. {@code state}: {@code site} (salvo no site),
     * {@code servidor} (guardado no servidor do jogo, sincroniza sozinho), {@code publicado},
     * {@code retirado}, {@code erro}.
     */
    public record Status(String draftKey, String state, long entryId, int version, int flags, String message) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(channel("status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.draftKey, KEY);
                    buf.writeUtf(data.state, 16);
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.version);
                    buf.writeVarInt(data.flags);
                    buf.writeUtf(data.message, MESSAGE);
                },
                buf -> new Status(buf.readUtf(KEY), buf.readUtf(16), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MESSAGE)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** O rascunho foi salvo em outro lugar (site ou outra sessão): eis a versão de lá. */
    public record Conflict(String draftKey, long entryId, int version, String title, String loreDate, String markup, String origin) implements CustomPacketPayload {
        public static final Type<Conflict> TYPE = new Type<>(channel("conflict"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Conflict> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.draftKey, KEY);
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.version);
                    buf.writeUtf(data.title, TITLE);
                    buf.writeUtf(data.loreDate, LORE_DATE);
                    buf.writeUtf(data.markup, MARKUP);
                    buf.writeUtf(data.origin, 8);
                },
                buf -> new Conflict(buf.readUtf(KEY), buf.readVarLong(), buf.readVarInt(), buf.readUtf(TITLE), buf.readUtf(LORE_DATE), buf.readUtf(MARKUP), buf.readUtf(8)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── Cliente → servidor ──────────────────────────────────────────────────────

    /** {@code action}: {@code abrir} uma entrada, ou {@code lista} para recarregar. */
    public record Request(String action, long entryId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(channel("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.action, 12);
                    buf.writeVarLong(data.entryId);
                },
                buf -> new Request(buf.readUtf(12), buf.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Salvar o rascunho. {@code draftKey} identifica a sessão de edição (estável enquanto a tela
     * edita a mesma entrada); {@code operationId} identifica esta gravação, para reenvio seguro.
     */
    public record Save(String draftKey, String operationId, long entryId, int baseVersion,
                       String title, String loreDate, String markup) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(channel("save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(data.draftKey, KEY);
                    buf.writeUtf(data.operationId, KEY);
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.baseVersion);
                    buf.writeUtf(data.title, TITLE);
                    buf.writeUtf(data.loreDate, LORE_DATE);
                    buf.writeUtf(data.markup, MARKUP_EDIT);
                },
                buf -> new Save(buf.readUtf(KEY), buf.readUtf(KEY), buf.readVarLong(), buf.readVarInt(),
                        buf.readUtf(TITLE), buf.readUtf(LORE_DATE), buf.readUtf(MARKUP_EDIT)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Publicar a versão exata indicada, ou retirar a publicação. */
    public record Publish(long entryId, int version, boolean publish) implements CustomPacketPayload {
        public static final Type<Publish> TYPE = new Type<>(channel("publish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Publish> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.version);
                    buf.writeBoolean(data.publish);
                },
                buf -> new Publish(buf.readVarLong(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
