package com.aurorion.utils.abduction;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.utils.AurorionUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * A origem guardada por {@code /abduzir} aponta para onde o personagem <b>anterior</b> estava.
 * Devolver alguem para la depois da troca mandaria o personagem novo para um lugar que ele nunca viu.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class AbductionCharacterReset {
    private AbductionCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        AbductionOriginData.get(event.server()).clear(event.account());
    }
}
