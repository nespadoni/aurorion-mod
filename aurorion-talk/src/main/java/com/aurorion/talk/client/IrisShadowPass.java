package com.aurorion.talk.client;

import com.aurorion.core.client.ShaderPacks;

import java.lang.reflect.Method;

/**
 * "O Iris esta desenhando o mapa de sombras agora?"
 *
 * <p>Com shader pack ligado, o Iris desenha as entidades duas vezes por quadro: uma para o mapa de
 * sombras e outra para a cena. O balao no passe de sombra so projeta um retangulo escuro no chao e
 * custa o dobro de vertices por jogador falando.
 *
 * <p>Por reflexao na API publica v0, como o {@link ShaderPacks} do core: nada do Iris aparece em
 * assinatura nossa. Sem shader pack (o caso de quem nao usa Iris, ou com ele desligado) a resposta e
 * {@code false} sem reflexao nenhuma — o {@link ShaderPacks#inUse()} ja e cacheado por segundo. Com
 * pack ligado, o passe muda dentro do mesmo quadro, entao a pergunta e feita a cada chamada, com os
 * {@link Method} resolvidos uma vez.
 */
public final class IrisShadowPass {
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";

    private static boolean initialized;
    private static Method getInstance;
    private static Method renderingShadowPass;

    private IrisShadowPass() {
    }

    public static boolean active() {
        if (!ShaderPacks.inUse() || !ready()) return false;
        try {
            return (boolean) renderingShadowPass.invoke(getInstance.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            renderingShadowPass = null;
            return false;
        }
    }

    private static boolean ready() {
        if (initialized) return renderingShadowPass != null;
        initialized = true;
        try {
            Class<?> api = Class.forName(API);
            getInstance = api.getMethod("getInstance");
            renderingShadowPass = api.getMethod("isRenderingShadowPass");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Oculus antigo ou API sem o metodo: o balao segue desenhado nos dois passes, como antes.
            getInstance = null;
            renderingShadowPass = null;
            return false;
        }
    }
}
