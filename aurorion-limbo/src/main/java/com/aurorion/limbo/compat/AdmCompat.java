package com.aurorion.limbo.compat;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

/**
 * Ponte para o Aviel's Dialogue Mod: o Oraculo ganha conversa.
 *
 * <h2>A divisao de trabalho</h2>
 *
 * <p>O ADM e bom no que a nossa tela e ruim — <b>fala</b>: arvore de dialogo, personalidade, preco
 * negociado, texto que a staff reescreve sem rebuild. E a nossa tela faz o que o ADM nao consegue:
 * mostrar uma <b>lista que muda a cada segundo</b>, porque quem esta no Limbo agora e dado vivo do
 * servidor, nao texto de arquivo.
 *
 * <p>Entao a integracao nao troca uma coisa pela outra: o dialogo e a moldura e a tela e o miolo. Uma
 * escolha do dialogo roda {@code /limbo oraculo}, que abre a lista. Sem o ADM, o esqueleto abre a
 * lista direto e o servidor perde a conversa, nao o resgate.
 *
 * <h2>Reflexao, pelo mesmo motivo de sempre</h2>
 *
 * <p>Compilar contra o ADM exigiria o jar dele num {@code libs/} — binario no git e build que quebra
 * na maquina de quem nao copiou o arquivo, por um mod opcional. Ver {@code ImmersiveBridge}; a regra
 * aqui e a mesma, e o custo tambem: tudo resolvido uma vez, nada por chamada.
 *
 * <h2>O que registramos</h2>
 *
 * <p>Tipos de condicao. Sao perguntas que so o Aurorion sabe responder, e que o JSON do dialogo passa
 * a poder fazer:
 *
 * <pre>{@code
 * "condition": { "type": "aurorion_limbo:exilados", "min": 1 }   // ha alguem no Limbo?
 * "condition": { "type": "aurorion_limbo:pode_pagar" }           // tem vida para a passagem?
 * "condition": { "type": "aurorion_limbo:no_limbo" }             // quem fala esta exilado?
 * }</pre>
 *
 * <p>Condicao e <b>comportamento</b>, entao vem de codigo. A fala e <b>conteudo</b>, entao mora no
 * arquivo do dialogo e a staff reescreve sem tocar no jar — diretriz 4 da §7.
 */
public final class AdmCompat {
    private static final String MOD_ID = "adm";
    private static final String API = "net.aviel.dialogue.api.AdmDialogueApi";
    private static final String HANDLER = "net.aviel.dialogue.api.DialogueConditionHandler";
    private static final String PREDICATE = "net.aviel.dialogue.npc.dialogue.DialogueCondition$Predicate";

    private static Method openDialogue;
    private static boolean available;

    private AdmCompat() {
    }

    public static boolean available() {
        return available;
    }

    /**
     * Registra as condicoes no boot. Chamado uma vez, do {@code ServerStartedEvent}.
     *
     * <p>Nunca lanca: um ADM com API diferente da que este codigo conhece desliga a integracao e
     * deixa o Oraculo funcionando pela tela, em vez de derrubar o servidor no boot.
     */
    public static void register() {
        if (!ModList.get().isLoaded(MOD_ID)) return;

        try {
            Class<?> api = Class.forName(API);
            Class<?> handler = Class.forName(HANDLER);
            Class<?> predicate = Class.forName(PREDICATE);

            Method registerType = api.getMethod("registerConditionType", String.class, handler);
            openDialogue = api.getMethod("openDialogue", ServerPlayer.class, Entity.class, String.class);

            Method min = predicate.getMethod("min");
            Method expected = predicate.getMethod("expected");

            condition(registerType, handler, min, expected, "aurorion_limbo:exilados",
                    player -> !LimboData.get(player.server).active().isEmpty(),
                    player -> LimboData.get(player.server).active().size());

            condition(registerType, handler, min, expected, "aurorion_limbo:pode_pagar",
                    player -> LivesManager.livesOf(player.server, player.getUUID())
                            >= LimboConfig.minLivesToRescue(),
                    player -> LivesManager.livesOf(player.server, player.getUUID()));

            condition(registerType, handler, min, expected, "aurorion_limbo:no_limbo",
                    player -> LivesManager.isExiled(player.server, player.getUUID()),
                    player -> LivesManager.isExiled(player.server, player.getUUID()) ? 1 : 0);

            available = true;
            AurorionLimbo.LOGGER.info("ADM encontrado: o Oraculo pode usar dialogo.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionLimbo.LOGGER.warn("ADM presente mas com API diferente da esperada ({}); "
                    + "o Oraculo abre a lista direto.", e.toString());
            available = false;
        }
    }

    /**
     * Uma condicao, com as duas leituras que o ADM oferece.
     *
     * <p>Sem {@code min}, a condicao e um sim/nao e {@code expected} inverte (o {@code "not"} do
     * JSON). Com {@code min}, ela compara a contagem — e o que faz
     * {@code {"type":"aurorion_limbo:exilados","min":3}} significar "tres ou mais perdidos".
     */
    private static void condition(Method registerType, Class<?> handlerType, Method minOf, Method expectedOf,
                                  String type, Predicate<ServerPlayer> yesNo,
                                  java.util.function.ToIntFunction<ServerPlayer> count)
            throws ReflectiveOperationException {

        InvocationHandler invoke = (proxy, method, args) -> {
            if (!"test".equals(method.getName()) || args == null || args.length < 3) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> type;
                    default -> false;
                };
            }
            if (!(args[0] instanceof ServerPlayer player)) return false;

            Object pred = args[2];
            Double min = pred == null ? null : (Double) minOf.invoke(pred);
            boolean expected = pred == null || (boolean) expectedOf.invoke(pred);

            boolean result = min != null
                    ? count.applyAsInt(player) >= min
                    : yesNo.test(player);

            return result == expected;
        };

        Object handler = Proxy.newProxyInstance(AdmCompat.class.getClassLoader(),
                new Class<?>[]{handlerType}, invoke);
        registerType.invoke(null, type, handler);
    }

    /**
     * Abre o dialogo do Oraculo, se houver um configurado e o ADM estiver presente.
     *
     * @return {@code false} quando o dialogo nao abriu — quem chama cai para a lista direta.
     */
    public static boolean openOracleDialogue(ServerPlayer player, Entity oracle) {
        String file = LimboConfig.ORACLE_DIALOGUE.get();
        if (!available || openDialogue == null || file == null || file.isBlank()) return false;

        try {
            openDialogue.invoke(null, player, oracle, file);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Dialogo com erro de sintaxe, arquivo que sumiu, id errado: a conversa falha, o resgate
            // nao. Cair para a lista e sempre melhor que um Oraculo que nao responde.
            AurorionLimbo.LOGGER.warn("Nao consegui abrir o dialogo '{}' do Oraculo; abrindo a lista.", file);
            return false;
        }
    }

    /** Solta o estado ligado ao processo, como o resto do mod faz no desligamento. */
    public static void reset() {
        available = false;
        openDialogue = null;
    }
}
