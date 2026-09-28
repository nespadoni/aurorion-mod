package com.aurorion.essentials.server;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneRetirements;
import com.aurorion.essentials.network.RetiredPhoneContactsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

/**
 * Leva a lista de {@link PhoneRetirements} aos clientes: inteira no login, e so a entrada nova para
 * quem esta online na hora do reset.
 *
 * <p>So para quem tem o canal: um cliente sem o essentials atualizado nao recebe nada (e mantem a
 * agenda antiga) em vez de derrubar a conexao com um pacote desconhecido.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneRetirementSync {
    private PhoneRetirementSync() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        Map<UUID, String> active = PhoneRetirements.get(player.getServer()).active(System.currentTimeMillis());
        if (!active.isEmpty()) send(player, new RetiredPhoneContactsPayload(active));
    }

    /** Chamado no reset de personagem, com a conta ja fora do servidor. */
    public static void retire(MinecraftServer server, UUID previousCharacter, String nick) {
        if (previousCharacter == null || nick == null || nick.isBlank()) return;
        PhoneRetirements.get(server).add(previousCharacter, nick, System.currentTimeMillis());
        RetiredPhoneContactsPayload payload = new RetiredPhoneContactsPayload(Map.of(previousCharacter, nick));
        for (ServerPlayer online : server.getPlayerList().getPlayers()) send(online, payload);
    }

    private static void send(ServerPlayer player, RetiredPhoneContactsPayload payload) {
        if (player.connection != null && player.connection.hasChannel(RetiredPhoneContactsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
