package com.aurorion.intencao.network;

import com.aurorion.intencao.AurorionIntencao;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O que a tela de um participante deve mostrar. Vai so para ele, poucas vezes por batalha (chamado,
 * chegada, troca de clima, fragmento tocado, veredito, volta) — nunca por tick.
 *
 * <p>Um formato para todos os tipos, com os campos que nao se aplicam vazios: o pacote mais gordo
 * (PLANE) fica abaixo de 1 KB, e um registro so evita seis classes quase iguais.
 *
 * @param members participantes, na ordem da sessao (o indice e o lado da arena)
 * @param self    indice de quem recebe em {@code members}, ou -1
 * @param value   {@link Tone} em PLANE/TONE, {@link Outcome} em VERDICT
 * @param elapsed segundos desde o inicio da batalha, para o relogio do HUD
 * @param text    chave de traducao (ou texto) do chamado, da frase ou do veredito
 */
public record ScenePayload(Kind kind, List<Member> members, int self, BlockPos center, int fog,
                           String ambience, String whisper, int value, int elapsed, String text)
        implements CustomPacketPayload {
    public static final Type<ScenePayload> TYPE = new Type<>(AurorionIntencao.id("cena"));
    /** Folgado em relacao ao teto do comando (8): o formato nao precisa mudar se o teto subir um pouco. */
    private static final int MAX_MEMBERS = 16;
    public static final StreamCodec<FriendlyByteBuf, ScenePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ScenePayload::read);

    /** Novos tipos sempre no fim: o ordinal vai na rede. */
    public enum Kind {
        /** O chamado: escurece, nomes frente a frente, palpebras fechando. O teleporte vem depois. */
        CALL,
        /** Chegou (ou voltou, depois de relogar) ao plano: neblina, som, cores dos lados, HUD. */
        PLANE,
        /** O mestre mudou o tom da cena. */
        TONE,
        /** Um fragmento de memoria falou. */
        PHRASE,
        /** O veredito, visto por quem recebe: vitoria, derrota ou empate. */
        VERDICT,
        /** Saiu do plano: apaga a cena. */
        LEAVE
    }

    public enum Outcome { WIN, LOSE, DRAW }

    public record Member(UUID id, String name, int color) {
    }

    public static ScenePayload simple(Kind kind, int value, String text) {
        return new ScenePayload(kind, List.of(), -1, BlockPos.ZERO, 0, "", "", value, 0, text);
    }

    private void write(FriendlyByteBuf buf) {
        if (members.size() > MAX_MEMBERS) throw new io.netty.handler.codec.EncoderException("Cena com " + members.size() + " participantes");
        buf.writeEnum(kind);
        buf.writeVarInt(members.size());
        for (Member member : members) {
            UUIDUtil.STREAM_CODEC.encode(buf, member.id());
            buf.writeUtf(member.name(), 64);
            buf.writeInt(member.color());
        }
        buf.writeVarInt(self);
        buf.writeBlockPos(center);
        buf.writeInt(fog);
        buf.writeUtf(ambience, 256);
        buf.writeUtf(whisper, 256);
        buf.writeVarInt(value);
        buf.writeVarInt(elapsed);
        buf.writeUtf(text, 1024);
    }

    private static ScenePayload read(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int count = buf.readVarInt();
        // Cortar em silencio desalinharia o resto do buffer; um pacote assim e invalido e acaba aqui.
        if (count < 0 || count > MAX_MEMBERS) throw new io.netty.handler.codec.DecoderException("Cena com " + count + " participantes");
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Member(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(64), buf.readInt()));
        }
        return new ScenePayload(kind, members, buf.readVarInt(), buf.readBlockPos(), buf.readInt(),
                buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readUtf(1024));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
