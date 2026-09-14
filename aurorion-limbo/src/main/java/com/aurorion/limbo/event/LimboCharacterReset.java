package com.aurorion.limbo.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.finale.FinaleData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * O exilio, o prazo e o epilogo pendente morrem com o personagem que os viveu.
 *
 * <p>O epilogo em especial precisa sair daqui: um registro sobrando faria o {@code FinaleManager}
 * reabrir a cena de morte para uma identidade que acabou de nascer.
 */
@EventBusSubscriber(modid = AurorionLimbo.MOD_ID)
public final class LimboCharacterReset {
    private LimboCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        LimboData.get(event.server()).clear(event.account());
        FinaleData.get(event.server()).complete(event.account());
    }
}
