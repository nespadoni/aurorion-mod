package com.aurorion.servicos.network;

import com.aurorion.servicos.AurorionServicos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma aba do app, pronta para desenhar. O servidor decide tudo — inclusive quais botoes cada linha
 * tem ({@link Row#flags()}) — e o cliente so mostra.
 *
 * @param working   se quem pediu esta com o "Trabalhando" ligado
 * @param canWork   se tem anuncio (sem anuncio nao ha o que trabalhar)
 * @param badge     pedidos esperando uma resposta dele, para o numero no icone do app
 * @param ok        se a ultima acao deu certo (o formulario so fecha quando da)
 * @param message   resultado da ultima acao, vazio quando nao houve
 * @param chatNick  nick de quem o celular deve abrir a conversa agora, vazio para nenhum
 * @param chatDraft texto ja escrito nessa conversa
 */
public record ServicosPagePayload(String tab, String filter, boolean working, boolean canWork, int badge,
                                  boolean ok, String message, String chatNick, String chatDraft, List<Row> rows)
        implements CustomPacketPayload {
    public static final int MAX_ROWS = 40;

    public static final int KIND_AD = 0;
    public static final int KIND_ORDER = 1;
    public static final int KIND_JOB = 2;
    public static final int KIND_CANDIDATE = 3;

    public static final int WORKING = 1;
    public static final int ONLINE = 1 << 1;
    public static final int REGISTERED = 1 << 2;
    public static final int MINE = 1 << 3;
    public static final int CAN_ORDER = 1 << 4;
    public static final int CAN_ACCEPT = 1 << 5;
    public static final int CAN_REFUSE = 1 << 6;
    public static final int CAN_COMPLETE = 1 << 7;
    public static final int CAN_CANCEL = 1 << 8;
    public static final int CAN_APPLY = 1 << 9;
    public static final int APPLIED = 1 << 10;
    public static final int CAN_REMOVE = 1 << 11;
    public static final int CAN_CHAT = 1 << 12;
    public static final int CAN_EDIT = 1 << 13;
    /** Pedido direto (feito a partir de um anuncio), e nao aberto para a area. */
    public static final int DIRECT = 1 << 14;
    /** Pedido que chegou para mim (sou o profissional), e nao que eu fiz. */
    public static final int INCOMING = 1 << 15;

    public static final Type<ServicosPagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionServicos.MOD_ID, "page"));

    public ServicosPagePayload {
        rows = rows.size() <= MAX_ROWS ? List.copyOf(rows) : List.copyOf(rows.subList(0, MAX_ROWS));
    }

    /**
     * Uma linha: anuncio, pedido, vaga ou candidato.
     *
     * @param title    titulo do anuncio/vaga, ou a area do pedido
     * @param subtitle area do anuncio/vaga, ou o estado do pedido
     * @param detail   descricao do anuncio/vaga, ou o texto do pedido
     * @param price    preco ou salario
     * @param person   nome do personagem do outro lado (dono, cliente, profissional, candidato)
     * @param nick     nick dessa pessoa, so para abrir a conversa no celular
     * @param parent   a vaga de um candidato
     * @param count    candidatos de uma vaga
     */
    public record Row(long id, int kind, int flags, String category, String title, String subtitle, String detail,
                      String price, String person, String nick, long time, long parent, int count) {
        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ServicosPagePayload> STREAM_CODEC = StreamCodec.of(
            ServicosPagePayload::write, ServicosPagePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ServicosPagePayload data) {
        buf.writeUtf(data.tab, 16);
        buf.writeUtf(data.filter, 32);
        buf.writeBoolean(data.working);
        buf.writeBoolean(data.canWork);
        buf.writeVarInt(data.badge);
        buf.writeBoolean(data.ok);
        buf.writeUtf(data.message, 256);
        buf.writeUtf(data.chatNick, 16);
        buf.writeUtf(data.chatDraft, 256);
        buf.writeVarInt(data.rows.size());
        for (Row row : data.rows) {
            buf.writeVarLong(row.id);
            buf.writeVarInt(row.kind);
            buf.writeVarInt(row.flags);
            buf.writeUtf(row.category, 32);
            buf.writeUtf(row.title, 128);
            buf.writeUtf(row.subtitle, 64);
            buf.writeUtf(row.detail, 512);
            buf.writeUtf(row.price, 64);
            buf.writeUtf(row.person, 96);
            buf.writeUtf(row.nick, 16);
            buf.writeLong(row.time);
            buf.writeVarLong(row.parent);
            buf.writeVarInt(row.count);
        }
    }

    private static ServicosPagePayload read(RegistryFriendlyByteBuf buf) {
        String tab = buf.readUtf(16);
        String filter = buf.readUtf(32);
        boolean working = buf.readBoolean();
        boolean canWork = buf.readBoolean();
        int badge = buf.readVarInt();
        boolean ok = buf.readBoolean();
        String message = buf.readUtf(256);
        String chatNick = buf.readUtf(16);
        String chatDraft = buf.readUtf(256);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ROWS) throw new IllegalArgumentException("Linhas demais: " + size);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Row(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readUtf(128),
                    buf.readUtf(64), buf.readUtf(512), buf.readUtf(64), buf.readUtf(96), buf.readUtf(16),
                    buf.readLong(), buf.readVarLong(), buf.readVarInt()));
        }
        return new ServicosPagePayload(tab, filter, working, canWork, badge, ok, message, chatNick, chatDraft, rows);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
