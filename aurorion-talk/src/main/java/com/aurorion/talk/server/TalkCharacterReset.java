package com.aurorion.talk.server;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.talk.AurorionTalk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** O estilo do balao de fala era daquela pessoa. A proxima escolhe o dela. */
@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkCharacterReset {
    private TalkCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        PlayerStyleData.get(event.server()).setStyle(event.account(), null);
    }
}
