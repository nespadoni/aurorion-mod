package com.aurorion.limbo.event;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.compat.AdmCompat;
import com.aurorion.limbo.compat.PlayerReviveCompat;
import com.aurorion.limbo.environment.LimboEnvironment;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.ForgottenDoor;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.exile.LimboSpawn;
import com.aurorion.limbo.network.LimboNetwork;
import com.aurorion.limbo.report.DiscordSink;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = AurorionLimbo.MOD_ID)
public final class LimboServerEvents {
    /** Uma varredura por segundo. Ver {@link LimboManager#sweep} para por que isso basta. */
    private static final int SWEEP_TICKS = 20;

    private static int counter;
    private static int cleanupCounter;

    private LimboServerEvents() {
    }

    /**
     * Abre o registro de quem acabou de zerar.
     *
     * <p>Prioridade {@code LOWEST} e a peca que faz isto funcionar: o {@code aurorion_vidas} cobra a
     * vida em {@code LOW}, entao so depois dele e que a pergunta "esta exilado?" tem a resposta desta
     * morte. Em qualquer prioridade mais alta, a ultima morte de alguem seria lida como a penultima.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                LimboManager.openIfNeeded(player);
            } catch (RuntimeException | LinkageError error) {
                // A morte ja foi aceita pelo vanilla e pelo Vidas. Uma falha de apresentacao,
                // auditoria ou compatibilidade nao pode interromper Entity#die no meio e deixar o
                // jogador vivo com zero de vida. Login/respawn reabre o registro se ainda faltar.
                AurorionLimbo.LOGGER.error(
                        "Falha ao abrir o registro do Limbo para {}; a morte segue normalmente.",
                        player.getGameProfile().getName(), error);
            }
        }
    }

    /**
     * Quem entra ja exilado tambem precisa de um registro.
     *
     * <p>Cobre tres casos reais: quem ja estava exilado antes deste mod existir, quem a staff exilou
     * na mao, e quem simplesmente deslogou no Limbo. Sem isto, essas pessoas ficariam sem prazo e sem
     * registro — sem saida e invisiveis para a staff ao mesmo tempo.
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LimboManager.openIfNeeded(player);
        PlayerReviveCompat.clearIfExiled(player);
        LimboEnvironment.reconcile(player);
        refreshPanel(player);
        announceArrival(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LimboEnvironment.disconnect(player);
        }
    }

    /**
     * Cada respawn no Limbo acorda a pessoa num lugar novo, sempre na superficie.
     *
     * <p>{@code LOWEST} porque o {@code aurorion_vidas} ja escolheu o destino em {@code LOW} — ele
     * manda para a ancora do exilio, que e um ponto so. Aqui a dimensao ja esta decidida e o que se
     * troca e apenas <b>onde</b> dentro dela, entao nada do outro mod precisa mudar. E o mesmo
     * arbitro da SDD §9.2: a regra mais especifica fala por ultimo.
     *
     * <p>Cobre os dois casos de uma vez, porque sao o mesmo evento: cair no Limbo pela primeira vez e
     * morrer ja estando dentro dele.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LivesManager.isExiled(player.server, player.getUUID())) return;

        ServerLevel limbo = player.server.getLevel(LimboManager.dimension());
        if (limbo == null || event.getDimensionTransition().newLevel() != limbo) return;

        BlockPos spot = LimboSpawn.scattered(limbo);
        if (spot == null) return;

        DimensionTransition current = event.getDimensionTransition();
        event.setDimensionTransition(new DimensionTransition(limbo, spot.getBottomCenter(), Vec3.ZERO,
                current.yRot(), current.xRot(), DimensionTransition.DO_NOTHING));
    }

    /** O respawn troca a entidade do jogador; e a primeira tela que o exilado ve. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LimboManager.openIfNeeded(player);
            PlayerReviveCompat.clearIfExiled(player);
            LimboEnvironment.reconcile(player);
            refreshPanel(player);
            announceArrival(player);
        }
    }

    /**
     * Entrar no Limbo acende o painel; sair apaga.
     *
     * <p>A saida e a metade que a varredura nao cobre: quem foi resgatado ou atravessou a Porta sai
     * do mapa de exilados, e a partir dai nenhuma volta do laco passa por ele de novo. Sem este
     * evento, o painel ficaria congelado na tela de quem ja voltou ao overworld.
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LimboEnvironment.reconcile(player);
            refreshPanel(player);
        }
    }

    @SubscribeEvent
    public static void onDarknessRemoved(MobEffectEvent.Remove event) {
        LimboEnvironment.keepDarkness(event);
    }

    @SubscribeEvent
    public static void onDarknessExpired(MobEffectEvent.Expired event) {
        LimboEnvironment.keepDarkness(event);
    }

    /**
     * Reenvia o painel agora, sem esperar a proxima mudanca de etapa.
     *
     * <p>Login, respawn e troca de dimensao sao os tres momentos em que a tela esquece o que sabia —
     * entao a etapa ja enviada e descartada antes do envio, ou o {@code syncDue} concluiria que nao
     * ha nada de novo a dizer e a tela ficaria vazia.
     */
    private static void refreshPanel(ServerPlayer player) {
        ExileRecord record = LimboData.get(player.server).record(player.getUUID());
        if (record != null) {
            record.invalidateSync();
        }
        LimboNetwork.sync(player);
    }

    private static void announceArrival(ServerPlayer player) {
        if (player.level().dimension() != LimboManager.dimension()) return;
        if (!LivesManager.isExiled(player.server, player.getUUID())) return;

        ExileRecord record = LimboData.get(player.server).record(player.getUUID());
        if (record != null) {
            if (record.remainingMillis() <= 0L) LimboManager.narrator().expired(player);
            else LimboManager.narrator().arrival(player, record.remainingMillis());
        }
    }

    /**
     * Avisa alto quando a instalacao esta incompleta.
     *
     * <p>Este mod traz a dimensao, mas quem decide para onde o exilio manda e o {@code aurorion_vidas}
     * — e o padrao dele e o Nether. Instalar o Limbo e nao trocar essa config resulta no pior tipo de
     * falha: tudo carrega, nada quebra, e simplesmente nada acontece. Duas linhas no log na hora do
     * boot custam menos que uma tarde procurando por que o prazo nunca comeca.
     *
     * <p>Nao corrigimos sozinhos de proposito: sobrescrever a config de outro mod em silencio e pior
     * que o problema que resolveria — quem instalou so o {@code aurorion_vidas} e depois acrescentou
     * este teria o destino do exilio trocado sem ter pedido.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        AdmCompat.register();

        ResourceKey<Level> dimension = LimboManager.dimension();

        if (event.getServer().getLevel(dimension) == null) {
            AurorionLimbo.LOGGER.error(
                    "A dimensao de exilio '{}' nao existe neste servidor. O Limbo nao vai receber ninguem.",
                    dimension.location());
            return;
        }

        if (!dimension.location().getNamespace().equals(AurorionLimbo.MOD_ID)) {
            AurorionLimbo.LOGGER.warn(
                    "O exilio aponta para '{}', que nao e o Limbo. Para usar este mod, ajuste "
                            + "exileDimension=\"{}:limbo\" em config/aurorion_vidas-server.toml.",
                    dimension.location(), AurorionLimbo.MOD_ID);
        }
    }

    /**
     * Clique direito num mob com a tag do Oraculo abre a tela do resgate.
     *
     * <p><b>Nao registramos entidade propria de proposito.</b> Um esqueleto com a tag
     * {@code aurorion_oraculo} ja e um Oraculo, e qualquer mob do modpack tambem pode ser — sem
     * modelo, sem renderer, sem IA, sem ovo de spawn e sem conteudo novo que fique preso no save para
     * sempre (SDD §6.1). E a mesma ideia da tag de altar do {@code aurorion_ethereal}: o acoplamento
     * e um dado, nunca uma classe.
     *
     * <p>Cancelamos o evento para o clique nao virar outra coisa — trocar de item, montar, abrir o
     * inventario do mob. Sem isso, um Oraculo num mob montavel viraria um cavalo.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getTarget().getTags().contains(LimboConfig.ORACLE_TAG.get())) return;

        // Dialogo primeiro: e ele que da personalidade ao Oraculo. A lista e o que o ADM nao
        // consegue mostrar, e uma escolha do dialogo a abre rodando /oraculo.
        if (!AdmCompat.openOracleDialogue(player, event.getTarget())) {
            LimboNetwork.openOracle(player);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < SWEEP_TICKS) return;

        counter = 0;
        LimboEnvironment.tick(event.getServer());
        LimboManager.sweep(event.getServer());
        if (++cleanupCounter >= 60) {
            cleanupCounter = 0;
            ForgottenDoor.cleanPending(event.getServer());
        }
    }

    /**
     * A Porta do Esquecido atravessa dimensao trancada.
     *
     * <p>Dois mods cancelam esta viagem por motivos legitimos: o {@code aurorion_vidas} enquanto a
     * pessoa tem zero vidas, e o {@code aurorion_portais} porque o Limbo e uma dimensao nova e nasce
     * trancada (SDD §8.2). O primeiro ja foi resolvido na origem — a vida e devolvida antes da
     * viagem. O segundo e resolvido aqui.
     *
     * <p>Duas coisas fazem isto funcionar sem que nenhum dos tres mods conheca os outros:
     *
     * <ul>
     *   <li>{@code LOWEST} — a regra mais especifica fala por ultimo, exatamente como o arbitro da
     *       SDD §9.2. Abrir uma excecao de "teleporte de sistema" dentro do {@code aurorion_portais}
     *       seria o buraco que a §8.4 diz que ele nao pode ter.</li>
     *   <li>{@code receiveCanceled} — sem isso, o evento ja cancelado por outro mod nunca chegaria
     *       aqui, e a autorizacao nao teria como ser aplicada.</li>
     * </ul>
     *
     * <p>A autorizacao e consumida mesmo que o evento nao estivesse cancelado: ela vale para uma
     * travessia, naquele tick, e nao pode sobrar para uma viagem futura (SDD §8.7).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ForgottenDoor.consumeAuthorization(player, event.getDimension())) return;

        if (event.isCanceled()) {
            event.setCanceled(false);
            AurorionLimbo.LOGGER.debug("Porta do Esquecido destravou a saida de {}",
                    player.getGameProfile().getName());
        }
    }

    /**
     * Solta o que e do processo, e nao do mundo.
     *
     * <p>O {@code SavedDataAccess} do core ja cuida dos dados. O que sobra aqui e o narrador (que
     * pode ter resolvido reflexao), o mapa de autorizacoes e a thread do webhook — tres coisas que,
     * no servidor integrado, atravessariam de um mundo aberto para o proximo.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LimboManager.reset();
        AdmCompat.reset();
        LimboEnvironment.reset();
        DiscordSink.shutdown();
        counter = 0;
        cleanupCounter = 0;
    }
}
