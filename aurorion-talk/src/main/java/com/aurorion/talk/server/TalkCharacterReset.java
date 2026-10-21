package com.aurorion.talk.server;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.talk.AurorionTalk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * O estilo do balao de fala era daquela pessoa. A proxima escolhe o dela.
 *
 * <p>Passa pelo {@link StyleManager}, e nao direto pelo {@link PlayerStyleData}: o reset acontece
 * com a conta online, e gravar sem broadcast deixava todo mundo vendo o balao do personagem morto
 * ate o proximo login de cada um.
 */
@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkCharacterReset {
    private TalkCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        StyleManager.setStyle(event.server(), event.account(), null);
    }
}
