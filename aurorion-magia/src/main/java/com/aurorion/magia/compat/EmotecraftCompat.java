package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Ponte opcional com o Emotecraft: a pose de joelhos do Genua Flecte.
 *
 * <p>Reflexiva, como a ponte do Iron's no {@code aurorion-areas}: os tipos da API do Emotecraft vem do
 * player-animation-lib, aninhado dentro do jar dele, e nenhum aparece em assinatura nossa. Sem o
 * Emotecraft, todos os metodos viram no-op e o cliente cai no agachado.
 *
 * <p>O emote ({@value #EMOTE}) vai dentro do nosso jar. O servidor o le uma vez, na primeira
 * conjuracao, e o toca como <b>forcado</b> ({@code forcePlayEmote}): o jogador nao consegue cancelar, e
 * o Emotecraft transmite a animacao para quem estiver vendo — inclusive quem nao tem o emote.
 */
public final class EmotecraftCompat {
    private static final String EMOTE = "/assets/aurorion_magia/emotes/kneel_down.emotecraft";
    private static final String API = "io.github.kosmx.emotes.api.events.server.ServerEmoteAPI";
    private static final String ANIMATION = "dev.kosmx.playerAnim.core.data.KeyframeAnimation";

    private static boolean initialized;
    private static Method forcePlay;
    private static Method setPlaying;
    private static Method getPlayed;
    @Nullable
    private static Object kneel;

    private EmotecraftCompat() {
    }

    public static void kneel(ServerPlayer player) {
        if (!ready()) return;
        invoke(forcePlay, player.getUUID(), kneel);
    }

    /** Relogou, trocou de dimensao ou o emote acabou: toca de novo. Chamado 1x/s pelo efeito. */
    public static void ensureKneeling(ServerPlayer player) {
        if (!ready()) return;
        if (invoke(getPlayed, player.getUUID()) == null) kneel(player);
    }

    public static void stop(ServerPlayer player) {
        if (!ready()) return;
        invoke(setPlaying, player.getUUID(), null);
    }

    private static boolean ready() {
        if (initialized) return kneel != null;
        initialized = true;
        if (!ModList.get().isLoaded("emotecraft")) return false;

        try (InputStream in = EmotecraftCompat.class.getResourceAsStream(EMOTE)) {
            if (in == null) throw new IllegalStateException("emote ausente no jar: " + EMOTE);
            Class<?> api = Class.forName(API);
            Class<?> animation = Class.forName(ANIMATION);
            Method deserialize = api.getMethod("deserializeEmote", InputStream.class, String.class, String.class);
            List<?> emotes = (List<?>) deserialize.invoke(null, in, "kneel_down", "emotecraft");
            if (emotes == null || emotes.isEmpty()) throw new IllegalStateException("emote vazio: " + EMOTE);

            forcePlay = api.getMethod("forcePlayEmote", UUID.class, animation);
            setPlaying = api.getMethod("setPlayerPlayingEmote", UUID.class, animation);
            getPlayed = api.getMethod("getPlayedEmote", UUID.class);
            kneel = emotes.get(0);
            AurorionMagia.LOGGER.info("Magia: pose de joelhos do Emotecraft carregada.");
        } catch (Exception | LinkageError exception) {
            kneel = null;
            AurorionMagia.LOGGER.error("Magia: API do Emotecraft incompativel; Genua Flecte fica sem a pose.", exception);
        }
        return kneel != null;
    }

    @Nullable
    private static Object invoke(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AurorionMagia.LOGGER.warn("Magia: chamada ao Emotecraft falhou.", exception);
            return null;
        }
    }
}
