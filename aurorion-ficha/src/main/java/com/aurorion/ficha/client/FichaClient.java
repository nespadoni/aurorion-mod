package com.aurorion.ficha.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.Texts;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Recebe os pacotes da ficha no cliente e liga teclas, HUD e telas. Só é carregada no cliente (ver
 * {@code FichaNetwork}).
 *
 * <p>As telas do {@code /ficha teste} recebem tudo do {@link FichaTestSession}; pacote de verdade
 * que chega com uma tela de teste aberta não a toca (e vice-versa), como no diário.
 *
 * <p>Nada se perde por falta de tela: pedido do mestre fica no HUD até vencer; aviso só começa a
 * contar o tempo quando aparece; erro e confirmação sem a tela da ficha vão para a barra de ação (sem
 * tela nenhuma) ou para o chat (com outra tela aberta, onde a barra não se lê).
 */
public final class FichaClient {
    static final String OPENING_SHEET = "Abrindo a ficha…";
    static final String OPENING_TABLE = "Abrindo a mesa do mestre…";
    /** Quanto tempo um convite ({@link FichaPayloads#PROMPT}) espera a tela do jogador fechar. */
    private static final long PROMPT_MS = 60_000L;

    /** Convite guardado porque chegou com outra tela aberta (carregando o mundo, inventário…). */
    @Nullable
    private static FichaPayloads.View prompt;
    private static boolean promptTest;
    private static long promptAt;
    /** Uma tela fechou desde que o convite chegou: o próximo tick confere se o jogador ficou livre. */
    private static boolean promptArmed;
    /** O aviso de "servidor sem o canal" no debug.log sai uma vez por sessão. */
    private static boolean noChannelLogged;

    /** A roda pediu a ficha já numa aba ou diálogo ({@link #OPEN_DICE}, {@link #OPEN_SKILLS}). */
    static final String OPEN_SHEET = "ficha";
    static final String OPEN_DICE = "dados";
    static final String OPEN_SKILLS = "habilidades";
    /** Quanto tempo o destino da roda vale: a ficha que chega muito depois abre na aba de sempre. */
    private static final long INTENT_MS = 15_000L;
    @Nullable
    private static String openIntent;
    private static boolean openIntentTest;
    private static long openIntentAt;
    /** O pedido do mestre respondido com "rolar" (roda, HUD ou ficha) e quando: a roda mostra "Rolando…". */
    @Nullable
    private static String answeredId;
    private static long answeredAt;
    private static final long ANSWER_MS = 12_000L;
    /** O nome do personagem da última ficha própria que chegou (o miolo da roda). */
    private static String characterName = "";

    private FichaClient() {
    }

    /** {@code ScreenEvent.Closing}: só arma — abrir outra tela dentro do {@code setScreen} em curso não pode. */
    static void onScreenClosing() {
        if (prompt != null) promptArmed = true;
    }

    /** Tick do cliente: uma comparação, a não ser com um convite guardado e uma tela que acabou de fechar. */
    static void tickPrompt() {
        if (prompt == null || !promptArmed) return;
        promptArmed = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (Util.getMillis() - promptAt > PROMPT_MS) {
            prompt = null;
            return;
        }
        // Fechou uma tela e abriu outra (mundo carregado → criação de personagem): espera a próxima.
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null) return;
        FichaPayloads.View view = prompt;
        prompt = null;
        minecraft.setScreen(new FichaScreen(view, promptTest, null));
    }

    /** Desconectou, trocou de servidor ou de alt: nada do perfil anterior fica no HUD nem na mesa. */
    static void clearSession() {
        prompt = null;
        noChannelLogged = false;
        openIntent = null;
        answeredId = null;
        characterName = "";
        FichaHud.clear();
        MestreScreen.clearLog();
        FichaTestSession.stop();
        FichaTestCommand.clear();
        FichaMenu.clear();
    }

    // ── Para o servidor ─────────────────────────────────────────────────────────

    /**
     * Manda ao servidor se ele tiver o canal; servidor sem a ficha recebe só um aviso local.
     * Devolve se saiu (quem chama só mostra "rolando…"/"abrindo…" quando saiu).
     */
    static boolean send(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(payload.type())) {
            if (!noChannelLogged) {
                noChannelLogged = true;
                AurorionFicha.LOGGER.debug("Ficha: {} sem canal no servidor (conexão {}); a tela só abre por /ficha teste",
                        payload.type().id(), connection == null ? "ausente" : "sem o mod");
            }
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Este servidor não tem a ficha do Aurorion. Use /ficha teste para ver a tela."), true);
            }
            return false;
        }
        PacketDistributor.sendToServer(payload);
        return true;
    }

    /** Tecla da ficha: o site pode levar um instante, então o HUD diz "Abrindo a ficha…" até a tela chegar. */
    static void requestOpen() {
        requestOpen(OPEN_SHEET, false);
    }

    /** Toque na tecla da ficha: com o menu do personagem instalado, o Perfil dele; sem, a ficha de sempre. */
    static void openFromKey() {
        if (!FichaMenu.openProfile()) requestOpen();
    }

    /**
     * Abre a ficha já em {@code intent} ({@link #OPEN_SHEET}, {@link #OPEN_DICE}, {@link #OPEN_SKILLS}).
     * O servidor não sabe de abas: o cliente guarda o destino e o aplica quando a tela chegar
     * ({@link #deliverView}). {@code test}: a roda do {@code /ficha teste roda}, respondida localmente.
     */
    static void requestOpen(String intent, boolean test) {
        openIntent = OPEN_SHEET.equals(intent) ? null : intent;
        openIntentTest = test;
        openIntentAt = Util.getMillis();
        if (test) {
            FichaTestSession.handle(new FichaPayloads.Action("abrir", "", ""));
            FichaHud.loading("abrir", OPENING_SHEET);
        } else if (send(new FichaPayloads.Action("abrir", "", ""))) {
            FichaHud.loading("abrir", OPENING_SHEET);
        } else {
            openIntent = null;
        }
    }

    static void requestTable() {
        if (send(new FichaPayloads.Action("mestre", "", ""))) FichaHud.loading("mestre", OPENING_TABLE);
    }

    /** O destino guardado pela roda, se ainda vale para esta tela (consumido: vale uma vez). */
    @Nullable
    private static String takeIntent(boolean test) {
        String intent = openIntent;
        openIntent = null;
        if (intent == null || openIntentTest != test || Util.getMillis() - openIntentAt > INTENT_MS) return null;
        return intent;
    }

    /**
     * Mestre aqui = o mesmo teste do servidor ({@code FichaServer.isGm}): a permissão de OP <b>deste
     * perfil</b> contra {@code permissaoMestre}. O vanilla já manda o nível de OP ao cliente no login e a
     * cada /op e /deop (evento de entidade 24–28) e a config SERVER chega sincronizada na conexão: nada
     * de pacote nosso nem conferência por tick. O alt nasce sem OP, então não é mestre. Se a conta
     * errar (config fora de sincronia), o servidor confere de novo ao abrir a mesa e explica.
     */
    static boolean isGm() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(FichaConfig.get(FichaConfig.GM_PERMISSION));
    }

    /** O nome do personagem para o miolo da roda: o da última ficha própria; antes dela, o do jogador. */
    static String characterName() {
        if (!characterName.isEmpty()) return characterName;
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "" : player.getName().getString();
    }

    /** A ficha própria (não espiada, não de teste) chegou: guarda o nome para a roda. */
    static void rememberCharacter(@Nullable String name) {
        characterName = name == null ? "" : name;
    }

    /** O pedido {@code id} foi respondido com "rolar" há pouco e o resultado ainda não chegou? */
    static boolean isAnswering(String id) {
        return id.equals(answeredId) && Util.getMillis() - answeredAt < ANSWER_MS;
    }

    /** Tecla de rolar ({@code roll}) ou recusar o pedido do mestre que está no HUD. */
    static void answerCurrent(boolean roll) {
        answer(FichaHud.currentAsk(), roll);
    }

    /**
     * Rola ou recusa {@code ask}: a roda e a mesa respondem ao pedido que mostraram, não ao que estiver
     * no topo do HUD quando o clique chega. Devolve se a resposta saiu.
     */
    static boolean answer(@Nullable FichaHud.Ask ask, boolean roll) {
        if (ask == null || roll && isAnswering(ask.id())) return false;
        if (ask.local()) {
            if (roll) markAnswering(ask.id());
            FichaTestSession.answer(ask, roll);
            return true;
        }
        if (roll) {
            // "Rolando…" na hora; volta aos botões se o servidor recusar ou não responder.
            if (!send(new FichaPayloads.Roll("pedido", "", 0, false, ask.id()))) return false;
            FichaHud.answering(ask.id());
            markAnswering(ask.id());
            return true;
        }
        if (!send(new FichaPayloads.Action("recusar", ask.id(), ""))) return false;
        FichaHud.closeAsk(ask.id());
        return true;
    }

    private static void markAnswering(String id) {
        answeredId = id;
        answeredAt = Util.getMillis();
    }

    // ── Do servidor ─────────────────────────────────────────────────────────────

    public static void view(FichaPayloads.View payload) {
        deliverView(payload, false);
    }

    static void deliverView(FichaPayloads.View payload, boolean test) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        boolean peek = (payload.flags() & FichaPayloads.PEEK) != 0;
        // Qualquer tela nova da ficha vale mais que um convite guardado.
        prompt = null;
        if ((payload.flags() & FichaPayloads.OPEN) != 0) FichaHud.loadingDone("abrir");
        // A ficha própria alimenta o Perfil do menu; a que o menu pediu fica nele, sem abrir a tela antiga.
        if (FichaMenu.accept(payload, test) && !(current instanceof FichaScreen)) return;
        // A roda pediu a ficha numa aba ou no diálogo de dados: vale para a próxima ficha própria aberta.
        String intent = (payload.flags() & FichaPayloads.OPEN) != 0 && !peek ? takeIntent(test) : null;
        if (current instanceof FichaScreen screen && screen.isTest() == test && screen.isPeek() == peek) {
            screen.update(payload);
            if (intent != null) screen.openOn(intent);
            return;
        }
        if ((payload.flags() & FichaPayloads.OPEN) == 0) {
            if ((payload.flags() & FichaPayloads.PROMPT) == 0 || peek) return;
            // Convite (alt sem ficha no login): abre já se o jogador está livre; senão espera a tela
            // dele fechar (ScreenEvent.Closing arma, o próximo tick abre) por até PROMPT_MS.
            if (current == null && minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new FichaScreen(payload, test, null));
            } else {
                prompt = payload;
                promptTest = test;
                promptAt = Util.getMillis();
                promptArmed = false;
            }
            return;
        }
        // A ficha espiada abre por cima da mesa e volta para ela ao fechar.
        Screen parent = peek && current instanceof MestreScreen mestre && mestre.isTest() == test ? mestre : null;
        FichaScreen opened = new FichaScreen(payload, test, parent);
        if (intent != null) opened.openOn(intent);
        minecraft.setScreen(opened);
    }

    public static void candidates(FichaPayloads.Candidates payload) {
        deliverCandidates(payload.json(), false);
    }

    static void deliverCandidates(String json, boolean test) {
        if (Minecraft.getInstance().screen instanceof FichaScreen screen && screen.isTest() == test) {
            screen.onCandidates(Json.parseObject(json));
        }
    }

    public static void card(FichaPayloads.Card payload) {
        RollCard card = RollCard.parse(Json.parseObject(payload.json()));
        if (card != null) deliverCard(card, false);
    }

    static void deliverCard(RollCard card, boolean test) {
        FichaHud.addCard(card);
        if (card.own() && card.requestId().equals(answeredId)) answeredId = null;
        Screen current = Minecraft.getInstance().screen;
        // A mesa recebe o resultado pela atualização do pedido (TableUpdate), não por aqui.
        if (current instanceof FichaScreen screen && screen.isTest() == test) screen.onCard(card);
    }

    public static void request(FichaPayloads.Request payload) {
        if (payload.active()) {
            long now = Util.getMillis();
            long expires = now + Math.max(5, payload.seconds()) * 1000L;
            boolean fresh = FichaHud.ask(new FichaHud.Ask(payload.id(), payload.gmName(), payload.title(), payload.test(),
                    payload.detail(), now, expires, false));
            if (fresh) chime();
        } else {
            FichaHud.closeAsk(payload.id());
            // Cancelado ou o mestre saiu: o motivo na barra de ação (com outra tela aberta, no chat), em
            // vez de o cartão só sumir. Respondido e vencido chegam sem motivo: o jogador viu o
            // resultado ou o prazo correr no próprio cartão.
            Minecraft minecraft = Minecraft.getInstance();
            if (!payload.detail().isEmpty() && minecraft.player != null) {
                String what = payload.test().isEmpty() ? "O pedido do mestre" : "O pedido de " + payload.test();
                minecraft.player.displayClientMessage(Component.literal(what + " " + payload.detail() + "."), minecraft.screen == null);
            }
        }
    }

    /** Um sino baixo quando chega um pedido novo: o cartão fica no canto e nem sempre se olha para lá. */
    static void chime() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.25f, 0.45f));
    }

    public static void status(FichaPayloads.Status payload) {
        deliverStatus(payload, false);
    }

    static void deliverStatus(FichaPayloads.Status payload, boolean test) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        boolean ok = "ok".equals(payload.state());
        if (!ok) {
            // A resposta pela tecla do HUD que falhou (ou "já a caminho") volta a mostrar as teclas.
            if ("rolar".equals(payload.context())) {
                FichaHud.answering(null);
                answeredId = null;
            }
            // A ficha não veio (site fora, sem vínculo…): o destino pedido pela roda não fica para depois.
            if ("abrir".equals(payload.context())) openIntent = null;
            FichaHud.loadingDone(payload.context());
            // O Perfil do menu pediu e o site recusou: o motivo vai para o cartão, não para o chat.
            if (!test && "abrir".equals(payload.context()) && FichaMenu.failed(payload.message())) return;
        }
        if (current instanceof FichaScreen screen && screen.isTest() == test) {
            screen.onStatus(payload);
            if (!"mestre".equals(payload.context())) return;
        }
        Screen table = current instanceof FichaScreen screen ? screen.parent() : current;
        if (table instanceof MestreScreen mestre && mestre.isTest() == test) {
            mestre.onStatus(payload);
            return;
        }
        // Sem a tela da ficha: o erro (a tecla da ficha com o site fora, vínculo recusado…) e a confirmação de um
        // comando de chat (pedido enviado, vínculo trocado) não podem sumir. Sem tela, a barra de
        // ação; com outra tela aberta, o chat, que continua lá ao fechar.
        if (minecraft.player == null || payload.message().isEmpty()) return;
        if (current == null) {
            minecraft.player.displayClientMessage(Component.literal(payload.message()), true);
        } else if (!(current instanceof FichaScreen) && !(current instanceof MestreScreen)) {
            minecraft.player.displayClientMessage(Component.literal((ok ? "✔ " : "✘ ") + payload.message()), false);
        }
    }

    public static void table(FichaPayloads.Table payload) {
        deliverTable(payload, false);
    }

    static void deliverTable(FichaPayloads.Table payload, boolean test) {
        Minecraft minecraft = Minecraft.getInstance();
        FichaHud.loadingDone("mestre");
        if (minecraft.screen instanceof MestreScreen screen && screen.isTest() == test) {
            screen.refresh(payload);
            return;
        }
        minecraft.setScreen(new MestreScreen(payload, test));
    }

    public static void tableUpdate(FichaPayloads.TableUpdate payload) {
        deliverTableUpdate(payload, false);
    }

    static void deliverTableUpdate(FichaPayloads.TableUpdate payload, boolean test) {
        MestreScreen.log(payload, test);
        MestreScreen mestre = openTable(test);
        if (mestre != null) mestre.onUpdate(payload);
    }

    public static void notice(FichaPayloads.Notice payload) {
        deliverNotice(payload);
    }

    /**
     * O servidor não sabe a tecla que cada um configurou: o aviso traz {@code {tecla}} e o nome da tecla
     * da ficha entra aqui ("aperte ' para ver"); sem tecla atribuída, "use /ficha".
     */
    static void deliverNotice(FichaPayloads.Notice payload) {
        String text = withOpenKey(payload.text());
        // Com o menu do personagem, o aviso aparece na frente da pessoa, no cartão com o personagem dela.
        List<String[]> rows = text.isBlank() ? List.of() : List.<String[]>of(new String[]{text, ""});
        if (CharacterMenu.notice(new CharacterMenu.Notice("AVISO DA FICHA", payload.title(), rows, null, Math.max(4, payload.seconds())))) return;
        FichaHud.notice(payload.title(), text, payload.seconds());
    }

    static String withOpenKey(String text) {
        String placeholder = Texts.KEY_PLACEHOLDER;
        if (!text.contains(placeholder)) return text;
        String key = FichaUi.keyName(FichaKeys.OPEN);
        if (key.isEmpty()) return text.replace("aperte " + placeholder, "use /ficha").replace(placeholder, "/ficha");
        return text.replace(placeholder, key);
    }

    /** A mesa aberta (direto ou por baixo da ficha espiada). */
    @Nullable
    private static MestreScreen openTable(boolean test) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof FichaScreen screen) current = screen.parent();
        return current instanceof MestreScreen mestre && mestre.isTest() == test ? mestre : null;
    }
}
