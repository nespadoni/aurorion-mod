package com.aurorion.portais.event;

import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.config.TransitConfig;
import com.aurorion.portais.line.LineCatalog;
import com.aurorion.portais.pass.PassData;
import com.aurorion.portais.pass.PendingPassConsumptions;
import com.aurorion.portais.runtime.DenyNotifier;
import com.aurorion.portais.runtime.RespawnAnchor;
import com.aurorion.portais.runtime.TransitClock;
import com.aurorion.portais.runtime.TransitGate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = AurorionPortais.MOD_ID)
public final class PortaisServerEvents {
    private PortaisServerEvents() {
    }

    /** Linhas sao datapack: recarregam junto com receitas e loot tables, no start e em cada /reload. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(LineCatalog.listener());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TransitClock.tick(event.getServer());
        // PlayerChangedDimensionEvent e sincrono com a viagem. O que sobrou aqui foi cancelado
        // depois da nossa autorizacao e nao pode vazar para outra tentativa.
        PendingPassConsumptions.clear();
    }

    /**
     * A palavra final sobre qualquer troca de dimensao.
     *
     * <p>O ponto de injecao e o mesmo por onde passa <b>todo</b> jeito de trocar de dimensao no
     * jogo: portal do Nether, do End, {@code /tp} entre dimensoes, e teleporte de mod que use a
     * maquinaria vanilla. E por isso que "dimensao de mod tambem" nao exige codigo por mod — nao ha
     * lista de portais conhecidos para manter, so este funil.
     *
     * <p>So jogador e barrado. Mob, item e projetil continuam viajando: o escopo do mod e o transito
     * de pessoas, e ampliar isso mexeria em mecanica de mod de terceiro sem pedido (SDD §2).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceKey<Level> from = player.level().dimension();
        ResourceKey<Level> to = event.getDimension();
        PendingPassConsumptions.forget(player.getUUID());

        TransitGate.Verdict verdict = TransitGate.check(player, from, to);
        if (verdict.allowed()) {
            if (verdict.reason() == TransitGate.Reason.PASS && verdict.dimension() != null) {
                PendingPassConsumptions.authorize(player.getUUID(), from, to, verdict.dimension());
            }
            return;
        }

        event.setCanceled(true);
        DenyNotifier.notify(player, verdict.dimension(), verdict.reason() == TransitGate.Reason.LOCKED_EXIT);
    }

    /** O passe so e debitado depois que o NeoForge confirma a troca de dimensao. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceKey<Level> passDimension = PendingPassConsumptions.complete(
                player.getUUID(), event.getFrom(), event.getTo());
        if (passDimension != null) {
            PassData.get(player.server).consume(player.getUUID(), passDimension, System.currentTimeMillis());
        }
    }

    /**
     * Morrer dentro nao e bilhete de volta.
     *
     * <p>Sem isto, a mecanica inteira teria uma porta dos fundos obvia: quem perdeu o trem se joga
     * na lava e reaparece no spawn do mundo de graca. Com isto, o unico jeito de sair e o proximo
     * trem ou um passe.
     *
     * <p>Cama ou ancora dentro da propria dimensao continuam valendo — se o vanilla ja escolheu
     * renascer ali, nao ha nada a corrigir.
     */
    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!TransitConfig.ENFORCE.get() || !TransitConfig.KEEP_PLAYERS_ON_DEATH.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // O portal de retorno do End, depois do dragao, passa por aqui com isFromEndFight() — e NAO
        // e tratado como excecao de proposito. Matar o dragao nao e passagem de volta: o jogador ve
        // os creditos e acorda no End, do mesmo jeito que qualquer um que perdeu a hora do trem.
        // A regra deste mod nao abre buraco nenhum sozinha; quem libera dimensao e o horario da
        // linha, o passe, ou a staff — mais nada.

        ResourceKey<Level> died = player.level().dimension();
        if (!TransitGate.isControlled(died) || TransitGate.bypasses(player)) return;

        DimensionTransition current = event.getDimensionTransition();
        if (current.newLevel().dimension() == died) return;

        ServerLevel level = player.server.getLevel(died);
        if (level == null) return;

        Vec3 pos = RespawnAnchor.findIn(level, died, BlockPos.containing(player.position()));
        if (pos == null) {
            // Coluna inteira intransponivel: o construtor de DimensionTransition que recebe a
            // entidade resolve o spawn da propria dimensao pelo caminho do vanilla. Continua dentro,
            // que e o que importa.
            event.setDimensionTransition(new DimensionTransition(level, player, DimensionTransition.DO_NOTHING));
            return;
        }

        event.setDimensionTransition(new DimensionTransition(
                level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DenyNotifier.forget(event.getEntity().getUUID());
        PendingPassConsumptions.forget(event.getEntity().getUUID());
    }

    /**
     * Servidor integrado: sem isto, uma sessao deixaria estado visivel para a proxima.
     *
     * <p>O {@code PassData} nao aparece aqui de proposito — quem solta o cache dele e o
     * {@code aurorion-core}, para nenhum mod precisar lembrar disso.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TransitClock.clear();
        DenyNotifier.clear();
        PendingPassConsumptions.clear();
    }
}
