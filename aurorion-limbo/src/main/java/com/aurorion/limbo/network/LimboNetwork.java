package com.aurorion.limbo.network;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.client.ClientLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.rescue.RescueManager;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AurorionLimbo.MOD_ID)
public final class LimboNetwork {
    private LimboNetwork() { }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(LimboNoticePayload.TYPE, LimboNoticePayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientLimbo.notice(payload));
        });
        registrar.playToClient(LimboStatusPayload.TYPE, LimboStatusPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientLimbo.status(payload));
        });
        registrar.playToClient(OpenOraclePayload.TYPE, OpenOraclePayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientLimbo.openOracle(payload));
        });
        registrar.playToServer(BeginRescuePayload.TYPE, BeginRescuePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                context.enqueueWork(() -> beginRescue(player, payload.target()));
            }
        });
    }

    // --- O Oraculo -----------------------------------------------------------------------------

    /**
     * Abre a tela de quem clicou no Oraculo. Cliente sem o mod simplesmente nao ve nada.
     *
     * <p>Confere a distancia aqui tambem, e nao so na hora de pagar: {@code /oraculo} e um comando sem
     * permissao (o dialogo do ADM precisa disso), entao sem esta checagem ele viraria um "listar todos
     * os exilados" que qualquer um roda de qualquer lugar do mundo. Saber quem caiu e um servico do
     * Oraculo, nao um comando.
     */
    public static void openOracle(ServerPlayer player) {
        if (!player.connection.hasChannel(OpenOraclePayload.TYPE.id())) return;
        if (!nearOracle(player)) {
            player.displayClientMessage(LimboText.oracleTooFar(), true);
            return;
        }

        var exiles = RescueManager.listExiles(player.server).stream()
                .limit(OpenOraclePayload.MAX_ENTRIES)
                .map(e -> new OpenOraclePayload.Entry(e.id(), e.name(), e.remainingMillis(),
                        e.attempted(), e.online()))
                .toList();

        PacketDistributor.sendToPlayer(player, new OpenOraclePayload(exiles,
                LivesManager.livesOf(player.server, player.getUUID()),
                LimboConfig.RESCUE_LIFE_COST.get(),
                LimboConfig.minLivesToRescue()));
    }

    /**
     * A escolha chegou do cliente. Tudo e reconferido aqui.
     *
     * <p>Inclusive a distancia: a tela pode continuar aberta enquanto a pessoa anda para longe do
     * Oraculo, e sem esta checagem daria para abrir passagem de qualquer lugar do mundo com a tela
     * que sobrou de dez minutos atras.
     */
    private static void beginRescue(ServerPlayer player, java.util.UUID target) {
        if (!nearOracle(player)) {
            player.displayClientMessage(LimboText.oracleTooFar(), true);
            return;
        }

        RescueManager.Refusal refusal = RescueManager.openPassage(player, target);
        if (refusal != RescueManager.Refusal.OK) {
            player.displayClientMessage(LimboText.refusal(refusal), true);
        }
    }

    /** Um Oraculo a menos de 8 blocos. O mesmo alcance que o vanilla usa para interagir com bau. */
    private static boolean nearOracle(ServerPlayer player) {
        String tag = LimboConfig.ORACLE_TAG.get();
        return !player.level()
                .getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(8.0D),
                        entity -> entity.getTags().contains(tag))
                .isEmpty();
    }

    public static boolean notice(ServerPlayer player, int kind, net.minecraft.network.chat.Component body) {
        if (!player.connection.hasChannel(LimboNoticePayload.TYPE.id())) return false;
        PacketDistributor.sendToPlayer(player, new LimboNoticePayload(kind, body));
        return true;
    }

    /**
     * A etapa que o painel mostra. Uma funcao so, porque quem decide reenviar compara etapas e
     * precisa chegar exatamente no mesmo numero que o pacote carrega — duas contas iguais escritas
     * em dois lugares e a que um dia discorda da outra.
     */
    public static int stageOf(ExileRecord record) {
        if (record.remainingMillis() <= 0L) return 3;
        if (record.doorPos() != null) return 2;
        return record.doorArmed() ? 1 : 0;
    }

    public static void sync(ServerPlayer player) {
        if (!player.connection.hasChannel(LimboStatusPayload.TYPE.id())) return;
        ExileRecord record = LimboData.get(player.server).record(player.getUUID());
        if (record == null || !LivesManager.isExiled(player.server, player.getUUID())
                || player.level().dimension() != LimboManager.dimension()) {
            PacketDistributor.sendToPlayer(player, LimboStatusPayload.CLEAR);
            return;
        }
        PacketDistributor.sendToPlayer(player, new LimboStatusPayload(record.remainingMillis(), stageOf(record),
                record.doorPos() == null ? BlockPos.ZERO : record.doorPos(),
                record.doorFacing() == null ? 0 : record.doorFacing().get2DDataValue()));
    }
}
