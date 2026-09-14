package com.aurorion.mundos.event;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.border.BorderSync;
import com.aurorion.mundos.portal.LinkCatalog;
import com.aurorion.mundos.portal.PortalRouter;
import com.aurorion.mundos.world.WorldCatalog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Os tres momentos em que este mod tem algo a fazer: o boot, a carga de datapack, e o jogador
 * chegando numa dimensao. Nada aqui roda por tick.
 */
@EventBusSubscriber(modid = AurorionMundos.MOD_ID)
public final class MundosServerEvents {
    private MundosServerEvents() {
    }

    /** Ligacoes sao datapack: recarregam junto com receitas e loot tables, no boot e a cada /reload. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(LinkCatalog.listener());
    }

    /**
     * Depois de {@code createLevels}, que e quem cria as dimensoes e amarra as barreiras.
     *
     * <p>Nao pode ser antes: e justamente o trabalho do {@code createLevels} que precisa ser desfeito.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BorderSync.install(event.getServer());
        report(event.getServer());
    }

    /**
     * Uma linha por mundo no log do boot, com o seed que a dimensao <b>realmente</b> esta usando.
     *
     * <p>Nao e diagnostico de desenvolvedor: e o que torna o fluxo de pre-geracao verificavel. Pre-
     * gerar terreno em outra maquina so funciona se o seed for identico nas duas, e a unica forma de
     * conferir isso sem abrir o mundo e comparar esta linha nos dois logs.
     *
     * <p>O valor vem de {@code level.getSeed()}, e nao da config — passa pelo mesmo mixin que o
     * gerador de terreno consulta. Se o numero aqui for o seed do mundo em vez do declarado, o mixin
     * nao aplicou, e o log diz isso antes de alguem perder uma pre-geracao inteira descobrindo.
     */
    private static void report(MinecraftServer server) {
        for (ResourceKey<Level> dimension : WorldCatalog.managed()) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) continue;

            AurorionMundos.LOGGER.info("{}: seed {}, barreira {} blocos, geracao em runtime {}.",
                    dimension.location(),
                    level.getSeed(),
                    Math.round(level.getWorldBorder().getSize()),
                    WorldCatalog.allowsRuntimeGeneration(dimension) ? "LIGADA" : "desligada");
        }
    }

    /**
     * Os tres caminhos por onde um jogador chega a uma dimensao. Nos tres, o vanilla acabou de mandar
     * a barreira do overworld ({@code PlayerList#sendLevelInfo}) e nos corrigimos por cima — os
     * eventos do NeoForge disparam depois dele nos tres casos:
     * {@code placeNewPlayer} (233 antes de 275), {@code respawn} (497 antes de 505) e
     * {@code ServerPlayer#changeDimension} (927 antes do fire).
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BorderSync.sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BorderSync.sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BorderSync.sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PortalRouter.forget(event.getEntity().getUUID());
    }

    /**
     * Servidor integrado: sem isto, uma sessao deixaria estado visivel para a proxima.
     *
     * <p>O {@code BorderData} nao aparece aqui de proposito — quem solta o cache dele e o
     * {@code aurorion-core}, para nenhum mod precisar lembrar disso.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PortalRouter.clear();
    }
}
