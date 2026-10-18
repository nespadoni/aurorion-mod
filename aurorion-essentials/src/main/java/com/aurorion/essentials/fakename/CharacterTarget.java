package com.aurorion.essentials.fakename;

import com.aurorion.essentials.server.FakeNameData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

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
 * Identidades locais são resolvidas na thread do servidor; a consulta por nick offline é assíncrona.
 * O argumento e {@code StringArgumentType.string()}
 * porque nome com espaco existe ("Bella Noob"); o Tab ja sugere com aspas.
 */
public final class CharacterTarget {
    /**
     * Os nomes gravados ja sem cor, para o Tab e para a busca por nome. O Tab roda a cada tecla
     * digitada, e sem isto cada tecla copiava o mapa e decodificava as cores de todos os nomes ja
     * usados no servidor. Refeito so quando {@link FakeNameData#version()} muda (um {@code setRaw}) ou
     * o mundo troca. Thread do servidor apenas, como o proprio {@link FakeNameData}.
     *
     * @param byKey a forma comparavel ({@link FakeNameLookup#key}) -> conta; com mais de uma conta no
     *              mesmo nome, a menor UUID, a mesma regra de {@link FakeNameLookup#find}
     */
    private record Index(FakeNameData data, int version, TreeSet<String> plain, Map<String, UUID> byKey) {
    }

    @Nullable
    private static Index index;

    private CharacterTarget() {
    }

    private static Index index(MinecraftServer server) {
        FakeNameData data = FakeNameData.get(server);
        Index current = index;
        if (current != null && current.data() == data && current.version() == data.version()) return current;
        Map<UUID, String> raw = data.allRaw();
        Map<String, UUID> byKey = new HashMap<>();
        for (Map.Entry<UUID, String> entry : raw.entrySet()) {
            String key = FakeNameLookup.key(entry.getValue());
            if (key.isEmpty()) continue;
            byKey.merge(key, entry.getKey(), (kept, other) -> other.compareTo(kept) < 0 ? other : kept);
        }
        current = new Index(data, data.version(), FakeNameLookup.plainNames(raw), byKey);
        index = current;
        return current;
    }

    /** Nomes de personagem conhecidos e nicks de quem esta online, com aspas quando preciso. */
    public static final SuggestionProvider<CommandSourceStack> SUGGESTIONS = (context, builder) -> {
        MinecraftServer server = context.getSource().getServer();
        // Copia do indice (mesma ordem sem caixa): os nicks online entram so nesta resposta.
        TreeSet<String> names = new TreeSet<>(index(server).plain());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) names.add(online.getGameProfile().getName());
        return SharedSuggestionProvider.suggest(names.stream().map(StringArgumentType::escapeIfRequired), builder);
    };

    /** Local identities only; never calls Mojang. */
    @Nullable
    static UUID resolveKnown(MinecraftServer server, String query) {
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

        return wanted.isEmpty() ? null : index(server).byKey().get(wanted);
    }

    /** Reports on the server thread, rechecking staff permission and the requester's session. */
    public static void resolve(CommandSourceStack source, String query, Consumer<UUID> result) {
        NameProfileLookup.resolve(source, query, result);
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
