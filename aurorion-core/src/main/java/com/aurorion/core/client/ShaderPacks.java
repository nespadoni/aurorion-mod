package com.aurorion.core.client;

import net.minecraft.Util;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * "Tem pacote de shaders ligado agora?" — a pergunta que decide como a neblina dos nossos mods e
 * desenhada.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>{@code ViewportEvent.RenderFog} e {@code ComputeFogColor} sao eventos do <b>pipeline do
 * vanilla</b>. Quando o Iris carrega um shader pack, quem calcula a neblina passa a ser o fragment
 * shader do pacote, com uniformes proprios; o plano distante que a gente pediu simplesmente nao e
 * consultado. Na pratica: a neblina da Floresta Negra e a do Devorar Luz existiam para quem jogava
 * sem shader e <b>sumiam</b> para quem jogava com BSL, Complementary ou Solas — que e a maioria do
 * servidor.
 *
 * <p>A saida nao e brigar com o shader: e desenhar a neblina <b>depois</b> dele, em espaco de tela
 * ({@link ScreenFog}), onde nenhum pacote interfere. Esta classe e so o interruptor entre os dois
 * caminhos.
 *
 * <h2>Como pergunta</h2>
 *
 * <p>Por reflexao na API publica v0 do Iris, como toda ponte opcional do monorepo: nada aqui aparece
 * em assinatura nossa, e sem o Iris instalado a resposta e sempre {@code false} sem custo nenhum.
 * A resposta e recalculada uma vez por segundo, porque o jogador liga e desliga shader em jogo (K,
 * no teclado padrao do Iris) e a neblina tem que trocar de tecnica junto.
 */
public final class ShaderPacks {
    /** Iris 1.21.x. O Oculus antigo vem depois, so por seguranca com packs mais velhos. */
    private static final String[] APIS = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };
    private static final long REFRESH_MILLIS = 1000L;

    private static boolean initialized;
    private static Method getInstance;
    private static Method inUse;

    private static long checkedAt;
    private static boolean primed;
    private static boolean cached;

    private ShaderPacks() {
    }

    /** {@code true} enquanto um shader pack estiver carregado no Iris/Oculus. */
    public static boolean inUse() {
        long now = Util.getMillis();
        if (primed && now - checkedAt < REFRESH_MILLIS) return cached;
        primed = true;
        checkedAt = now;
        cached = query();
        return cached;
    }

    private static boolean query() {
        if (!ready()) return false;
        try {
            return (boolean) inUse.invoke(getInstance.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            inUse = null;
            return false;
        }
    }

    private static boolean ready() {
        if (initialized) return inUse != null;
        initialized = true;
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return false;
        for (String name : APIS) {
            try {
                Class<?> api = Class.forName(name);
                getInstance = api.getMethod("getInstance");
                inUse = api.getMethod("isShaderPackInUse");
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Proxima assinatura; sem nenhuma delas, a neblina fica so no caminho do vanilla.
            }
        }
        getInstance = null;
        inUse = null;
        return false;
    }
}
