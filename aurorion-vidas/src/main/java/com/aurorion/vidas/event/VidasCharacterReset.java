package com.aurorion.vidas.event;

import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import com.aurorion.vidas.lives.LivesData;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Personagem novo nasce com as vidas cheias.
 *
 * <p>A escrita vai direto no {@link LivesData}, e nao pelo {@code LivesManager.setLives}, por um
 * motivo de ordem: durante o reset a conta <b>ainda esta marcada como morta</b> — a identidade nova
 * so e publicada quando o reset inteiro termina. O atalho do manager existe justamente para forcar
 * zero nesse estado, e usa-lo aqui devolveria um personagem recem-nascido sem nenhuma vida.
 */
@EventBusSubscriber(modid = AurorionVidas.MOD_ID)
public final class VidasCharacterReset {
    private VidasCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        LivesData.get(event.server()).setLives(event.account(), LivesConfig.MAX_LIVES.get());
    }

    /** O HUD so pode mostrar o numero novo depois que a identidade existe. */
    @SubscribeEvent
    public static void onNamed(CharacterNamedEvent event) {
        ServerPlayer player = event.player();
        if (event.replacement()) LivesManager.sync(player);
    }
}
