package com.aurorion.intencao.network;

import com.aurorion.intencao.AurorionIntencao;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Os pacotes do duelo com ficha. Vão só para os duelistas, a cada mudança de turno ou de vida — nunca por
 * tick. Listas com teto na leitura: um pacote fora do formato acaba aqui em vez de desalinhar o buffer.
 */
public final class DuelPayloads {
    static final int MAX_FIGHTERS = 16;
    static final int MAX_CHOICES = 24;

    private DuelPayloads() {
    }

    /** {@code flags}: 1 defendendo, 2 derrotado, 4 fora (saiu/desistiu), 8 exausto. */
    public record Fighter(String name, int side, int color, int hp, int hpMax, int pe, int peMax, int stopHp, int flags) {
    }

    /**
     * O estado do duelo, visto por quem recebe.
     *
     * @param phase   0 contagem, 1 escolhendo, 2 reação, 3 entre turnos, 4 fim
     * @param seconds segundos que faltam (contagem ou escolha)
     */
    public record State(List<Fighter> fighters, int self, int actor, int round, int limit, int phase, int seconds,
                        String log, boolean paused) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(AurorionIntencao.id("duelo_estado"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.fighters.size());
            for (Fighter f : p.fighters) {
                buf.writeUtf(f.name(), 64);
                buf.writeVarInt(f.side());
                buf.writeInt(f.color());
                buf.writeVarInt(f.hp());
                buf.writeVarInt(f.hpMax());
                buf.writeVarInt(f.pe());
                buf.writeVarInt(f.peMax());
                buf.writeVarInt(f.stopHp());
                buf.writeVarInt(f.flags());
            }
            buf.writeVarInt(p.self);
            buf.writeVarInt(p.actor);
            buf.writeVarInt(p.round);
            buf.writeVarInt(p.limit + 1);
            buf.writeVarInt(p.phase);
            buf.writeVarInt(p.seconds);
            buf.writeUtf(p.log, 512);
            buf.writeBoolean(p.paused);
        }, buf -> {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_FIGHTERS) throw new DecoderException("Duelo com " + count + " lutadores");
            List<Fighter> fighters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                fighters.add(new Fighter(buf.readUtf(64), buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new State(fighters, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1,
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(512), buf.readBoolean());
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma ação do menu. {@code kind}: 0 corpo a corpo, 1 à distância, 2 defender, 3 técnica, 4 magia, 5 desistir. */
    public record Choice(int kind, int index, String label, int cost, boolean enabled) {
    }

    public record Target(int index, String name) {
    }

    /** É a sua vez: o que dá para fazer e em quem. */
    public record Prompt(int seconds, List<Choice> choices, List<Target> targets) implements CustomPacketPayload {
        public static final Type<Prompt> TYPE = new Type<>(AurorionIntencao.id("duelo_vez"));
        public static final StreamCodec<FriendlyByteBuf, Prompt> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.seconds);
            buf.writeVarInt(p.choices.size());
            for (Choice c : p.choices) {
                buf.writeVarInt(c.kind());
                buf.writeVarInt(c.index());
                buf.writeUtf(c.label(), 128);
                buf.writeVarInt(c.cost());
                buf.writeBoolean(c.enabled());
            }
            buf.writeVarInt(p.targets.size());
            for (Target t : p.targets) {
                buf.writeVarInt(t.index());
                buf.writeUtf(t.name(), 64);
            }
        }, buf -> {
            int seconds = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CHOICES) throw new DecoderException("Duelo com " + count + " acoes");
            List<Choice> choices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                choices.add(new Choice(buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean()));
            }
            int targets = buf.readVarInt();
            if (targets < 0 || targets > MAX_FIGHTERS) throw new DecoderException("Duelo com " + targets + " alvos");
            List<Target> list = new ArrayList<>(targets);
            for (int i = 0; i < targets; i++) list.add(new Target(buf.readVarInt(), buf.readUtf(64)));
            return new Prompt(seconds, choices, list);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Um golpe vem aí: a janela da reação cronometrada. O golpe acerta {@code ticks} depois de o pacote
     * sair; clique esquerdo apara, direito esquiva.
     */
    public record React(String attacker, String label, int ticks, int windowMs, int perfectMs) implements CustomPacketPayload {
        public static final Type<React> TYPE = new Type<>(AurorionIntencao.id("duelo_reacao"));
        public static final StreamCodec<FriendlyByteBuf, React> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.attacker, 64);
            buf.writeUtf(p.label, 128);
            buf.writeVarInt(p.ticks);
            buf.writeVarInt(p.windowMs);
            buf.writeVarInt(p.perfectMs);
        }, buf -> new React(buf.readUtf(64), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Alguém desafiou você: a janela de convite (Aceitar / Recusar), além da linha no chat. */
    public record Invite(String challenger, String limit, boolean death, int seconds) implements CustomPacketPayload {
        public static final Type<Invite> TYPE = new Type<>(AurorionIntencao.id("duelo_convite"));
        public static final StreamCodec<FriendlyByteBuf, Invite> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.challenger, 64);
            buf.writeUtf(p.limit, 64);
            buf.writeBoolean(p.death);
            buf.writeVarInt(p.seconds);
        }, buf -> new Invite(buf.readUtf(64), buf.readUtf(64), buf.readBoolean(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Do cliente: a ação escolhida ({@code kind} 0: {@code a} tipo, {@code b} índice, {@code c} alvo) ou a
     * reação ({@code kind} 1: {@code a} 0 aparo / 1 esquiva, {@code b} desvio em ms do instante do golpe).
     */
    public record Input(int kind, int a, int b, int c) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(AurorionIntencao.id("duelo_entrada"));
        public static final StreamCodec<FriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.kind);
            buf.writeVarInt(p.a);
            buf.writeInt(p.b);
            buf.writeVarInt(p.c);
        }, buf -> new Input(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A arena de um duelo para quem está perto (duelistas e plateia): o cliente desenha o campo, as faíscas e
     * o resto sozinho. Um pacote ao abrir (e a quem chega perto depois), um ao fechar; nada por tick.
     * {@code open = false}: o duelo acabou; {@code headline} é o resultado para a plateia.
     */
    public record Arena(long idHigh, long idLow, double x, double y, double z, float radius, boolean death, boolean open,
                        String headline) implements CustomPacketPayload {
        public static final Type<Arena> TYPE = new Type<>(AurorionIntencao.id("duelo_arena"));
        public static final StreamCodec<FriendlyByteBuf, Arena> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeLong(p.idHigh);
            buf.writeLong(p.idLow);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.radius);
            buf.writeBoolean(p.death);
            buf.writeBoolean(p.open);
            buf.writeUtf(p.headline, 160);
        }, buf -> new Arena(buf.readLong(), buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean(), buf.readUtf(160)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * O resultado para cada duelista, no cartão do meio da tela. {@code outcome}: 0 empate, 1 vitória,
     * 2 derrota. {@code rows}: rótulo e valor (dano causado, recebido, golpes, defesas, críticos, XP).
     */
    public record Result(int outcome, String headline, List<String[]> rows) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(AurorionIntencao.id("duelo_resultado"));
        public static final StreamCodec<FriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.outcome);
            buf.writeUtf(p.headline, 160);
            buf.writeVarInt(p.rows.size());
            for (String[] row : p.rows) {
                buf.writeUtf(row[0], 64);
                buf.writeUtf(row[1], 64);
            }
        }, buf -> {
            int outcome = buf.readVarInt();
            String headline = buf.readUtf(160);
            int count = buf.readVarInt();
            if (count < 0 || count > 12) throw new DecoderException("Resultado com " + count + " linhas");
            List<String[]> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) rows.add(new String[]{buf.readUtf(64), buf.readUtf(64)});
            return new Result(outcome, headline, rows);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Um raio de efeito (abertura, crítico, derrota) desenhado no cliente: sem entidade, sem trovão para o mundo. */
    public record Bolt(double x, double y, double z, boolean death) implements CustomPacketPayload {
        public static final Type<Bolt> TYPE = new Type<>(AurorionIntencao.id("duelo_raio"));
        public static final StreamCodec<FriendlyByteBuf, Bolt> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeBoolean(p.death);
        }, buf -> new Bolt(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
