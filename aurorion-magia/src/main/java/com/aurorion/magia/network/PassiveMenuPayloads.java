package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * A roda de passivas (segurar a tecla de passiva). Tres pacotes pequenos, so quando a pessoa abre e
 * escolhe: nada de sincronizar passivas no login nem por tick. O cliente pergunta, o servidor responde
 * com o que <i>ela</i> tem agora, e a escolha volta como um id — quem decide se vale continua sendo o
 * servidor.
 */
public final class PassiveMenuPayloads {
    /** Teto folgado: hoje sao 8 passivas no codigo. Estourar um teto de codec derruba a conexao. */
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_KEY = 64;

    private PassiveMenuPayloads() {
    }

    /** "Segurei a tecla: quais passivas eu tenho?" Sem campo nenhum. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(AurorionMagia.id("passive_menu_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Uma fatia da roda.
     *
     * @param key        o id curto da passiva ({@code Passive#key()})
     * @param toggleable de ligar e desligar (a roda mostra o estado); senao, de usar
     * @param active     ligada, ou em uso agora (fora do corpo, dentro do bolso)
     */
    public record Entry(String key, boolean toggleable, boolean active) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_KEY), Entry::key,
                ByteBufCodecs.BOOL, Entry::toggleable,
                ByteBufCodecs.BOOL, Entry::active,
                Entry::new);
    }

    /** A resposta: as passivas da roda, na ordem do codigo. */
    public record Menu(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(AurorionMagia.id("passive_menu"));
        public static final StreamCodec<ByteBuf, Menu> STREAM_CODEC = Entry.STREAM_CODEC
                .apply(ByteBufCodecs.list(MAX_ENTRIES)).map(Menu::new, Menu::entries);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Escolhi esta." Shift ou clique direito: a acao alternativa. */
    public record Select(String key, boolean alternate) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(AurorionMagia.id("passive_select"));
        public static final StreamCodec<ByteBuf, Select> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_KEY), Select::key,
                ByteBufCodecs.BOOL, Select::alternate,
                Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
