package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ponte opcional com o Emotecraft: as poses que o servidor forca no corpo de alguem.
 *
 * <p>Reflexiva, como a ponte do Iron's no {@code aurorion-areas}: os tipos da API do Emotecraft vem do
 * player-animation-lib, aninhado dentro do jar dele, e nenhum aparece em assinatura nossa. Sem o
 * Emotecraft, todos os metodos viram no-op e o cliente cai no agachado.
 *
 * <p>Os emotes vao dentro do nosso jar. Cada um e lido uma vez, na primeira vez que for pedido, e
 * tocado como <b>forcado</b> ({@code forcePlayEmote}): o jogador nao consegue cancelar, e o Emotecraft
 * transmite a animacao para quem estiver vendo — inclusive para quem nao tem o emote.
 */
public final class EmotecraftCompat {
    private static final String API = "io.github.kosmx.emotes.api.events.server.ServerEmoteAPI";
    private static final String ANIMATION = "dev.kosmx.playerAnim.core.data.KeyframeAnimation";

    private static boolean initialized;
    private static Method forcePlay;
    private static Method setPlaying;
    private static Method getPlayed;
    private static final Map<Pose, Object> EMOTES = new EnumMap<>(Pose.class);

    private EmotecraftCompat() {
    }

    /** As duas poses do mod, e a diferenca entre elas e a diferenca entre as duas magias. */
    public enum Pose {
        /** Prostracao: os dois joelhos no chao, o corpo curvado. */
        KNEEL("kneel_down"),
        /** Um joelho, o corpo cedendo sem se entregar. */
        ONE_KNEE("kneel_one_knee");

        private final String file;

        Pose(String file) {
            this.file = file;
        }
    }

    public static void kneel(ServerPlayer player) {
        play(player, Pose.KNEEL);
    }

    /**
     * A pose de quem cede diante da Presenca Aterradora.
     *
     * @param deep prostracao, os dois joelhos no chao ({@code dreadProstrates}); {@code false} cede um
     *             joelho so
     */
    public static void prostrate(ServerPlayer player, boolean deep) {
        play(player, deep ? Pose.KNEEL : Pose.ONE_KNEE);
    }

    /** Relogou, trocou de dimensao ou o emote acabou: toca de novo. Chamado 1x/s pelo efeito. */
    public static void ensureKneeling(ServerPlayer player) {
        ensure(player, Pose.KNEEL);
    }

    public static void ensureProstrating(ServerPlayer player, boolean deep) {
        ensure(player, deep ? Pose.KNEEL : Pose.ONE_KNEE);
    }

    public static void stop(ServerPlayer player) {
        if (!ready()) return;
        invoke(setPlaying, player.getUUID(), null);
    }

    private static void play(ServerPlayer player, Pose pose) {
        Object emote = emote(pose);
        if (emote != null) invoke(forcePlay, player.getUUID(), emote);
    }

    /**
     * So retoma quando <b>nada</b> esta tocando. Se a magia Prostracao ja pos alguem de joelhos e a
     * aura o alcanca no mesmo instante, os dois efeitos nao ficam brigando pelo corpo a cada segundo:
     * quem chegou primeiro fica.
     */
    private static void ensure(ServerPlayer player, Pose pose) {
        if (!ready()) return;
        if (invoke(getPlayed, player.getUUID()) == null) play(player, pose);
    }

    /** Uma pose que falhou ao carregar fica gravada como {@code null} e nao e relida a cada chamada. */
    @Nullable
    private static Object emote(Pose pose) {
        if (!ready()) return null;
        if (!EMOTES.containsKey(pose)) EMOTES.put(pose, load(pose));
        return EMOTES.get(pose);
    }

    @Nullable
    private static Object load(Pose pose) {
        String path = "/assets/aurorion_magia/emotes/" + pose.file + ".emotecraft";
        try (InputStream in = EmotecraftCompat.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("emote ausente no jar: " + path);
            Method deserialize = Class.forName(API)
                    .getMethod("deserializeEmote", InputStream.class, String.class, String.class);
            List<?> emotes = (List<?>) deserialize.invoke(null, in, pose.file, "emotecraft");
            if (emotes == null || emotes.isEmpty()) throw new IllegalStateException("emote vazio: " + path);
            AurorionMagia.LOGGER.info("Magia: pose '{}' do Emotecraft carregada.", pose.file);
            return emotes.get(0);
        } catch (Exception | LinkageError exception) {
            AurorionMagia.LOGGER.error("Magia: nao consegui carregar a pose '{}' do Emotecraft.", pose.file, exception);
            return null;
        }
    }

    private static boolean ready() {
        if (initialized) return forcePlay != null;
        initialized = true;
        if (!ModList.get().isLoaded("emotecraft")) return false;
        try {
            Class<?> api = Class.forName(API);
            Class<?> animation = Class.forName(ANIMATION);
            forcePlay = api.getMethod("forcePlayEmote", UUID.class, animation);
            setPlaying = api.getMethod("setPlayerPlayingEmote", UUID.class, animation);
            getPlayed = api.getMethod("getPlayedEmote", UUID.class);
        } catch (Exception | LinkageError exception) {
            forcePlay = null;
            AurorionMagia.LOGGER.error("Magia: API do Emotecraft incompativel; as poses ficam de fora.", exception);
        }
        return forcePlay != null;
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
