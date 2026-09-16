package com.aurorion.essentials.server;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import com.aurorion.essentials.network.SyncFakeNamesPayload;
import com.aurorion.essentials.network.UpdateFakeNamePayload;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto unico de escrita dos nomes falsos: valida, persiste, atualiza o cache e sincroniza.
 *
 * <p>Toda entrada vinda de comando e hostil ate validada aqui — nome vazio, longo demais, ou
 * igual ao nome (real ou falso) de outro jogador online sao rejeitados antes de qualquer
 * escrita, porque cosmetico hoje (uma troca de nome) e vetor de abuso amanha (se phishing/scam
 * se passando por outro jogador virar problema no servidor).</p>
 */
public final class FakeNameManager {

    public enum Result {
        OK,
        BLANK,
        TOO_LONG,
        IMPERSONATION
    }

    private FakeNameManager() {
    }

    public static Result set(ServerPlayer target, String rawInput) {
        FakeName candidate = FakeName.parse(rawInput);

        if (candidate.plain().isBlank()) return Result.BLANK;
        if (candidate.plain().length() > FakeName.MAX_LENGTH) return Result.TOO_LONG;
        if (collidesWithSomeoneElse(target, candidate.plain())) return Result.IMPERSONATION;

        apply(target.getServer(), target.getUUID(), candidate);
        return Result.OK;
    }

    public static void clear(ServerPlayer target) {
        apply(target.getServer(), target.getUUID(), null);
    }

    private static boolean collidesWithSomeoneElse(ServerPlayer setter, String plain) {
        for (ServerPlayer other : setter.getServer().getPlayerList().getPlayers()) {
            if (other.getUUID().equals(setter.getUUID())) continue;

            if (other.getGameProfile().getName().equalsIgnoreCase(plain)) return true;

            FakeName otherFake = FakeNameRegistry.get(other.getUUID());
            if (otherFake != null && otherFake.plain().equalsIgnoreCase(plain)) return true;
        }
        return false;
    }

    private static void apply(MinecraftServer server, UUID player, @Nullable FakeName fakeName) {
        boolean changed = FakeNameData.get(server).setRaw(player, fakeName == null ? null : fakeName.raw());
        if (!changed) return;

        if (fakeName != null) {
            FakeNameRegistry.put(player, fakeName);
        } else {
            FakeNameRegistry.remove(player);
        }

        PacketDistributor.sendToAllPlayers(new UpdateFakeNamePayload(player, Optional.ofNullable(fakeName).map(FakeName::raw)));
        forgetCachedDisplayName(server, player);
        refreshTabList(server, player);
    }

    /**
     * Derruba o {@code displayname} que o NeoForge guarda dentro do proprio {@code Player}.
     *
     * <p>Sobrescrever {@code getName()} no mixin nao basta: {@code Player#getDisplayName()} calcula
     * o nome <b>uma vez</b> e guarda num campo, que so zera em {@code refreshDisplayName()}. Quem
     * chamasse primeiro — e no login sempre chama alguem — congelava o nick da Mojang, e dali em
     * diante toda mensagem construida sobre {@code getDisplayName()} (anuncio de conquista, retorno
     * de comando, mensagem de morte, e a maior parte dos mods) mostrava o nick real enquanto a
     * plaqueta sobre a cabeca, que le {@code getName()}, ja mostrava o nome do personagem.</p>
     */
    private static void forgetCachedDisplayName(MinecraftServer server, UUID player) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
        if (serverPlayer != null) serverPlayer.refreshDisplayName();
    }

    /**
     * Reenvia a entrada da tab list para todo mundo. O nome exibido nela vai embutido no
     * {@code ClientboundPlayerInfoUpdatePacket} no momento do envio — so sobrescrever o metodo
     * no mixin nao alcanca quem ja recebeu o pacote antigo, entao o refresh precisa ser explicito.
     */
    private static void refreshTabList(MinecraftServer server, UUID player) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
        if (serverPlayer == null) return;

        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(serverPlayer)));
    }

    /** Manda para quem acabou de entrar o snapshot atual, e avisa os demais sobre o recem-chegado. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        FakeNameData data = FakeNameData.get(server);

        String ownRaw = data.getRaw(player.getUUID());
        if (ownRaw != null) FakeNameRegistry.put(player.getUUID(), FakeName.parse(ownRaw));

        Map<UUID, String> online = new HashMap<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            String raw = data.getRaw(other.getUUID());
            if (raw != null) online.put(other.getUUID(), raw);
        }
        PacketDistributor.sendToPlayer(player, new SyncFakeNamesPayload(online));

        if (ownRaw != null) {
            PacketDistributor.sendToAllPlayers(new UpdateFakeNamePayload(player.getUUID(), Optional.of(ownRaw)));
            // O nome so entra no registry agora, entao o que tiver sido calculado durante o login
            // ainda e o nick da Mojang.
            player.refreshDisplayName();
            refreshTabList(server, player.getUUID());
        }
    }

    /** So limpa o cache em memoria — o que esta em disco continua valendo se o jogador voltar. */
    public static void onPlayerLeave(ServerPlayer player) {
        FakeNameRegistry.remove(player.getUUID());
    }
}
