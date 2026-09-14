package com.aurorion.ethereal.event;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.EtherealTags;
import com.aurorion.ethereal.block.AeonicProjectorBlock;
import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.ceremony.CeremonyManager;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.house.PendingSelections;
import com.aurorion.ethereal.network.EtherealNetwork;
import com.aurorion.ethereal.ranking.BoardMode;
import com.aurorion.ethereal.ranking.BoardService;
import com.aurorion.ethereal.ranking.RankingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class EtherealServerEvents {
    private EtherealServerEvents() {
    }

    /** Casas e perguntas sao datapack: recarregam com receitas e loot tables, no start e em cada /reload. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(HouseCatalog.listener());
    }

    /**
     * Um {@code /reload} pode ter mudado nome, cor ou ate a existencia de uma casa — os placares de
     * casa precisam ser redesenhados, senao ficam mostrando o catalogo antigo ate a proxima morte.
     *
     * <p>O evento tambem dispara por jogador no login; ali nao ha nada a fazer, porque o placar e
     * estado do mundo e nao da sessao de quem entrou.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            BoardService.refreshHouses(event.getPlayerList().getServer());
        }
    }

    /**
     * Um unico listener para os dois blocos deste mod.
     *
     * <p>Num modpack pesado isto roda em <b>todo</b> clique com botao direito do servidor inteiro,
     * entao o caminho de "nao e comigo" e um {@code getBlockState} e dois testes — e nada e
     * cancelado antes de a decisao estar tomada, para nao atrapalhar interacao de mais ninguem
     * (SDD §2).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);

        if (state.getBlock() instanceof AeonicProjectorBlock) {
            consume(event);
            openProjector(player, pos);
            return;
        }

        if (state.is(EtherealTags.HOUSE_ALTARS)) {
            consume(event);
            openAltar(player, pos);
        }
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        // Cancelar so depois de confirmar que o bloco e nosso: impede colocar bloco em cima do altar
        // e impede o item da mao ser usado, sem tocar em interacao de mais ninguem.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void openProjector(ServerPlayer player, BlockPos pos) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("aurorion_ethereal.projector.error.permission"), true);
            return;
        }
        if (player.level().getBlockEntity(pos) instanceof AeonicProjectorBlockEntity projector) {
            BoardService.refresh(projector);
            EtherealNetwork.openProjectorConfig(player, projector);
        }
    }

    /**
     * Clique no altar.
     *
     * <p>O altar nao decide mais nada: quem define a casa e a staff, por {@code /casa cerimonia}. Ele
     * e onde se <b>consulta</b> a propria casa — e, com {@code ceremonyRequired} desligado, onde o
     * jogador escolhe sozinho, para quando nao houver staff conduzindo.
     */
    private static void openAltar(ServerPlayer player, BlockPos pos) {
        if (CeremonyManager.isRunning(player.getUUID())) return;

        boolean locked = EtherealConfig.CEREMONY_REQUIRED.get()
                || HouseManager.houseIdOf(player.server, player.getUUID()) != null;
        HouseManager.openSelection(player, pos, locked);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RankingData.get(player.server).seePlayer(player.getUUID(), player.getScoreboardName());
        // Login e evento raro; tambem cobre primeiro acesso e mudanca de nome sem qualquer polling.
        BoardService.refresh(player.server, BoardMode.TOP_PLAYERS, BoardMode.WORST_PLAYERS,
                BoardMode.MISSIONS, BoardMode.DEATHS, BoardMode.DUEL_WINS);

        // Uma casa definida com ele offline: o rito acontece agora, e nao vira um anuncio de chat
        // que ele nunca viu.
        CeremonyManager.playPending(player);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        RankingData data = RankingData.get(victim.server);
        data.recordDeath(victim.getUUID(), victim.getScoreboardName());
        BoardService.refresh(victim.server, BoardMode.DEATHS);

        if (event.getSource().getEntity() instanceof ServerPlayer winner && winner != victim) {
            data.recordDuelWin(winner.getUUID(), winner.getScoreboardName());
            BoardService.refresh(victim.server, BoardMode.DUEL_WINS);
        }
    }

    /** Sair no meio das perguntas descarta o andamento; um veredito ja fechado fica no disco. */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PendingSelections.forget(event.getEntity().getUUID());
        CeremonyManager.forget(event.getEntity().getUUID());
    }

    /**
     * Servidor integrado: sem isto, uma sessao deixaria pendencias visiveis para a proxima.
     *
     * <p>Os {@code SavedData} nao aparecem aqui de proposito — quem solta o cache deles e o
     * {@code aurorion-core}, para nenhum mod precisar lembrar disso (SDD §3.1).
     */
    /**
     * O unico laco por tick do mod, e ele sai na primeira linha enquanto nao houver rito nenhum —
     * que e o estado do servidor quase o tempo inteiro. Ver {@link com.aurorion.ethereal.ceremony.BindingRite}.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.aurorion.ethereal.ceremony.BindingRite.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PendingSelections.clear();
        CeremonyManager.clear();
        BoardService.clear();
    }
}
