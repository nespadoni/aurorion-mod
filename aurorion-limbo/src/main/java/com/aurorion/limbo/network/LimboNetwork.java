package com.aurorion.limbo.network;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.client.ClientLimbo;
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
