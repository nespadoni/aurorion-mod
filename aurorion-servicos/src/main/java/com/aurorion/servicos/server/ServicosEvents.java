package com.aurorion.servicos.server;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.servicos.AurorionServicos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Entrada, saida, morte definitiva e o relogio do app.
 *
 * <p>O relogio passa a cada 20 s, nao a cada tick: pedido que vence em 2 h nao precisa de precisao de
 * 50 ms, e a passada e uma volta pelas colecoes pequenas do {@code ServicosData}.
 */
@EventBusSubscriber(modid = AurorionServicos.MOD_ID)
public final class ServicosEvents {
    private static final int AGE_INTERVAL_TICKS = 400;
    private static int ticks;

    private ServicosEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ServicosManager.onLogin(player);
    }

    /** "Trabalhando" e da sessao: quem sai deixa de receber pedido aberto. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServicosManager.onLogout(event.getEntity().getUUID());
    }

    /** O personagem novo nao herda anuncio, pedido, vaga nem candidatura do que morreu. */
    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        ServicosManager.onReset(event.server(), event.account());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        ServicosManager.tickReminders(event.getServer());
        if (++ticks < AGE_INTERVAL_TICKS) return;
        ticks = 0;
        ServicosManager.age(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ticks = 0;
        ServicosManager.clear();
    }
}
