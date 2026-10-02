package com.aurorion.personagem.alt;

import com.aurorion.core.character.AltData;
import com.aurorion.core.character.CharacterData;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.config.CreationConfig;
import com.aurorion.personagem.network.SwitchingCharacterPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O segundo personagem da staff, do lado do servidor: quem entra como quem, e a troca.
 *
 * <h2>Como o alt vira outro jogador</h2>
 *
 * <p>No login, depois da autenticacao da Mojang e antes de qualquer checagem do vanilla, o perfil da
 * conexao e trocado pelo do alt ({@link #resolve}, chamado pelo {@code LoginProfileMixin}). Ban,
 * whitelist, playerdata, OP e todos os mods passam a ver o alt — ver {@link AltData} para o porque
 * dessa escolha.
 *
 * <h2>Por que a troca desconecta</h2>
 *
 * <p>O UUID vale para a conexao inteira; trocar com a pessoa dentro do mundo quebraria metade dos
 * mods. Entao {@link #switchCharacter} grava qual personagem entra no proximo login, desconecta, e o
 * cliente com o mod reconecta sozinho ({@code ClientSwitch}).
 */
public final class AltLogin {
    /**
     * UUIDs de quem esta conectado como alt agora. Lido pelo mixin da sessao de chat, que roda na
     * thread de rede — por isso um conjunto concorrente, e nao uma consulta ao {@link AltData}.
     */
    private static final Set<UUID> ONLINE_ALTS = ConcurrentHashMap.newKeySet();

    private static final int NAME_ATTEMPTS = 1000;

    private AltLogin() {
    }

    /**
     * O perfil com que esta conexao entra. Roda na thread do servidor, no inicio de
     * {@code verifyLoginAndFinishConnectionSetup}.
     *
     * @return {@code real} quando nada muda, ou o perfil do alt
     */
    public static GameProfile resolve(MinecraftServer server, GameProfile real, Connection connection) {
        // Mundo local (singleplayer, LAN do anfitriao): nao ha conta de staff para separar.
        if (connection.isMemoryConnection() || !CreationConfig.ALT_ENABLED.get()) return real;

        AltData alts = AltData.get(server);
        AltData.Alt alt = alts.byOwner(real.getId());
        if (alt == null || !alt.active()) return real;

        PlayerList players = server.getPlayerList();
        // O ban e da pessoa: conta banida nao entra pelo alt. Devolver o perfil real deixa o vanilla
        // recusar com o motivo e o prazo do ban.
        if (players.getBans().isBanned(real)) return real;

        // Alt morto em definitivo e sem liberacao da staff seria recusado em todo login — e como o alt
        // e quem entra, a pessoa nunca mais chegaria a conta principal. Volta para a principal.
        CharacterData characters = CharacterData.get(server);
        if (characters.isDead(alt.altId()) && !characters.isAuthorized(alt.altId())
                && characters.pending(alt.altId()) == null) {
            alts.setActive(real.getId(), false);
            AurorionPersonagem.LOGGER.info("Alt {} de {} esta morto sem liberacao; entrando na conta principal.",
                    alt.altName(), real.getName());
            return real;
        }

        GameProfile swapped = new GameProfile(alt.altId(), alt.altName());
        if (server.getProfileCache() != null) server.getProfileCache().add(swapped);
        // As propriedades carregam a skin. O alt pode trocar a dele com o /skin do SkinRestorer.
        swapped.getProperties().putAll(real.getProperties());

        if (players.isUsingWhitelist() && players.isWhiteListed(real) && !players.isWhiteListed(swapped)) {
            players.getWhiteList().add(new UserWhiteListEntry(swapped));
        }

        ONLINE_ALTS.add(alt.altId());
        AurorionPersonagem.LOGGER.info("{} entra como o alt {} ({}).", real.getName(), alt.altName(), alt.altId());
        return swapped;
    }

    /** Conectado como alt agora? Seguro em qualquer thread. */
    public static boolean isOnlineAlt(UUID player) {
        return ONLINE_ALTS.contains(player);
    }

    public static void onLogout(UUID player) {
        ONLINE_ALTS.remove(player);
    }

    public static void reset() {
        ONLINE_ALTS.clear();
    }

    /** Pode criar alts: staff na conta principal, com a funcionalidade ligada. */
    public static boolean canCreate(ServerPlayer player) {
        AltData alts = AltData.get(player.server);
        return CreationConfig.ALT_ENABLED.get() && !alts.isAlt(player.getUUID()) && player.hasPermissions(2);
    }

    /** Cria o alt da conta de quem chamou. @return o alt, ou null se nenhum nome de perfil estava livre */
    @Nullable
    public static AltData.Alt create(ServerPlayer owner) {
        if (!canCreate(owner)) throw new IllegalStateException("Cannot create an alt from this account");
        MinecraftServer server = owner.server;
        AltData alts = AltData.get(server);

        String name = null;
        for (int attempt = 0; attempt < NAME_ATTEMPTS && name == null; attempt++) {
            String candidate = AltData.altNameOf(owner.getGameProfile().getName(), attempt);
            if (!profileNameTaken(server, candidate, alts)) name = candidate;
        }
        if (name == null) return null;

        AltData.Alt alt = alts.create(owner.getUUID(), name);
        if (server.getProfileCache() != null) {
            server.getProfileCache().add(new GameProfile(alt.altId(), alt.altName()));
        }
        PlayerList players = server.getPlayerList();
        if (players.isUsingWhitelist() && players.isWhiteListed(owner.getGameProfile())) {
            players.getWhiteList().add(new UserWhiteListEntry(new GameProfile(alt.altId(), alt.altName())));
        }
        AurorionPersonagem.LOGGER.info("{} criou o alt {} ({}).", owner.getGameProfile().getName(), name, alt.altId());
        return alt;
    }

    /**
     * O nome de perfil ja e de alguem? {@code GameProfileCache.get(nome)} consulta a Mojang quando o
     * nome nao esta no cache — trava a thread por um instante, mas so no {@code /personagem alt criar},
     * e e o que impede o alt de ganhar o nick de uma conta real.
     *
     * <p><b>Modo offline</b> ({@code online-mode=false}, ou atras de proxy): quando a Mojang nao
     * conhece o nome, o vanilla nao responde "ninguem" — inventa um perfil offline para ele
     * ({@code GameProfileCache.createUnknownProfile}). Tratar isso como "ocupado" fazia as dez
     * tentativas falharem sempre, e o comando respondia "Nenhum nome de perfil livre". Aqui o perfil
     * inventado nao conta; ver {@link #realProfile}.
     */
    private static boolean profileNameTaken(MinecraftServer server, String name, AltData alts) {
        if (alts.nameTaken(name) || server.getPlayerList().getPlayerByName(name) != null) return true;
        GameProfileCache cache = server.getProfileCache();
        return cache != null && cache.get(name).filter(profile -> realProfile(server, profile)).isPresent();
    }

    /**
     * O perfil que o cache devolveu e de uma conta de verdade, e nao um inventado pelo modo offline?
     *
     * <p>O inventado tem sempre a UUID offline do nome ({@code UUIDUtil.createOfflinePlayerUUID}). Uma
     * conta real com essa mesma UUID so existe se ja entrou neste mundo — e ai tem playerdata. Com
     * outra UUID (a da Mojang, repassada por um proxy), o perfil veio do cache de quem entrou e e real.
     * O {@code CharacterTarget} do essentials usa a mesma regra.
     */
    private static boolean realProfile(MinecraftServer server, GameProfile profile) {
        if (server.usesAuthentication()) return true;
        UUID offline = UUIDUtil.createOfflinePlayerUUID(profile.getName());
        if (!offline.equals(profile.getId())) return true;
        return Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(offline + ".dat"));
    }

    /**
     * Troca de personagem: grava quem entra no proximo login e desconecta.
     *
     * <p>Funciona de dentro do alt, que nao e OP: quem pode trocar e decidido pelo cadastro (este
     * UUID e dono de um alt, ou e um alt), e nunca pelo nivel de permissao.
     *
     * @return false (com a mensagem ja enviada) quando nao ha para onde trocar
     */
    public static boolean switchCharacter(ServerPlayer player) {
        return switchCharacter(player, null);
    }

    public static boolean switchCharacter(ServerPlayer player, @Nullable String requested) {
        MinecraftServer server = player.server;
        if (!CreationConfig.ALT_ENABLED.get()) {
            player.sendSystemMessage(Component.literal("A troca de personagem está desligada neste servidor."));
            return false;
        }

        AltData alts = AltData.get(server);
        boolean fromAlt = alts.isAlt(player.getUUID());
        UUID owner = fromAlt ? alts.ownerOf(player.getUUID()) : player.getUUID();
        AltData.Alt alt = owner == null ? null : alts.byOwner(owner);
        if (alt == null) {
            player.sendSystemMessage(Component.literal("Esta conta não tem um segundo personagem."));
            return false;
        }

        UUID target = fromAlt ? owner : alt.altId();
        if (requested != null) {
            if (requested.equalsIgnoreCase("principal")) {
                target = owner;
            } else {
                AltData.Alt chosen = alts.forOwner(owner).stream()
                        .filter(candidate -> candidate.altName().equalsIgnoreCase(requested)
                                || candidate.altId().toString().equalsIgnoreCase(requested)).findFirst().orElse(null);
                if (chosen == null) {
                    player.sendSystemMessage(Component.literal("Personagem não encontrado nesta conta. Use /personagem alt ver."));
                    return false;
                }
                target = chosen.altId();
            }
        }
        if (target.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Você já está neste personagem."));
            return false;
        }
        UUID previous = alt.active() ? alt.altId() : null;
        alts.select(owner, target.equals(owner) ? null : target);
        try {
            SavedDataAccess.flushAll(server);
        } catch (IOException failure) {
            alts.select(owner, previous);
            AurorionPersonagem.LOGGER.error("Nao consegui gravar a troca de personagem.", failure);
            player.sendSystemMessage(Component.literal("O servidor não conseguiu gravar a troca. Tente de novo."));
            return false;
        }

        AltData.Alt selected = alts.find(target);
        String targetName = characterName(server, target, selected == null ? null : selected.altName());
        AurorionPersonagem.LOGGER.info("{} troca de personagem: {} -> {}.",
                player.getGameProfile().getName(), player.getUUID(), target);

        if (player.connection.hasChannel(SwitchingCharacterPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new SwitchingCharacterPayload(targetName));
        }
        player.connection.disconnect(Component.literal(format(CreationConfig.SWITCHING.get(), targetName)));
        return true;
    }

    /** Um {@code %s} opcional. Texto de config nao pode derrubar a troca por formato errado. */
    private static String format(String template, String name) {
        try {
            return template.formatted(name);
        } catch (RuntimeException badFormat) {
            return template;
        }
    }

    /** Nome da conta (o nick da Mojang), para a staff. */
    public static String accountName(MinecraftServer server, UUID account) {
        return AltData.accountName(server, account);
    }

    /** O nome do personagem daquele UUID, ou o nome de perfil enquanto ele nao tem personagem. */
    public static String characterName(MinecraftServer server, UUID id, @Nullable String fallback) {
        CharacterData.Character character = CharacterData.get(server).find(id);
        if (character != null && character.named()) return character.fullName();
        if (fallback != null) return fallback;
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? id.toString() : cache.get(id).map(GameProfile::getName).orElse(id.toString());
    }
}
