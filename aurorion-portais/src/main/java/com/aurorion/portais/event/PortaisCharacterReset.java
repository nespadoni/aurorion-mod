package com.aurorion.portais.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.pass.PassData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Passe de viagem e uma permissao conquistada por uma historia — ela nao atravessa para a proxima.
 */
@EventBusSubscriber(modid = AurorionPortais.MOD_ID)
public final class PortaisCharacterReset {
    private PortaisCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        PassData.get(event.server()).revokeAll(event.account());
    }
}
