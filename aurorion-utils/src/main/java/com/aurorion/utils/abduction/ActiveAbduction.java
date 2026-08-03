package com.aurorion.utils.abduction;

import com.aurorion.utils.entity.AbductionBeamEntity;

import java.util.UUID;

/**
 * Uma abducao em andamento, com sua propria maquina de estados de 3 fases:
 *
 * <ol>
 *   <li>{@code HOLD} — feixe ja na altura cheia, jogador montado nele (parado, so pode olhar ao
 *       redor), som tocando. Pausa dramatica antes da subida comecar.</li>
 *   <li>{@code ASCEND} — o feixe (e o jogador montado nele) sobe ate a altura configurada; ao
 *       terminar, o teleporte de verdade acontece e o jogador desmonta.</li>
 *   <li>{@code RETRACT} — sem ninguem mais montado, o feixe "recolhe" de baixo pra cima ate sumir
 *       e se descartar sozinho.</li>
 * </ol>
 *
 * <p>Tudo que vem de config e "congelado" aqui no momento do spawn — editar o config nunca afeta
 * uma abducao que ja esta em andamento, so as proximas.</p>
 *
 * @param returnTrip true quando veio de {@code /abduzir voltar} — ao completar a fase ASCEND, a
 *                   origem salva daquele jogador em {@link AbductionOriginData} e apagada.
 */
final class ActiveAbduction {
    enum Phase {
        HOLD, ASCEND, RETRACT
    }

    final UUID targetId;
    final AbductionBeamEntity beam;
    final TeleportSpot startSpot;
    final TeleportSpot destination;
    final boolean returnTrip;
    final int holdTicks;
    final int ascentTicks;
    final int retractTicks;
    final int ascentHeightBlocks;

    Phase phase = Phase.HOLD;
    int phaseStartTick;

    ActiveAbduction(UUID targetId, AbductionBeamEntity beam, TeleportSpot startSpot, TeleportSpot destination,
                     boolean returnTrip, int holdTicks, int ascentTicks, int retractTicks, int ascentHeightBlocks,
                     int startTick) {
        this.targetId = targetId;
        this.beam = beam;
        this.startSpot = startSpot;
        this.destination = destination;
        this.returnTrip = returnTrip;
        this.holdTicks = holdTicks;
        this.ascentTicks = ascentTicks;
        this.retractTicks = retractTicks;
        this.ascentHeightBlocks = ascentHeightBlocks;
        this.phaseStartTick = startTick;
    }
}
