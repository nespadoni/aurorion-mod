package com.aurorion.ficha.client;

import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.DiceFormula;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import com.tesseraui.TesseraFocusManager;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A mesa do mestre ({@code /ficha mestre}): escolher jogadores por perto, montar o teste (atributo ou
 * perícia, CD visível ou oculta, vantagem/desvantagem, secreto, motivo) — ou pedir <b>dados</b>
 * (rolagem livre: 1d20, 2d6, qualquer fórmula do motor, com CD opcional comparada ao total, que vale
 * até para quem ainda tem a ficha antiga) — e pedir; as respostas chegam
 * ao vivo no painel da direita, agrupadas por pedido. "Ficha" ao lado do jogador abre a ficha dele só
 * para leitura.
 *
 * <h2>Layout</h2>
 * Três colunas (jogadores, teste, respostas) quando a tela tem largura; em tela estreita (GUI de
 * 320–380 px) ficam duas, e a da direita alterna entre "Teste" e "Respostas" com o número de
 * respostas novas na aba. Os grupos de botões quebram linha pela largura medida — nada vaza da
 * coluna — e os rótulos longos encurtam quando não cabem.
 *
 * <h2>Pedidos</h2>
 * Cada pedido é uma "rodada" com as respostas na ordem dos alvos: resposta atrasada de um pedido
 * antigo atualiza a linha dela, nunca a rodada atual. O cartão do pedido atual mostra "3/5
 * responderam", o tempo que falta e "Reenviar" para quem recusou, deixou vencer ou deu erro (com o
 * mesmo teste). Rodadas, seleção e o teste montado ficam no cliente até sair do servidor — fechar e
 * reabrir a mesa não perde nada.
 *
 * <h2>Custo</h2>
 * A árvore do Tessera só é remontada quando algo muda, no máximo uma vez por quadro ({@code dirty});
 * o CSS é interpretado uma vez ({@link FichaUi#template}). Por quadro só rodam os desenhos diretos
 * (rostos, textos, barra) e algumas comparações de relógio; animações são pelo relógio e curtas.
 *
 * <p>Só abre para quem o servidor considera mestre; o servidor confere de novo a cada pedido, e o
 * site confere a staff para vantagem, desvantagem e atributo alternativo.
 */
public final class MestreScreen extends TesseraScreen {
    private static final int MAX_W = 476;
    private static final int MAX_H = 292;
    private static final int PAD = 7;
    private static final int GAP = 4;
    /** Abaixo disto a mesa fica em duas colunas. */
    private static final int COMPACT_W = 380;
    /** Altura e passo das linhas de botões da coluna do teste. */
    private static final int ROW_H = 12, ROW_PITCH = 14;
    /** Rodadas guardadas; a mais velha sai quando chega outra. */
    private static final int MAX_ROUNDS = 6;
    /** "Perto": quem está a até tantos blocos. */
    private static final int NEAR = 10;
    private static final int[] DC_PRESETS = {10, 15, 20, 25};
    /** Os atalhos do modo Dados; o campo ao lado aceita qualquer fórmula do motor. */
    private static final String[] DICE_PRESETS = {"1d20", "1d100", "2d6", "1d6", "1d4"};
    private static final String[] DC_NAMES = {"Fácil", "Médio", "Difícil", "Muito difícil"};
    /** Prazos do lado do cliente: sem confirmação nesse tempo, o botão destrava com um aviso. */
    private static final long SEND_TIMEOUT_MS = 8_000, PEEK_TIMEOUT_MS = 10_000, REFRESH_TIMEOUT_MS = 6_000;
    /** Duração das micro-animações: brilho de resposta nova, barra, clique e o "enviado". */
    private static final long FLASH_MS = 320, BAR_MS = 240, SENT_MS = 1_600, CONFIRM_MS = 3_000;
    private static final String LEFT_DETAIL = "saiu do servidor";

    // ── Estado que sobrevive à tela (até sair do servidor) ─────────────────────

    /** Uma resposta. {@code at}: quando o estado mudou por último (o brilho de chegada parte daí). */
    private record Answer(UUID target, String name, String state, String detail, @Nullable RollCard card, long at) {
    }

    /** Um pedido enviado e as respostas dele, na ordem dos alvos. */
    private static final class Round {
        final String id;
        final boolean local;
        final long createdAt;
        String test;
        /** O pedido como saiu da mesa, para reenviar; {@code null} se veio do {@code /ficha mestre pedir}. */
        @Nullable FichaPayloads.Ask ask;
        final LinkedHashMap<UUID, Answer> answers = new LinkedHashMap<>();
        // A barra de progresso anima de onde estava até o novo valor.
        float barFrom, barTo;
        long barAt;

        Round(String id, boolean local, long createdAt, String test) {
            this.id = id;
            this.local = local;
            this.createdAt = createdAt;
            this.test = test;
        }

        float bar(long now) {
            float t = Math.min(1f, (now - barAt) / (float) BAR_MS);
            float eased = 1f - (1f - t) * (1f - t) * (1f - t);
            return barFrom + (barTo - barFrom) * eased;
        }

        long latest() {
            long at = 0;
            for (Answer a : answers.values()) at = Math.max(at, a.at());
            return at;
        }
    }

    /** Mais nova primeiro. */
    private static final List<Round> ROUNDS = new ArrayList<>();
    /** Rodadas limpas ou descartadas ("t:"/"r:" + id): resposta atrasada delas não ressuscita a linha. */
    private static final Set<String> GONE = new LinkedHashSet<>();
    /** Quem saiu do servidor durante um pedido; some quando a lista é atualizada e ele está lá. */
    private static final Set<UUID> LEFT = new LinkedHashSet<>();
    private static final Set<UUID> SELECTED_REAL = new LinkedHashSet<>();
    private static final Set<UUID> SELECTED_TEST = new LinkedHashSet<>();
    /** O pedido que saiu e ainda não foi confirmado: a primeira rodada nova que chegar é dele. */
    @Nullable private static FichaPayloads.Ask pendingAsk;
    private static boolean pendingLocal;

    // O teste montado: a mesa reabre com a última escolha.
    private static String attribute = "des";
    private static String skill = "furtividade";
    private static String altAttribute = "";
    private static boolean showDc = true;
    private static String mode = "normal";
    private static boolean secret;
    /** Modo Dados: o pedido é uma rolagem livre ({@code FORMULA}), sem atributo, perícia nem vantagem. */
    private static boolean dice;
    private static final TesseraInputState CD = fresh();
    private static final TesseraInputState MOTIVO = fresh();
    private static final TesseraInputState FORMULA = fresh();

    static {
        setText(FORMULA, "1d20");
    }

    // ── Estado desta tela ───────────────────────────────────────────────────────

    private final boolean test;
    private List<FichaPayloads.Player> players;
    private int radius;
    private int requestSeconds;
    private final TesseraInputState search = fresh();
    private final Map<String, TesseraInputState> inputs = new HashMap<>();
    private int playerPage;
    private int answerPage;

    // Tela estreita: a coluna da direita mostra o teste ou as respostas.
    private boolean showAnswers;
    private int unseen;

    private String statusKind = "info";
    private String statusText = "";

    // Ações em andamento (o botão mostra e trava) e os momentos das animações.
    private boolean sending;
    private long sendingAt;
    private long sentOkAt;
    @Nullable private UUID peeking;
    private long peekAt;
    private boolean refreshing;
    private long refreshAt;
    private long confirmClearAt;
    /** Cancelar pede um segundo clique em 3 s (há gente aguardando); {@code canceling}: esperando o servidor. */
    private long confirmCancelAt;
    private boolean canceling;
    private long cancelAt;
    /** Primeira rodada com gente aguardando a vencer: nessa hora a mesa se remonta sozinha. */
    private long nextDeadline;

    private TesseraPanel root = TesseraPanel.column(0, 0, 1, 1);
    private final List<Consumer<GuiGraphics>> overlays = new ArrayList<>();
    private Map<String, Runnable> clicks = new HashMap<>();
    private Map<String, Consumer<String>> submits = new HashMap<>();
    private TesseraPanel building = root;
    private boolean compact;
    private boolean dirty = true;
    /** A moldura só anima ao abrir; nas remontagens seguintes ela fica parada. */
    private boolean arriving = true;
    /** Áreas clicáveis desta montagem, para o realce de clique (o Tessera 1.1 ignora {@code :active}). */
    private final List<int[]> pressables = new ArrayList<>();
    @Nullable private int[] pressed;
    private long pressedAt;
    /** Onde começa a coluna das respostas (a roda do mouse decide o que paginar por ela). */
    private int answersX = Integer.MAX_VALUE;
    /** Campos visíveis nesta montagem, na ordem do Tab. */
    private final List<TesseraInputState> fields = new ArrayList<>();
    private int timerSeconds = -1;
    private String timerText = "";

    /**
     * Tooltips próprios: no Tessera 1.1 o atributo {@code tooltip} da raiz de um painel (o row
     * clicável) é ignorado, e o dos filhos sai numa linha só, por baixo do que o Java desenha depois.
     * Cada área registra o texto; o desenho vem por último, quebrado na largura e preso à tela.
     */
    private record Tip(int x, int y, int w, int h, String text) {
    }

    private final List<Tip> tips = new ArrayList<>();
    private String hoverText = "";
    /** O pedido de outro mestre para mim que o HUD mostraria (a mesa o cobre); a mesa avisa no cabeçalho. */
    @Nullable private FichaHud.Ask hudAsk;
    private long hoverSince;
    @Nullable private FichaUi.Tooltip hoverTooltip;
    /**
     * Animações de chegada (.ask-in, .done-in, .confirm-in, .m-new-in) só na montagem em que o estado
     * apareceu: a mesa se remonta a cada clique e resposta, e o clarão não pode repetir junto.
     */
    private String askAnimated = "";
    private long doneAnimated, clearAnimated, cancelAnimated;
    private int newAnimated;

    MestreScreen(FichaPayloads.Table table, boolean test) {
        super(Component.literal("Mesa do mestre"));
        this.test = test;
        inputs.put("cd", CD);
        inputs.put("motivo", MOTIVO);
        inputs.put("formula", FORMULA);
        inputs.put("busca", search);
        CD.focused = false;
        MOTIVO.focused = false;
        FORMULA.focused = false;
        apply(table);
        statusText = test ? "TESTE LOCAL — jogadores e respostas fictícios." : "";
    }

    boolean isTest() {
        return test;
    }

    // ── Dados ───────────────────────────────────────────────────────────────────

    void refresh(FichaPayloads.Table table) {
        apply(table);
        if (refreshing) {
            refreshing = false;
            statusKind = "ok";
            statusText = players.isEmpty() ? "Lista atualizada: ninguém por perto." : "Lista atualizada: " + players.size() + " por perto.";
        }
        dirty = true;
    }

    private void apply(FichaPayloads.Table table) {
        players = table.players();
        radius = table.radius();
        requestSeconds = table.requestSeconds();
        Set<UUID> online = new LinkedHashSet<>();
        for (FichaPayloads.Player p : players) online.add(p.id());
        LEFT.removeAll(online);
        selected().retainAll(online);
    }

    private Set<UUID> selected() {
        return test ? SELECTED_TEST : SELECTED_REAL;
    }

    private static String key(boolean local, String id) {
        return (local ? "t:" : "r:") + id;
    }

    @Nullable
    private static Round round(String id, boolean local) {
        for (Round r : ROUNDS) if (r.local == local && r.id.equals(id)) return r;
        return null;
    }

    /** Guarda a resposta (mesmo com a mesa fechada); a mesa aberta se remonta pelo {@link #onUpdate}. */
    static void log(FichaPayloads.TableUpdate update, boolean local) {
        String id = update.requestId();
        if (GONE.contains(key(local, id))) return;
        long now = Util.getMillis();
        Round round = round(id, local);
        if (round == null) {
            round = new Round(id, local, now, update.test());
            // "aguardando" de um id novo é o pedido que acabou de sair: vai para o topo e herda o que reenviar.
            if ("aguardando".equals(update.state())) {
                if (pendingAsk != null && pendingLocal == local) round.ask = pendingAsk;
                ROUNDS.add(0, round);
            } else {
                ROUNDS.add(round);
            }
            while (ROUNDS.size() > MAX_ROUNDS) forget(ROUNDS.remove(ROUNDS.size() - 1));
        }
        if (!update.test().isEmpty()) round.test = update.test();
        RollCard card = update.card().isEmpty() ? null : RollCard.parse(Json.parseObject(update.card()));
        Answer old = round.answers.get(update.target());
        boolean changed = old == null || !old.state().equals(update.state()) || (card != null) != (old.card() != null);
        float before = round.bar(now);
        round.answers.put(update.target(), new Answer(update.target(), update.name(), update.state(), update.detail(), card,
                changed ? now : old.at()));
        round.barFrom = before;
        round.barTo = progress(round);
        round.barAt = now;
        if ("erro".equals(update.state()) && update.detail().startsWith(LEFT_DETAIL)) {
            LEFT.add(update.target());
            (local ? SELECTED_TEST : SELECTED_REAL).remove(update.target());
        }
    }

    private static void forget(Round round) {
        GONE.add(key(round.local, round.id));
        Iterator<String> it = GONE.iterator();
        while (GONE.size() > 64 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    static void clearLog() {
        ROUNDS.clear();
        GONE.clear();
        LEFT.clear();
        SELECTED_REAL.clear();
        SELECTED_TEST.clear();
        pendingAsk = null;
    }

    void onUpdate(FichaPayloads.TableUpdate update) {
        // Em tela estreita, com o teste à mostra, a aba das respostas conta o que chegou.
        if (compact && !showAnswers && !"aguardando".equals(update.state()) && !"rolando".equals(update.state())) unseen++;
        dirty = true;
    }

    void onStatus(FichaPayloads.Status status) {
        boolean ok = "ok".equals(status.state());
        // "ocupado" (já a caminho, espere) é espera, não erro — como na ficha.
        statusKind = ok ? "ok" : "ocupado".equals(status.state()) ? "espera" : "erro";
        statusText = status.message();
        if ("mestre".equals(status.context())) {
            // O contexto "mestre" serve ao pedido e à ficha espiada: um erro destrava os dois.
            if (canceling) {
                // O cancelar não corre junto com um envio (os botões se travam): este status é dele.
                canceling = false;
            } else if (sending) {
                pendingAsk = null;
                sending = false;
                if (ok) {
                    sentOkAt = Util.getMillis();
                    MOTIVO.text = "";
                    MOTIVO.cursor = 0;
                    MOTIVO.selStart = 0;
                    answerPage = 0;
                    // Tela estreita: mostra as respostas chegando.
                    if (compact) {
                        showAnswers = true;
                        unseen = 0;
                    }
                }
            }
            if (!ok) peeking = null;
            if (!sending) pendingAsk = null;
        }
        dirty = true;
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────────────

    @Override
    protected void init() {
        FichaUi.reloadCss();
        // Voltando da ficha espiada (ou a janela mudou): o "abrindo…" já cumpriu o papel.
        peeking = null;
        arriving = true;
        rebuild();
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        timers(now);
        // Só uma comparação por quadro: o cartão do HUD mudou (chegou ou saiu um pedido para mim)?
        FichaHud.Ask ask = FichaHud.currentAsk();
        if (ask != hudAsk) {
            hudAsk = ask;
            dirty = true;
        }
        if (dirty) rebuild();
        graphics.fill(0, 0, width, height, 0x98000000);
        root.render(graphics, mouseX, mouseY);
        for (Consumer<GuiGraphics> overlay : overlays) overlay.accept(graphics);
        int[] p = pressed;
        if (p != null) {
            long age = now - pressedAt;
            if (age < FichaUi.PRESS_MS) FichaUi.press(graphics, p[0], p[1], p[2], p[3], age);
            else pressed = null;
        }
        renderTesseraOverlays(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        hoverTip(graphics, mouseX, mouseY, now);
    }

    /** O tooltip da área sob o mouse (a última registrada vence: botões ficam sobre as linhas). */
    private void hoverTip(GuiGraphics graphics, int mouseX, int mouseY, long now) {
        String text = "";
        for (int i = tips.size() - 1; i >= 0; i--) {
            Tip t = tips.get(i);
            if (mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() && mouseY < t.y() + t.h()) {
                text = t.text();
                break;
            }
        }
        if (!text.equals(hoverText)) {
            // Quebra uma vez por texto; o atraso curto evita tooltips piscando ao passar o mouse pela lista.
            hoverText = text;
            hoverSince = now;
            hoverTooltip = text.isEmpty() ? null : FichaUi.tooltip(font, text, Math.min(220, width / 2));
        }
        if (hoverTooltip != null && now - hoverSince >= FichaUi.TIP_DELAY_MS) {
            FichaUi.drawTooltip(graphics, font, hoverTooltip, mouseX, mouseY, width, height);
        }
    }

    private void tip(int x, int y, int w, int h, String text) {
        if (!text.isEmpty()) tips.add(new Tip(x, y, w, h, text));
    }

    /** Prazos do cliente: poucas comparações por quadro, e só remonta quando um deles vence. */
    private void timers(long now) {
        if (sending && now - sendingAt > SEND_TIMEOUT_MS) {
            sending = false;
            pendingAsk = null;
            statusKind = "erro";
            statusText = "O servidor não confirmou o pedido. Confira as respostas ou tente de novo.";
            dirty = true;
        }
        if (peeking != null && now - peekAt > PEEK_TIMEOUT_MS) {
            peeking = null;
            statusKind = "erro";
            statusText = "A ficha não chegou. O site pode estar lento — tente de novo.";
            dirty = true;
        }
        if (refreshing && now - refreshAt > REFRESH_TIMEOUT_MS) {
            refreshing = false;
            statusKind = "erro";
            statusText = "O servidor não mandou a lista. Tente Atualizar de novo.";
            dirty = true;
        }
        if (sentOkAt > 0 && now - sentOkAt > SENT_MS) {
            sentOkAt = 0;
            dirty = true;
        }
        if (canceling && now - cancelAt > SEND_TIMEOUT_MS) {
            canceling = false;
            statusKind = "erro";
            statusText = "O servidor não confirmou o cancelamento. Tente de novo.";
            dirty = true;
        }
        if (confirmCancelAt > 0 && now - confirmCancelAt > CONFIRM_MS) {
            confirmCancelAt = 0;
            dirty = true;
        }
        if (confirmClearAt > 0 && now - confirmClearAt > CONFIRM_MS) {
            confirmClearAt = 0;
            dirty = true;
        }
        if (nextDeadline > 0 && now >= nextDeadline) {
            nextDeadline = 0;
            dirty = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Entrada ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicar fora do campo tira o foco dele; clicar no campo devolve.
        TesseraFocusManager.clear();
        inputs.values().forEach(state -> state.focused = false);
        if (root.mouseClicked(mouseX, mouseY, button)) {
            for (int[] r : pressables) {
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    pressed = r;
                    pressedAt = Util.getMillis();
                    break;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** O campo com o cursor. O foco do Tessera vive no estado do campo, não no FocusManager. */
    @Nullable
    private TesseraInputState focusedField() {
        for (TesseraInputState state : inputs.values()) if (state.focused) return state;
        return null;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedField() != null) return root.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        TesseraInputState field = focusedField();
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleField(field, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (field != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Esc no campo: limpa a busca, ou só sai do campo. O segundo Esc fecha a mesa.
                if (field == search && !search.text.isEmpty()) {
                    setText(search, "");
                    playerPage = 0;
                    dirty = true;
                } else {
                    field.focused = false;
                }
                return true;
            }
            // Enter vira o onsubmit do campo; o resto (letras, setas, Ctrl+A no texto) fica com ele.
            root.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // Esc em camadas, como na ficha: primeiro desarma o "Certeza?", depois fecha.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (confirmCancelAt > 0 || confirmClearAt > 0)) {
            confirmCancelAt = 0;
            confirmClearAt = 0;
            dirty = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            ask();
            return true;
        }
        // Pedido de outro mestre para mim (o selo do cabeçalho): as teclas de pedido, se o jogador as
        // atribuiu (não têm padrão), valem aqui também, como os botões Rolar/Recusar.
        FichaHud.Ask mine = hudAsk;
        if (mine != null && (FichaKeys.ROLL_REQUEST.matches(keyCode, scanCode) || FichaKeys.DECLINE_REQUEST.matches(keyCode, scanCode))) {
            answerMine(FichaKeys.ROLL_REQUEST.matches(keyCode, scanCode));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            if (fields.contains(search)) focus(search);
            return true;
        }
        if (!ctrl && keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + Catalog.ATTRIBUTES.size()) {
            pickAttribute(Catalog.ATTRIBUTES.get(keyCode - GLFW.GLFW_KEY_1));
            return true;
        }
        // A tecla depois dos atributos (7): o modo Dados.
        if (!ctrl && keyCode == GLFW.GLFW_KEY_1 + Catalog.ATTRIBUTES.size()) {
            pickDice();
            return true;
        }
        if (FichaKeys.TABLE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        // O TesseraScreen 1.1 liga o overlay de depuração com a tecla I.
        if (keyCode == GLFW.GLFW_KEY_I) return false;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void cycleField(@Nullable TesseraInputState current, boolean back) {
        if (fields.isEmpty()) return;
        int i = current == null ? (back ? 0 : -1) : fields.indexOf(current);
        int next = Math.floorMod(i + (back ? -1 : 1), fields.size());
        focus(fields.get(next));
    }

    private void focus(TesseraInputState state) {
        inputs.values().forEach(s -> s.focused = false);
        state.focused = true;
        state.focusStartMs = System.currentTimeMillis();
        int end = state.text == null ? 0 : state.text.length();
        state.cursor = end;
        state.selStart = end;
    }

    /** A roda pagina a coluna sob o mouse: jogadores à esquerda, respostas à direita. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int step = -(int) Math.signum(scrollY);
            if (mouseX >= answersX && (!compact || showAnswers)) answerPage = Math.max(0, answerPage + step);
            else playerPage = Math.max(0, playerPage + step);
            dirty = true; // cada coluna limita a página ao que existe
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Ações ───────────────────────────────────────────────────────────────────

    /** {@code false}: o servidor não tem o canal da ficha (nada saiu; quem chamou destrava). */
    private boolean send(CustomPacketPayload payload) {
        if (!test) return FichaClient.send(payload);
        FichaTestSession.handle(payload);
        return true;
    }

    /** O pacote não saiu: volta ao estado de antes, com o motivo no rodapé. */
    private void notSent() {
        statusKind = "erro";
        statusText = "Este servidor não tem a ficha do Aurorion: a mesa só funciona com ela instalada.";
        dirty = true;
    }

    /** {@code null} se a CD está boa (vazia ou 1–99); senão, o que dizer. */
    @Nullable
    private static String dcProblem() {
        String raw = CD.text == null ? "" : CD.text.strip();
        if (raw.isEmpty()) return null;
        try {
            int dc = Integer.parseInt(raw);
            return dc >= 1 && dc <= 99 ? null : "A CD vai de 1 a 99 (ou deixe vazia).";
        } catch (NumberFormatException e) {
            return "A CD é um número de 1 a 99 (ou deixe vazia).";
        }
    }

    private static int dc() {
        String raw = CD.text == null ? "" : CD.text.strip();
        if (raw.isEmpty() || dcProblem() != null) return 0;
        return Integer.parseInt(raw);
    }

    /** {@code null} se a fórmula do modo Dados vale (a gramática do motor); senão, o que dizer. */
    @Nullable
    private static String formulaProblem() {
        String problem = DiceFormula.problem(text(FORMULA));
        return problem == null ? null : Character.toUpperCase(problem.charAt(0)) + problem.substring(1) + ".";
    }

    /** A fórmula do modo Dados como o motor a escreve ({@code d20} → {@code 1d20}); a digitada se não valer. */
    private static String formula() {
        String canonical = DiceFormula.normalize(text(FORMULA));
        return canonical == null ? text(FORMULA).strip() : canonical;
    }

    private void ask() {
        if (sending || canceling) return;
        if (selected().isEmpty()) {
            status("erro", "Escolha pelo menos um jogador (Ctrl+A marca todos).");
            return;
        }
        String bad = dice ? formulaProblem() : null;
        if (bad != null) {
            status("erro", "Fórmula de dados: " + bad);
            if (fields.contains(FORMULA)) focus(FORMULA);
            return;
        }
        String problem = dcProblem();
        if (problem != null) {
            status("erro", problem);
            if (fields.contains(CD)) focus(CD);
            return;
        }
        if (dice) {
            submit(FichaPayloads.Ask.dice(new ArrayList<>(selected()), formula(), dc(), showDc, secret, clip(text(MOTIVO).strip(), 60)));
            return;
        }
        boolean skillTest = !skill.isEmpty();
        submit(new FichaPayloads.Ask(new ArrayList<>(selected()), skillTest ? "skill" : "attribute", skillTest ? skill : attribute,
                skillTest ? altAttribute : attribute, dc(), showDc, mode, secret, clip(text(MOTIVO).strip(), 60)));
    }

    /** O mesmo teste da rodada para quem recusou, deixou vencer ou deu erro. */
    private void resend(Round round) {
        FichaPayloads.Ask base = round.ask;
        List<UUID> targets = resendTargets(round, Util.getMillis());
        if (base == null || targets.isEmpty() || sending) return;
        submit(new FichaPayloads.Ask(targets, base.kind(), base.skill(), base.attribute(), base.dc(), base.showDc(), base.mode(),
                base.secret(), base.label(), base.formula()));
    }

    private void submit(FichaPayloads.Ask ask) {
        // Tudo marcado antes de mandar: o modo de teste responde na mesma chamada.
        sending = true;
        sendingAt = Util.getMillis();
        sentOkAt = 0;
        pendingAsk = ask;
        pendingLocal = test;
        answerPage = 0;
        statusKind = "espera";
        statusText = "Enviando o pedido a " + Texts.count(ask.targets().size(), "jogador", "jogadores") + "…";
        dirty = true;
        if (!send(ask)) {
            sending = false;
            pendingAsk = null;
            notSent();
        }
    }

    /**
     * Cancela o pedido para quem ainda não respondeu (quem já rolou ou está rolando não muda). O
     * servidor (ou o modo de teste) manda "cancelado" por alvo e depois o status.
     */
    private void cancel(Round round) {
        if (canceling || sending) return;
        confirmCancelAt = 0;
        canceling = true;
        cancelAt = Util.getMillis();
        statusKind = "espera";
        statusText = "Cancelando o pedido " + round.id + "…";
        dirty = true;
        if (!send(new FichaPayloads.Action("cancelar", round.id, ""))) {
            canceling = false;
            notSent();
        }
    }

    private void peek(FichaPayloads.Player p) {
        if (peeking != null) return;
        peeking = p.id();
        peekAt = Util.getMillis();
        statusKind = "espera";
        statusText = "Abrindo a ficha de " + p.name() + "…";
        dirty = true;
        if (!send(new FichaPayloads.Action("espiar", p.id().toString(), ""))) {
            peeking = null;
            notSent();
        }
    }

    private void requestRefresh() {
        if (refreshing) return;
        refreshing = true;
        refreshAt = Util.getMillis();
        statusKind = "espera";
        statusText = "Buscando os jogadores por perto…";
        dirty = true;
        if (!send(new FichaPayloads.Action("mestre", "", ""))) {
            refreshing = false;
            notSent();
        }
    }

    private void selectAll() {
        List<FichaPayloads.Player> shown = filtered();
        boolean all = !shown.isEmpty();
        for (FichaPayloads.Player p : shown) if (!LEFT.contains(p.id()) && !selected().contains(p.id())) all = false;
        // Tudo já marcado: o mesmo atalho desmarca (o botão "Todos" alterna).
        for (FichaPayloads.Player p : shown) {
            if (LEFT.contains(p.id())) continue;
            if (all) selected().remove(p.id());
            else selected().add(p.id());
        }
        dirty = true;
    }

    private void selectNear() {
        selected().clear();
        for (FichaPayloads.Player p : filtered()) if (p.distance() <= NEAR && !LEFT.contains(p.id())) selected().add(p.id());
        dirty = true;
    }

    /** O modo Dados (botão "Dados" ou a tecla 7); o atributo e a perícia ficam guardados para a volta. */
    private void pickDice() {
        dice = true;
        dirty = true;
    }

    private void pickAttribute(Catalog.Attribute a) {
        dice = false;
        attribute = a.id();
        List<Catalog.Skill> skills = Catalog.skillsOf(a.id());
        // Mesmo grupo de novo: fica a perícia que estava.
        if (skill.isEmpty() || Catalog.skill(skill) == null || !a.id().equals(Catalog.skill(skill).attribute())) {
            skill = skills.isEmpty() ? "" : skills.get(0).id();
        }
        altAttribute = "";
        dirty = true;
    }

    private void status(String kind, String text) {
        statusKind = kind;
        statusText = text;
        dirty = true;
    }

    private static String testName() {
        if (dice) return formula();
        return skill.isEmpty() ? Catalog.attributeLabel(attribute) : Texts.testName("skill", skill, altAttribute);
    }

    /** "CD 15 (oculta) · com vantagem · secreto · ouvir a porta" — o que foi pedido, para o tooltip da rodada. */
    private static String askDetail(FichaPayloads.Ask ask) {
        List<String> parts = new ArrayList<>();
        parts.add(ask.dc() > 0 ? "CD " + ask.dc() + (ask.showDc() ? "" : " (oculta)") : "sem CD");
        if (!Texts.modeLabel(ask.mode()).isEmpty()) parts.add(Texts.modeLabel(ask.mode()));
        if (ask.secret()) parts.add("secreto");
        if (!ask.label().isEmpty()) parts.add(ask.label());
        return String.join(" · ", parts);
    }

    private List<FichaPayloads.Player> filtered() {
        String query = Catalog.normalize(text(search));
        if (query.isEmpty()) return players;
        List<FichaPayloads.Player> out = new ArrayList<>();
        for (FichaPayloads.Player p : players) {
            if (Catalog.normalize(p.name()).contains(query) || Catalog.normalize(p.profile()).contains(query)) out.add(p);
        }
        return out;
    }

    // ── Classificação das respostas ─────────────────────────────────────────────

    private enum Kind { WAIT, ROLLING, OK, CRIT, FAIL, ROLLED, DECLINED, EXPIRED, CANCELED, LEFT, ERROR }

    /** A ordem dos trechos da barra do pedido (o desenho é por frame: a lista é uma só). */
    private static final Kind[] BAR_ORDER = {Kind.CRIT, Kind.OK, Kind.ROLLED, Kind.FAIL, Kind.DECLINED};

    private Kind kind(Round round, Answer a, long now) {
        if (a.card() != null) {
            String outcome = Texts.outcome(a.card().roll());
            // Como FichaUi.outcomeColor, o d20 e o HUD: todo crítico é dourado ("CRÍTICO, MAS FALHA" também).
            if (outcome.startsWith("CRÍTICO")) return Kind.CRIT;
            if (outcome.contains("FALHA")) return Kind.FAIL;
            if (outcome.equals("SUCESSO")) return Kind.OK;
            return Kind.ROLLED;
        }
        return switch (a.state()) {
            // O servidor não avisa quando vence (o prazo é conferido ao tocar no pedido): a mesa calcula.
            case "aguardando" -> now > round.createdAt + requestSeconds * 1000L ? Kind.EXPIRED : Kind.WAIT;
            case "rolando" -> Kind.ROLLING;
            case "recusou" -> Kind.DECLINED;
            case "expirou" -> Kind.EXPIRED;
            case "cancelado" -> Kind.CANCELED;
            case "rolou" -> Kind.ROLLED;
            case "erro" -> a.detail().startsWith(LEFT_DETAIL) ? Kind.LEFT : Kind.ERROR;
            default -> Kind.ERROR;
        };
    }

    private static boolean responded(Answer a) {
        return a.card() != null || "rolou".equals(a.state()) || "recusou".equals(a.state());
    }

    private static float progress(Round round) {
        if (round.answers.isEmpty()) return 0f;
        int done = 0;
        for (Answer a : round.answers.values()) if (responded(a)) done++;
        return done / (float) round.answers.size();
    }

    private List<UUID> resendTargets(Round round, long now) {
        List<UUID> out = new ArrayList<>();
        for (Answer a : round.answers.values()) {
            Kind k = kind(round, a, now);
            if ((k == Kind.DECLINED || k == Kind.EXPIRED || k == Kind.CANCELED || k == Kind.ERROR) && !LEFT.contains(a.target())) out.add(a.target());
        }
        return out;
    }

    private static int color(Kind k) {
        return switch (k) {
            case OK -> FichaUi.OK;
            case CRIT -> FichaUi.CRIT;
            case FAIL, ERROR -> FichaUi.BAD;
            case ROLLED -> FichaUi.GOLD_HI;
            case ROLLING -> FichaUi.WARN;
            case DECLINED -> 0xFFE09A9A;
            case WAIT, EXPIRED, CANCELED, LEFT -> FichaUi.FAINT;
        };
    }

    @Nullable
    private Round current() {
        for (Round r : ROUNDS) if (r.local == test) return r;
        return null;
    }

    // ── Montagem ────────────────────────────────────────────────────────────────

    private String on(String name, Runnable action) {
        clicks.put(name, action);
        return name;
    }

    private String onText(String name, Consumer<String> action) {
        submits.put(name, action);
        return name;
    }

    private void panel(String html, int x, int y, int w, int h) {
        FichaUi.add(building, html, clicks, submits, inputs, x, y, w, h);
    }

    /** Um painel clicável: ganha o realce de clique e o tooltip. */
    private void pressable(String html, int x, int y, int w, int h, String tooltip) {
        panel(html, x, y, w, h);
        pressables.add(new int[]{x, y, w, h});
        tip(x, y, w, h, tooltip);
    }

    /** Um rótulo de uma linha num painel próprio; cortado com "…", o texto inteiro vai para o tooltip. */
    private void label(String cls, String text, int x, int y, int w, int h, int size) {
        String shown = FichaUi.fit(font, text, w, size);
        panel("<col class=\"cell\"><label class=\"" + cls + "\">" + FichaUi.esc(shown) + "</label></col>", x, y, w, h);
        if (!shown.equals(text)) tip(x, y, w, h, text);
    }

    /**
     * Pedido de outro mestre para mim, no cabeçalho: o selo (quem pede o quê) e os botões Rolar e
     * Recusar, sem letra fixa — as teclas de pedido não têm padrão; atribuídas, o tooltip diz quais.
     * Rolar fecha a mesa para o d20 aparecer no HUD; recusar fica. Sem espaço, o selo some antes dos botões.
     */
    private void askBadge(int ix, int y, int iw, int buttonsW) {
        FichaHud.Ask mine = hudAsk;
        if (mine == null) return;
        String keys = FichaUi.keyName(FichaKeys.ROLL_REQUEST), decline = FichaUi.keyName(FichaKeys.DECLINE_REQUEST);
        boolean rolling = FichaClient.isAnswering(mine.id());
        String about = (mine.gmName().isEmpty() ? "Outro mestre" : mine.gmName()) + " pede: " + mine.test()
                + (mine.detail().isEmpty() ? "" : "\n" + mine.detail());
        int rollW = FichaUi.buttonWidth(font, rolling ? "Rolando…" : "Rolar", 6, 5, true, true);
        int declineW = rolling ? 0 : FichaUi.buttonWidth(font, "Recusar", 6, 5, true, false);
        int titleW = FichaUi.width(font, "Pedir um teste", 10) + 8;
        int room = iw - buttonsW - 10 - titleW;
        int actionsW = rollW + (declineW > 0 ? 3 + declineW : 0);
        if (room < actionsW) return;
        int right = ix + iw - buttonsW - 6;
        int by = y + PAD + 8;
        int bx = right - actionsW;
        if (rolling) {
            panel("<row class=\"b-off\"><label>ROLANDO…</label></row>", bx, by, rollW, 11);
            tip(bx, by, rollW, 11, "Rolando o pedido: o resultado aparece no HUD.");
        } else {
            pressable("<row class=\"b-gold\" onclick=\"" + on("pedido_rolar", () -> answerMine(true)) + "\"><label>ROLAR</label></row>",
                    bx, by, rollW, 11, "Rolar\n" + about + "\nFecha a mesa para o d20 aparecer no HUD"
                            + (keys.isEmpty() ? "." : " (atalho: " + keys + ")."));
            pressable("<row class=\"b\" onclick=\"" + on("pedido_recusar", () -> answerMine(false)) + "\"><label>RECUSAR</label></row>",
                    bx + rollW + 3, by, declineW, 11, "Recusar\nA mesa de " + (mine.gmName().isEmpty() ? "quem pediu" : mine.gmName())
                            + " fica sabendo" + (decline.isEmpty() ? "." : " (atalho: " + decline + ")."));
        }
        String chip = "Pedido para você: " + mine.test();
        int chipW = Math.min(room - actionsW - 3, FichaUi.width(font, chip, 5) + 10);
        if (chipW < 50) return;
        int chipX = bx - 3 - chipW;
        boolean fresh = !mine.id().equals(askAnimated);
        askAnimated = mine.id();
        panel("<row class=\"" + (fresh ? "m-ask ask-in" : "m-ask") + "\"><label>" + FichaUi.esc(FichaUi.fit(font, chip, chipW - 8, 5))
                + "</label></row>", chipX, by, chipW, 11);
        tip(chipX, by, chipW, 11, about);
    }

    /** Rola (fecha a mesa, o d20 aparece no HUD) ou recusa (fica, com o aviso no rodapé) o pedido do selo. */
    private void answerMine(boolean roll) {
        FichaHud.Ask mine = hudAsk;
        if (mine == null || !FichaClient.answer(mine, roll)) return;
        if (roll) onClose();
        else status("info", "Pedido de " + (mine.gmName().isEmpty() ? "outro mestre" : mine.gmName()) + " recusado.");
    }

    private void rebuild() {
        dirty = false;
        if (width <= 0 || height <= 0) return;
        clicks = new HashMap<>();
        submits = new HashMap<>();
        overlays.clear();
        pressables.clear();
        tips.clear();
        fields.clear();
        // O Tessera só soma campos ao FocusManager; sem limpar, a lista guardaria os de montagens velhas.
        TesseraFocusManager.clear();
        building = TesseraPanel.column(0, 0, width, height);

        int w = Math.min(width - 12, MAX_W);
        int h = Math.min(height - 12, MAX_H);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int ix = x + PAD, iw = w - 2 * PAD;
        compact = iw < COMPACT_W;
        if (!compact) showAnswers = false;
        panel("<col class=\"" + (arriving ? "frame-gm" : "frame-gm-still") + "\"></col>", x, y, w, h);
        arriving = false;

        // Cabeçalho: título à esquerda, Atualizar e fechar à direita (cada um com o próprio realce).
        int refreshW = Math.max(FichaUi.buttonWidth(font, "Atualizar", 6, 7, true, false), FichaUi.buttonWidth(font, "Buscando…", 6, 7, true, false));
        int closeW = FichaUi.buttonWidth(font, "×", 8, 5, false, false);
        int buttonsW = refreshW + 3 + closeW;
        panel("<col class=\"cell\"><label class=\"kicker-gold\">Mesa do mestre</label></col>", ix, y + PAD, iw - buttonsW - 6, 7);
        label("name", "Pedir um teste", ix, y + PAD + 7, iw - buttonsW - 6, 14, 10);
        if (hudAsk != null) askBadge(ix, y, iw, buttonsW);
        if (refreshing) {
            panel("<row class=\"b-off\"><label>BUSCANDO…</label></row>", ix + iw - buttonsW, y + PAD, refreshW, 14);
            tip(ix + iw - buttonsW, y + PAD, refreshW, 14, "Esperando a lista do servidor");
        } else {
            pressable("<row class=\"b\" onclick=\"" + on("atualizar", this::requestRefresh) + "\"><label>ATUALIZAR</label></row>",
                    ix + iw - buttonsW, y + PAD, refreshW, 14, "Atualizar\nBuscar de novo os jogadores a até " + radius + " blocos.");
        }
        pressable("<row class=\"b-icon\" onclick=\"" + on("fechar", this::onClose) + "\"><label>×</label></row>",
                ix + iw - closeW, y + PAD, closeW, 14, "Fechar (Esc)\nOs pedidos continuam valendo e as respostas ficam guardadas.");

        int top = y + PAD + 26;
        int footY = y + h - PAD - 11;
        int bodyH = footY - GAP - top;
        long now = Util.getMillis();
        if (compact) {
            int leftW = Math.max(110, Math.min(140, iw * 40 / 100));
            int rightW = iw - leftW - GAP;
            int rx = ix + leftW + GAP;
            playersColumn(ix, top, leftW, bodyH, now);
            answersX = rx;
            switcher(rx, top, rightW);
            if (showAnswers) answersColumn(rx, top + 16, rightW, bodyH - 16, now);
            else testColumn(rx, top + 16, rightW, bodyH - 16);
        } else {
            int leftW = Math.max(104, Math.min(150, iw * 28 / 100));
            int rightW = Math.max(100, Math.min(150, iw * 27 / 100));
            int midW = iw - leftW - rightW - 2 * GAP;
            playersColumn(ix, top, leftW, bodyH, now);
            testColumn(ix + leftW + GAP, top, midW, bodyH);
            answersX = ix + leftW + GAP + midW + GAP;
            answersColumn(answersX, top, rightW, bodyH, now);
        }
        footer(ix, footY + 1, iw);
        nextDeadline = nextDeadline(now);
        building.layout();
        root = building;
    }

    /** Situação à esquerda; os atalhos à direita, discretos, quando sobra espaço. */
    private void footer(int x, int y, int w) {
        String text = statusText.isEmpty() ? "Pedidos vencem em " + Texts.duration(requestSeconds) + ". Quem não tem o mod recebe o pedido no chat."
                : statusText;
        String cls = statusText.isEmpty() ? "status-info" : "status-" + statusKind;
        String keys = "Enter pede · Ctrl+A todos · 1–6 atributo · 7 dados · Esc fecha";
        int keysW = FichaUi.width(font, keys, 5);
        if (w - keysW - 8 < 170) {
            keys = "Enter pede · Esc fecha";
            keysW = FichaUi.width(font, keys, 5);
        }
        boolean showKeys = w - keysW - 8 >= 150;
        int statusW = showKeys ? w - keysW - 8 : w;
        label(cls, text, x, y, statusW, 9, 6);
        if (showKeys) {
            panel("<col class=\"cell\"><label class=\"keys\">" + FichaUi.esc(keys) + "</label></col>", x + w - keysW, y + 1, keysW, 8);
            tip(x + w - keysW, y, keysW, 9, "Atalhos\nEnter: pedir · Ctrl+A: marcar todos · Ctrl+F: buscar · 1–6: atributo · 7: dados · Tab: próximo campo · Esc: sair do campo / fechar");
        }
    }

    /** Tela estreita: as abas "Teste" e "Respostas" da coluna da direita. */
    private void switcher(int x, int y, int w) {
        Round current = current();
        String count = "";
        if (current != null) {
            int done = 0;
            for (Answer a : current.answers.values()) if (responded(a)) done++;
            count = " · " + done + "/" + current.answers.size();
        }
        String answers = "Respostas" + (unseen > 0 ? " · " + unseen + (unseen > 1 ? " novas" : " nova") : count);
        int testW = FichaUi.buttonWidth(font, "Teste", 6, 4, false, false);
        pressable("<row class=\"" + (showAnswers ? "b-mini" : "b-mini-on") + "\" onclick=\"" + on("ver_teste", () -> {
            showAnswers = false;
            dirty = true;
        }) + "\"><label>Teste</label></row>", x, y, testW, 13, "Montar o teste");
        int answersW = Math.min(w - testW - 3, FichaUi.buttonWidth(font, answers, 6, 4, false, false));
        String answersClass = showAnswers ? "b-mini-on" : unseen > 0 ? (unseen != newAnimated ? "m-new m-new-in" : "m-new") : "b-mini";
        newAnimated = showAnswers ? 0 : unseen;
        pressable("<row class=\"" + answersClass + "\" onclick=\""
                + on("ver_respostas", () -> {
            showAnswers = true;
            unseen = 0;
            dirty = true;
        }) + "\"><label>" + FichaUi.esc(FichaUi.fit(font, answers, answersW - 8, 6)) + "</label></row>",
                x + testW + 3, y, answersW, 13, "As respostas chegando ao vivo");
    }

    // ── Coluna dos jogadores ────────────────────────────────────────────────────

    private void playersColumn(int x, int y, int w, int h, long now) {
        panel("<col class=\"panel\"></col>", x, y, w, h);
        List<FichaPayloads.Player> shown = filtered();
        boolean filtering = !text(search).isBlank();
        Set<UUID> selected = selected();
        String head = (filtering ? shown.size() + " de " + players.size() : "Por perto · " + players.size()
                + (players.size() >= FichaPayloads.MAX_TABLE ? "+" : ""))
                + (selected.isEmpty() ? "" : " · " + Texts.count(selected.size(), "marcado", "marcados"));
        label("section", head, x + 7, y + 5, w - 14, 7, 5);
        if (players.size() >= FichaPayloads.MAX_TABLE) {
            tip(x + 7, y + 5, w - 14, 7, "Os " + FichaPayloads.MAX_TABLE + " mais perto\nA mesa lista no máximo " + FichaPayloads.MAX_TABLE
                    + " jogadores; chegue mais perto de quem não aparece.");
        }

        // Marcar: todos (do que a busca mostra), nenhum, quem está perto.
        boolean all = !shown.isEmpty();
        int near = 0;
        for (FichaPayloads.Player p : shown) {
            if (LEFT.contains(p.id())) continue;
            if (!selected.contains(p.id())) all = false;
            if (p.distance() <= NEAR) near++;
        }
        List<Item> tools = new ArrayList<>();
        tools.add(button(shown.isEmpty() ? "b-off" : all ? "b-mini-on" : "b-mini", "Todos", "todos", shown.isEmpty() ? null : this::selectAll,
                all ? "Desmarcar todos" + (filtering ? " os da busca" : "") + " (Ctrl+A)" : "Marcar todos" + (filtering ? " os da busca" : "") + " (Ctrl+A)", w - 12));
        tools.add(button(selected.isEmpty() ? "b-off" : "b-mini", "Nenhum", "nenhum", selected.isEmpty() ? null : () -> {
            selected.clear();
            dirty = true;
        }, selected.isEmpty() ? "Ninguém marcado" : "Desmarcar todos", w - 12));
        tools.add(button(near == 0 ? "b-off" : "b-mini", "Perto", "perto", near == 0 ? null : this::selectNear,
                near == 0 ? "Ninguém a até " + NEAR + " blocos" : "Marcar só quem está a até " + NEAR + " blocos (" + near + ")", w - 12));
        int cy = flow(tools, x + 6, y + 15, w - 12);

        // Busca por nome do personagem ou da conta: Enter marca o que ela mostra.
        if (!players.isEmpty()) {
            fields.add(search);
            panel("<row class=\"cell\"><input id=\"busca\" class=\"input\" placeholder=\"Buscar nome… (Ctrl+F)\" maxlength=\"24\" oninput=\""
                    + onText("busca", t -> {
                        playerPage = 0;
                        dirty = true;
                    }) + "\" onsubmit=\"" + onText("busca_ok", t -> {
                        for (FichaPayloads.Player p : filtered()) if (!LEFT.contains(p.id())) selected().add(p.id());
                        setText(search, "");
                        search.focused = false;
                        playerPage = 0;
                        dirty = true;
                    }) + "\" value=\"" + FichaUi.esc(text(search)) + "\"/></row>", x + 6, cy, w - 12, 13);
            tip(x + 6, cy, w - 12, 13, "Buscar\nPelo nome do personagem ou da conta. Enter marca quem aparece; Esc limpa.");
            cy += 16;
        }

        if (players.isEmpty() || shown.isEmpty()) {
            String message = players.isEmpty() ? "Ninguém a até " + radius + " blocos. Chegue mais perto dos jogadores e clique em Atualizar."
                    : "Ninguém com \"" + text(search).strip() + "\" por perto.";
            List<String> lines = FichaUi.wrap(font, message, w - 14, 6, 4);
            StringBuilder html = new StringBuilder("<col class=\"cell\">");
            for (String line : lines) html.append("<label class=\"hint\">").append(FichaUi.esc(line)).append("</label>");
            panel(html.append("</col>").toString(), x + 7, cy + 1, w - 14, lines.size() * 9 + 2);
            int by = cy + 4 + lines.size() * 9;
            if (players.isEmpty()) {
                flow(List.of(button(refreshing ? "b-off" : "b-mini", refreshing ? "Buscando…" : "Atualizar", "vazio_atualizar",
                        refreshing ? null : this::requestRefresh, "Buscar de novo os jogadores por perto", w - 14)), x + 7, by, w - 14);
            } else {
                flow(List.of(button("b-mini", "Limpar busca", "limpar_busca", () -> {
                    setText(search, "");
                    dirty = true;
                }, "Mostrar todos de novo (Esc no campo)", w - 14)), x + 7, by, w - 14);
            }
            return;
        }

        int rowH = 16;
        int room = y + h - 3 - cy;
        int perPage = Math.max(1, room / rowH);
        if (shown.size() > perPage) perPage = Math.max(1, (room - 14) / rowH);
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        playerPage = Math.min(playerPage, pages - 1);
        int peekW = FichaUi.buttonWidth(font, "Ficha", 6, 4, false, false);
        Round current = current();
        for (int i = 0; i < perPage; i++) {
            int index = playerPage * perPage + i;
            if (index >= shown.size()) break;
            playerRow(shown.get(index), index, x, cy + i * rowH, w, peekW, current, now);
        }
        if (pages > 1) pager("p", x + w - 6, y + h - 14, playerPage, pages, next -> playerPage = next);
    }

    private void playerRow(FichaPayloads.Player p, int index, int x, int ry, int w, int peekW, @Nullable Round current, long now) {
        boolean left = LEFT.contains(p.id());
        boolean picked = !left && selected().contains(p.id());
        int rowW = w - 12 - peekW - 3;
        // A resposta deste jogador ao pedido atual, como um ponto colorido no fim da linha.
        Answer answer = current == null ? null : current.answers.get(p.id());
        Kind answerKind = answer == null ? null : kind(current, answer, now);
        int px = x + w - 6 - peekW;
        if (left) {
            panel("<row class=\"pick-off\"></row>", x + 6, ry, rowW, 15);
            tip(x + 6, ry, rowW, 15, p.name() + "\nSaiu do servidor durante o pedido. Clique em Atualizar.");
        } else {
            String tooltip = p.name() + "\nConta " + p.profile() + " · " + p.distance() + " blocos"
                    + (p.modded() ? "" : "\nSem o mod: recebe o pedido no chat, com [Rolar] e [Recusar].")
                    + sheetHint(p.sheet(), dice)
                    + (answerKind == null ? "" : "\nPedido atual: " + kindLabel(answerKind) + ".")
                    + (picked ? "\nClique para desmarcar." : "\nClique para marcar.");
            // A linha inteira marca/desmarca (row com onclick, com hover); rosto, nome e distância são
            // desenhados por cima em duas linhas — cabe mais do nome numa coluna estreita.
            pressable("<row class=\"" + (picked ? "pick-on" : "pick") + "\" onclick=\"" + on("pick_" + index, () -> {
                if (!selected().remove(p.id())) selected().add(p.id());
                dirty = true;
            }) + "\"></row>", x + 6, ry, rowW, 15, tooltip);
            boolean loading = p.id().equals(peeking);
            if (loading || peeking != null) {
                panel("<row class=\"b-off\"><label>" + (loading ? "…" : "Ficha") + "</label></row>", px, ry + 1, peekW, 12);
                tip(px, ry + 1, peekW, 12, loading ? "Abrindo a ficha de " + p.name() + "…" : "Espere a outra ficha abrir.");            } else {
                pressable("<row class=\"b-mini\" onclick=\"" + on("ver_" + index, () -> peek(p)) + "\"><label>Ficha</label></row>",
                        px, ry + 1, peekW, 12, "Ver a ficha de " + p.name() + "\nSó leitura; Esc volta para a mesa.");
            }
        }
        int fx = x + 9, fy = ry + 3, tx = x + 6 + 17, room = rowW - 17 - 3 - (answerKind == null ? 0 : 6);
        String name = FichaUi.fitScaled(font, p.name(), room, 0.75f);
        boolean noSheet = !left && !p.sheet().isEmpty() && !p.sheet().equals("ok");
        // Ficha antiga só barra atributo e perícia: no modo Dados ela responde normal (sem o âmbar de aviso).
        boolean warn = noSheet && !(dice && "sem_construcao".equals(p.sheet()));
        String sub = FichaUi.fitScaled(font, left ? "saiu do servidor"
                : p.distance() + "m · " + (noSheet ? switch (p.sheet()) {
                    case "sem_ficha" -> "sem ficha";
                    case "sem_construcao" -> "ficha antiga";
                    default -> "sem vínculo";
                }
                : p.modded() ? p.profile() : "sem o mod (chat)"), room, 0.55f);
        int dotX = x + 6 + rowW - 7, dotColor = answerKind == null ? 0 : color(answerKind);
        overlays.add(g -> {
            FichaUi.face(g, p.id(), fx, fy, 9);
            if (left) g.fill(fx, fy, fx + 9, fy + 9, 0xA0100818);
            // Marcado: um canto dourado sobre o rosto, legível mesmo sem distinguir cor.
            if (picked) g.fill(fx + 6, fy + 6, fx + 10, fy + 10, FichaUi.GOLD);
            FichaUi.text(g, font, name, tx, ry + 2.5f, left ? FichaUi.FAINT : picked ? FichaUi.GOLD_HI : FichaUi.INK, 0.75f, false);
            FichaUi.text(g, font, sub, tx, ry + 9.5f, !warn && (left || p.modded() || noSheet) ? FichaUi.FAINT : FichaUi.WARN, 0.55f, false);
            if (dotColor != 0) FichaUi.rounded(g, dotX, ry + 6, 3, 3, dotColor);
        });
    }

    private static String sheetHint(String sheet, boolean dice) {
        return switch (sheet) {
            case "sem_ficha" -> "\nSem ficha no site: o pedido volta como erro.";
            case "sem_construcao" -> dice
                    ? "\nFicha antiga (sem a construção nova aprovada): em Dados responde normal — a rolagem livre não usa a ficha."
                    : "\nFicha ainda não atualizada para o sistema novo no site: testes de atributo e perícia voltam como erro."
                    + " Dados (rolagem livre) funcionam.";
            case "sem_vinculo" -> "\nAlt sem ficha escolhida: o pedido volta como erro até ele vincular uma ("
                    + openKeyName() + " → Vínculo).";
            default -> "";
        };
    }

    /** A tecla da ficha como este cliente a configurou (de fábrica, é a mesma de quem recebe o pedido). */
    private static String openKeyName() {
        String key = FichaUi.keyName(FichaKeys.OPEN);
        return key.isEmpty() ? "/ficha" : "tecla " + key;
    }

    private static String kindLabel(Kind k) {
        return switch (k) {
            case WAIT -> "aguardando";
            case ROLLING -> "rolando";
            case OK -> "sucesso";
            case CRIT -> "crítico";
            case FAIL -> "falha";
            case ROLLED -> "rolou";
            case DECLINED -> "recusou";
            case EXPIRED -> "venceu";
            case CANCELED -> "cancelado";
            case LEFT -> "saiu";
            case ERROR -> "erro";
        };
    }

    // ── Coluna do teste ─────────────────────────────────────────────────────────

    /** Um item de uma linha que quebra: a largura e quem monta o painel na posição dada. */
    private record Item(int width, BiConsumer<Integer, Integer> place) {
    }

    /** Põe os itens da esquerda para a direita, quebrando linha quando não cabem. Devolve o y logo abaixo. */
    private static int flow(List<Item> items, int x, int y, int w) {
        int cx = x, cy = y;
        for (Item item : items) {
            if (cx > x && cx + item.width() > x + w) {
                cx = x;
                cy += ROW_PITCH;
            }
            item.place().accept(cx, cy);
            cx += item.width() + 3;
        }
        return cy + ROW_PITCH;
    }

    /**
     * Um botão pequeno como item de {@link #flow}; o rótulo encurta com "…" se nem sozinho couber.
     * Sem ação ({@code null}): desenhado apagado, só com o tooltip do motivo.
     */
    private Item button(String cls, String label, String click, @Nullable Runnable action, String tooltip, int maxW) {
        int padX = cls.startsWith("b-toggle") ? 6 : 4;
        String shown = FichaUi.fit(font, label, maxW - 2 * padX, 6);
        int bw = Math.min(maxW, FichaUi.buttonWidth(font, shown, 6, padX, false, false));
        String onclick = action == null ? "" : " onclick=\"" + on(click, action) + "\"";
        // Rótulo cortado: o nome inteiro abre o tooltip.
        String tip = shown.equals(label) ? tooltip : tooltip.isEmpty() ? label : label + "\n" + tooltip;
        return new Item(bw, (bx, by) -> {
            String html = "<row class=\"" + cls + "\"" + onclick + "><label>" + FichaUi.esc(shown) + "</label></row>";
            if (action == null) {
                panel(html, bx, by, bw, ROW_H);
                tip(bx, by, bw, ROW_H, tip);
            } else {
                pressable(html, bx, by, bw, ROW_H, tip);
            }
        });
    }

    private void testColumn(int x, int y, int w, int h) {
        panel("<col class=\"panel\"></col>", x, y, w, h);
        int ix = x + 7, iw = w - 14;
        panel("<col class=\"cell\"><label class=\"section\">" + (dice ? "Dados · rolagem livre" : "Atributo e perícia")
                + "</label></col>", ix, y + 5, iw, 7);
        int cy = y + 14;

        // Atributos: escolhem o grupo de perícias (teclas 1–6); "Dados" (7) troca para a rolagem livre.
        List<Item> attrs = new ArrayList<>();
        for (int i = 0; i < Catalog.ATTRIBUTES.size(); i++) {
            Catalog.Attribute a = Catalog.ATTRIBUTES.get(i);
            attrs.add(button(!dice && a.id().equals(attribute) ? "b-mini-on" : "b-mini", a.abbreviation(), "grupo_" + a.id(), () -> pickAttribute(a),
                    a.label() + " (tecla " + (i + 1) + ")", iw));
        }
        attrs.add(button(dice ? "b-mini-on" : "b-mini", "Dados", "modo_dados", this::pickDice,
                "Dados (tecla " + (Catalog.ATTRIBUTES.size() + 1) + ")\nPedir uma rolagem livre — 1d20, 2d6, 1d100 ou qualquer fórmula —, sem atributo"
                        + " nem perícia. Funciona até para quem ainda tem a ficha antiga (sem a construção nova aprovada no site).", iw));
        cy = flow(attrs, ix, cy, iw);
        cy = dice ? diceRow(ix, cy, iw) : optionsRow(ix, cy, iw);

        // Divisória fina no lugar de um título: a coluna precisa caber em 240 px de altura.
        int lineY = cy;
        overlays.add(g -> g.fill(ix, lineY, ix + iw, lineY + 1, 0x30D7A857));
        cy += 4;
        cy = rulesRow(ix, cy, iw);

        // O motivo só entra se couber acima do botão (em GUI 320x240 com um grupo grande, não cabe).
        int sendY = y + h - 20;
        if (cy + 13 <= sendY - 2) {
            fields.add(MOTIVO);
            panel("<row class=\"cell\"><input id=\"motivo\" class=\"input\" placeholder=\"Motivo (opcional): ouvir a porta…\" maxlength=\"60\" onsubmit=\""
                    + onText("motivo_ok", t -> ask()) + "\" value=\"" + FichaUi.esc(text(MOTIVO)) + "\"/></row>", ix, cy, iw, 13);
            tip(ix, cy, iw, 13, "Motivo\nAparece para o jogador junto do pedido. Enter pede.");
        }
        String formulaProblem = dice ? formulaProblem() : null;
        String dcProblem = dcProblem();
        sendButton(ix, sendY, iw, formulaProblem != null ? "Fórmula inválida\n" + formulaProblem : dcProblem != null ? "CD inválida\n" + dcProblem : null);
    }

    /** Modo Dados: os atalhos e o campo da fórmula, validado com a gramática do motor a cada tecla. */
    private int diceRow(int ix, int cy, int iw) {
        String current = DiceFormula.normalize(text(FORMULA));
        String problem = formulaProblem();
        List<Item> row = new ArrayList<>();
        for (String preset : DICE_PRESETS) {
            row.add(button(preset.equals(current) ? "b-mini-on" : "b-mini", preset, "dado_" + preset, () -> {
                setText(FORMULA, preset);
                dirty = true;
            }, preset + ("1d20".equals(preset) ? "\nUm d20: a CD se lê como num teste, mas sem atributo nem perícia."
                    : "\nVale o total dos dados; com CD, o total precisa chegar nela."), iw));
        }
        int fieldW = Math.min(iw, 70);
        fields.add(FORMULA);
        row.add(new Item(fieldW, (bx, by) -> {
            panel("<row class=\"cell\"><input id=\"formula\" class=\"" + (problem == null ? "input" : "input-bad")
                    + "\" placeholder=\"2d6+3\" maxlength=\"" + DiceFormula.MAX_LENGTH + "\" oninput=\"" + onText("formula_mudou", t -> dirty = true)
                    + "\" onsubmit=\"" + onText("formula_ok", t -> ask()) + "\" value=\"" + FichaUi.esc(text(FORMULA)) + "\"/></row>",
                    bx, by - 1, fieldW, ROW_H + 1);
            tip(bx, by - 1, fieldW, ROW_H + 1, problem != null ? "Fórmula inválida\n" + problem
                    : "Fórmula livre\nNdF ± K, como 2d6+3 ou 1d20-1; kh/kl ficam com os maiores/menores (4d6kh3; 2d20kh1 faz as vezes de"
                    + " vantagem). Até " + DiceFormula.MAX_TERMS + " parcelas e " + DiceFormula.MAX_DICE + " dados. Enter pede.");
        }));
        return flow(row, ix, cy, iw);
    }

    /** "Só o atributo" e as perícias do grupo. */
    private int optionsRow(int ix, int cy, int iw) {
        List<Item> options = new ArrayList<>();
        options.add(button(skill.isEmpty() ? "b-mini-on" : "b-mini", "Só " + Catalog.attributeLabel(attribute), "teste_attr", () -> {
            skill = "";
            altAttribute = "";
            dirty = true;
        }, "Só o atributo\nTeste do atributo puro, sem perícia.", iw));
        for (Catalog.Skill s : Catalog.skillsOf(attribute)) {
            options.add(button(s.id().equals(skill) ? "b-mini-on" : "b-mini", s.label(), "teste_" + s.id(), () -> {
                skill = s.id();
                altAttribute = "";
                dirty = true;
            }, "", iw));
        }
        return flow(options, ix, cy, iw);
    }

    /**
     * Uma linha que quebra: CD + atalhos de CD, visibilidade, secreto, modo e atributo alternativo. Nos
     * dados não há modo (o site recusa vantagem em rolagem livre; 2d20kh1 faz o papel) e os atalhos de CD
     * só aparecem para um d20 — para 2d6, CD 20 não faz sentido.
     */
    private int rulesRow(int ix, int cy, int iw) {
        String problem = dcProblem();
        int currentDc = dc();
        int cdW = 42;
        boolean longLabels = FichaUi.rowWidth(3, cdW, FichaUi.buttonWidth(font, "CD visível", 6, 6, false, false),
                FichaUi.buttonWidth(font, "○ Secreto", 6, 6, false, false)) <= iw;
        List<Item> row = new ArrayList<>();
        fields.add(CD);
        row.add(new Item(cdW, (bx, by) -> {
            panel("<col class=\"cell\"><label class=\"field-label\">CD</label></col>", bx, by + 3, 14, 7);
            panel("<row class=\"cell\"><input id=\"cd\" class=\"" + (problem == null ? "input" : "input-bad")
                    + "\" placeholder=\"—\" maxlength=\"2\" oninput=\"" + onText("cd_mudou", t -> dirty = true) + "\" onsubmit=\""
                    + onText("cd_ok", t -> ask()) + "\" value=\"" + FichaUi.esc(text(CD)) + "\"/></row>", bx + 14, by - 1, cdW - 14, ROW_H + 1);
            tip(bx, by - 1, cdW, ROW_H + 1, problem != null ? "CD inválida\n" + problem
                    : dice ? "CD (opcional)\nO total dos dados precisa chegar nela (total ≥ CD) para dar sucesso. Vazia: vale só o número rolado."
                    : "Classe de dificuldade\nO total do jogador precisa chegar nela. Vazia: sem CD, vale só o número rolado.");
        }));
        boolean d20 = !dice || DiceFormula.singleD20(text(FORMULA));
        for (int i = 0; d20 && i < DC_PRESETS.length; i++) {
            int value = DC_PRESETS[i];
            boolean on = value == currentDc;
            row.add(button(on ? "b-mini-on" : "b-mini", String.valueOf(value), "cd_" + value, () -> {
                setText(CD, on ? "" : String.valueOf(value));
                dirty = true;
            }, on ? DC_NAMES[i] + " · CD " + value + "\nClique de novo para tirar a CD." : DC_NAMES[i] + " · CD " + value, iw));
        }
        String dcLabel = showDc ? (longLabels ? "CD visível" : "Visível") : (longLabels ? "CD oculta" : "Oculta");
        row.add(button(showDc ? "b-toggle" : "b-toggle-on", dcLabel, "cd_visivel", () -> {
            showDc = !showDc;
            dirty = true;
        }, showDc ? "CD visível\nOs jogadores veem a CD. Clique para esconder." : "CD oculta\nSó você vê a CD; o jogador vê \"CD oculta\" e só o resultado.", iw));
        row.add(button(secret ? "b-toggle-on" : "b-toggle", secret ? "● Secreto" : "○ Secreto", "secreto", () -> {
            secret = !secret;
            dirty = true;
        }, "Secreto\nSó o jogador e os mestres veem o resultado; quem está por perto não.", iw));

        // Vantagem/desvantagem, como um seletor de três.
        boolean longModes = FichaUi.rowWidth(3, FichaUi.buttonWidth(font, "Normal", 6, 4, false, false),
                FichaUi.buttonWidth(font, "Vantagem", 6, 4, false, false), FichaUi.buttonWidth(font, "Desvantagem", 6, 4, false, false)) <= iw;
        String[][] modes = dice ? new String[0][] : new String[][]{{"normal", "Normal", "Normal"}, {"vantagem", "Vantagem", "Vant."},
                {"desvantagem", "Desvantagem", "Desv."}};
        for (String[] m : modes) {
            row.add(button(m[0].equals(mode) ? "b-mini-on" : "b-mini", longModes ? m[1] : m[2], "modo_" + m[0], () -> {
                mode = m[0];
                dirty = true;
            }, "normal".equals(m[0]) ? "Normal\nUm d20." : m[1] + "\nRola 2d20 e fica com o " + ("vantagem".equals(m[0]) ? "maior" : "menor")
                    + ". Exige que o site confirme você como staff.", iw));
        }
        if (!dice && !skill.isEmpty()) {
            Catalog.Skill s = Catalog.skill(skill);
            String base = s == null ? "" : s.attribute();
            String current = altAttribute.isEmpty() ? Catalog.attributeAbbreviation(base) + " (padrão)" : Catalog.attributeAbbreviation(altAttribute);
            row.add(button(altAttribute.isEmpty() ? "b-mini" : "b-mini-on", "Atributo: " + current, "alt_attr", () -> {
                // Gira pelos atributos: padrão → FOR → DES … → VIT → padrão (pulando o da própria perícia).
                List<String> cycle = new ArrayList<>();
                cycle.add("");
                for (Catalog.Attribute a : Catalog.ATTRIBUTES) if (!a.id().equals(base)) cycle.add(a.id());
                int i = cycle.indexOf(altAttribute);
                altAttribute = cycle.get((i + 1) % cycle.size());
                dirty = true;
            }, "Atributo da perícia\nClique para trocar o atributo que soma no teste. Exige que o site confirme você como staff.", iw));
        }
        return flow(row, ix, cy, iw);
    }

    /** O botão de pedir, com a largura toda: pronto, travado com o motivo, enviando ou enviado. */
    private void sendButton(int x, int y, int w, @Nullable String problem) {
        Set<UUID> selected = selected();
        if (sending || canceling) {
            panel("<row class=\"send-wait\"><label class=\"send-t-wait\">" + (sending ? "ENVIANDO…" : "CANCELANDO…") + "</label></row>", x, y, w, 15);
            tip(x, y, w, 15, "Enviando\nEsperando o servidor confirmar o pedido.");
            return;
        }
        if (sentOkAt > 0) {
            boolean fresh = doneAnimated != sentOkAt;
            doneAnimated = sentOkAt;
            panel("<row class=\"" + (fresh ? "send-done done-in" : "send-done") + "\"><label class=\"send-t-done\">✔ PEDIDO ENVIADO</label></row>", x, y, w, 15);
            tip(x, y, w, 15, "Pedido enviado\nAs respostas chegam na coluna das respostas.");
            return;
        }
        String label = "Pedir " + testName() + (selected.isEmpty() ? "" : " a " + Texts.count(selected.size(), "jogador", "jogadores"));
        String shown = FichaUi.esc(fitBold(label.toUpperCase(Locale.ROOT), w - 10));
        if (selected.isEmpty() || problem != null) {
            panel("<row class=\"send-off\"><label class=\"send-t-off\">" + shown + "</label></row>", x, y, w, 15);
            tip(x, y, w, 15, problem != null ? problem : "Ninguém marcado\nMarque os jogadores à esquerda (Ctrl+A marca todos).");
            return;
        }
        String detail = label + "\n" + (dice ? "Rolagem livre · " : "") + (dc() == 0 ? "Sem CD" : "CD " + dc() + (showDc ? "" : " (oculta)")
                + (dice && !DiceFormula.singleD20(text(FORMULA)) ? ": o total precisa chegar nela" : ""))
                + (Texts.modeLabel(mode).isEmpty() ? "" : " · " + Texts.modeLabel(mode)) + (secret ? " · secreto" : "")
                + (text(MOTIVO).isBlank() ? "" : " · " + text(MOTIVO).strip())
                + (dice ? "\nVale até para quem ainda tem a ficha antiga." : "") + "\nEnter também pede.";
        pressable("<row class=\"send\" onclick=\"" + on("pedir", this::ask) + "\"><label class=\"send-t\">" + shown + "</label></row>", x, y, w, 15, detail);
    }

    /** Corta o rótulo em negrito de 6px (o botão de pedir) para caber em {@code width}. */
    private String fitBold(String text, int width) {
        if (FichaUi.buttonWidth(font, text, 6, 0, false, true) <= width) return text;
        for (int n = text.length() - 1; n > 1; n--) {
            String cut = text.substring(0, n).stripTrailing() + "…";
            if (FichaUi.buttonWidth(font, cut, 6, 0, false, true) <= width) return cut;
        }
        return "…";
    }

    // ── Coluna das respostas ────────────────────────────────────────────────────

    /** Uma linha da lista: o título de uma rodada antiga ({@code answer == null}) ou uma resposta. */
    private record Entry(Round round, @Nullable Answer answer, int height) {
    }

    private static final int ANSWER_H = 22, ANSWER_PITCH = 24, HEADER_H = 12;

    private void answersColumn(int x, int y, int w, int h, long now) {
        panel("<col class=\"panel\"></col>", x, y, w, h);
        List<Round> rounds = new ArrayList<>();
        for (Round r : ROUNDS) if (r.local == test) rounds.add(r);
        int clearW = Math.max(FichaUi.buttonWidth(font, "Limpar", 6, 4, false, false), FichaUi.buttonWidth(font, "Certeza?", 6, 4, false, false));
        panel("<col class=\"cell\"><label class=\"section\">Respostas</label></col>", x + 7, y + 5, w - 14 - clearW - 4, 7);
        if (rounds.isEmpty()) {
            List<String> lines = FichaUi.wrap(font, "Nenhum pedido ainda. Marque jogadores, escolha o teste e aperte Enter: as respostas chegam aqui ao vivo.",
                    w - 14, 6, 6);
            StringBuilder html = new StringBuilder("<col class=\"cell\">");
            for (String line : lines) html.append("<label class=\"hint\">").append(FichaUi.esc(line)).append("</label>");
            panel(html.append("</col>").toString(), x + 7, y + 15, w - 14, lines.size() * 9 + 2);
            return;
        }
        // Limpar pede confirmação: o segundo clique em 3 s apaga.
        boolean confirming = confirmClearAt > 0;
        String clearClass = confirming ? (clearAnimated != confirmClearAt ? "b-confirm confirm-in" : "b-confirm") : "b-mini";
        clearAnimated = confirmClearAt;
        pressable("<row class=\"" + clearClass + "\" onclick=\"" + on("limpar", () -> {
            if (confirmClearAt == 0) {
                confirmClearAt = Util.getMillis();
            } else {
                confirmClearAt = 0;
                for (Iterator<Round> it = ROUNDS.iterator(); it.hasNext(); ) {
                    Round r = it.next();
                    if (r.local != test) continue;
                    forget(r);
                    it.remove();
                }
                answerPage = 0;
                status("info", "Respostas limpas. Os pedidos continuam valendo até vencer.");
            }
            dirty = true;
        }) + "\"><label>" + (confirming ? "Certeza?" : "Limpar") + "</label></row>", x + w - 6 - clearW, y + 3, clearW, 11,
                confirming ? "Apagar as respostas?\nClique de novo em 3 s para apagar. Os pedidos continuam valendo até vencer."
                        : "Limpar\nApaga a lista de respostas desta tela (pede confirmação).");

        Round current = rounds.get(0);
        int cy = y + 16 + roundCard(current, x + 5, y + 16, w - 10, now) + 3;

        List<Entry> entries = new ArrayList<>();
        for (Answer a : current.answers.values()) entries.add(new Entry(current, a, ANSWER_PITCH));
        for (int i = 1; i < rounds.size(); i++) {
            Round r = rounds.get(i);
            entries.add(new Entry(r, null, HEADER_H));
            for (Answer a : r.answers.values()) entries.add(new Entry(r, a, ANSWER_PITCH));
        }
        // Páginas pela altura (títulos são mais baixos que respostas); o paginador só toma espaço se houver.
        int bottom = y + h - 3;
        List<Integer> starts = pages(entries, bottom - cy);
        if (starts.size() > 1) starts = pages(entries, bottom - 14 - cy);
        int pages = starts.size();
        answerPage = Math.min(answerPage, pages - 1);
        int from = starts.get(answerPage), to = answerPage + 1 < pages ? starts.get(answerPage + 1) : entries.size();
        for (int i = from; i < to; i++) {
            Entry e = entries.get(i);
            if (e.answer() == null) roundHeader(e.round(), x + 7, cy, w - 14, now);
            else answerRow(e.round(), e.answer(), x + 5, cy, w - 10, now);
            cy += e.height();
        }
        if (pages > 1) pager("r", x + w - 6, y + h - 14, answerPage, pages, next -> answerPage = next);
    }

    /** A primeira rodada com gente aguardando a vencer: nessa hora a mesa se refaz sozinha ("venceu"). */
    private long nextDeadline(long now) {
        long next = 0;
        for (Round r : ROUNDS) {
            long deadline = r.createdAt + requestSeconds * 1000L + 1;
            if (r.local != test || deadline <= now) continue;
            for (Answer a : r.answers.values()) {
                if ("aguardando".equals(a.state()) && a.card() == null) {
                    next = next == 0 ? deadline : Math.min(next, deadline);
                    break;
                }
            }
        }
        return next;
    }

    private static List<Integer> pages(List<Entry> entries, int room) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int used = 0;
        for (int i = 0; i < entries.size(); i++) {
            int hgt = entries.get(i).height();
            if (used > 0 && used + hgt > room) {
                starts.add(i);
                used = 0;
            }
            used += hgt;
        }
        return starts;
    }

    /** O cartão do pedido atual: teste, "3/5 responderam", barra por resultado, prazo e reenviar. Devolve a altura. */
    private int roundCard(Round round, int x, int y, int w, long now) {
        int total = round.answers.size(), done = 0, pending = 0;
        int[] counts = new int[Kind.values().length];
        for (Answer a : round.answers.values()) {
            Kind k = kind(round, a, now);
            counts[k.ordinal()]++;
            if (responded(a)) done++;
            if (k == Kind.WAIT || k == Kind.ROLLING) pending++;
        }
        // Quem recusou, deixou vencer ou deu erro; pedido do comando (sem o teste guardado) não reenvia daqui.
        List<UUID> missing = resendTargets(round, now);
        boolean resendable = round.ask != null && !missing.isEmpty();
        boolean cancelable = counts[Kind.WAIT.ordinal()] > 0;
        int h = resendable || cancelable ? 41 : 29;
        StringBuilder summary = new StringBuilder();
        for (Kind k : Kind.values()) {
            if (counts[k.ordinal()] == 0) continue;
            if (!summary.isEmpty()) summary.append(" · ");
            summary.append(counts[k.ordinal()]).append(' ').append(kindLabel(k));
        }
        // Verde só quando todos responderam; encerrado com faltas (venceu, cancelado) fica na moldura comum.
        panel("<col class=\"" + (pending == 0 && done == total ? "round-done" : "round") + "\"></col>", x, y, w, h);
        tip(x, y, w, 26, round.test + " · pedido #" + round.id + "\n" + summary
                + (round.ask == null ? "" : "\n" + askDetail(round.ask)));
        List<Item> actions = new ArrayList<>();
        if (resendable) {
            StringBuilder names = new StringBuilder();
            for (UUID id : missing) {
                Answer a = round.answers.get(id);
                if (a == null) continue;
                if (!names.isEmpty()) names.append(", ");
                names.append(a.name());
            }
            actions.add(button(sending || canceling ? "b-off" : "b-mini", "Reenviar a " + missing.size(), "reenviar",
                    sending || canceling ? null : () -> resend(round), "Reenviar\nPedir de novo " + round.test + " a: " + names + ".", w - 8));
        }
        if (cancelable) {
            boolean confirming = confirmCancelAt > 0;
            int waitingCount = counts[Kind.WAIT.ordinal()];
            String cancelClass = sending || canceling ? "b-off"
                    : confirming ? (cancelAnimated != confirmCancelAt ? "b-confirm confirm-in" : "b-confirm") : "b-danger";
            cancelAnimated = confirmCancelAt;
            actions.add(button(cancelClass, confirming ? "Certeza?" : "Cancelar",
                    "cancelar", sending || canceling ? null : () -> {
                        if (confirmCancelAt == 0) {
                            confirmCancelAt = Util.getMillis();
                            dirty = true;
                        } else {
                            cancel(round);
                        }
                    }, confirming ? "Cancelar o pedido?\nClique de novo em 3 s. " + Texts.count(waitingCount, "jogador aguardando", "jogadores aguardando")
                            + " perde" + (waitingCount == 1 ? "" : "m") + " o pedido; quem já rolou continua valendo."
                            : "Cancelar\nTira o pedido de quem ainda não respondeu (pede confirmação).", w - 8));
        }
        if (!actions.isEmpty()) flow(actions, x + 4, y + 27, w - 8);
        String title = round.test;
        String id = "#" + round.id;
        int idW = Math.round(font.width(id) * 0.55f);
        String titleShown = FichaUi.fitScaled(font, title, w - 10 - idW - 4, 0.72f);
        String countText = pending == 0 && done == total ? "todos responderam" : done + "/" + total + " responderam";
        int countColor = pending == 0 && done == total ? FichaUi.OK : FichaUi.SOFT;
        long deadline = round.createdAt + requestSeconds * 1000L;
        boolean waiting = pending > 0;
        int left = x + 5, right = x + w - 5, barW = w - 10;
        int[] segment = new int[Kind.values().length];
        System.arraycopy(counts, 0, segment, 0, counts.length);
        long flashAt = round.latest();
        overlays.add(g -> {
            long t = Util.getMillis();
            FichaUi.text(g, font, titleShown, left, y + 4, FichaUi.GOLD_HI, 0.72f, false);
            FichaUi.textRight(g, font, id, right, y + 5, FichaUi.FAINT, 0.55f, false);
            // Barra: crítico, sucesso, rolou, falha, recusou, faltou — e o que falta, vazio.
            g.fill(left, y + 13, left + barW, y + 17, 0x60000000);
            float shown = Math.max(0f, Math.min(1f, round.bar(t)));
            int filled = Math.round(barW * shown);
            int bx = left;
            if (total > 0) {
                for (Kind k : BAR_ORDER) {
                    int n = segment[k.ordinal()];
                    if (n == 0) continue;
                    int sw = Math.round(barW * n / (float) total);
                    int end = Math.min(left + filled, bx + sw);
                    if (end > bx) g.fill(bx, y + 13, end, y + 17, color(k));
                    bx += sw;
                }
            }
            FichaUi.text(g, font, countText, left, y + 20, countColor, 0.6f, false);
            if (waiting) {
                long remain = (deadline - t) / 1000L;
                FichaUi.textRight(g, font, remain > 0 ? timer((int) remain) : "venceu", right, y + 20.5f, FichaUi.FAINT, 0.55f, false);
            }
            long age = t - flashAt;
            if (age >= 0 && age < FLASH_MS) {
                float k = 1f - age / (float) FLASH_MS;
                FichaUi.rounded(g, x + 1, y + 1, w - 2, h - 2, FichaUi.alpha(0x30FFE9A7, k * k));
            }
        });
        return h;
    }

    /** "vence em 2:31", refeito só quando o segundo muda (o desenho roda todo quadro). */
    private String timer(int seconds) {
        if (seconds != timerSeconds) {
            timerSeconds = seconds;
            timerText = "vence em " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        }
        return timerText;
    }

    private void roundHeader(Round round, int x, int y, int w, long now) {
        int done = 0;
        for (Answer a : round.answers.values()) if (responded(a)) done++;
        long minutes = (now - round.createdAt) / 60_000L;
        String text = round.test + " · " + done + "/" + round.answers.size() + " · " + (minutes < 1 ? "agora há pouco" : "há " + minutes + " min");
        String shown = FichaUi.fitScaled(font, text.toUpperCase(Locale.ROOT), w, 0.55f);
        overlays.add(g -> {
            g.fill(x, y + 2, x + w, y + 3, 0x30D7A857);
            FichaUi.text(g, font, shown, x, y + 5, FichaUi.GOLD, 0.55f, false);
        });
    }

    private void answerRow(Round round, Answer a, int x, int y, int w, long now) {
        Kind k = kind(round, a, now);
        RollCard card = a.card();
        int color = card != null ? FichaUi.outcomeColor(card.roll()) : color(k);
        String big;
        String small = "";
        String detail;
        if (card != null) {
            RollResult roll = card.roll();
            big = roll.repeated() ? Texts.totals(roll) : String.valueOf(roll.total());
            String outcome = Texts.outcome(roll);
            small = outcome.toLowerCase(Locale.ROOT);
            detail = Texts.breakdown(roll, card.bonusHidden(), false);
        } else {
            big = switch (k) {
                case ROLLING -> "rolando…";
                case ROLLED -> "rolou";
                default -> kindLabel(k);
            };
            detail = switch (k) {
                case WAIT -> a.detail().isEmpty() ? "sem resposta ainda" : a.detail();
                case EXPIRED -> "não respondeu a tempo";
                case LEFT -> a.detail().isEmpty() ? LEFT_DETAIL : a.detail();
                case CANCELED -> "você cancelou o pedido";
                case DECLINED -> "recusou o teste";
                case ROLLING -> "o site está rolando";
                default -> a.detail();
            };
        }
        String tooltip;
        if (card != null) {
            RollResult roll = card.roll();
            StringBuilder dropped = new StringBuilder();
            for (RollResult.Die die : roll.dice()) if (die.dropped()) dropped.append(dropped.isEmpty() ? "" : ", ").append(die.value());
            String outcome = Texts.outcome(roll);
            tooltip = a.name() + " · " + round.test + "\n" + Texts.breakdown(roll, card.bonusHidden(), false)
                    + (outcome.isEmpty() ? "" : " → " + outcome)
                    + (Texts.modeLabel(roll.mode()).isEmpty() ? "" : "\n" + Texts.modeLabel(roll.mode())
                    + (dropped.isEmpty() ? "" : " (descartado: " + dropped + ")"))
                    + (card.dcHidden() && roll.hasTarget() ? "\nA CD ficou oculta para o jogador." : "")
                    + (card.bonusHidden() ? "\nBônus de NPC: escondido do público." : "")
                    + (card.secret() ? "\nSecreta: só o jogador e os mestres viram." : "");
        } else {
            tooltip = a.name() + " · " + round.test + "\n" + detail;
        }
        String cls = switch (k) {
            case CRIT -> "answer-crit";
            case FAIL, ERROR -> "answer-bad";
            case OK -> "answer-ok";
            case WAIT, ROLLING -> "answer-wait";
            default -> "answer";
        };
        panel("<col class=\"" + cls + "\"></col>", x, y, w, ANSWER_H);
        tip(x, y, w, ANSWER_H, tooltip);
        boolean rolled = card != null;
        float bigScale = rolled ? 1.2f : 0.66f;
        int resultW = Math.round(font.width(big) * bigScale) + 4;
        int nx = x + 6, right = x + w - 5, ny = y + 4;
        String name = FichaUi.fitScaled(font, a.name(), right - nx - resultW, 0.8f);
        String detailShown = FichaUi.fitScaled(font, detail, right - nx - (small.isEmpty() ? 2 : Math.round(font.width(small) * 0.55f) + 4), 0.6f);
        long at = a.at();
        String outcomeShown = small;
        overlays.add(g -> {
            // Faixa à esquerda com a cor do resultado: lê de relance, mesmo de longe.
            g.fill(x + 1, y + 3, x + 3, y + ANSWER_H - 3, FichaUi.alpha(color, k == Kind.WAIT || k == Kind.EXPIRED || k == Kind.CANCELED || k == Kind.LEFT ? 0.45f : 1f));
            FichaUi.text(g, font, name, nx, ny, k == Kind.LEFT || k == Kind.EXPIRED || k == Kind.CANCELED ? FichaUi.FAINT : FichaUi.INK, 0.8f, false);
            FichaUi.text(g, font, detailShown, nx, ny + 9, FichaUi.FAINT, 0.6f, false);
            FichaUi.textRight(g, font, big, right, rolled ? ny - 1 : ny + 1, color, bigScale, rolled);
            if (!outcomeShown.isEmpty()) FichaUi.textRight(g, font, outcomeShown, right, ny + 10, color, 0.55f, false);
            // Chegou agora: um brilho da cor do resultado que some em ~0,3 s.
            long age = Util.getMillis() - at;
            if (age >= 0 && age < FLASH_MS) {
                float f = 1f - age / (float) FLASH_MS;
                FichaUi.rounded(g, x + 1, y + 1, w - 2, ANSWER_H - 2, FichaUi.alpha(color & 0x00FFFFFF | 0x40000000, f * f));
            }
        });
    }

    /** "< 1/3 >" alinhado pela direita em {@code right}; a seta da ponta fica apagada. */
    private void pager(String id, int right, int y, int page, int pages, IntConsumer go) {
        String label = (page + 1) + "/" + pages;
        int arrowW = FichaUi.buttonWidth(font, "<", 7, 4, false, false);
        int labelW = FichaUi.width(font, label, 6) + 4;
        int x = right - (arrowW * 2 + labelW + 4);
        String hint = "\nA roda do mouse também passa as páginas.";
        if (page > 0) {
            pressable("<row class=\"b-page\" onclick=\"" + on(id + "_ant", () -> { go.accept(Math.max(0, page - 1)); dirty = true; })
                    + "\"><label>&lt;</label></row>", x, y, arrowW, 12, "Página anterior" + hint);
        } else {
            panel("<row class=\"b-off\"><label>&lt;</label></row>", x, y, arrowW, 12);
        }
        panel("<row class=\"pager\"><label class=\"page-label\">" + label + "</label></row>", x + arrowW + 2, y + 3, labelW, 7);
        if (page < pages - 1) {
            pressable("<row class=\"b-page\" onclick=\"" + on(id + "_prox", () -> { go.accept(Math.min(pages - 1, page + 1)); dirty = true; })
                    + "\"><label>&gt;</label></row>", right - arrowW, y, arrowW, 12, "Próxima página" + hint);
        } else {
            panel("<row class=\"b-off\"><label>&gt;</label></row>", right - arrowW, y, arrowW, 12);
        }
    }

    // ── Utilitários ─────────────────────────────────────────────────────────────

    /** O pacote tem teto de tamanho (estourar derruba o envio): corta antes, não confia no maxlength. */
    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static TesseraInputState fresh() {
        TesseraInputState state = new TesseraInputState();
        if (state.text == null) state.text = "";
        return state;
    }

    private static String text(TesseraInputState state) {
        return state.text == null ? "" : state.text;
    }

    private static void setText(TesseraInputState state, String text) {
        state.text = text;
        state.cursor = text.length();
        state.selStart = text.length();
    }
}
