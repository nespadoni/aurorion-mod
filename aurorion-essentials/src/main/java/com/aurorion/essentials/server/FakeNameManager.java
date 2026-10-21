package com.aurorion.essentials.server;

import com.aurorion.core.character.CharacterData;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import com.aurorion.essentials.fakename.NameKey;
import com.aurorion.essentials.network.SyncFakeNamesPayload;
import com.aurorion.essentials.network.UpdateFakeNamePayload;
import com.aurorion.essentials.tab.TabNames;
import com.aurorion.essentials.voice.VoiceNameSync;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ponto unico de escrita dos nomes falsos: valida, persiste, atualiza o cache e sincroniza.
 *
 * <p>Toda entrada vinda de comando e hostil ate validada aqui — nome vazio, longo demais, com
 * caractere invisivel, ou igual a um nome que ja pertence a outra conta sao rejeitados antes de
 * qualquer escrita. Nome exibido nao e so cosmetico: o celular resolve o destinatario do PIX e do
 * e-mail pelo nome que mostra, entao vestir o nome de outra pessoa era receber o dinheiro dela.</p>
 *
 * <p>"Igual" e pelo {@link NameKey} (sem cor, acento, caixa, espaco extra, caractere invisivel nem
 * letra cirilica disfarcada), e "nome de outra conta" e tudo que o servidor conhece, online <b>ou
 * offline</b>: nome falso gravado ({@link FakeNameData}), personagem atual, morto ou reservado
 * ({@link CharacterData}), e nick da Mojang de quem esta online ou no cache de perfis. So comparar com
 * quem estava online deixava qualquer um adotar o nome de quem saiu ha cinco minutos.</p>
 *
 * <p>O nome do <b>proprio personagem</b> da conta sempre vence: o {@code aurorion_personagem} ja
 * garante que ele e unico entre personagens. Se um nome falso antigo de outra conta coincidir com
 * ele, quem perde e o nome falso ({@link #evictImpostors}).</p>
 */
public final class FakeNameManager {

    public enum Result {
        OK,
        BLANK,
        TOO_LONG,
        HIDDEN_CHARACTERS,
        IMPERSONATION
    }

    private FakeNameManager() {
    }

    public static Result set(ServerPlayer target, String rawInput) {
        FakeName candidate = FakeName.parse(rawInput);
        String key = NameKey.of(candidate.plain());

        if (candidate.plain().isBlank() || key.isEmpty()) return Result.BLANK;
        if (candidate.plain().length() > FakeName.MAX_LENGTH) return Result.TOO_LONG;
        if (NameKey.hasHiddenCharacters(candidate.plain())) return Result.HIDDEN_CHARACTERS;

        MinecraftServer server = target.getServer();
        if (!claim(server, target.getUUID(), key)) return Result.IMPERSONATION;

        apply(server, target.getUUID(), candidate);
        return Result.OK;
    }

    public static void clear(ServerPlayer target) {
        apply(target.getServer(), target.getUUID(), null);
    }

    /**
     * A conta pode usar este nome? Quando e o nome do proprio personagem, ainda tira o nome falso de
     * quem estiver usando ele indevidamente.
     */
    private static boolean claim(MinecraftServer server, UUID self, String key) {
        boolean ownCharacter = ownsCharacterName(server, self, key);
        // Com o nome do proprio personagem, outro nome falso igual nao bloqueia: o impostor e ele.
        if (reservedByOthers(server, self, key, !ownCharacter)) return false;
        if (ownCharacter) evictImpostors(server, self, key);
        return true;
    }

    /** O personagem atual da conta, ou o que ela tem reservado num reset em andamento, tem este nome. */
    private static boolean ownsCharacterName(MinecraftServer server, UUID account, String key) {
        CharacterData characters = CharacterData.get(server);
        CharacterData.Character current = characters.find(account);
        if (current != null && sameName(current, key)) return true;
        CharacterData.Pending pending = characters.pending(account);
        return pending != null && sameName(pending.next(), key);
    }

    /**
     * Algum nome que pertence a outra conta tem esta chave?
     *
     * <p>Roda so quando um nome muda e no login: varre os personagens e nomes gravados (algumas
     * centenas de entradas), sem rede — o cache de perfis e consultado por UUID, o que nunca chama a
     * Mojang (a consulta por nome chamaria, e travaria a thread do servidor).</p>
     */
    private static boolean reservedByOthers(MinecraftServer server, UUID self, String key, boolean includeFakeNames) {
        Set<UUID> accounts = new HashSet<>();

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(self)) continue;
            if (NameKey.of(other.getGameProfile().getName()).equals(key)) return true;
            if (includeFakeNames) {
                FakeName otherFake = FakeNameRegistry.get(other.getUUID());
                if (otherFake != null && NameKey.of(otherFake.plain()).equals(key)) return true;
            }
        }

        for (Map.Entry<UUID, String> entry : FakeNameData.get(server).allRaw().entrySet()) {
            if (entry.getKey().equals(self)) continue;
            accounts.add(entry.getKey());
            if (includeFakeNames && NameKey.of(entry.getValue()).equals(key)) return true;
        }

        CharacterData characters = CharacterData.get(server);
        for (Map.Entry<UUID, CharacterData.Character> entry : characters.characters().entrySet()) {
            if (entry.getKey().equals(self)) continue;
            accounts.add(entry.getKey());
            if (sameName(entry.getValue(), key)) return true;
        }
        // O nome de quem morreu continua reservado: vestir o nome do morto e golpe do mesmo jeito.
        for (CharacterData.Archived archived : characters.history().values()) {
            if (archived.account().equals(self)) continue;
            accounts.add(archived.account());
            if (sameName(archived.character(), key)) return true;
        }
        for (CharacterData.Pending pending : characters.pendingResets().values()) {
            if (pending.account().equals(self)) continue;
            if (sameName(pending.next(), key)) return true;
        }

        // Nick de quem esta offline: o telefone entrega PIX e e-mail pelo nick.
        var profiles = server.getProfileCache();
        if (profiles != null) {
            for (UUID account : accounts) {
                var profile = profiles.get(account);
                if (profile.isPresent() && NameKey.of(profile.get().getName()).equals(key)) return true;
            }
        }
        return false;
    }

    private static boolean sameName(CharacterData.Character character, String key) {
        return character.named() && NameKey.of(character.fullName()).equals(key);
    }

    /**
     * Tira o nome falso de toda outra conta que esteja usando o nome de personagem de {@code owner}.
     * So acontece com dado antigo, gravado antes de a reserva valer para quem esta offline.
     */
    private static void evictImpostors(MinecraftServer server, UUID owner, String key) {
        for (Map.Entry<UUID, String> entry : FakeNameData.get(server).allRaw().entrySet()) {
            UUID other = entry.getKey();
            if (other.equals(owner) || !NameKey.of(entry.getValue()).equals(key)) continue;
            if (ownsCharacterName(server, other, key)) continue;
            AurorionEssentials.LOGGER.warn("Nome falso '{}' da conta {} removido: e o nome do personagem da conta {}",
                    entry.getValue(), other, owner);
            apply(server, other, null);
        }
    }

    private static void apply(MinecraftServer server, UUID player, @Nullable FakeName fakeName) {
        boolean changed = FakeNameData.get(server).setRaw(player, fakeName == null ? null : fakeName.raw());
        if (!changed) return;

        if (fakeName != null) {
            FakeNameRegistry.put(player, fakeName);
        } else {
            FakeNameRegistry.remove(player);
        }

        sendToModded(server, new UpdateFakeNamePayload(player, Optional.ofNullable(fakeName).map(FakeName::raw)));
        PhoneNameDirectorySync.changed(server, player, fakeName == null ? null : fakeName.plain());
        forgetCachedDisplayName(server, player);
        refreshTabList(server, player);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) VoiceNameSync.refresh(online);
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

    /**
     * Manda para quem acabou de entrar o snapshot atual, e avisa os demais sobre o recem-chegado.
     *
     * <p>O nome gravado e conferido de novo aqui, antes de valer: ele pode ter sido gravado antes de a
     * reserva contar quem esta offline, ou outra conta pode ter ganho um personagem com esse nome
     * enquanto esta estava fora. Nome que hoje seria recusado no comando e apagado (e logado).</p>
     */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        FakeNameData data = FakeNameData.get(server);

        String ownRaw = data.getRaw(player.getUUID());
        if (ownRaw != null && !stillValid(server, player.getUUID(), ownRaw)) {
            AurorionEssentials.LOGGER.warn("Nome falso '{}' de {} removido no login: coincide com o nome de outra conta",
                    ownRaw, player.getGameProfile().getName());
            data.setRaw(player.getUUID(), null);
            PhoneNameDirectorySync.changed(server, player.getUUID(), null);
            ownRaw = null;
        }
        if (ownRaw != null) FakeNameRegistry.put(player.getUUID(), FakeName.parse(ownRaw));

        Map<UUID, String> online = new HashMap<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            String raw = data.getRaw(other.getUUID());
            if (raw != null) online.put(other.getUUID(), raw);
        }
        if (hasChannel(player, SyncFakeNamesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SyncFakeNamesPayload(online));
        }

        // A cor da casa tambem so e conhecida agora: a entrada da tab que o vanilla mandou no login
        // saiu sem ela.
        boolean houseColor = TabNames.track(player);
        if (ownRaw != null) {
            sendToModded(server, new UpdateFakeNamePayload(player.getUUID(), Optional.of(ownRaw)));
            // O nome so entra no registry agora, entao o que tiver sido calculado durante o login
            // ainda e o nick da Mojang.
            player.refreshDisplayName();
        }
        if (ownRaw != null || houseColor) refreshTabList(server, player.getUUID());
        // Fora do 'if': o Voice Chat pode ter criado o estado da pessoa antes deste login, ainda com o
        // nick, e quem nao tem nome de personagem tambem precisa do estado conferido.
        VoiceNameSync.refresh(player);
    }

    private static boolean stillValid(MinecraftServer server, UUID account, String raw) {
        String plain = FakeName.parse(raw).plain();
        String key = NameKey.of(plain);
        if (key.isEmpty() || NameKey.hasHiddenCharacters(plain)) return false;
        return claim(server, account, key);
    }

    /**
     * Os payloads deste mod sao opcionais ({@code registrar.optional()}): cliente sem o mod entra no
     * servidor, e mandar a ele um payload que ele nao registrou derruba a conexao. Como no
     * {@code PhoneNameDirectorySync}, so recebe quem anunciou o canal.
     */
    private static void sendToModded(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (hasChannel(online, payload.type())) PacketDistributor.sendToPlayer(online, payload);
        }
    }

    private static boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }

    /** So limpa o cache em memoria — o que esta em disco continua valendo se o jogador voltar. */
    public static void onPlayerLeave(ServerPlayer player) {
        FakeNameRegistry.remove(player.getUUID());
        TabNames.forget(player.getUUID());
    }
}
