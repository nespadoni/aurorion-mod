package com.aurorion.ficha.client;

import com.aurorion.ficha.AurorionFicha;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * As teclas da ficha, a roda e o registro do HUD.
 *
 * <p><b>A tecla da ficha</b> (padrão: a tecla à esquerda do 1, {@code GRAVE_ACCENT}: {@code `} no teclado
 * americano, a de aspas {@code ' "} no ABNT2), do mesmo jeito da roda de passivas da magia:
 * <ul>
 *   <li><b>Toque rápido</b>: com o {@code aurorion-menu}, abre o menu do personagem no Perfil; sem ele, a ficha
 *       direto, como sempre.</li>
 *   <li><b>Segurar</b> ({@value #HOLD_TICKS} ticks): abre a {@link FichaRoda} — Ficha, Dados, Habilidades,
 *       a mesa (mestre) e, com um pedido do mestre, Rolar/Recusar com o cartão do pedido no miolo.
 *       Soltar em cima de uma fatia escolhe; soltar no miolo ou Esc cancela.</li>
 * </ul>
 * O toque abre a ficha <b>ao soltar</b> (só então se sabe que não era segurar): 50–300 ms a mais que
 * antes, imperceptível perto da ida ao site. O inverso (toque = roda) custaria um clique a mais a quem
 * só quer a ficha, o caso mais comum. A roda é toda do cliente: abrir não manda nada ao servidor.
 *
 * <p>{@code K}, a tecla de antes, colidia com Iris "ligar shaders", Pufferfish's Skills, Quark "travar
 * rotação" e o Kubedex do KubeJS; a de aspas é a única livre de fábrica no modpack. No Windows o GLFW
 * traduz pela <b>posição</b> (scancode 0x29 → {@code GRAVE_ACCENT}): a do ABNT2 chega como a mesma
 * tecla e a tela de Controles a mostra como {@code '}. Texto vindo do servidor nunca cita a tecla
 * fixa: usa {@code {tecla}} ({@code FichaClient.withOpenKey}) ou "/ficha". Cada toque/segurada deixa
 * uma linha DEBUG no debug.log ("Ficha: tecla … tocada/segurada").
 *
 * <p><b>Rolar/recusar o pedido</b> ficam <b>sem tecla</b> de fábrica: Y colidia com a habilidade de
 * peitoral do Cataclysm e a {@code ability_2} do hazentouvelib, e U com a roda de passivas da magia.
 * Responde-se pela roda (segurar a tecla da ficha), pelos botões da ficha e da mesa, ou pela tecla que
 * o jogador atribuir em Controles. Os ids mudaram ({@code pedido_rolar}/{@code pedido_recusar}) para o
 * {@code options.txt} de quem já jogava — que guarda Y/U — não as manter. Atribuídas, usam
 * {@link #WHEN_ASKED}: só ficam ativas em jogo <b>e</b> com um pedido no HUD (o NeoForge só entrega o
 * clique a mapeamentos com o contexto ativo, então sem pedido a tecla fica inteira para o outro mod).
 * A mesa do mestre também fica sem tecla. Ficha e mesa são de jogo comum
 * ({@link KeyConflictContext#IN_GAME}): dentro de uma tela não disparam.
 */
public final class FichaKeys {
    static final String CATEGORY = "key.categories.aurorion_ficha";

    /**
     * Em jogo (sem tela aberta) e com um pedido do mestre no HUD. Na tela de Controles conflita como
     * uma tecla de jogo comum: o aviso vermelho continua dizendo a verdade.
     */
    static final IKeyConflictContext WHEN_ASKED = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && FichaHud.hasAsk();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };

    public static final KeyMapping OPEN = new KeyMapping("key.aurorion_ficha.abrir", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
    public static final KeyMapping ROLL_REQUEST = new KeyMapping("key.aurorion_ficha.pedido_rolar", WHEN_ASKED,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping DECLINE_REQUEST = new KeyMapping("key.aurorion_ficha.pedido_recusar", WHEN_ASKED,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping TABLE = new KeyMapping("key.aurorion_ficha.mestre", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    /** 0,3 s, como a roda de passivas: curto para a roda não parecer lenta, longo para o toque não abri-la. */
    static final int HOLD_TICKS = 6;

    private static boolean wasDown;
    private static int heldTicks;

    // answerHint: refeito só quando uma das três teclas (ou o idioma) muda.
    @Nullable
    private static InputConstants.Key hintOpen, hintRoll, hintDecline;
    @Nullable
    private static KeyModifier hintOpenMod, hintRollMod, hintDeclineMod;
    @Nullable
    private static String hintLanguage;
    private static String hint = "";

    private FichaKeys() {
    }

    /**
     * A dica do cartão de pedido no HUD: {@code "Segure ' para responder"} com o nome real da tecla da
     * ficha; {@code "Y rola · U recusa"} se o jogador atribuiu as teclas de pedido; sem tecla nenhuma,
     * o caminho que sempre funciona ({@code /ficha}, que tem os botões). Barato por quadro: só remonta
     * o texto quando alguma tecla ou o idioma muda.
     */
    public static String answerHint() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (OPEN.getKey() != hintOpen || ROLL_REQUEST.getKey() != hintRoll || DECLINE_REQUEST.getKey() != hintDecline
                || OPEN.getKeyModifier() != hintOpenMod || ROLL_REQUEST.getKeyModifier() != hintRollMod
                || DECLINE_REQUEST.getKeyModifier() != hintDeclineMod || !language.equals(hintLanguage)) {
            hintOpen = OPEN.getKey();
            hintRoll = ROLL_REQUEST.getKey();
            hintDecline = DECLINE_REQUEST.getKey();
            hintOpenMod = OPEN.getKeyModifier();
            hintRollMod = ROLL_REQUEST.getKeyModifier();
            hintDeclineMod = DECLINE_REQUEST.getKeyModifier();
            hintLanguage = language;
            hint = buildHint(FichaUi.keyName(OPEN), FichaUi.keyName(ROLL_REQUEST), FichaUi.keyName(DECLINE_REQUEST));
        }
        return hint;
    }

    /** O texto de {@link #answerHint} a partir dos nomes das teclas (vazio = sem tecla). */
    static String buildHint(String open, String roll, String decline) {
        if (!roll.isEmpty()) return roll + " rola" + (decline.isEmpty() ? "" : " · " + decline + " recusa");
        if (!open.isEmpty()) return "Segure " + open + " para responder";
        return "Use /ficha para responder";
    }

    /** Abre a roda (a tecla segurada; ou o {@code /ficha teste roda}, com {@code test}). */
    static void openWheel(boolean holdMode, boolean test, boolean gm) {
        Minecraft.getInstance().setScreen(new FichaRoda(holdMode, test, gm));
    }

    /** Registros do bus do mod. */
    @EventBusSubscriber(modid = AurorionFicha.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
            event.register(ROLL_REQUEST);
            event.register(DECLINE_REQUEST);
            event.register(TABLE);
        }

        /** O Perfil no menu do personagem (registro do core; quem desenha é o aurorion-menu). */
        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            FichaMenu.register();
        }

        @SubscribeEvent
        public static void registerLayers(RegisterGuiLayersEvent event) {
            // Acima do chat: um pedido do mestre não pode sumir atrás de mensagens.
            event.registerAbove(VanillaGuiLayers.CHAT, FichaHud.ID, new FichaHud());
        }
    }

    /** Reações do bus do jogo. */
    @EventBusSubscriber(modid = AurorionFicha.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean free = minecraft.player != null && minecraft.screen == null;
            openKey(minecraft, free);
            while (TABLE.consumeClick()) {
                if (free) FichaClient.requestTable();
            }
            // Só chegam cliques aqui com um pedido no HUD (WHEN_ASKED); answerCurrent confere de novo.
            while (ROLL_REQUEST.consumeClick()) {
                if (free) FichaClient.answerCurrent(true);
            }
            while (DECLINE_REQUEST.consumeClick()) {
                if (free) FichaClient.answerCurrent(false);
            }
            FichaClient.tickPrompt();
        }

        /**
         * Toque ou segurada da tecla da ficha. O que importa é quanto tempo ela ficou apertada; o clique
         * contado só cobre o toque que aperta e solta dentro do mesmo tick, que o {@code isDown()} perderia.
         */
        private static void openKey(Minecraft minecraft, boolean free) {
            boolean clicked = false;
            while (OPEN.consumeClick()) clicked = true;
            if (!free) {
                // Só no debug.log e só quando o jogador aperta: diz se a tecla chegou e por que não abriu.
                if (clicked) AurorionFicha.LOGGER.debug("Ficha: tecla {} apertada sem abrir (tela={})", OPEN.getKey().getName(),
                        minecraft.screen == null ? "-" : minecraft.screen.getClass().getSimpleName());
                wasDown = false;
                // Fechar uma tela com a tecla ainda apertada: ela volta "apertada" ao jogo e não pode contar
                // como toque nem abrir a roda. Zera no primeiro tick em que for solta.
                heldTicks = HOLD_TICKS;
                return;
            }
            boolean down = OPEN.isDown();
            if (down) {
                heldTicks++;
                if (heldTicks == HOLD_TICKS) {
                    AurorionFicha.LOGGER.debug("Ficha: tecla {} segurada (roda)", OPEN.getKey().getName());
                    openWheel(true, false, FichaClient.isGm());
                }
            } else if ((wasDown || clicked) && heldTicks < HOLD_TICKS) {
                AurorionFicha.LOGGER.debug("Ficha: tecla {} tocada (ficha)", OPEN.getKey().getName());
                FichaClient.openFromKey();
            }
            if (!down) heldTicks = 0;
            wasDown = down;
        }

        /** Uma tela fechou: se há um convite da ficha guardado, o próximo tick o abre (ver FichaClient). */
        @SubscribeEvent
        public static void onScreenClosing(ScreenEvent.Closing event) {
            FichaClient.onScreenClosing();
        }

        /**
         * Onde as barras de chefe terminam neste frame (a camada delas desenha antes da nossa). Por
         * último e vendo os cancelados: um mod que desenha a própria barra cancela a do vanilla mas
         * ocupa o mesmo lugar, às vezes mais alto ({@code increment}).
         */
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
            FichaHud.bossBar(event.getX(), event.getY(), event.getIncrement());
        }

        /** Sem isto os cartões, pedidos e respostas da mesa de um servidor apareceriam no próximo. */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            FichaClient.clearSession();
        }

        /** De novo ao entrar: troca de alt e de servidor reconectam, e nada do perfil anterior vale. */
        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            FichaClient.clearSession();
        }
    }
}
