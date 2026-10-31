package com.aurorion.core.compat;

import com.aurorion.core.AurorionCore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte opcional com o Emotecraft para tocar, no servidor, um emote que vem dentro do jar de um mod (a
 * meditação do {@code aurorion-menu}; as poses da magia usam a cópia própria dela). Reflexiva: os tipos da
 * API vêm do player-animation-lib aninhado no jar do Emotecraft e nenhum aparece em assinatura nossa.
 *
 * <p>O emote é tocado como <b>forçado</b>: o Emotecraft transmite para quem estiver vendo, inclusive quem
 * não tem o arquivo. Sem o Emotecraft, {@link #available()} é {@code false} e tudo vira no-op — quem chama
 * desenha a pose do jeito dele.
 *
 * <p>Cada arquivo é lido uma vez; um que falhou fica marcado e não é relido a cada chamada.
 */
public final class EmotecraftBridge {
    private static final String API = "io.github.kosmx.emotes.api.events.server.ServerEmoteAPI";
    private static final String ANIMATION = "dev.kosmx.playerAnim.core.data.KeyframeAnimation";
    private static final Object MISSING = new Object();

    private static volatile boolean initialized;
    @Nullable private static Method forcePlay, setPlaying, getPlayed, deserialize;
    private static final Map<String, Object> EMOTES = new ConcurrentHashMap<>();

    private EmotecraftBridge() {
    }

    public static boolean available() {
        return ready();
    }

    /**
     * @param owner    uma classe do mod dono do arquivo (o recurso é lido pelo class loader dela)
     * @param resource caminho absoluto no jar, ex. {@code /assets/aurorion_menu/emotes/meditar.emotecraft}
     * @return {@code false} se não tocou (sem Emotecraft, arquivo ruim)
     */
    public static boolean play(ServerPlayer player, Class<?> owner, String resource) {
        Object emote = emote(owner, resource);
        if (emote == null) return false;
        return invoke(forcePlay, player.getUUID(), emote) != MISSING;
    }

    public static void stop(ServerPlayer player) {
        if (ready()) invoke(setPlaying, player.getUUID(), null);
    }

    /** Algum emote tocando nessa pessoa? */
    public static boolean playing(UUID player) {
        if (!ready()) return false;
        Object played = invoke(getPlayed, player);
        return played != null && played != MISSING;
    }

    @Nullable
    private static Object emote(Class<?> owner, String resource) {
        if (!ready()) return null;
        Object emote = EMOTES.computeIfAbsent(resource, path -> load(owner, path));
        return emote == MISSING ? null : emote;
    }

    private static Object load(Class<?> owner, String path) {
        try (InputStream in = owner.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("emote ausente no jar: " + path);
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".emotecraft", "");
            List<?> emotes = (List<?>) deserialize.invoke(null, in, name, "emotecraft");
            if (emotes == null || emotes.isEmpty()) throw new IllegalStateException("emote vazio: " + path);
            return emotes.get(0);
        } catch (Exception | LinkageError e) {
            AurorionCore.LOGGER.error("Core: não consegui carregar o emote {} do Emotecraft.", path, e);
            return MISSING;
        }
    }

    private static boolean ready() {
        if (initialized) return forcePlay != null;
        synchronized (EmotecraftBridge.class) {
            if (initialized) return forcePlay != null;
            if (ModList.get() != null && ModList.get().isLoaded("emotecraft")) {
                try {
                    Class<?> api = Class.forName(API);
                    Class<?> animation = Class.forName(ANIMATION);
                    forcePlay = api.getMethod("forcePlayEmote", UUID.class, animation);
                    setPlaying = api.getMethod("setPlayerPlayingEmote", UUID.class, animation);
                    getPlayed = api.getMethod("getPlayedEmote", UUID.class);
                    deserialize = api.getMethod("deserializeEmote", InputStream.class, String.class, String.class);
                } catch (Exception | LinkageError e) {
                    forcePlay = null;
                    AurorionCore.LOGGER.error("Core: API do Emotecraft incompatível; emotes ficam de fora.", e);
                }
            }
            initialized = true;
        }
        return forcePlay != null;
    }

    @Nullable
    private static Object invoke(@Nullable Method method, Object... args) {
        if (method == null) return MISSING;
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionCore.LOGGER.warn("Core: chamada ao Emotecraft falhou.", e);
            return MISSING;
        }
    }
}
