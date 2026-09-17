package com.aurorion.ethereal.gametest;

import com.aurorion.ethereal.ceremony.BindingRite;
import com.aurorion.ethereal.ceremony.CeremonyManager;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.network.RitePayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.UUID;

@Mod("aurorion_ethereal_tests")
@GameTestHolder("aurorion_ethereal_tests")
@PrefixGameTestTemplate(false)
public final class CeremonyGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void commandedRevealAndAudience(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        TestPlayer selected = player(helper, "EtherealChosen", 0);
        TestPlayer viewer = player(helper, "EtherealViewer", 80);
        TestPlayer outside = player(helper, "EtherealOutside", 120);
        var nyx = ResourceLocation.parse("aurorion_ethereal:nyx");
        var ignivar = ResourceLocation.parse("aurorion_ethereal:ignivar");
        try {
            selected.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 1));
            var dispatcher = server.getCommands().getDispatcher();
            var staff = server.createCommandSourceStack();
            // Seletor, e nao o nick cru: o GameProfileArgument resolve nome offline pelo
            // getProfileCache(), que o GameTestServer nao tem (fica null). Em producao o nick
            // funciona; aqui so o seletor chega na lista de jogadores. O que o teste cobre e o
            // comando, nao a traducao nick -> perfil, que e do vanilla.
            helper.assertTrue(dispatcher.execute(
                    "casa definir @a[name=EtherealChosen,limit=1] aurorion_ethereal:nyx", staff) == 1,
                    "Staff cadastra a casa");
            helper.assertTrue(!BindingRite.isBusy(), "Cadastrar nao inicia cena");
            helper.assertTrue(nyx.equals(HouseManager.houseIdOf(server, selected.player.getUUID())),
                    "Cadastro persiste antes da revelacao");
            clearPackets(viewer.channel);
            clearPackets(outside.channel);
            helper.assertTrue(dispatcher.execute("casa cerimonia EtherealChosen", staff) == 1,
                    "Comando sem casa revela o cadastro do site");
            RitePayload start = lastRite(viewer.channel);
            helper.assertTrue(start != null && start.active() && start.color() == 0x439CFF,
                    "Plateia a 80 blocos recebe inicio com a paleta de Nyx");
            helper.assertTrue(lastRite(outside.channel) == null, "Fora do raio nao recebe cena nem musica");
            helper.assertTrue(CeremonyManager.bind(server, viewer.player.getUUID(), ignivar)
                            == CeremonyManager.Result.ALREADY_RUNNING,
                    "Uma pessoa por vez, sem sobrepor trilhas");
            helper.assertTrue(HouseManager.houseIdOf(server, viewer.player.getUUID()) == null,
                    "Recusar a segunda cena nao atribui uma casa por engano");
            viewer.player.setPos(selected.player.getX() + 150, selected.player.getY(), selected.player.getZ());
            helper.assertTrue(dispatcher.execute(
                    "casa cerimonia cancelar @a[name=EtherealChosen,limit=1]", staff) == 1,
                    "Staff cancela a cena");
            RitePayload end = lastRite(viewer.channel);
            helper.assertTrue(end != null && !end.active(), "Cancelamento alcanca quem ja se afastou");
            helper.assertTrue(nyx.equals(HouseManager.houseIdOf(server, selected.player.getUUID())),
                    "Cancelar preserva a casa");
            helper.assertTrue(selected.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1
                            && !selected.player.isInvulnerable(),
                    "Cerimonia nao remove efeitos preexistentes nem altera invulnerabilidade");
            helper.assertTrue(dispatcher.execute("casa cerimonia EtherealChosen aurorion_ethereal:ignivar", staff) == 1,
                    "Comando com casa atribui e revela");
            for (int i = 0; i < BindingRite.TOTAL_TICKS; i++) BindingRite.tick(server);
            helper.assertTrue(!BindingRite.isBusy(), "Cena termina e libera a proxima pessoa");
            helper.assertTrue(ignivar.equals(HouseManager.houseIdOf(server, selected.player.getUUID())),
                    "Final preserva atribuicao");

            UUID offline = UUID.randomUUID();
            HouseManager.assign(server, offline, nyx);
            helper.assertTrue(CeremonyManager.bind(server, offline, ignivar) == CeremonyManager.Result.OFFLINE
                            && nyx.equals(HouseManager.houseIdOf(server, offline)),
                    "Revelacao offline falha sem mudar cadastro nem agendar no login");
            HouseManager.clear(server, offline);

            CeremonyManager.bind(server, selected.player.getUUID(), nyx);
            server.getPlayerList().remove(selected.player);
            BindingRite.tick(server);
            helper.assertTrue(!BindingRite.isBusy()
                            && nyx.equals(HouseManager.houseIdOf(server, selected.player.getUUID())),
                    "Desconexao encerra a cena e preserva o vinculo");
            helper.succeed();
        } finally {
            BindingRite.clear();
            for (TestPlayer test : new TestPlayer[]{selected, viewer, outside}) {
                if (server.getPlayerList().getPlayer(test.player.getUUID()) == test.player) {
                    server.getPlayerList().remove(test.player);
                }
                HouseManager.clear(server, test.player.getUUID());
                test.channel.finishAndReleaseAll();
            }
        }
    }

    @GameTest(template = "empty")
    public static void datapackAndNetworkPalette(GameTestHelper helper) {
        var house = HouseCatalog.get(ResourceLocation.parse("aurorion_ethereal:aetheris"));
        helper.assertTrue(house != null && house.color() == 0xA567F4 && house.ceremony().secondary() == 0x100B19,
                "Codec real carrega Aetheris roxa e preta");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            House.STREAM_CODEC.encode(buffer, house);
            House copy = House.STREAM_CODEC.decode(buffer);
            helper.assertTrue(copy.ceremony().equals(house.ceremony()) && copy.color() == house.color(),
                    "Catalogo enviado ao cliente preserva paleta e musica");
            RitePayload payload = new RitePayload(UUID.randomUUID(), 71, true,
                    helper.getLevel().dimension().location(), helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO),
                    100, house.name(), house.motto(), house.color(), house.ceremony());
            buffer.clear();
            RitePayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(RitePayload.STREAM_CODEC.decode(buffer)),
                    "Snapshot do rito preserva identidade, ancora, fase, texto e trilha");
            helper.succeed();
        } finally {
            buffer.release();
        }
    }

    private record TestPlayer(ServerPlayer player, EmbeddedChannel channel) {}

    private static TestPlayer player(GameTestHelper helper, String name, int offset) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        ChannelAttributes.getOrCreateAdHocChannels(connection).add(RitePayload.TYPE.id());
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        var at = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(at.getX() + offset + .5, at.getY() + 2, at.getZ() + .5);
        return new TestPlayer(player, channel);
    }

    private static void clearPackets(EmbeddedChannel channel) { while (channel.readOutbound() != null) {} }

    private static RitePayload lastRite(EmbeddedChannel channel) {
        channel.runPendingTasks();
        RitePayload found = null;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof RitePayload rite) {
                found = rite;
            }
        }
        return found;
    }
}
