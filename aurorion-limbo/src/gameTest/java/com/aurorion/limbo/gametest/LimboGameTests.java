package com.aurorion.limbo.gametest;

import com.aurorion.limbo.compat.PlayerReviveCompat;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.ForgottenDoor;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.environment.LimboEnvironment;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.config.LivesConfig;
import com.aurorion.vidas.lives.LivesManager;
import com.aurorion.vidas.network.SyncLivesPayload;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.function.Consumer;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Mod("aurorion_limbo_tests")
@GameTestHolder("aurorion_limbo_tests")
@PrefixGameTestTemplate(false)
public class LimboGameTests {
    public LimboGameTests() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            LivesConfig.EXILE_DIMENSION.set("aurorion_limbo:limbo");
            LimboConfig.WEBHOOK_URL.set("");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void exileLifecycle(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("aurorion_limbo:limbo"));
        ServerLevel limbo = server.getLevel(dimension);
        helper.assertTrue(limbo != null, "Dimensao do Limbo deve carregar no servidor dedicado");
        LivesConfig.EXILE_DIMENSION.set(dimension.location().toString());
        LivesConfig.MAX_LIVES.set(5);
        LimboConfig.WEBHOOK_URL.set("");
        LimboConfig.DOOR_WALK_MIN.set(1);
        LimboConfig.DOOR_WALK_MAX.set(1);
        LimboConfig.LEASH_RADIUS.set(0);
        limbo.getChunk(0, 0);
        limbo.getChunk(1, 0);
        limbo.getChunk(0, 1);
        limbo.getChunk(-1, 0);
        limbo.getChunk(0, -1);

        // Chegada conhecida: o teste nao depende de terreno aleatorio no overworld.
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 2, 1));
        server.overworld().setBlockAndUpdate(spawn.below(), Blocks.STONE.defaultBlockState());
        server.overworld().setBlockAndUpdate(spawn, Blocks.AIR.defaultBlockState());
        server.overworld().setBlockAndUpdate(spawn.above(), Blocks.AIR.defaultBlockState());
        server.overworld().setDefaultSpawnPos(spawn, 0);

        ServerPlayer first = player(helper, "LimboTest1");
        ServerPlayer second = player(helper, "LimboTest2");
        LimboData data = LimboData.get(server);
        try {
            enter(first, limbo, 0);
            enter(second, limbo, 40);
            LimboEnvironment.tick(server);
            helper.assertTrue(first.hasEffect(MobEffects.DARKNESS),
                    "Entrar no Limbo deve aplicar Darkness I");
            first.removeEffect(MobEffects.DARKNESS);
            helper.assertTrue(first.hasEffect(MobEffects.DARKNESS),
                    "Leite e effect clear nao podem remover Darkness dentro do Limbo");
            verifyPlayerReviveCompatibility(helper, first);
            List<String> response = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override public void sendSystemMessage(Component text) { response.add(text.getString()); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            var source = server.createCommandSourceStack().withSource(capture);
            helper.assertTrue(server.getCommands().getDispatcher().execute("limbo relatorio", source) == 2,
                    "Relatorio deve retornar os dois exilados");
            helper.assertTrue(response.size() == 3 && response.getFirst().startsWith("limbo v1 exilados=2 "),
                    "Contrato RCON: cabecalho e uma linha por jogador");
            LimboManager.sweep(server); // arma as duas portas
            first.awardStat(Stats.WALK_ONE_CM, 200);
            second.awardStat(Stats.WALK_ONE_CM, 200);
            LimboManager.sweep(server); // revela
            ExileRecord a = data.record(first.getUUID());
            ExileRecord b = data.record(second.getUUID());
            helper.assertTrue(a.doorPos() != null && b.doorPos() != null, "Caminhada deve revelar duas portas");
            BlockPos doorA = a.doorPos(), doorB = b.doorPos();
            first.teleportTo(doorA.getX() + .5, doorA.getY() + 1, doorA.getZ() + .5);
            second.teleportTo(doorB.getX() + .5, doorB.getY() + 1, doorB.getZ() + .5);
            LimboManager.sweep(server); // o caso que quebrava a iteracao do HashMap
            helper.assertTrue(first.level() == server.overworld() && second.level() == server.overworld(),
                    "Dois exilados devem atravessar na mesma varredura com Portais ativo");
            helper.assertTrue(!first.hasEffect(MobEffects.DARKNESS)
                            && !second.hasEffect(MobEffects.DARKNESS),
                    "Sair do Limbo deve remover a escuridao ambiental");
            helper.assertTrue(data.active().isEmpty(), "Travessia confirmada fecha ambos os registros");
            helper.assertTrue(data.forgottenExits(first.getUUID()) == 1 && LivesManager.livesOf(server, first.getUUID()) == 1,
                    "Porta devolve exatamente uma vida e registra uma saida");
            helper.assertTrue(limbo.getBlockState(doorA).isAir(), "Moldura deve sumir depois da travessia");

            enter(first, limbo, 0);
            ExileRecord canceled = data.record(first.getUUID());
            canceled.arm(1, ForgottenDoor.walkedCm(first));
            canceled.setDoorPos(first.blockPosition());
            Consumer<EntityTravelToDimensionEvent> veto = event -> {
                if (event.getEntity() == first && event.getDimension() == Level.OVERWORLD) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTravelToDimensionEvent.class, veto);
            try {
                LimboManager.sweep(server);
                helper.assertTrue(first.level() == limbo && LivesManager.livesOf(server, first.getUUID()) == 0,
                        "Viagem cancelada mantem jogador exilado e reverte a vida");
                helper.assertTrue(data.record(first.getUUID()) == canceled && data.forgottenExits(first.getUUID()) == 1,
                        "Viagem cancelada nao fecha registro nem inventa sucesso");
                helper.assertTrue(!ForgottenDoor.consumeAuthorization(first, Level.OVERWORLD), "Autorizacao nao pode vazar");
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }

            LimboManager.markRescueAttempt(server, first.getUUID());
            helper.assertTrue(!canceled.doorArmed() && canceled.doorPos() == null, "Tentativa desliga porta armada");
            LimboManager.setDeadline(server, first.getUUID(), 0);
            LimboManager.sweep(server);
            LimboManager.sweep(server);
            long expirations = AuditLog.tail(server, 200).stream()
                    .filter(line -> line.contains("PRAZO_VENCIDO") && line.contains(first.getUUID().toString())).count();
            helper.assertTrue(expirations == 1, "Prazo zero audita uma vez");

            LivesManager.addLives(server, first.getUUID(), 1);
            LimboManager.sweep(server);
            helper.assertTrue(first.level() == server.overworld() && LivesManager.livesOf(server, first.getUUID()) == 2,
                    "Resgate pela staff retorna ao overworld com duas vidas");
            helper.assertTrue(!data.isTracked(first.getUUID()), "Resgate fecha o registro");

            enter(second, limbo, 40);
            UUID offlineId = second.getUUID();
            server.getPlayerList().remove(second);
            LivesManager.addLives(server, offlineId, 1);
            long offlineDeadline = data.record(offlineId).remainingMillis();
            LimboManager.sweep(server);
            helper.assertTrue(data.record(offlineId).remainingMillis() == offlineDeadline,
                    "Resgate offline espera login sem consumir prazo");
            second = player(helper, "LimboTest2", offlineId);
            second.hasChangedDimension();
            LimboManager.sweep(server);
            helper.assertTrue(second.level() == server.overworld() && !data.isTracked(offlineId)
                            && LivesManager.livesOf(server, offlineId) == 2,
                    "Resgate offline completa apos reconectar");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(first);
            server.getPlayerList().remove(second);
        }
    }

    /** Exercitado de verdade quando o run recebe os jars opcionais de PlayerRevive e CreativeCore. */
    private static void verifyPlayerReviveCompatibility(GameTestHelper helper, ServerPlayer player)
            throws Exception {
        if (!ModList.get().isLoaded("playerrevive")) return;

        Class<?> reviveEvents = Class.forName(
                "team.creative.playerrevive.server.ReviveEventServer");
        Method isReviveActive = reviveEvents.getMethod("isReviveActive",
                net.minecraft.world.entity.Entity.class);
        helper.assertTrue(!(boolean) isReviveActive.invoke(null, player),
                "PlayerRevive deve ignorar quem ja esta exilado");

        Class<?> reviveServer = Class.forName(
                "team.creative.playerrevive.server.PlayerReviveServer");
        Method startBleeding = reviveServer.getMethod("startBleeding",
                net.minecraft.world.entity.player.Player.class,
                net.minecraft.world.damagesource.DamageSource.class);
        Method getBleeding = reviveServer.getMethod("getBleeding",
                net.minecraft.world.entity.player.Player.class);
        startBleeding.invoke(null, player, player.damageSources().genericKill());

        Object bleeding = getBleeding.invoke(null, player);
        Method isBleeding = bleeding.getClass().getMethod("isBleeding");
        helper.assertTrue((boolean) isBleeding.invoke(bleeding),
                "Pre-condicao: estado antigo de sangramento deve existir");

        PlayerReviveCompat.clearIfExiled(player);
        helper.assertTrue(!(boolean) isBleeding.invoke(bleeding)
                        && !player.getPersistentData().getBoolean("playerrevive:bleeding"),
                "Login no Limbo deve limpar estado antigo e sincronizar o PlayerRevive");
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return player(helper, name, UUID.randomUUID());
    }

    private static ServerPlayer player(GameTestHelper helper, String name, UUID id) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // O helper vanilla pula a negociacao; anunciamos o canal que este cliente simulado aceita.
        var channels = ChannelAttributes.getOrCreateAdHocChannels(connection);
        channels.add(SyncLivesPayload.TYPE.id());
        // O CreativeCore numera os wrappers conforme a ordem de registro dos mods. O cliente
        // embutido nao faz a negociacao real, entao anuncia a faixa reservada a este run.
        if (ModList.get().isLoaded("creativecore")) {
            for (int packetId = 0; packetId < 64; packetId++) {
                channels.add(ResourceLocation.fromNamespaceAndPath("creativecore", packetId + "s"));
            }
        }
        if (ModList.get().isLoaded("playerrevive")) {
            for (int packetId = 0; packetId < 64; packetId++) {
                channels.add(ResourceLocation.fromNamespaceAndPath("playerrevive", packetId + "s"));
            }
        }
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void enter(ServerPlayer player, ServerLevel limbo, int x) {
        limbo.getChunk(x >> 4, 0);
        limbo.getChunk(x >> 4, 1);
        ForgottenDoor.authorize(player, limbo.dimension());
        try {
            player.changeDimension(new DimensionTransition(limbo, new Vec3(x + .5, 5, .5), Vec3.ZERO,
                    0, 0, DimensionTransition.DO_NOTHING));
        } finally {
            ForgottenDoor.clear();
        }
        player.hasChangedDimension();
        LivesManager.setLives(player.server, player.getUUID(), 0);
        LimboManager.openIfNeeded(player);
        LimboManager.setDeadline(player.server, player.getUUID(), 3_600_000L);
    }
}
