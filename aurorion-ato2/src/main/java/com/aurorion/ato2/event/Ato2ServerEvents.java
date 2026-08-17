package com.aurorion.ato2.event;

import com.aurorion.ato2.AurorionAto2;
import com.aurorion.ato2.Ato2Tags;
import com.aurorion.ato2.house.HouseCatalog;
import com.aurorion.ato2.house.HouseManager;
import com.aurorion.ato2.house.PendingSelections;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AurorionAto2.MOD_ID)
public final class Ato2ServerEvents {
    private Ato2ServerEvents() {
    }

    /** Casas sao datapack: recarregam junto com receitas e loot tables, no start e em cada /reload. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(HouseCatalog.listener());
    }

    /**
     * Clique no altar abre a escolha.
     *
     * <p>O listener sai na primeira linha para qualquer bloco fora da tag — num modpack pesado, isso
     * aqui roda em todo clique com botao direito do servidor inteiro, entao o caminho de "nao e
     * comigo" precisa ser um teste de tag e nada mais (SDD §2, nunca interferir fora do escopo).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(Ato2Tags.HOUSE_ALTARS)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Cancelar so depois de confirmar que o bloco e nosso: impede colocar bloco em cima do altar
        // e impede o item da mao ser usado, sem tocar em interacao de mais ninguem.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        HouseManager.openSelection(player, event.getPos());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PendingSelections.forget(event.getEntity().getUUID());
    }

    /**
     * Servidor integrado: sem isso, uma sessao deixaria pendencias visiveis para a proxima.
     *
     * <p>O {@code HouseData} nao aparece aqui de proposito — quem solta o cache dele e o
     * {@code aurorion-core}, para nenhum mod precisar lembrar disso.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PendingSelections.clear();
    }
}
