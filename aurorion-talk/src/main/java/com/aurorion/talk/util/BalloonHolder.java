package com.aurorion.talk.util;

import com.aurorion.talk.client.BalloonMessage;

import java.util.List;

/**
 * Duck interface enxertada em {@code AbstractClientPlayer} pelo mixin.
 * Mantem as falas ativas daquele jogador, da mais antiga para a mais recente.
 */
public interface BalloonHolder {
    void aurorion_talk$addBalloon(BalloonMessage message);

    /**
     * Descarta falas vencidas. Chamado no render em vez de no tick de proposito: jogador que nao
     * esta na tela nao paga nada, e a lista ja e limitada por {@code maxBalloons}.
     */
    void aurorion_talk$pruneBalloons(long gameTime);

    /** Lista viva (nunca nula, possivelmente vazia). Nao modificar de fora. */
    List<BalloonMessage> aurorion_talk$getBalloons();
}
