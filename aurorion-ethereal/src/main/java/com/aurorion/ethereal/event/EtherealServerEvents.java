package com.aurorion.ethereal.event;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.EtherealTags;
import com.aurorion.ethereal.block.AeonicProjectorBlock;
import com.aurorion.ethereal.block.HouseMuralBlock;
import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.ceremony.CeremonyManager;
import com.aurorion.ethereal.ceremony.BindingRite;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.house.HouseMuralManager;
import com.aurorion.ethereal.house.PendingSelections;
import com.aurorion.ethereal.house.PlayerThrottle;
import com.aurorion.ethereal.network.EtherealNetwork;
import com.aurorion.ethereal.ranking.BoardMode;
import com.aurorion.ethereal.ranking.BoardService;
import com.aurorion.ethereal.ranking.RankingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.aurorion.core.house.HouseChangedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class EtherealServerEvents {
    /** O altar manda o catalogo inteiro a cada abertura; clique segurado nao pode virar enxurrada. */
    private static final PlayerThrottle ALTAR_OPENS = new PlayerThrottle(500L);
    /** Vitima que renasceu (ou entrou) ha menos de 30 s nao conta vitoria de duelo. */
    private static final int DUEL_FRESH_SPAWN_TICKS = 30 * 20;
    /** O mesmo vencedor contra a mesma vitima so pontua uma vez a cada 10 minutos. */
    private static final long DUEL_PAIR_COOLDOWN_MILLIS = 10L * 60_000L;
    private static final int DUEL_PAIRS_PRUNE_AT = 512;
    private static final Map<DuelPair, Long> DUEL_PAIRS = new HashMap<>();

    private record DuelPair(UUID winner, UUID victim) { }

    private EtherealServerEvents() {
    }

    /** Casas sao datapack: recarregam com receitas e loot tables, no start e em cada /reload. */
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
            // A cor de uma casa pode ter mudado: a tab de todo mundo e repintada uma vez.
            NeoForge.EVENT_BUS.post(new HouseChangedEvent(event.getPlayerList().getServer(), null));
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

        if (state.getBlock() instanceof HouseMuralBlock) {
            consume(event);
            HouseMuralManager.open(player, pos);
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
        if (CeremonyManager.isRunning(player.getUUID()) || ALTAR_OPENS.throttled(player)) return;

        // Ja ter casa (e o allowRechoose) e decidido em openSelection; travar aqui por "tem casa"
        // deixava o allowRechoose sem efeito nenhum.
        HouseManager.openSelection(player, pos, EtherealConfig.CEREMONY_REQUIRED.get());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RankingData.get(player.server).seePlayer(player.getUUID(), player.getScoreboardName());
        // Login e evento raro; tambem cobre primeiro acesso e mudanca de nome sem qualquer polling.
        BoardService.refresh(player.server, BoardMode.TOP_PLAYERS, BoardMode.WORST_PLAYERS,
                BoardMode.MISSIONS, BoardMode.LIVES, BoardMode.DUEL_WINS);

        // A apresentacao e sempre disparada pela staff; entrar nunca consome uma fila antiga.
        BindingRite.syncTo(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer
                && BindingRite.isBinding(event.getTarget().getUUID())) BindingRite.syncTo(viewer);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        RankingData data = RankingData.get(victim.server);
        data.recordDeath(victim.getUUID(), victim.getScoreboardName());

        if (event.getSource().getEntity() instanceof ServerPlayer winner && winner != victim
                && countsAsDuel(winner, victim)) {
            data.recordDuelWin(winner.getUUID(), winner.getScoreboardName());
            BoardService.refresh(victim.server, BoardMode.DUEL_WINS);
        }
    }

    /**
     * Vitoria de duelo que vale ponto. Sem isto, dois amigos (ou um alt) se matavam em loop no ponto
     * de renascimento e subiam o ranking: quem acabou de renascer nao conta, e o mesmo par so pontua
     * uma vez a cada {@link #DUEL_PAIR_COOLDOWN_MILLIS}.
     */
    private static boolean countsAsDuel(ServerPlayer winner, ServerPlayer victim) {
        // tickCount zera no renascimento e no login: o ServerPlayer e uma entidade nova.
        if (victim.tickCount < DUEL_FRESH_SPAWN_TICKS) return false;
        long now = System.currentTimeMillis();
        if (DUEL_PAIRS.size() > DUEL_PAIRS_PRUNE_AT) {
            DUEL_PAIRS.values().removeIf(at -> now - at >= DUEL_PAIR_COOLDOWN_MILLIS);
        }
        DuelPair pair = new DuelPair(winner.getUUID(), victim.getUUID());
        Long last = DUEL_PAIRS.get(pair);
        if (last != null && now - last < DUEL_PAIR_COOLDOWN_MILLIS) return false;
        DUEL_PAIRS.put(pair, now);
        return true;
    }

    // --- Mural e projetor ----------------------------------------------------------------------

    /**
     * Mural e projetor sao mobilia da staff: um jogador quebrando o mural da propria Casa (ou da
     * rival) apagava o vinculo e o acesso ao cofre de todo mundo. So nivel 2 quebra; os dois tambem
     * resistem a explosao (ver {@code EtherealBlocks}).
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Block block = event.getState().getBlock();
        if (!(block instanceof HouseMuralBlock) && !(block instanceof AeonicProjectorBlock)) return;
        if (event.getPlayer() != null && event.getPlayer().hasPermissions(2)) return;
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_ethereal.block.staff_only"), true);
        }
    }

    /** Sair encerra a apresentacao e preserva a casa. */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PendingSelections.forget(event.getEntity().getUUID());
        CeremonyManager.forget(event.getEntity().getUUID());
    }

    /** Sem rito ativo, o relogio sai imediatamente. */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        BindingRite.tick(event.getServer());
    }

    /** Limpa estado transitorio inclusive entre mundos do servidor integrado. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DUEL_PAIRS.clear();
        PendingSelections.clear();
        CeremonyManager.clear();
        BoardService.clear();
    }
}
