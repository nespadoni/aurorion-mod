package com.aurorion.limbo.narrate;

import com.aurorion.limbo.AurorionLimbo;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * A ponte reflexiva para o {@code ImmersiveMessage} do mod {@code immersivemessages}.
 *
 * <h2>Por que reflexao, se reflexao normalmente e cheiro</h2>
 *
 * <p>Porque a alternativa e pior. Compilar contra o mod exigiria o jar dele num {@code libs/} do
 * repositorio: um binario versionado no git, um build que quebra na maquina de quem nao copiou o
 * arquivo, e um acoplamento de <em>compilacao</em> a um mod que a SDD trata como opcional. Aqui o
 * acoplamento e so de execucao, e some sozinho quando o mod nao esta la.
 *
 * <p>O custo normal da reflexao — resolver metodo por chamada — nao existe: tudo e resolvido uma vez
 * na carga da classe. E nenhum destes caminhos e quente: sao eventos de exilio, nao de tick.
 *
 * <p>Se um dia o Immersive Messages entrar num maven publico, esta classe inteira vira um punhado de
 * chamadas normais e nada mais no mod precisa mudar — quem usa fala com o {@link LimboNarrator}.
 *
 * <h2>O que a ponte deliberadamente nao expoe</h2>
 *
 * <p>O mod tem bem mais que isto (subtexto, onda, tremor, arco-iris, animacao livre). A ponte para
 * em tres formatos — centro, topo e rodape — porque cada assinatura a mais e mais uma coisa que pode
 * nao existir na versao instalada e derrubar a checagem inteira para o chat. Tres cobrem todos os
 * momentos do Limbo.
 */
final class ImmersiveBridge {
    private static final String PKG = "toni.immersivemessages.api.";

    /** Fonte do Limbo. Se o resource pack nao a tiver, o proprio arquivo de fonte cai para a vanilla. */
    static final String FONT = AurorionLimbo.MOD_ID + ":limbo";

    @Nullable
    private static final Method BUILDER;
    @Nullable
    private static final Method FONT_OF;
    @Nullable
    private static final Method ANCHOR;
    @Nullable
    private static final Method COLOR;
    @Nullable
    private static final Method TYPEWRITER;
    @Nullable
    private static final Method OBFUSCATE;
    @Nullable
    private static final Method FADE_IN;
    @Nullable
    private static final Method FADE_OUT;
    @Nullable
    private static final Method SOUND;
    @Nullable
    private static final Method SEND_ONE;
    @Nullable
    private static final Method SEND_ALL;

    @Nullable
    private static final Object ANCHOR_CENTER;
    @Nullable
    private static final Object ANCHOR_TOP;
    @Nullable
    private static final Object ANCHOR_BOTTOM;
    @Nullable
    private static final Object OBFUSCATE_CENTER;
    @Nullable
    private static final Object SOUND_LOW;

    private static final boolean AVAILABLE;

    static {
        Method builder = null, font = null, anchor = null, color = null, typewriter = null;
        Method obfuscate = null, fadeIn = null, fadeOut = null, sound = null;
        Method sendOne = null, sendAll = null;
        Object anchorCenter = null, anchorTop = null, anchorBottom = null;
        Object obfuscateCenter = null, soundLow = null;
        boolean ok = false;

        try {
            Class<?> message = Class.forName(PKG + "ImmersiveMessage");
            Class<?> anchorType = Class.forName(PKG + "TextAnchor");
            Class<?> obfuscateType = Class.forName(PKG + "ObfuscateMode");
            Class<?> soundType = Class.forName(PKG + "SoundEffect");

            builder = message.getMethod("builder", float.class, MutableComponent.class);
            font = message.getMethod("font", String.class);
            anchor = message.getMethod("anchor", anchorType);
            color = message.getMethod("color", int.class);
            typewriter = message.getMethod("typewriter", float.class, boolean.class);
            obfuscate = message.getMethod("obfuscate", obfuscateType, float.class);
            fadeIn = message.getMethod("fadeIn", float.class);
            fadeOut = message.getMethod("fadeOut", float.class);
            sound = message.getMethod("sound", soundType);
            sendOne = message.getMethod("sendServer", ServerPlayer.class);
            sendAll = message.getMethod("sendServerToAll", MinecraftServer.class);

            anchorCenter = constant(anchorType, "CENTER_CENTER");
            anchorTop = constant(anchorType, "TOP_CENTER");
            anchorBottom = constant(anchorType, "BOTTOM_CENTER");
            obfuscateCenter = constant(obfuscateType, "CENTER");
            soundLow = constant(soundType, "LOW");

            ok = true;
            AurorionLimbo.LOGGER.info("Immersive Messages encontrado: as mensagens do Limbo vao por ele.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Nao e erro: e o caminho normal de um pack que nao tem o mod. Fica em debug para nao
            // poluir o boot de quem nunca quis o Immersive Messages.
            AurorionLimbo.LOGGER.debug("Immersive Messages indisponivel ({}); o Limbo usa o chat.", e.toString());
        }

        BUILDER = builder;
        FONT_OF = font;
        ANCHOR = anchor;
        COLOR = color;
        TYPEWRITER = typewriter;
        OBFUSCATE = obfuscate;
        FADE_IN = fadeIn;
        FADE_OUT = fadeOut;
        SOUND = sound;
        SEND_ONE = sendOne;
        SEND_ALL = sendAll;
        ANCHOR_CENTER = anchorCenter;
        ANCHOR_TOP = anchorTop;
        ANCHOR_BOTTOM = anchorBottom;
        OBFUSCATE_CENTER = obfuscateCenter;
        SOUND_LOW = soundLow;
        AVAILABLE = ok;
    }

    private ImmersiveBridge() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<Enum>) enumType, name);
    }

    static boolean available() {
        return AVAILABLE;
    }

    /**
     * Tela cheia, maquina de escrever, texto se revelando do centro para fora. O tratamento mais
     * pesado que existe aqui — so para queda, saida e vencimento de prazo.
     */
    static boolean center(ServerPlayer player, MutableComponent text, int color, float seconds) {
        Object message = compose(text, seconds, ANCHOR_CENTER, color);
        if (message == null) return false;

        message = call(message, OBFUSCATE, OBFUSCATE_CENTER, 0.6f);
        message = call(message, TYPEWRITER, 0.9f, true);
        message = call(message, SOUND, SOUND_LOW);
        return send(message, SEND_ONE, player);
    }

    /** Topo, discreto. Avisos de prazo e coleira. */
    static boolean top(ServerPlayer player, MutableComponent text, int color, float seconds) {
        Object message = compose(text, seconds, ANCHOR_TOP, color);
        return message != null && send(message, SEND_ONE, player);
    }

    /** Rodape, discreto. Progresso e dicas dentro do Limbo. */
    static boolean bottom(ServerPlayer player, MutableComponent text, int color, float seconds) {
        Object message = compose(text, seconds, ANCHOR_BOTTOM, color);
        return message != null && send(message, SEND_ONE, player);
    }

    /** Topo, para o servidor inteiro. O anuncio publico da queda. */
    static boolean broadcast(MinecraftServer server, MutableComponent text, int color, float seconds) {
        Object message = compose(text, seconds, ANCHOR_TOP, color);
        if (message == null) return false;

        message = call(message, OBFUSCATE, OBFUSCATE_CENTER, 0.5f);
        return send(message, SEND_ALL, server);
    }

    @Nullable
    private static Object compose(MutableComponent text, float seconds, @Nullable Object anchorConstant, int color) {
        if (!AVAILABLE) return null;

        try {
            Object message = BUILDER.invoke(null, seconds, text);
            message = call(message, FONT_OF, FONT);
            message = call(message, ANCHOR, anchorConstant);
            message = call(message, COLOR, color);
            message = call(message, FADE_IN, 0.6f);
            message = call(message, FADE_OUT, 0.8f);
            return message;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionLimbo.LOGGER.warn("Falha ao montar mensagem do Immersive Messages", e);
            return null;
        }
    }

    /** Cada passo do builder devolve a propria mensagem; um passo que falhe nao perde os anteriores. */
    private static Object call(Object message, @Nullable Method method, Object... args) {
        if (method == null) return message;

        try {
            Object next = method.invoke(message, args);
            return next != null ? next : message;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return message;
        }
    }

    private static boolean send(Object message, @Nullable Method method, Object target) {
        if (method == null) return false;

        try {
            method.invoke(message, target);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Devolver false faz quem chamou cair para o chat: a mensagem sai de um jeito ou de
            // outro. Silencio seria a pior falha possivel aqui.
            AurorionLimbo.LOGGER.warn("Falha ao enviar mensagem do Immersive Messages", e);
            return false;
        }
    }
}
