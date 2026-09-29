package com.aurorion.essentials.fakename;

import com.aurorion.essentials.server.FakeNameData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.TreeSet;
import java.util.UUID;

/**
 * O argumento "pessoa" dos comandos de staff.
 *
 * <p>Ninguem decora UUID, e num servidor de RP a staff quase nunca sabe o nick da Mojang de ninguem:
 * sabe o nome do personagem, que e o que aparece no chat, na tab e sobre a cabeca. Por isso o
 * argumento aceita, nesta ordem:
 * <ol>
 *   <li>a UUID da conta, se alguem tiver;</li>
 *   <li>o nome de personagem de quem esta online agora ({@link FakeNameRegistry});</li>
 *   <li>o nick da Mojang de quem esta online;</li>
 *   <li>o nome de personagem de quem esta OFFLINE ({@link FakeNameData}, que fica em disco);</li>
 *   <li>o nick da Mojang de quem esta offline (cache de perfis do vanilla).</li>
 * </ol>
 * Tudo sincrono e sem disco, na thread do servidor. O argumento e {@code StringArgumentType.string()}
 * porque nome com espaco existe ("Bella Noob"); o Tab ja sugere com aspas.
 */
public final class CharacterTarget {
    private CharacterTarget() {
    }

    /** Nomes de personagem conhecidos e nicks de quem esta online, com aspas quando preciso. */
    public static final SuggestionProvider<CommandSourceStack> SUGGESTIONS = (context, builder) -> {
        MinecraftServer server = context.getSource().getServer();
        TreeSet<String> names = FakeNameLookup.plainNames(FakeNameData.get(server).allRaw());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) names.add(online.getGameProfile().getName());
        return SharedSuggestionProvider.suggest(names.stream().map(StringArgumentType::escapeIfRequired), builder);
    };

    /** A conta de quem atende por {@code query}, ou {@code null} se ninguem conhecido. */
    @Nullable
    public static UUID resolve(MinecraftServer server, String query) {
        try {
            return UUID.fromString(query);
        } catch (IllegalArgumentException notAnId) {
            // Segue para os nomes.
        }
        String wanted = FakeNameLookup.key(query);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            FakeName fakeName = FakeNameRegistry.get(online.getUUID());
            if (fakeName != null && FakeNameLookup.key(fakeName.raw()).equals(wanted)) return online.getUUID();
        }
        ServerPlayer byNick = server.getPlayerList().getPlayerByName(query);
        if (byNick != null) return byNick.getUUID();

        UUID offline = FakeNameLookup.find(FakeNameData.get(server).allRaw(), query);
        if (offline != null) return offline;

        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(query)
                .filter(profile -> realProfile(server, profile))
                .map(GameProfile::getId).orElse(null);
    }

    /**
     * O perfil do cache e de uma conta de verdade? Em modo offline ({@code online-mode=false}, ou atras
     * de proxy), o vanilla nao responde "ninguem" para um nome desconhecido: inventa um perfil com a
     * UUID offline do nome. Sem este filtro, qualquer nome digitado errado "existia" — o
     * {@code /gritar} ligava o grito numa conta que nao existe, e o {@code /deathhistory} nunca chegava
     * a procurar nos nomes antigos das mortes.
     *
     * <p>Uma conta real com a UUID offline so existe se ja entrou neste mundo, e entao tem playerdata.
     * Com outra UUID (a da Mojang, repassada por proxy), o perfil veio de quem entrou e e real. Mesma
     * regra do {@code AltLogin} do aurorion-personagem.
     */
    private static boolean realProfile(MinecraftServer server, GameProfile profile) {
        if (server.usesAuthentication()) return true;
        UUID offline = UUIDUtil.createOfflinePlayerUUID(profile.getName());
        if (!offline.equals(profile.getId())) return true;
        return Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(offline + ".dat"));
    }

    /** Como a staff conhece a conta: o nome do personagem, senao o nick, senao a UUID. */
    public static String displayName(MinecraftServer server, UUID account) {
        FakeName online = FakeNameRegistry.get(account);
        if (online != null && !online.plain().isBlank()) return online.plain();
        String raw = FakeNameData.get(server).getRaw(account);
        if (raw != null) {
            String plain = LegacyColorCodes.parse(raw).getString().trim();
            if (!plain.isEmpty()) return plain;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(account);
        if (player != null) return player.getGameProfile().getName();
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(account);
            if (profile.isPresent()) return profile.get().getName();
        }
        return account.toString();
    }
}
