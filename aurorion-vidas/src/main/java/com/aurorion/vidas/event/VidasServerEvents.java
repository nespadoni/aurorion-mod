package com.aurorion.vidas.event;

import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

@EventBusSubscriber(modid = AurorionVidas.MOD_ID)
public final class VidasServerEvents {
    private VidasServerEvents() {
    }

    /**
     * Gasta a vida.
     *
     * <p>Prioridade baixa de proposito: assim qualquer mod que <em>cancele</em> a morte (ressurreicao,
     * segunda chance, totem custom) age primeiro, e nos nem somos chamados — {@code @SubscribeEvent}
     * nao entrega evento ja cancelado. Cobrar a vida de uma morte que nao aconteceu seria o bug mais
     * caro possivel num sistema de vidas limitadas.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (LivesConfig.IGNORE_CREATIVE.get() && (player.isCreative() || player.isSpectator())) return;

        LivesManager.loseLife(player);
    }

    /**
     * Quem esta sem vidas renasce no exilio.
     *
     * <p>Prioridade baixa tambem, e por outro motivo: o {@code aurorion_portais} tem um listener
     * neste mesmo evento para prender quem morreu dentro de dimensao trancada. O exilio e a regra
     * mais especifica das duas, entao ele roda depois e tem a ultima palavra — sem que nenhum dos
     * dois mods precise conhecer o outro.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LivesManager.isExiled(player.server, player.getUUID())) return;

        DimensionTransition exile = LivesManager.exileTransition(player);
        if (exile == null) return;

        event.setDimensionTransition(exile);
        // A cama continua sendo a do jogador: quando ele recuperar vidas, volta a renascer nela.
        event.setCopyOriginalSpawnPosition(true);
    }

    /**
     * O exilado nao pega o trem.
     *
     * <p>Este listener e a outra metade do acordo com o {@code aurorion_portais}: os dois cancelam o
     * mesmo evento do vanilla, cada um pelo seu motivo, sem import entre eles. Sem isto, bastaria
     * esperar a janela semanal do Nether abrir para sair de graca — e a taxa de resgate nao teria
     * para que existir.
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!LivesConfig.BLOCK_EXILE_EXIT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (LivesConfig.IGNORE_CREATIVE.get() && (player.isCreative() || player.isSpectator())) return;

        ResourceKey<Level> exile = LivesManager.exileDimension();
        if (player.level().dimension() != exile) return;
        if (event.getDimension() == exile) return;
        if (!LivesManager.isExiled(player.server, player.getUUID())) return;

        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("aurorion_vidas.exilado.preso").withStyle(ChatFormatting.DARK_RED), true);
    }

    /** O HUD precisa do numero assim que a tela existe. */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LivesManager.sync(player);
        }
    }

    /** O respawn troca a entidade do jogador; reenviar garante o HUD certo na tela nova. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LivesManager.sync(player);
        }
    }

}
