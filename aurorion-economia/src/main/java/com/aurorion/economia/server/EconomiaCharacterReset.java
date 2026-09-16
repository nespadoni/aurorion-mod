package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.economia.AurorionEconomia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * O dinheiro e do personagem, nao da conta: morte definitiva zera o saldo.
 *
 * <p>A escolha tem consequencia economica e esta registrada aqui de proposito. Saldo virtual morre
 * junto; moeda que um dia vire item guardado num bau <b>nao</b> morre, porque o bau continua no
 * mundo. Quem quiser passar patrimonio para o proximo personagem tem esse caminho — e ele custa o
 * risco de alguem achar o bau. Apagar o saldo aqui e o que impede o caminho mais barato, que seria
 * morrer de proposito sem perder nada.</p>
 *
 * <p>Idempotente, como o {@link CharacterResetEvent} exige: zerar duas vezes da no mesmo.</p>
 */
@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomiaCharacterReset {
    private EconomiaCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        WalletData.get(event.server()).setBalance(event.account(), 0L);
    }
}
