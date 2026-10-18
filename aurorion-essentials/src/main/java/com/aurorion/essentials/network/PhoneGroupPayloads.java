package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGroupBook;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pacotes dos grupos do app Mensagens. Todos pequenos e so para quem esta no grupo: nada e enviado a
 * todos os jogadores.
 *
 * <p>Os tetos dos codecs sao folgados em relacao ao {@link PhoneGroupBook} de proposito: estourar um
 * teto de codec derruba a conexao, entao a regra de verdade (e a mensagem de erro) fica no servidor.
 */
public final class PhoneGroupPayloads {
    /** Nick da Mojang tem ate 16; perfis de alt podem fugir disso, e um teto estourado derruba a conexao. */
    private static final int NICK = 64;
    private static final int NAME = 256;
    private static final int TEXT = 1024;

    private PhoneGroupPayloads() {
    }

    // ---- cliente -> servidor -------------------------------------------------------------------

    public static final byte CREATE = 0;
    public static final byte ADD = 1;
    public static final byte REMOVE = 2;
    public static final byte LEAVE = 3;

    /**
     * Criar, adicionar, remover ou sair.
     *
     * @param group vazio ({@link #NONE}) ao criar
     * @param name nome do grupo ao criar; ignorado nas outras acoes
     * @param nicks convidados ao criar, quem entra ao adicionar, quem sai ao remover
     */
    public record Action(byte action, UUID group, String name, List<String> nicks) implements CustomPacketPayload {
        public static final Type<Action> TYPE = type("phone_group_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, Action::action,
                UUIDUtil.STREAM_CODEC, Action::group,
                ByteBufCodecs.stringUtf8(NAME), Action::name,
                ByteBufCodecs.stringUtf8(NICK).apply(ByteBufCodecs.list(PhoneGroupBook.MAX_MEMBERS)), Action::nicks,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma mensagem de texto para o grupo. */
    public record Send(UUID group, String text) implements CustomPacketPayload {
        public static final Type<Send> TYPE = type("phone_group_send");
        public static final StreamCodec<RegistryFriendlyByteBuf, Send> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Send::group,
                ByteBufCodecs.stringUtf8(TEXT), Send::text,
                Send::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- servidor -> cliente -------------------------------------------------------------------

    /** Um membro: o nick (o telefone roteia por nick) e o nome do personagem, que e o que aparece. */
    public record Member(String nick, String name) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NICK), Member::nick,
                ByteBufCodecs.stringUtf8(NAME), Member::name,
                Member::new);
    }

    /** Um grupo como o membro o ve. O primeiro membro e o dono. */
    public record View(UUID id, String name, List<Member> members) {
        static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, View::id,
                ByteBufCodecs.stringUtf8(NAME), View::name,
                Member.STREAM_CODEC.apply(ByteBufCodecs.list(PhoneGroupBook.MAX_MEMBERS)), View::members,
                View::new);
    }

    /**
     * Estado dos grupos do jogador.
     *
     * @param full {@code true} no login: a lista inteira, e o que nao esta nela o jogador nao tem mais
     * @param groups grupos novos ou mudados
     * @param gone grupos de que o jogador saiu, foi removido, ou que acabaram
     */
    public record Sync(boolean full, List<View> groups, List<UUID> gone) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = type("phone_group_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Sync::full,
                View.STREAM_CODEC.apply(ByteBufCodecs.list(PhoneGroupBook.MAX_GROUPS_PER_MEMBER * 4)), Sync::groups,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Sync::gone,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma linha de conversa ({@link PhoneGroupBook.Line} na rede). */
    public record Line(UUID group, byte kind, String nick, String name, String text, long at) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Line::group,
                ByteBufCodecs.BYTE, Line::kind,
                ByteBufCodecs.stringUtf8(NICK), Line::nick,
                ByteBufCodecs.stringUtf8(NAME), Line::name,
                ByteBufCodecs.stringUtf8(TEXT), Line::text,
                ByteBufCodecs.VAR_LONG, Line::at,
                Line::new);

        public static Line of(PhoneGroupBook.Line line) {
            return new Line(line.group(), line.kind(), line.nick(), line.name(), line.text(), line.at());
        }
    }

    /** Mensagens e avisos: uma ao vivo, ou a fila inteira de quem acabou de entrar. */
    public record Messages(List<Line> lines) implements CustomPacketPayload {
        /** Mais que a fila de uma conta inteira (50): o login cabe num pacote so. */
        public static final int MAX_LINES = 64;
        public static final Type<Messages> TYPE = type("phone_group_messages");
        public static final StreamCodec<RegistryFriendlyByteBuf, Messages> STREAM_CODEC = StreamCodec.composite(
                Line.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), Messages::lines,
                Messages::new);

        /** A fila em pacotes de ate {@link #MAX_LINES}. */
        public static List<Messages> split(List<PhoneGroupBook.Line> lines) {
            List<Messages> result = new ArrayList<>();
            for (int start = 0; start < lines.size(); start += MAX_LINES) {
                List<Line> page = new ArrayList<>();
                for (PhoneGroupBook.Line line : lines.subList(start, Math.min(lines.size(), start + MAX_LINES))) {
                    page.add(Line.of(line));
                }
                result.add(new Messages(page));
            }
            return result;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Resposta a uma acao: o {@link PhoneGroupBook.Result} (ordinal) que o cliente mostra no aviso. */
    public record Notice(byte result) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = type("phone_group_notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, Notice::result,
                Notice::new);

        public static Notice of(PhoneGroupBook.Result result) {
            return new Notice((byte) result.ordinal());
        }

        public PhoneGroupBook.Result value() {
            PhoneGroupBook.Result[] all = PhoneGroupBook.Result.values();
            return result >= 0 && result < all.length ? all[result] : PhoneGroupBook.Result.NOT_FOUND;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** O id que vai no lugar do grupo ao criar. */
    public static final UUID NONE = new UUID(0L, 0L);

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, path));
    }
}
