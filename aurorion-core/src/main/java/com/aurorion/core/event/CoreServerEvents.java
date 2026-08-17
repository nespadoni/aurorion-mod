package com.aurorion.core.event;

import com.aurorion.core.AurorionCore;
import com.aurorion.core.data.SavedDataAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * O unico listener do {@code aurorion-core}, e ele existe para que os outros mods <b>nao</b>
 * precisem de um.
 *
 * <p>Antes, cada mod que guardava um {@link SavedDataAccess} tinha que lembrar de zera-lo ao parar o
 * servidor — dois dos quatro nao lembravam. Centralizar aqui transforma "cada autor precisa saber
 * disso" em "ja esta resolvido".
 */
@EventBusSubscriber(modid = AurorionCore.MOD_ID)
public final class CoreServerEvents {
    private CoreServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SavedDataAccess.invalidateAll();
    }
}
