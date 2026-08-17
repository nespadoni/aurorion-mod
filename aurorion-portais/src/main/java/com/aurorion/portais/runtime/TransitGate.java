package com.aurorion.portais.runtime;

import com.aurorion.portais.AurorionPortais;
import com.aurorion.core.config.DerivedConfig;
import com.aurorion.portais.config.TransitConfig;
import com.aurorion.portais.pass.PassData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quem pode atravessar o que, e quando. Toda decisao do mod passa por aqui.
 *
 * <p>A regra e simetrica: <b>sair de uma dimensao controlada exige a mesma janela que entrar
 * nela</b>. Nao e um detalhe de implementacao, e a mecanica inteira — e o que faz quem perdeu a
 * hora do trem ficar do lado de dentro. Se so a entrada fosse controlada, voltar para o overworld
 * (que e livre) seria sempre permitido e a aventura nao teria risco nenhum.
 *
 * <p>A lista de dimensoes livres vem da config como texto e e convertida para {@link ResourceKey}
 * uma unica vez, em cache. O motivo e concreto: {@link #mayEnterPortal} roda a cada tick em que um
 * jogador esta parado dentro de um portal, e comparar {@code ResourceLocation.toString()} ali
 * alocaria uma {@link String} por jogador por tick (SDD §2).
 */
public final class TransitGate {
    /** Por que a viagem foi liberada ou barrada. */
    public enum Reason {
        /** Nenhuma das duas pontas e controlada. */
        FREE(true),
        /** {@code enforce = false} — o mod esta desligado por config. */
        DISABLED(true),
        /** Staff, criativo ou espectador. */
        BYPASS(true),
        /** A janela da linha esta aberta agora. */
        OPEN(true),
        /** Passe individual valido — o portador gasta um uso. */
        PASS(true),
        /** Tentou entrar numa dimensao controlada fora do horario. */
        LOCKED_ENTER(false),
        /** Tentou sair de uma dimensao controlada fora do horario. */
        LOCKED_EXIT(false);

        private final boolean allowed;

        Reason(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean allowed() {
            return allowed;
        }
    }

    /**
     * @param dimension a dimensao que motivou o veredito: a que barrou, ou aquela cujo passe sera
     *                  gasto. {@code null} quando a decisao nao envolveu dimensao controlada.
     */
    public record Verdict(Reason reason, @Nullable ResourceKey<Level> dimension) {
        public boolean allowed() {
            return reason.allowed();
        }
    }

    private static final Verdict FREE = new Verdict(Reason.FREE, null);
    private static final Verdict DISABLED = new Verdict(Reason.DISABLED, null);
    private static final Verdict BYPASS = new Verdict(Reason.BYPASS, null);
    private static final Verdict OPEN = new Verdict(Reason.OPEN, null);

    /**
     * Converte a lista de texto da config em chaves de dimensao, e refaz sozinho quando o arquivo
     * muda — sem listener de config para alguem esquecer de ligar.
     */
    private static final DerivedConfig<List<? extends String>, Set<ResourceKey<Level>>> FREE_DIMENSIONS =
            new DerivedConfig<>(TransitConfig.FREE_DIMENSIONS::get, TransitGate::parseDimensions);

    private TransitGate() {
    }

    // --- Decisao principal -------------------------------------------------------------------

    /**
     * Decide uma troca de dimensao. Nao tem efeito colateral: quem chama e que consome o passe se o
     * veredito for {@link Reason#PASS} e a viagem realmente acontecer.
     */
    public static Verdict check(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (from == to) return FREE;
        if (!TransitConfig.ENFORCE.get()) return DISABLED;
        if (bypasses(player)) return BYPASS;

        boolean controlledFrom = isControlled(from);
        boolean controlledTo = isControlled(to);
        if (!controlledFrom && !controlledTo) return FREE;

        long now = System.currentTimeMillis();
        ResourceKey<Level> passToSpend = null;

        if (controlledFrom && !isGateOpen(from)) {
            if (!hasPass(player, from, now)) {
                return new Verdict(Reason.LOCKED_EXIT, from);
            }
            passToSpend = from;
        }

        if (controlledTo && !isGateOpen(to)) {
            if (!hasPass(player, to, now)) {
                return new Verdict(Reason.LOCKED_ENTER, to);
            }
            if (passToSpend == null) {
                passToSpend = to;
            }
        }

        // No maximo um passe por viagem, mesmo quando as duas pontas sao controladas: atravessar e
        // uma acao so, e cobrar dois bilhetes por ela seria surpresa desagradavel para o jogador.
        return passToSpend == null ? OPEN : new Verdict(Reason.PASS, passToSpend);
    }

    /**
     * Vale a pena deixar este jogador comecar uma travessia de portal?
     *
     * <p>Chamado antes de o servidor procurar (e, no caso do Nether, <b>escavar</b>) o portal de
     * destino. So conhece a origem, entao responde a uma pergunta mais fraca que {@link #check}: nao
     * "essa viagem pode?", e sim "existe alguma viagem possivel agora?". Errar aqui nunca libera
     * nada — {@link #check} continua sendo a palavra final na hora da troca.
     */
    public static boolean mayEnterPortal(ServerPlayer player) {
        if (!TransitConfig.BLOCK_PORTALS_EARLY.get()) return true;
        if (!TransitConfig.ENFORCE.get()) return true;
        if (bypasses(player)) return true;

        ResourceKey<Level> origin = player.level().dimension();
        long now = System.currentTimeMillis();

        if (isControlled(origin)) {
            // Preso do lado de dentro: a origem sozinha ja decide, sem ambiguidade nenhuma.
            if (isGateOpen(origin) || hasPass(player, origin, now)) return true;

            DenyNotifier.notify(player, origin, true);
            return false;
        }

        // Vindo de uma dimensao livre nao da para saber o destino. So barramos cedo no modo
        // "tranca por padrao", onde a chance de o portal levar a algo controlado e alta; com
        // lockUnscheduledDimensions desligado o servidor esta em modo permissivo e nao vale o risco
        // de barrar um portal de mod que so anda dentro da propria dimensao.
        if (!TransitConfig.LOCK_UNSCHEDULED_DIMENSIONS.get()) return true;
        if (TransitClock.anyOpen()) return true;
        if (PassData.get(player.server).hasAny(player.getUUID(), now)) return true;

        DenyNotifier.notify(player, origin, false);
        return false;
    }

    // --- Predicados ---------------------------------------------------------------------------

    public static boolean bypasses(ServerPlayer player) {
        if (TransitConfig.BYPASS_CREATIVE.get() && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        return player.hasPermissions(TransitConfig.BYPASS_PERMISSION_LEVEL.get());
    }

    /** Esta dimensao esta sujeita a horario? */
    public static boolean isControlled(ResourceKey<Level> dimension) {
        if (FREE_DIMENSIONS.get().contains(dimension)) return false;
        if (TransitClock.forDimension(dimension) != null) return true;

        return TransitConfig.LOCK_UNSCHEDULED_DIMENSIONS.get();
    }

    /** A linha desta dimensao esta com a janela aberta? Falso tambem quando nao existe linha. */
    public static boolean isGateOpen(ResourceKey<Level> dimension) {
        LineClock clock = TransitClock.forDimension(dimension);
        return clock != null && clock.isOpen();
    }

    private static boolean hasPass(ServerPlayer player, ResourceKey<Level> dimension, long now) {
        return PassData.get(player.server).has(player.getUUID(), dimension, now);
    }

    private static Set<ResourceKey<Level>> parseDimensions(List<? extends String> configured) {
        Set<ResourceKey<Level>> parsed = new HashSet<>(configured.size());

        for (String raw : configured) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                AurorionPortais.LOGGER.error("freeDimensions: '{}' nao e um id valido e foi ignorado.", raw);
                continue;
            }
            parsed.add(ResourceKey.create(Registries.DIMENSION, id));
        }
        return parsed;
    }
}
