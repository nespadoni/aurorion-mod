package com.aurorion.diario.network;

import com.aurorion.diario.AurorionDiario;
import com.aurorion.diario.markup.DiaryMarkup;
import com.aurorion.diario.markup.DiaryText;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Os pacotes do diário. Todo texto que vem do cliente tem teto no próprio codec: um cliente
 * adulterado não consegue alocar além disso no servidor, e o servidor ainda revalida tudo.
 *
 * <p>Todo texto que <b>sai</b> do servidor passa por {@link DiaryText#clip} no próprio encoder: o
 * {@code writeUtf} lança exceção quando o texto passa do teto, e exceção no encoder desconecta o
 * jogador. O servidor já corta antes ({@code DiaryServer}); o corte aqui é a rede de segurança.
 */
public final class DiaryPayloads {
    /** Tetos do site, em caracteres Unicode. */
    public static final int TITLE = 200;
    public static final int LORE_DATE = 120;
    /**
     * Tetos no pacote, em unidades UTF-16 (a conta do {@code writeUtf}): o dobro, para que um título
     * de 200 caracteres do site cheio de emoji (dois cada) ainda caiba inteiro.
     */
    public static final int TITLE_WIRE = 2 * TITLE;
    public static final int LORE_DATE_WIRE = 2 * LORE_DATE;
    public static final int CHARACTER = 160;
    public static final int SITE_URL = 200;
    public static final int ORIGIN = 8;
    public static final int STATE = 16;
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
                    buf.writeUtf(DiaryText.clip(data.character, CHARACTER), CHARACTER);
                    int count = Math.min(data.entries.size(), MAX_ENTRIES);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        Summary s = data.entries.get(i);
                        buf.writeVarLong(s.id());
                        buf.writeUtf(DiaryText.clip(s.title(), TITLE_WIRE), TITLE_WIRE);
                        buf.writeVarInt(s.flags());
                    }
                    buf.writeUtf(DiaryText.clip(data.siteUrl, SITE_URL), SITE_URL);
                },
                buf -> {
                    String character = buf.readUtf(CHARACTER);
                    int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
                    List<Summary> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(new Summary(buf.readVarLong(), buf.readUtf(TITLE_WIRE), buf.readVarInt()));
                    return new Open(character, entries, buf.readUtf(SITE_URL));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Uma entrada aberta para edição, já convertida para a marcação do jogo. {@code draftKey} vem
     * preenchido quando é a cópia guardada no servidor ({@link #LOCAL}): a tela continua aquela
     * sessão de edição, e as próximas gravações substituem a cópia em vez de competir com ela.
     */
    public record Entry(long id, int version, String title, String loreDate, String markup, int flags, String draftKey) implements CustomPacketPayload {
        public static final Type<Entry> TYPE = new Type<>(channel("entry"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeVarLong(data.id);
                    buf.writeVarInt(data.version);
                    buf.writeUtf(DiaryText.clip(data.title, TITLE_WIRE), TITLE_WIRE);
                    buf.writeUtf(DiaryText.clip(data.loreDate, LORE_DATE_WIRE), LORE_DATE_WIRE);
                    // Texto que nao cabe vira o aviso, e o aviso so abre para leitura: salvar o aviso
                    // por cima apagaria o texto de verdade no site.
                    boolean fits = data.markup.length() <= MARKUP;
                    buf.writeUtf(fits ? data.markup : DiaryText.TOO_LONG, MARKUP);
                    buf.writeVarInt(fits ? data.flags : data.flags | READ_ONLY);
                    buf.writeUtf(DiaryText.clip(data.draftKey, KEY), KEY);
                },
                buf -> new Entry(buf.readVarLong(), buf.readVarInt(), buf.readUtf(TITLE_WIRE), buf.readUtf(LORE_DATE_WIRE),
                        buf.readUtf(MARKUP), buf.readVarInt(), buf.readUtf(KEY)));

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
                    buf.writeUtf(DiaryText.clip(data.draftKey, KEY), KEY);
                    buf.writeUtf(DiaryText.clip(data.state, STATE), STATE);
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.version);
                    buf.writeVarInt(data.flags);
                    buf.writeUtf(DiaryText.clip(data.message, MESSAGE), MESSAGE);
                },
                buf -> new Status(buf.readUtf(KEY), buf.readUtf(STATE), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MESSAGE)));

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
                    buf.writeUtf(DiaryText.clip(data.draftKey, KEY), KEY);
                    buf.writeVarLong(data.entryId);
                    buf.writeVarInt(data.version);
                    buf.writeUtf(DiaryText.clip(data.title, TITLE_WIRE), TITLE_WIRE);
                    buf.writeUtf(DiaryText.clip(data.loreDate, LORE_DATE_WIRE), LORE_DATE_WIRE);
                    buf.writeUtf(DiaryText.fitOrPlaceholder(data.markup, MARKUP), MARKUP);
                    buf.writeUtf(DiaryText.clip(data.origin, ORIGIN), ORIGIN);
                },
                buf -> new Conflict(buf.readUtf(KEY), buf.readVarLong(), buf.readVarInt(), buf.readUtf(TITLE_WIRE),
                        buf.readUtf(LORE_DATE_WIRE), buf.readUtf(MARKUP), buf.readUtf(ORIGIN)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── Cliente → servidor ──────────────────────────────────────────────────────

    /**
     * {@code action}: {@code abrir} uma entrada, {@code lista} para recarregar, ou {@code descartar} a
     * cópia guardada no servidor quando a pessoa fica com a versão do site.
     */
    public record Request(String action, long entryId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(channel("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeUtf(DiaryText.clip(data.action, 12), 12);
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
                    buf.writeUtf(DiaryText.clip(data.title, TITLE_WIRE), TITLE_WIRE);
                    buf.writeUtf(DiaryText.clip(data.loreDate, LORE_DATE_WIRE), LORE_DATE_WIRE);
                    buf.writeUtf(data.markup, MARKUP_EDIT);
                },
                buf -> new Save(buf.readUtf(KEY), buf.readUtf(KEY), buf.readVarLong(), buf.readVarInt(),
                        buf.readUtf(TITLE_WIRE), buf.readUtf(LORE_DATE_WIRE), buf.readUtf(MARKUP_EDIT)));

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
