package com.aurorion.ficha.client;

import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Binding;
import com.aurorion.ficha.sheet.Candidate;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.Habilidades;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraFocusManager;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A tela da {@code /ficha} (a tecla da ficha; padrão à esquerda do 1).
 *
 * <h2>Abas</h2>
 * <ul>
 *   <li><b>Ficha</b>: retrato, nome, título, Alma, Marca/Elemento; barras de Vida, Stamina, Sanidade e
 *       P.E.; condições; Defesa, RD, Movimento, Iniciativa; os 6 atributos (clicar rola o
 *       atributo).</li>
 *   <li><b>Perícias</b>: as 22, agrupadas por atributo, com o bônus total; clicar abre o teste com
 *       CD opcional e "secreto". Digitar filtra.</li>
 *   <li><b>Técnicas</b>: as técnicas da ficha e, ao lado, a história (lore).</li>
 *   <li><b>Vínculo</b>: qual ficha este perfil usa e a lista do que ele pode usar.</li>
 *   <li><b>Habilidades</b>: o que o personagem já tem dentro do jogo, vindo dos outros mods (magias,
 *       profissão, Marca, passivas…), no campo {@code habilidades} da View. Não depende do site:
 *       aparece até sem ficha.</li>
 * </ul>
 * Sem conta do site vinculada, o lugar da ficha vira um passo a passo com o link do site e um campo
 * para o código (a tela manda o {@code /vincular} sozinha e recarrega quando o site confirmar).
 *
 * <h2>Duas camadas, duas montagens</h2>
 * Molduras, textos e botões são HTML/CSS do Tessera ({@code assets/aurorion_ficha/ui/ficha.css}),
 * em painéis com posição fixa (ver {@link FichaUi}). Por cima, o Java desenha o que o CSS não faz:
 * barras, o rosto da skin, os números grandes, o d20, a linha da aba ativa, o clarão do clique e os
 * tooltips (o do Tessera não vale na raiz de um painel e sai numa linha só). A tela e o diálogo de
 * rolagem são montagens separadas ({@link Layer}): mexer no diálogo (CD, secreto, resultado) não
 * remonta a ficha atrás. Nada é montado por frame; o render só percorre listas prontas e as
 * animações (barras, aba, d20) são contas sobre o relógio que param sozinhas.
 *
 * <h2>Teclado</h2>
 * {@code 1}–{@code 5} e {@code Tab} trocam a aba, {@code ←}/{@code →} (ou a roda) as páginas, letras
 * filtram as perícias, {@code Esc} volta um passo (campo, filtro, confirmação, tela). No diálogo:
 * {@code Enter} rola, {@code Esc} fecha, {@code ↑}/{@code ↓} e a roda mudam a CD, {@code Tab} (ou
 * {@code S}) alterna o secreto.
 *
 * <p>Modo espiar ({@link FichaPayloads#PEEK}): a ficha de um jogador vista pelo mestre, só leitura,
 * aberta por cima da mesa e voltando para ela.
 */
public final class FichaScreen extends TesseraScreen {
    private enum Tab {
        FICHA("Ficha", "Recursos, condições, números de combate e atributos"),
        PERICIAS("Perícias", "As perícias com o bônus de cada teste"),
        TECNICAS("Técnicas", "Técnicas e a história do personagem"),
        VINCULO("Vínculo", "Qual ficha ou NPC este perfil usa no jogo"),
        HABILIDADES("Habilidades", "Magias, profissão, Marca e passivas que o personagem já tem no servidor");

        final String label;
        final String about;

        Tab(String label, String about) {
            this.label = label;
            this.about = about;
        }
    }

    private static final int MAX_W = 448;
    private static final int MAX_H = 292;
    private static final int PAD = 7;
    private static final int HEAD_H = 40;
    private static final int TABS_H = 15;
    private static final int FOOT_H = 15;
    private static final int GAP = 4;
    /**
     * O diálogo é desenhado neste z: o texto com sombra do Minecraft sai 0,03 à frente do próprio z e
     * os ícones de item a 150+, e o teste de profundidade os deixaria vazar por cima do fundo do
     * diálogo (os números grandes da ficha apareciam por cima dele). Abaixo dos tooltips (400).
     */
    private static final float MODAL_Z = 300f;
    /** Diálogo: onde fica a caixa do resultado (o desenho por frame e a montagem usam o mesmo). */
    private static final int RESULT_Y = 74;
    /** O site tem 12 s de prazo e a rolagem pode tentar de novo uma vez: 30 s cobre os dois. */
    private static final long WAIT_TIMEOUT_MS = 30_000L;
    private static final long RELOAD_TIMEOUT_MS = 15_000L;
    private static final long TAB_ANIM_MS = 200L;
    private static final long BAR_ANIM_MS = 380L;
    private static final long FRESH_MS = 6_000L;
    private static final long CONFIRM_MS = 4_000L;
    private static final long TABLE_COOLDOWN_MS = 2_500L;
    /** Conferir se a ficha mudou no site com a tela aberta (o servidor ainda limita a 1 a cada 10 s). */
    private static final long CHECK_EVERY_MS = 30_000L;
    private static final long CHECK_MIN_MS = 10_000L;
    private static final long ASK_ANSWER_MS = 12_000L;
    /** Depois do {@code /vincular}: quando perguntar de novo ao servidor se a conta já está ligada. */
    private static final long[] LINK_POLLS = {2_500L, 6_000L, 12_000L};
    private static final int[] DC_PRESETS = {10, 15, 20, 25};
    private static final int[] WAIT_PALETTE = {0xFF4A2F8F, 0xFF7C5CD6, 0xFF1E1240, 0xFFFFF4DA};
    /** Números do d20 "rolando" já prontos: nada de String por frame. */
    private static final String[] FACES = new String[21];

    static {
        for (int i = 0; i < FACES.length; i++) FACES[i] = String.valueOf(i);
    }

    private final boolean test;
    @Nullable
    private final Screen parent;

    // Dados
    private int flags;
    private String code = "";
    private String siteUrl = "";
    @Nullable
    private FichaSheet sheet;
    @Nullable
    private Binding binding;
    private List<Candidate> candidates = List.of();
    /** O que o jogo já liberou (aba Habilidades); vem inteiro em cada View. */
    private Habilidades habilidades = Habilidades.EMPTY;
    private boolean candidatesLoaded;
    private boolean candidatesLoading;
    private long loadingSince;
    private String profileName = "";
    private String peekName = "";
    @Nullable
    private UUID peekId;

    // Interface
    private Tab tab = Tab.FICHA;
    private int page;
    /** Páginas da aba aberta na última montagem (para a roda e as setas não remontarem à toa). */
    private int pageCount = 1;
    private boolean lore;
    private String statusKind = "info";
    private String statusText = "";
    /** Filtro digitado na aba Perícias. */
    private String filter = "";
    /** A moldura só anima na primeira montagem (os painéis são refeitos a cada mudança). */
    private boolean entered;
    /** Quando a ficha chegou nova do site com a tela aberta (selo "atualizada agora"). */
    private long freshAt;

    // Ações em andamento (cada uma trava o próprio botão até a resposta ou o prazo)
    private boolean reloading;
    private long reloadingSince;
    private String bindingKey = "";
    private long bindingSince;
    private long clearArmedAt;
    private long tableAskedAt;
    /** Última conferida de versão com o servidor (abrir a tela conta como uma). */
    private long checkedAt = Util.getMillis();
    /** O pedido do mestre mostrado no rodapé ({@code ""} = nenhum) e o que já está sendo respondido. */
    private String askId = "";
    private String askAnswering = "";
    private long askAnsweringSince;
    /** A tecla de rolar/recusar virou ação: o caractere que o GLFW manda logo depois não vai para o filtro. */
    private boolean swallowChar;

    // Vincular a conta do site (passo a passo do not_linked)
    private long linkSentAt;
    private int linkPolls;
    private String linkNote = "";
    private String linkNoteKind = "info";

    // Barras: o valor desenhado anda do antigo para o novo
    private final float[] barFrom = new float[4];
    private final int[] barTo = new int[4];
    private long barAt;

    // Linha dourada da aba ativa (desliza ao trocar)
    private float lineX, lineW, lineFromX, lineFromW;
    private long lineAt;

    // Clarão do clique
    private int pressX, pressY, pressW, pressH;
    private long pressAt;

    // Tooltip sob o mouse (o texto) e desde quando: o atraso de FichaUi.TIP_DELAY_MS
    private String hoverText = "";
    private long hoverSince;

    /*
     * Animações de chegada que não podem recomeçar a cada remontagem (os painéis são refeitos a cada
     * mudança): a classe "-in" entra só na primeira montagem do estado. Guardam o instante do estado
     * já animado.
     */
    private long confirmAnimated;
    private long critAnimated;

    // Textos do resultado no diálogo, montados uma vez por rolagem (o desenho é por frame)
    @Nullable
    private RollCard textFor;
    private int textW;
    private String resultTotal = "", resultOutcome = "", resultMode = "", resultLine = "";
    /** Escala do total: com repetição ("17 · 9") o número grande encolhe para caber. */
    private float resultScale = 2.2f;

    // Diálogo de rolagem
    private record Dialog(String kind, String key, String kicker, String title, String subtitle) {
    }

    @Nullable
    private Dialog dialog;
    private boolean dialogFresh;
    private boolean secret;
    private String dcText = "";
    /** Uma rolagem viaja por vez: fechar o diálogo não a cancela, o botão fica travado até ela voltar. */
    private boolean waiting;
    private long waitingSince;
    @Nullable
    private Dialog waitingFor;
    @Nullable
    private RollCard result;
    private long resultAt;
    private boolean landedShown;
    private String dialogError = "";
    private int dx, dy, dw, dh;

    // Montagem
    /** Um tooltip: a área e o texto; as linhas quebradas só na primeira vez que o mouse passa. */
    private static final class Tip {
        final int x, y, w, h;
        final String text;
        @Nullable
        FichaUi.Tooltip lines;

        Tip(int x, int y, int w, int h, String text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Uma montagem: os painéis, o que o Java desenha por cima, os tooltips e as áreas clicáveis. */
    private static final class Layer {
        final TesseraPanel panel;
        final List<Consumer<GuiGraphics>> draws = new ArrayList<>();
        final List<Tip> tips = new ArrayList<>();
        final List<int[]> hits = new ArrayList<>();

        Layer(TesseraPanel panel) {
            this.panel = panel;
        }
    }

    private final Map<String, TesseraInputState> inputs = new HashMap<>();
    private Layer main = new Layer(TesseraPanel.column(0, 0, 1, 1));
    @Nullable
    private Layer modal;
    private Layer layer = main;
    private Map<String, Runnable> clicks = new HashMap<>();
    private Map<String, Consumer<String>> submits = new HashMap<>();

    FichaScreen(FichaPayloads.View view, boolean test, @Nullable Screen parent) {
        super(Component.literal("Ficha"));
        this.test = test;
        this.parent = parent;
        inputs.put("formula", fresh());
        inputs.put("codigo", fresh());
        apply(view, true);
    }

    boolean isTest() {
        return test;
    }

    boolean isPeek() {
        return (flags & FichaPayloads.PEEK) != 0;
    }

    @Nullable
    Screen parent() {
        return parent;
    }

    /**
     * A roda pediu esta tela já num lugar: {@code dados} abre a rolagem livre, {@code habilidades} a aba
     * 5. Antes do init só muda o estado (o init monta tudo). A ficha espiada pelo mestre não muda.
     */
    void openOn(String intent) {
        if (isPeek()) return;
        switch (intent) {
            case FichaClient.OPEN_SKILLS -> switchTab(Tab.HABILIDADES, true);
            case FichaClient.OPEN_DICE -> {
                if (sheet != null) openFree();
                else setStatus("info", "A rolagem livre libera quando houver uma ficha: veja a aba "
                        + (tab == Tab.VINCULO ? "Vínculo." : "Ficha."), true);
            }
            default -> { }
        }
    }

    // ── Dados vindos do servidor ────────────────────────────────────────────────

    void update(FichaPayloads.View view) {
        apply(view, false);
        // A ficha sumiu (desvinculada no site, /ficha teste semficha): o teste de atributo/perícia aberto
        // não tem mais de onde rolar e ficaria por cima do Vínculo. A rolagem livre não depende dela.
        Dialog d = dialog;
        if (sheet == null && d != null && !"free".equals(d.kind())) {
            closeDialog();
            if (statusText.isEmpty()) setStatus("erro", "A ficha deixou de estar disponível: o teste foi fechado.", false);
        }
        rebuildMain();
    }

    private void apply(FichaPayloads.View view, boolean first) {
        long now = Util.getMillis();
        FichaSheet before = sheet;
        String beforeCode = code;
        flags = view.flags();
        code = view.code();
        siteUrl = view.siteUrl();
        JsonObject json = Json.parseObject(view.json());
        sheet = FichaSheet.parse(Json.object(json, "sheet"));
        // O miolo da roda mostra o personagem: só a ficha própria de verdade (nem a espiada, nem a de teste).
        if (!test && (flags & FichaPayloads.PEEK) == 0) FichaClient.rememberCharacter(sheet == null ? "" : sheet.name());
        binding = Binding.parse(Json.object(json, "binding"));
        habilidades = Habilidades.parse(json);
        var list = Json.array(json, "candidates");
        // Lista cheia substitui a antiga. Vazia não quer dizer "nenhuma": o site só manda candidatos em
        // needs_binding/no_sheet. Então a vazia descarta a antiga (que pode ter ficado velha, como depois
        // de um /bind) e o Vínculo busca de novo quando for mostrado.
        if (list != null && !list.isEmpty()) {
            candidates = Candidate.parseList(list);
            candidatesLoaded = true;
            candidatesLoading = false;
        } else if (list != null && !candidatesLoading) {
            candidates = List.of();
            candidatesLoaded = false;
        }
        profileName = Json.str(json, "profile_name", 16);
        peekName = Json.str(json, "peek_name", 64);
        String peek = Json.str(json, "peek_id", 36);
        try {
            peekId = peek.isEmpty() ? null : UUID.fromString(peek);
        } catch (IllegalArgumentException e) {
            peekId = null;
        }

        // Uma tela nova é a resposta de quem esperava: recarregar, vincular, limpar.
        reloading = false;
        bindingKey = "";
        clearArmedAt = 0;
        boolean linkedNow = "not_linked".equals(beforeCode) && !"not_linked".equals(code) && !first;
        if (!"not_linked".equals(code)) {
            linkSentAt = 0;
            linkNote = "";
        } else if (first) {
            // O passo a passo já abre com o campo do código pronto para colar.
            TesseraInputState field = inputs.get("codigo");
            if (field != null) field.focused = true;
        }

        // Aba: acabou de ganhar ficha (vinculou) → a ficha; needs_binding/no_sheet → Vínculo; sem
        // ficha por outro motivo (não vinculado, outra conta, site fora) a aba Ficha explica o código.
        boolean gotSheet = !first && before == null && sheet != null;
        if (gotSheet) switchTab(Tab.FICHA, false);
        else if ((flags & FichaPayloads.TAB_BIND) != 0 && !isPeek()) switchTab(Tab.VINCULO, false);
        else if (first || sheet == null && tab != Tab.VINCULO && tab != Tab.HABILIDADES) tab = Tab.FICHA;
        else if (tab == Tab.VINCULO && !candidatesLoaded && !candidatesLoading && !isPeek()) switchTab(Tab.VINCULO, false);

        boolean changed = !first && sheet != null && before != null && !sheet.equals(before);
        if (changed || gotSheet) freshAt = now;
        if (linkedNow) setStatus("ok", sheet != null ? "Conta vinculada! Esta é a sua ficha." : "Conta vinculada ao site.", false);
        else if (!view.message().isEmpty()) setStatus("ok".equals(code) ? "ok" : "info", view.message(), false);
        else if (first) setStatus("info", test ? "TESTE LOCAL — dados fictícios; nada vai ao servidor nem ao site." : "", false);
        else if (gotSheet) setStatus("ok", "Ficha carregada" + (sheet.name().isEmpty() ? "." : ": " + sheet.name() + "."), false);
        else if (changed) setStatus("ok", "Ficha atualizada agora.", false);
        updateBars(before, first, now);
    }

    void onCandidates(@Nullable JsonObject json) {
        candidatesLoading = false;
        candidatesLoaded = true;
        candidates = Candidate.parseList(Json.array(json, "candidates"));
        Binding fresh = Binding.parse(Json.object(json, "binding"));
        if (fresh != null) binding = fresh;
        String c = Json.str(json, "code", 24);
        if (!c.isEmpty() && !"ok".equals(c)) setStatus("erro", candidatesError(c), false);
        rebuildMain();
    }

    void onCard(RollCard card) {
        if (card.own() && !card.requestId().isEmpty() && card.requestId().equals(askAnswering)) {
            // O pedido do mestre respondido daqui: o resultado também no rodapé (o cartão grande fica no HUD).
            askAnswering = "";
            String outcome = Texts.outcome(card.roll());
            setStatus("ok", "Pedido do mestre: " + card.roll().label() + " — total " + card.roll().total()
                    + (outcome.isEmpty() ? "." : " · " + outcome.toLowerCase(Locale.ROOT) + "."), true);
            return;
        }
        if (!waiting || !card.own() || !card.requestId().isEmpty()) return;
        waiting = false;
        // O resultado só entra no diálogo do mesmo teste; em outro, o botão só destrava (o HUD mostra o cartão).
        if (dialog != null && dialog.equals(waitingFor)) {
            result = card;
            resultAt = Util.getMillis();
            landedShown = false;
            dialogError = "";
        }
        waitingFor = null;
        rebuildModal();
    }

    void onStatus(FichaPayloads.Status status) {
        String context = status.context();
        boolean ok = "ok".equals(status.state());
        // A resposta ao pedido do mestre falhou (ou foi recusada pelo servidor): o botão do rodapé volta.
        if ("rolar".equals(context) && !ok && !waiting) askAnswering = "";
        if ("rolar".equals(context) && waiting && !ok) {
            boolean same = dialog != null && dialog.equals(waitingFor);
            waiting = false;
            waitingFor = null;
            if (same) {
                dialogError = status.message();
                rebuildModal();
                return;
            }
            rebuildModal();
        }
        if ("vincular".equals(context)) {
            candidatesLoading = false;
            bindingKey = "";
        }
        if ("abrir".equals(context)) reloading = false;
        setStatus(ok ? "ok" : "ocupado".equals(status.state()) ? "espera" : "erro", status.message(), true);
    }

    /** O código do site na lista de candidatos, em português. */
    private static String candidatesError(String code) {
        return switch (code) {
            case "not_linked" -> "Sua conta do Minecraft não está vinculada ao site: veja a aba Ficha para vincular.";
            case "foreign" -> "Este perfil pertence a outra conta do site.";
            case "forbidden" -> "O site não deixou listar as fichas deste perfil.";
            case "unavailable", "busy" -> "O site não respondeu agora. Tente Atualizar em instantes.";
            default -> "O site não listou as fichas agora. Tente Atualizar.";
        };
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────────────

    @Override
    protected void init() {
        FichaUi.reloadCss();
        FichaHud.Ask ask = isPeek() ? null : FichaHud.currentAsk();
        askId = ask == null ? "" : ask.id();
        rebuild();
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return main.panel;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // O renderBackground do Tessera não escurece o mundo; aqui sim, para o texto ler sobre qualquer cena.
        graphics.fill(0, 0, width, height, 0x98000000);
        long now = Util.getMillis();
        Layer top = modal;
        // Com o diálogo aberto, a ficha atrás não reage ao mouse (nem hover, nem tooltip).
        main.panel.render(graphics, top != null ? -10_000 : mouseX, top != null ? -10_000 : mouseY);
        for (Consumer<GuiGraphics> draw : main.draws) draw.accept(graphics);
        if (top != null) {
            // Um degrau de z inteiro para o diálogo (ver MODAL_Z): a ficha atrás fica toda atrás do véu,
            // inclusive os números com sombra e os ícones de item, que o teste de profundidade puxa para frente.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, MODAL_Z);
            graphics.fill(0, 0, width, height, 0x80000000);
            top.panel.render(graphics, mouseX, mouseY);
            for (Consumer<GuiGraphics> draw : top.draws) draw.accept(graphics);
            renderResult(graphics, now);
            renderPress(graphics, now);
            graphics.pose().popPose();
        } else {
            renderPress(graphics, now);
        }
        Tip tip = hoveredTip(top != null ? top : main, mouseX, mouseY);
        // O atraso conta pelo texto (não pelo objeto): uma remontagem com o mouse parado não o reinicia.
        String tipText = tip == null ? "" : tip.text;
        if (!tipText.equals(hoverText)) {
            hoverText = tipText;
            hoverSince = now;
        }
        if (tip != null && now - hoverSince >= FichaUi.TIP_DELAY_MS) {
            if (tip.lines == null) tip.lines = FichaUi.tooltip(font, tip.text, Math.min(220, width / 2));
            FichaUi.drawTooltip(graphics, font, tip.lines, mouseX, mouseY, width, height);
        }
        renderTesseraOverlays(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Nullable
    private static Tip hoveredTip(Layer layer, int mouseX, int mouseY) {
        // O último registrado é o de cima (o botão sobre o cartão, o selo sobre o painel).
        for (int i = layer.tips.size() - 1; i >= 0; i--) {
            Tip tip = layer.tips.get(i);
            if (tip.contains(mouseX, mouseY)) return tip;
        }
        return null;
    }

    private void renderPress(GuiGraphics g, long now) {
        if (pressAt != 0) FichaUi.press(g, pressX, pressY, pressW, pressH, now - pressAt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Prazos e animações que mudam a montagem: só remonta quando algo vence, nunca por tick à toa. */
    @Override
    public void tick() {
        super.tick();
        long now = Util.getMillis();
        boolean mainDirty = false;
        boolean modalDirty = false;
        // O dado pousou: a moldura do resultado ganha a cor (verde, vermelha, dourada).
        if (result != null && !landedShown && !D20.spinning(now - resultAt)) {
            landedShown = true;
            modalDirty = true;
        }
        // Resposta que nunca chegou (servidor reiniciou, pacote descartado): a tela volta a aceitar cliques.
        if (waiting && now - waitingSince > WAIT_TIMEOUT_MS) {
            waiting = false;
            if (dialog != null && dialog.equals(waitingFor)) dialogError = "O servidor não respondeu. Tente de novo.";
            waitingFor = null;
            modalDirty = true;
        }
        if (candidatesLoading && now - loadingSince > WAIT_TIMEOUT_MS) {
            candidatesLoading = false;
            setStatus("erro", "A lista de fichas não chegou. Clique em Atualizar.", false);
            mainDirty = true;
        }
        if (reloading && now - reloadingSince > RELOAD_TIMEOUT_MS) {
            reloading = false;
            setStatus("erro", "O servidor não respondeu. Tente de novo em instantes.", false);
            mainDirty = true;
        }
        if (!bindingKey.isEmpty() && now - bindingSince > WAIT_TIMEOUT_MS) {
            bindingKey = "";
            setStatus("erro", "O vínculo não respondeu. Clique em Atualizar e confira.", false);
            mainDirty = true;
        }
        if (clearArmedAt > 0 && now - clearArmedAt > CONFIRM_MS) {
            clearArmedAt = 0;
            mainDirty = true;
        }
        if (tableAskedAt > 0 && now - tableAskedAt > TABLE_COOLDOWN_MS) {
            tableAskedAt = 0;
            mainDirty = true;
        }
        if (freshAt > 0 && now - freshAt > FRESH_MS) {
            freshAt = 0;
            mainDirty = true;
        }
        if (now - checkedAt >= CHECK_EVERY_MS && dialog == null && (tab == Tab.FICHA || tab == Tab.PERICIAS)) checkFresh(now);
        // Pedido do mestre que chegou (ou saiu) com a tela aberta: o botão no rodapé acompanha o HUD.
        FichaHud.Ask ask = isPeek() ? null : FichaHud.currentAsk();
        String id = ask == null ? "" : ask.id();
        if (!askAnswering.isEmpty() && (!askAnswering.equals(id) || now - askAnsweringSince > ASK_ANSWER_MS)) {
            askAnswering = "";
            mainDirty = true;
        }
        if (!id.equals(askId)) {
            askId = id;
            mainDirty = true;
        }
        // Depois do /vincular: pergunta de novo algumas vezes; a resposta (update) encerra sozinha.
        if (linkSentAt > 0) {
            long since = now - linkSentAt;
            if (linkPolls < LINK_POLLS.length && since >= LINK_POLLS[linkPolls]) {
                linkPolls++;
                send(new FichaPayloads.Action("abrir", "", ""));
            } else if (linkPolls >= LINK_POLLS.length && since > LINK_POLLS[LINK_POLLS.length - 1] + 4_000L) {
                linkSentAt = 0;
                linkNote = "O site ainda não confirmou. Veja a resposta no chat (código vencido?) e tente de novo.";
                linkNoteKind = "erro";
                mainDirty = true;
            }
        }
        if (mainDirty) rebuildMain();
        if (modalDirty) rebuildModal();
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    // ── Entrada ─────────────────────────────────────────────────────────────────

    /** O campo de texto com foco (o código do site); o do diálogo é tratado no próprio diálogo. */
    @Nullable
    private TesseraInputState typing() {
        TesseraInputState code = inputs.get("codigo");
        return code != null && code.focused ? code : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // O campo clicado pega o foco de novo (o Tessera marca o estado dele); clicar fora tira.
        inputs.values().forEach(state -> state.focused = false);
        Layer top = modal;
        if (dialog != null && top != null) {
            if (!(mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh)) {
                closeDialog();
                return true;
            }
            press(top, mouseX, mouseY);
            top.panel.mouseClicked(mouseX, mouseY, button);
            // O diálogo da fórmula tem um campo só: ele fica com o foco o tempo todo.
            if (dialog != null && "free".equals(dialog.kind())) inputs.get("formula").focused = true;
            return true;
        }
        press(main, mouseX, mouseY);
        if (main.panel.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** O clarão de "pressionado" no botão sob o mouse. */
    private void press(Layer layer, double mouseX, double mouseY) {
        for (int i = layer.hits.size() - 1; i >= 0; i--) {
            int[] r = layer.hits.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                pressX = r[0];
                pressY = r[1];
                pressW = r[2];
                pressH = r[3];
                pressAt = Util.getMillis();
                return;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (swallowChar) {
            swallowChar = false;
            return true;
        }
        Dialog d = dialog;
        Layer top = modal;
        if (d != null && top != null) {
            if ("free".equals(d.kind())) {
                top.panel.charTyped(codePoint, modifiers);
            } else if (codePoint >= '0' && codePoint <= '9') {
                String next = dcText.equals("0") ? String.valueOf(codePoint) : dcText + codePoint;
                if (next.length() <= 2) {
                    dcText = next;
                    rebuildModal();
                }
            } else if (codePoint == 's' || codePoint == 'S') {
                toggleSecret();
            }
            return true;
        }
        if (typing() != null) {
            main.panel.charTyped(codePoint, modifiers);
            return true;
        }
        if (filterable() && filter.length() < 16 && (Character.isLetter(codePoint) || codePoint == ' ' && !filter.isEmpty())) {
            filter += codePoint;
            page = 0;
            rebuildMain();
            return true;
        }
        return false;
    }

    /** A aba Perícias com perícias à mostra: letras viram filtro. */
    private boolean filterable() {
        return tab == Tab.PERICIAS && sheet != null && !sheet.skills().isEmpty();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Nada vai para o keyPressed do TesseraScreen: lá o I liga a depuração e o Tab anda por uma
        // lista de foco global que não é a desta tela.
        swallowChar = false;
        if (dialog != null) return dialogKey(keyCode, scanCode, modifiers);
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        TesseraInputState field = typing();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (field != null) field.focused = false;
            else if (!filter.isEmpty()) setFilter("");
            else if (clearArmedAt > 0) {
                clearArmedAt = 0;
                rebuildMain();
            } else onClose();
            return true;
        }
        if (field != null) {
            if (enter) claimLink();
            else main.panel.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // As teclas de rolar/recusar o pedido (sem tecla de fábrica; valem se o jogador as atribuiu) também
        // respondem com a ficha aberta (as teclas de jogo não chegam aqui). Com um filtro sendo digitado, a
        // letra é do filtro: "furtividade" não pode recusar o pedido no "u".
        if (!askId.isEmpty() && filter.isEmpty()
                && (FichaKeys.ROLL_REQUEST.matches(keyCode, scanCode) || FichaKeys.DECLINE_REQUEST.matches(keyCode, scanCode))) {
            swallowChar = true;
            answerAsk(FichaKeys.ROLL_REQUEST.matches(keyCode, scanCode));
            return true;
        }
        if (filterable()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) {
                setFilter(filter.substring(0, filter.length() - 1));
                return true;
            }
            if (enter && !filter.isEmpty()) {
                FichaSheet.Skill first = firstFiltered();
                if (first != null && !isPeek()) openSkill(first);
                return true;
            }
        }
        if (filter.isEmpty() && FichaKeys.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        int digit = keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5 ? keyCode - GLFW.GLFW_KEY_1
                : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_5 ? keyCode - GLFW.GLFW_KEY_KP_1 : -1;
        List<Tab> tabs = visibleTabs();
        if (digit >= 0) {
            if (digit < tabs.size()) switchTab(tabs.get(digit), true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && !tabs.isEmpty()) {
            int at = Math.max(0, tabs.indexOf(tab));
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            switchTab(tabs.get(Math.floorMod(at + step, tabs.size())), true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            turnPage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            turnPage(1);
            return true;
        }
        return keyCode == GLFW.GLFW_KEY_I;
    }

    private boolean dialogKey(int keyCode, int scanCode, int modifiers) {
        Dialog d = dialog;
        Layer top = modal;
        if (d == null) return true;
        boolean free = "free".equals(d.kind());
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> closeDialog();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> roll();
            case GLFW.GLFW_KEY_TAB -> toggleSecret();
            case GLFW.GLFW_KEY_UP -> {
                if (!free) stepDc((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!free) stepDc((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -5 : -1);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (free) {
                    if (top != null) top.panel.keyPressed(keyCode, scanCode, modifiers);
                } else if (!dcText.isEmpty()) {
                    dcText = dcText.substring(0, dcText.length() - 1);
                    rebuildModal();
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (free) {
                    if (top != null) top.panel.keyPressed(keyCode, scanCode, modifiers);
                } else setDc(0);
            }
            default -> {
                if (free && top != null) top.panel.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            if (!"free".equals(dialog.kind()) && scrollY != 0) stepDc(scrollY > 0 ? 1 : -1);
            return true;
        }
        if (scrollY != 0 && tab != Tab.FICHA) {
            turnPage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Ações ───────────────────────────────────────────────────────────────────

    /** Devolve se saiu: sem o canal da ficha no servidor nada fica "esperando" na tela. */
    private boolean send(CustomPacketPayload payload) {
        if (!test) return FichaClient.send(payload);
        FichaTestSession.handle(payload);
        return true;
    }

    private static final String OFFLINE = "Este servidor não tem a ficha do Aurorion.";

    /**
     * Confere com o servidor se a ficha mudou no site ({@code atualizar} com a versão vista): ao voltar
     * para Ficha/Perícias e a cada {@link #CHECK_EVERY_MS} com a tela aberta. O servidor limita e só
     * responde (uma View sem OPEN) quando a versão é outra; {@link #update} troca os dados sem perder
     * aba, página nem o diálogo aberto.
     */
    private void checkFresh(long now) {
        if (test || isPeek() || now - checkedAt < CHECK_MIN_MS) return;
        checkedAt = now;
        FichaSheet s = sheet;
        send(new FichaPayloads.Action("atualizar", s != null && !s.version().isEmpty() ? s.version() : "code:" + code, ""));
    }

    private List<Tab> visibleTabs() {
        List<Tab> out = new ArrayList<>(5);
        if (sheet != null) {
            out.add(Tab.FICHA);
            out.add(Tab.PERICIAS);
            out.add(Tab.TECNICAS);
        }
        if (!isPeek()) out.add(Tab.VINCULO);
        // Vem do jogo, não do site: fica até sem ficha (alt sem vínculo, conta não vinculada).
        out.add(Tab.HABILIDADES);
        return out;
    }

    private void switchTab(Tab next, boolean rebuild) {
        if (next != tab) {
            // A linha dourada sai de onde está desenhada agora (no meio de outra troca, inclusive).
            lineFromX = lineX;
            lineFromW = lineW;
            lineAt = Util.getMillis();
            if (rebuild && (next == Tab.FICHA || next == Tab.PERICIAS)) checkFresh(lineAt);
        }
        tab = next;
        page = 0;
        filter = "";
        if (next == Tab.VINCULO && !candidatesLoaded && !candidatesLoading && !isPeek()) {
            candidatesLoading = send(new FichaPayloads.Action("candidatos", "", ""));
            loadingSince = Util.getMillis();
        }
        if (rebuild) rebuildMain();
    }

    private void turnPage(int delta) {
        int next = Math.max(0, Math.min(pageCount - 1, page + delta));
        if (next == page) return;
        page = next;
        rebuildMain();
    }

    private void setFilter(String next) {
        filter = next;
        page = 0;
        rebuildMain();
    }

    private void reload() {
        if (reloading) return;
        if (send(new FichaPayloads.Action("abrir", "", ""))) {
            reloading = true;
            reloadingSince = Util.getMillis();
            setStatus("espera", "Buscando a ficha no site…", false);
        } else setStatus("erro", OFFLINE, false);
        rebuildMain();
    }

    private void refreshCandidates() {
        if (candidatesLoading) return;
        candidatesLoading = send(new FichaPayloads.Action("candidatos", "", ""));
        loadingSince = Util.getMillis();
        if (!candidatesLoading) setStatus("erro", OFFLINE, false);
        rebuildMain();
    }

    private void useCandidate(Candidate c) {
        if (!bindingKey.isEmpty()) return;
        if (send(new FichaPayloads.Action("vincular", c.subjectType(), String.valueOf(c.subjectId())))) {
            bindingKey = c.subjectType() + "_" + c.subjectId();
            bindingSince = Util.getMillis();
            setStatus("espera", "Vinculando " + (c.name().isEmpty() ? "a ficha" : c.name()) + "…", false);
        } else setStatus("erro", OFFLINE, false);
        rebuildMain();
    }

    /** "Padrão" apaga o vínculo: o primeiro clique arma, o segundo (em até 4 s) confirma. */
    private void clearBinding() {
        if (!bindingKey.isEmpty()) return;
        long now = Util.getMillis();
        if (clearArmedAt == 0 || now - clearArmedAt > CONFIRM_MS) {
            clearArmedAt = now;
            rebuildMain();
            return;
        }
        clearArmedAt = 0;
        if (send(new FichaPayloads.Action("limpar", "", ""))) {
            bindingKey = "limpar";
            bindingSince = now;
            setStatus("espera", "Removendo o vínculo…", false);
        } else setStatus("erro", OFFLINE, false);
        rebuildMain();
    }

    private void askTable() {
        if (tableAskedAt > 0) return;
        if (send(new FichaPayloads.Action("mestre", "", ""))) tableAskedAt = Util.getMillis();
        else setStatus("erro", OFFLINE, false);
        rebuildMain();
    }

    /**
     * Responde ao pedido do mestre que está no HUD (o mesmo da roda e das teclas de pedido, que dentro
     * de uma tela não disparam). Rolar trava os botões até o pedido sair do HUD ou o prazo vencer.
     */
    private void answerAsk(boolean roll) {
        FichaHud.Ask ask = FichaHud.currentAsk();
        if (ask == null || ask.id().equals(askAnswering)) return;
        FichaClient.answerCurrent(roll);
        if (roll) {
            askAnswering = ask.id();
            askAnsweringSince = Util.getMillis();
            setStatus("espera", "Rolando o pedido do mestre: " + ask.test() + "…", false);
        } else {
            setStatus("info", "Pedido do mestre recusado.", false);
        }
        askId = "";
        rebuildMain();
    }

    /** Manda o {@code /vincular CÓDIGO} pela própria tela e passa a perguntar se o site já ligou a conta. */
    private void claimLink() {
        long now = Util.getMillis();
        if (linkSentAt > 0) return; // já conferindo: o servidor do /vincular também tem 5 s entre tentativas
        String raw = field("codigo").replaceAll("\\s+", "");
        if (raw.isEmpty()) {
            linkNote = "Cole primeiro o código que o site mostrou.";
            linkNoteKind = "erro";
        } else if (raw.length() > 32 || !raw.matches("[A-Za-z0-9_-]+")) {
            linkNote = "Esse código tem caracteres que o site não gera. Copie de novo, só o código.";
            linkNoteKind = "erro";
        } else {
            ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
            if (!test && connection == null) {
                linkNote = "Sem conexão com o servidor agora.";
                linkNoteKind = "erro";
            } else {
                if (!test) connection.sendCommand("vincular " + raw);
                linkSentAt = now;
                linkPolls = 0;
                linkNote = test ? "TESTE LOCAL — simulando o vínculo; a ficha abre sozinha."
                        : "Conferindo o código com o site… a ficha abre sozinha quando ele confirmar.";
                linkNoteKind = "espera";
                TesseraInputState state = inputs.get("codigo");
                if (state != null) state.focused = false;
            }
        }
        rebuildMain();
    }

    private void openSkill(FichaSheet.Skill skill) {
        FichaSheet s = sheet;
        FichaSheet.Attribute a = s == null ? null : s.attribute(skill.attribute());
        String attr = a != null && !a.label().isEmpty() ? a.label() : Catalog.attributeLabel(skill.attribute());
        String parts = a == null ? attr : attr + " " + a.effective() + " + treino " + skill.value();
        openDialog(new Dialog("skill", skill.id(), "TESTE DE PERÍCIA", skill.label(),
                parts + "  ·  1d20 " + Texts.signed(skill.bonus())));
    }

    private void openAttribute(String id, String label, int value) {
        openDialog(new Dialog("attribute", id, "TESTE DE ATRIBUTO", label.isEmpty() ? Catalog.attributeLabel(id) : label,
                Catalog.attributeAbbreviation(id) + " " + value + "  ·  1d20 " + Texts.signed(value)));
    }

    private void openFree() {
        openDialog(new Dialog("free", "", "ROLAGEM LIVRE", "Dados", "Fórmula do motor: 1d20+5, 2d6+3, 4d6kh3, 2#d20+5 (2 rolagens)"));
    }

    private void openDialog(Dialog next) {
        if (isPeek()) return;
        dialog = next;
        dialogFresh = true;
        dcText = ""; // a CD é de cada teste; a fórmula livre fica para repetir
        result = null;
        dialogError = "";
        inputs.values().forEach(state -> state.focused = false);
        if ("free".equals(next.kind())) {
            TesseraInputState formula = inputs.get("formula");
            formula.focused = true;
            formula.cursor = field("formula").length();
            formula.selStart = formula.cursor;
        }
        rebuildModal();
    }

    private void closeDialog() {
        dialog = null;
        result = null;
        dialogError = "";
        modal = null;
        inputs.values().forEach(state -> state.focused = false);
    }

    private void toggleSecret() {
        secret = !secret;
        rebuildModal();
    }

    private int dcValue() {
        try {
            return dcText.isEmpty() ? 0 : Integer.parseInt(dcText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setDc(int dc) {
        dcText = dc <= 0 ? "" : String.valueOf(Math.min(99, dc));
        rebuildModal();
    }

    /** Setas/roda: sem CD, começa em 10 (a CD "média"); abaixo de 1 volta a vazia. */
    private void stepDc(int delta) {
        int now = dcValue();
        setDc(now == 0 ? (delta > 0 ? 10 : 0) : now + delta);
    }

    private void roll() {
        Dialog d = dialog;
        if (d == null || waiting) return;
        if ("free".equals(d.kind())) {
            String formula = field("formula").strip();
            if (formula.isEmpty()) {
                dialogError = "Escreva uma fórmula, como 2d6+3.";
                rebuildModal();
                return;
            }
            // O pacote tem teto de tamanho (estourar derruba o envio): corta antes, não confia no maxlength.
            if (!send(new FichaPayloads.Roll("free", clip(formula, 60), 0, secret, ""))) {
                dialogError = OFFLINE;
                rebuildModal();
                return;
            }
        } else {
            int dc = dcValue();
            if (!dcText.isEmpty() && (dc < 1 || dc > 99)) {
                dialogError = "A CD vai de 1 a 99 (ou deixe vazia).";
                rebuildModal();
                return;
            }
            if (!send(new FichaPayloads.Roll(d.kind(), d.key(), dc, secret, ""))) {
                dialogError = OFFLINE;
                rebuildModal();
                return;
            }
        }
        waiting = true;
        waitingSince = Util.getMillis();
        waitingFor = d;
        result = null;
        dialogError = "";
        rebuildModal();
    }

    private void openSite() {
        openUrl(siteUrl);
    }

    /** A página do site em que se gera o código de vínculo (Perfil → RPG → No jogo), na mesma origem da ficha. */
    private String linkPageUrl() {
        if (siteUrl.isEmpty()) return "";
        try {
            URI uri = URI.create(siteUrl);
            if (uri.getScheme() == null || uri.getHost() == null) return siteUrl;
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/profile/rpg#no-jogo";
        } catch (IllegalArgumentException e) {
            return siteUrl;
        }
    }

    private void openUrl(String url) {
        if (url.isEmpty() || minecraft == null) {
            setStatus("info", test ? "TESTE LOCAL — sem endereço de site." : "O endereço do site não veio do servidor.", true);
            return;
        }
        // O construtor (callback, url, confiável) existe igual há várias versões; confirmar e voltar à ficha.
        minecraft.setScreen(new ConfirmLinkScreen(accepted -> {
            if (accepted) Util.getPlatform().openUri(url);
            if (minecraft != null) minecraft.setScreen(this);
        }, url, true));
    }

    private void setStatus(String kind, String text, boolean rebuild) {
        statusKind = kind;
        statusText = text;
        if (rebuild) rebuildMain();
    }

    // ── Montagem: peças ─────────────────────────────────────────────────────────

    /** Registra um clique (o nome vai no {@code onclick} do HTML). */
    private String on(String name, Runnable action) {
        clicks.put(name, action);
        return name;
    }

    private void panel(String html, int x, int y, int w, int h) {
        FichaUi.add(layer.panel, html, clicks, submits, inputs, x, y, w, h);
    }

    private void draw(Consumer<GuiGraphics> draw) {
        layer.draws.add(draw);
    }

    private void tip(int x, int y, int w, int h, String text) {
        if (!text.isEmpty() && w > 0 && h > 0) layer.tips.add(new Tip(x, y, w, h, text));
    }

    /** Uma linha de texto num painel: cortada com "…" quando não cabe, com o texto inteiro no tooltip. */
    private void text(String cls, String text, int size, int x, int y, int w, int h) {
        if (text.isEmpty() || w <= 0) return;
        String shown = FichaUi.fit(font, text, w, size);
        panel("<col class=\"cell\"><label class=\"" + cls + "\">" + FichaUi.esc(shown) + "</label></col>", x, y, w, h);
        if (!shown.equals(text)) tip(x, y, Math.min(w, FichaUi.width(font, shown, size) + 2), h, text);
    }

    /** Texto quebrado em até {@code maxLines} linhas de {@code lineH}; o que sobrar vai para o tooltip. Devolve a altura. */
    private int lines(String cls, String text, int size, int x, int y, int w, int maxLines, int lineH) {
        if (text.isEmpty() || w <= 0) return 0;
        List<String> all = FichaUi.wrap(font, text, w, size, maxLines + 1);
        List<String> shown = all.size() > maxLines ? FichaUi.wrap(font, text, w, size, maxLines) : all;
        // Um painel por linha, no y exato: num <col> com vários labels o Tessera 1.1 deixa ~4px a mais
        // entre as linhas e o parágrafo transbordava o cartão (técnicas, história).
        int labelH = Math.max(lineH, (int) Math.ceil(9 * size / 7.0));
        for (int i = 0; i < shown.size(); i++) {
            panel("<col class=\"cell\"><label class=\"" + cls + "\">" + FichaUi.esc(shown.get(i)) + "</label></col>",
                    x, y + i * lineH, w, labelH);
        }
        int h = shown.size() * lineH + 2;
        if (all.size() > maxLines) tip(x, y, w, h, text);
        return h;
    }

    /** Largura de um botão {@code .b-*}: o texto na fonte do Tessera, 6px de respiro de cada lado e a borda. */
    private int buttonW(String text, int size, boolean upper, boolean bold) {
        return FichaUi.buttonWidth(font, text, size, 6, upper, bold) + 2;
    }

    /**
     * Um botão: {@code <row onclick>} com o texto num label (o {@code <button>} do Tessera 1.1 não tem
     * borda nem cantos). Sem ação, sai sem {@code onclick} — o "desabilitado" — e o tooltip diz o porquê.
     */
    private void button(String id, String cls, String text, boolean upper, int x, int y, int w, int h,
                        @Nullable Runnable action, String tooltip) {
        String label = upper ? text.toUpperCase(Locale.ROOT) : text;
        String click = action == null ? "" : " onclick=\"" + on(id, action) + "\"";
        panel("<row class=\"" + cls + "\"" + click + "><label>" + FichaUi.esc(label) + "</label></row>", x, y, w, h);
        if (action != null) layer.hits.add(new int[]{x, y, w, h});
        tip(x, y, w, h, tooltip);
    }

    private int chipW(String text) {
        return FichaUi.width(font, text.toUpperCase(Locale.ROOT), 5) + 10;
    }

    /** Um selo (um {@code <row>} com fundo; o label não pinta fundo). Devolve a largura. */
    private int chip(String cls, String text, int x, int y, String tooltip) {
        int w = chipW(text);
        panel("<row class=\"" + cls + "\"><label>" + FichaUi.esc(text.toUpperCase(Locale.ROOT)) + "</label></row>", x, y, w, 9);
        tip(x, y, w, 9, tooltip);
        return w;
    }

    /** Selos lado a lado até {@code w}; o que não cabe fica de fora. {@code chips}: classe, texto, tooltip. */
    private void chipLine(List<String[]> chips, int x, int y, int w) {
        int used = 0;
        for (String[] c : chips) {
            int cw = chipW(c[1]);
            if (used + cw > w) break;
            chip(c[0], c[1], x + used, y, c.length > 2 ? c[2] : "");
            used += cw + 3;
        }
    }

    // ── Montagem: a tela ────────────────────────────────────────────────────────

    private void rebuild() {
        rebuildMain();
        rebuildModal();
    }

    private void rebuildMain() {
        if (width <= 0 || height <= 0 || font == null) return; // ainda sem init: o init monta tudo
        // A lista de foco do Tessera é global e só cresce a cada montagem; esta tela não a usa.
        TesseraFocusManager.clear();
        clicks = new HashMap<>();
        submits = new HashMap<>();
        layer = new Layer(TesseraPanel.column(0, 0, width, height));

        int w = Math.min(width - 12, MAX_W);
        int h = Math.min(height - 12, MAX_H);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int ix = x + PAD, iw = w - 2 * PAD;
        panel("<col class=\"" + (entered ? "frame" : "frame frame-in") + "\"></col>", x, y, w, h);
        entered = true;
        header(ix, y + PAD, iw);
        int tabsY = y + PAD + HEAD_H + GAP;
        tabs(ix, tabsY, iw);
        int bodyY = tabsY + TABS_H + GAP;
        int footY = y + h - PAD - FOOT_H;
        int bodyH = footY - GAP - bodyY;
        pageCount = 1;
        if (sheet == null && tab != Tab.VINCULO && tab != Tab.HABILIDADES) stateBody(ix, bodyY, iw, bodyH);
        else switch (tab) {
            case FICHA -> fichaBody(ix, bodyY, iw, bodyH);
            case PERICIAS -> skillsBody(ix, bodyY, iw, bodyH);
            case TECNICAS -> abilitiesBody(ix, bodyY, iw, bodyH);
            case VINCULO -> bindingBody(ix, bodyY, iw, bodyH);
            case HABILIDADES -> habilidadesBody(ix, bodyY, iw, bodyH);
        }
        footer(ix, footY, iw);
        layer.panel.layout();
        main = layer;
    }

    private void rebuildModal() {
        if (dialog == null || width <= 0 || height <= 0 || font == null) {
            modal = null;
            return;
        }
        Map<String, Runnable> mainClicks = clicks;
        Map<String, Consumer<String>> mainSubmits = submits;
        clicks = new HashMap<>();
        submits = new HashMap<>();
        layer = new Layer(TesseraPanel.column(0, 0, width, height));
        dialogPanels();
        layer.panel.layout();
        modal = layer;
        layer = main;
        clicks = mainClicks;
        submits = mainSubmits;
    }

    // ── Cabeçalho ───────────────────────────────────────────────────────────────

    private void header(int x, int y, int w) {
        FichaSheet s = sheet;
        panel("<col class=\"portrait\"></col>", x, y, 38, 38);
        UUID faceOf = isPeek() ? peekId : null;
        draw(g -> FichaUi.face(g, faceOf, x + 3, y + 3, 32));
        tip(x, y, 38, 38, isPeek() ? "Ficha de " + peekName + ", vista pelo mestre" : "Seu personagem no jogo");

        boolean gmButton = (flags & FichaPayloads.GM) != 0 && !isPeek();
        boolean siteButton = !siteUrl.isEmpty() || test;
        int gmW = gmButton ? buttonW("Mesa", 6, true, false) : 0;
        int siteW = siteButton ? buttonW("Site", 6, true, false) : 0;
        int buttonsUsed = gmButton && siteButton ? gmW + 3 + siteW : gmW + siteW;
        int buttonsW = Math.max(16, buttonsUsed);
        int tx = x + 46;
        int tw = w - 46 - buttonsW - 6;
        String name = isPeek() ? (s != null && !s.name().isEmpty() ? s.name() : peekName)
                : s != null && !s.name().isEmpty() ? s.name() : "Ficha do personagem";
        text("kicker", isPeek() ? "VISÃO DO MESTRE · " + peekName.toUpperCase(Locale.ROOT)
                : s != null && s.npc() ? "NPC CONTROLADO" : "FICHA DE PERSONAGEM", 5, tx, y, tw, 7);
        // Kicker, nome, subtítulo e selos com 2px de ar entre um e outro (o nome em 10px não encosta mais no kicker).
        text("name", name, 10, tx, y + 9, tw, 13);
        if (s != null) {
            List<String> parts = new ArrayList<>();
            if (!s.title().isEmpty()) parts.add(s.title());
            parts.add("Alma " + s.soul());
            // O nível da ficha antiga: número ("Legado 2") ou rótulo do site ("Legado: Anomalia").
            String legacy = s.legacy();
            if (!legacy.isEmpty()) parts.add(legacy.chars().allMatch(Character::isDigit) ? "Legado " + legacy
                    : "Legado: " + Catalog.legacyLabel(legacy));
            text("subtitle", String.join("  ·  ", parts), 6, tx, y + 22, tw, 8);
            chipLine(headerChips(s), tx, y + 31, tw);
        }

        // Fechar (ou voltar à mesa) no canto; Mesa e Site embaixo dele, alinhados à direita.
        int bx = x + w - buttonsW;
        boolean back = parent != null;
        button("fechar", "b-icon", back ? "«" : "×", false, bx + buttonsW - 14, y - 2, 14, 12, this::onClose,
                back ? "Voltar à mesa do mestre (Esc)" : "Fechar a ficha (Esc ou " + keyName() + ")");
        int right = bx + buttonsW;
        if (siteButton) {
            right -= siteW;
            button("site", "b", "Site", true, right, y + 16, siteW, 13, this::openSite, "Abrir esta ficha no site (pede confirmação)");
            right -= 3;
        }
        if (gmButton) {
            right -= gmW;
            boolean asked = tableAskedAt > 0;
            button("mesa", asked ? "b-off" : "b-gm", "Mesa", true, right, y + 16, gmW, 13, asked ? null : this::askTable,
                    asked ? "Abrindo a mesa do mestre…" : "Mesa do mestre: pedir testes aos jogadores por perto");
        }
    }

    /** "Teste de X" → "teste de X", para o meio da frase. */
    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private String keyName() {
        String k = FichaUi.keyName(FichaKeys.OPEN);
        return k.isEmpty() ? "a tecla da ficha" : k;
    }

    private List<String[]> headerChips(FichaSheet s) {
        List<String[]> chips = new ArrayList<>();
        if (freshAt > 0) chips.add(new String[]{"chip-ok", "Atualizada agora", "A ficha acabou de chegar do site."});
        if (test) chips.add(new String[]{"chip-cyan", "Teste local", "/ficha teste: dados fictícios, nada vai ao servidor nem ao site."});
        if (isPeek()) chips.add(new String[]{"chip-gold", "Só leitura", "Você vê esta ficha como mestre; os testes são do jogador."});
        if (s.element() != null) chips.add(new String[]{"el-" + Catalog.elementClass(s.element().id()), s.element().label(),
                "Marca: " + s.element().label()});
        if (s.grade().value() > 0 && !s.grade().label().isEmpty()) chips.add(new String[]{"chip-gold", s.grade().label(),
                "Grau " + s.grade().value()});
        if (s.npc()) chips.add(new String[]{"chip", "NPC", "Este perfil interpreta um NPC."});
        if (s.pendingReview()) chips.add(new String[]{"chip-warn", "Aguardando aprovação",
                "A construção foi enviada e espera a equipe aprovar no site."});
        if (s.progressionPending()) chips.add(new String[]{"chip-cyan", "Progressão pendente",
                "Há uma progressão da ficha esperando aprovação no site."});
        if (!s.hasBuild() && !s.pendingReview()) chips.add(new String[]{"chip-warn", "Ficha antiga",
                "A ficha ainda não foi atualizada para o sistema novo no site. Monte a construção lá e envie para aprovar."});
        if (binding != null && binding.stale()) chips.add(new String[]{"chip-bad", "Vínculo antigo",
                "A ficha escolhida para este perfil saiu do seu controle. Veja a aba Vínculo."});
        return chips;
    }

    // ── Abas ────────────────────────────────────────────────────────────────────

    private void tabs(int x, int y, int w) {
        List<Tab> visible = visibleTabs();
        List<String> labels = new ArrayList<>(visible.size());
        for (Tab t : visible) labels.add(tabLabel(t, true));
        int total = tabsWidth(labels);
        if (total > w) {
            labels.clear();
            for (Tab t : visible) labels.add(tabLabel(t, false));
            total = tabsWidth(labels);
        }
        int cx = x;
        float activeX = -1, activeW = 0;
        for (int i = 0; i < visible.size(); i++) {
            Tab t = visible.get(i);
            String label = labels.get(i);
            int tw = buttonW(label, 7, false, false) + 4;
            boolean on = t == tab;
            String about = t.about + "\nAtalho: " + (i + 1) + " (Tab passa para a próxima)";
            button("aba_" + t.name(), on ? "b-tab-on" : "b-tab", label, false, cx, y, tw, TABS_H, on ? null : () -> switchTab(t, true), about);
            if (on) {
                activeX = cx;
                activeW = tw;
            }
            String digit = FACES[i + 1];
            int dxx = cx + tw - 4, dyy = y + 2;
            draw(g -> FichaUi.text(g, font, digit, dxx, dyy, 0x708C8098, 0.5f, false));
            cx += tw + 3;
        }
        if (tab == Tab.PERICIAS && sheet != null && !sheet.skills().isEmpty()) filterBox(cx + 4, y, x + w - cx - 4);
        // A linha dourada sob as abas, e o trecho mais forte sob a aba ativa (desliza de uma para outra).
        float toX = activeX, toW = activeW;
        draw(g -> {
            g.fill(x, y + TABS_H + 1, x + w, y + TABS_H + 2, 0x30D7A857);
            if (toX < 0) {
                lineW = 0;
                return;
            }
            float t = lineAt == 0 || lineFromW <= 0 ? 1f : FichaUi.easeOut((Util.getMillis() - lineAt) / (float) TAB_ANIM_MS);
            lineX = t >= 1f ? toX : lineFromX + (toX - lineFromX) * t;
            lineW = t >= 1f ? toW : lineFromW + (toW - lineFromW) * t;
            g.fill(Math.round(lineX) + 3, y + TABS_H, Math.round(lineX + lineW) - 3, y + TABS_H + 2, FichaUi.GOLD);
        });
    }

    private String tabLabel(Tab t, boolean counts) {
        FichaSheet s = sheet;
        if (counts && t == Tab.HABILIDADES && !habilidades.isEmpty()) return t.label + " · " + habilidades.total();
        if (!counts || s == null) return t.label;
        return switch (t) {
            case PERICIAS -> s.skills().isEmpty() ? t.label : t.label + " · " + s.skills().size();
            case TECNICAS -> s.abilities().isEmpty() ? t.label : t.label + " · " + s.abilities().size();
            default -> t.label;
        };
    }

    private int tabsWidth(List<String> labels) {
        int total = 0;
        for (String label : labels) total += buttonW(label, 7, false, false) + 4 + 3;
        return total - 3;
    }

    /** O filtro das perícias, no fim da linha das abas: o que foi digitado e um ✕; vazio, só a dica. */
    private void filterBox(int x, int y, int room) {
        if (room < 50) return;
        int w = Math.min(room, 110);
        int fx = x + room - w;
        if (filter.isEmpty()) {
            String hint = FichaUi.fit(font, "digite para filtrar", w - 4, 5);
            draw(g -> FichaUi.textRight(g, font, hint, fx + w - 2, y + 5, FichaUi.FAINT, 5 / 7f, false));
            return;
        }
        panel("<row class=\"filter\"><label>" + FichaUi.esc(FichaUi.fit(font, "Filtro: " + filter, w - 22, 6)) + "</label></row>", fx, y + 1, w, TABS_H - 2);
        button("limpar_filtro", "b-icon", "×", false, fx + w - 13, y + 2, 12, TABS_H - 4, () -> setFilter(""), "Limpar o filtro (Esc)");
    }

    // ── Sem ficha (código do site) ──────────────────────────────────────────────

    private void stateBody(int x, int y, int w, int h) {
        if ("not_linked".equals(code) && !isPeek()) {
            linkBody(x, y, w, h);
            return;
        }
        String title = switch (code) {
            case "foreign" -> "Perfil de outra conta";
            case "gm_required" -> "Mestre não confirmado";
            case "needs_binding" -> "Este personagem ainda não tem ficha";
            case "no_sheet" -> "Nenhuma ficha ativa";
            case "not_linked" -> isPeek() ? "Conta do site não vinculada" : "A ficha não carregou";
            default -> "A ficha não carregou";
        };
        String body = statusText.isEmpty() || "espera".equals(statusKind) ? switch (code) {
            case "foreign" -> "Este perfil Minecraft está vinculado a outra conta do site. Desvincule por lá ou entre com a conta certa.";
            case "needs_binding", "no_sheet" -> "Escolha qual ficha este perfil usa na aba Vínculo.";
            default -> "O site não respondeu agora. Tente de novo em instantes.";
        } : statusText;
        int pw = Math.min(w, 300);
        int px = x + (w - pw) / 2;
        List<String> wrapped = FichaUi.wrap(font, body, pw - 20, 6, 4);
        int ph = 46 + wrapped.size() * 9;
        int py = y + Math.max(0, (h - ph) / 2 - 6);
        panel("<col class=\"panel-warn\"></col>", px, py, pw, ph);
        text("line-gold", title, 7, px + 10, py + 7, pw - 20, 10);
        lines("line-soft", body, 6, px + 10, py + 19, pw - 20, 4, 9);
        if (isPeek()) return;
        // O que dá para fazer, a um clique: tentar de novo, escolher ficha, abrir o site.
        int by = py + ph - 19;
        int bx = px + 10;
        if ("needs_binding".equals(code) || "no_sheet".equals(code)) {
            int bw = buttonW("Escolher ficha", 6, true, true);
            button("ir_vinculo", "b-gold", "Escolher ficha", true, bx, by, bw, 13, () -> switchTab(Tab.VINCULO, true), "Abrir a aba Vínculo (4)");
            bx += bw + 4;
        } else {
            int bw = buttonW(reloading ? "Buscando…" : "Tentar de novo", 6, true, true);
            button("recarregar", reloading ? "b-off" : "b-gold", reloading ? "Buscando…" : "Tentar de novo", true, bx, by, bw, 13,
                    reloading ? null : this::reload, reloading ? "Esperando a resposta do site…" : "Pedir a ficha ao site de novo");
            if (reloading) {
                int dotX = bx + bw + 10, dotY = by + 6;
                draw(g -> FichaUi.dots(g, dotX, dotY, Util.getMillis(), FichaUi.WARN));
            }
            bx += bw + 4;
        }
        if (!siteUrl.isEmpty()) {
            int bw = buttonW("Abrir o site", 6, true, false);
            button("site_estado", "b", "Abrir o site", true, bx + (reloading ? 14 : 0), by, bw, 13, this::openSite, "Abrir o site (pede confirmação)");
        }
    }

    /**
     * Conta do site não vinculada: o passo a passo, com o link da página do código e um campo para
     * colá-lo. "Vincular" manda o {@code /vincular} por conta própria e a tela pergunta de novo ao
     * servidor até a ficha chegar (ou desistir com a explicação).
     */
    private void linkBody(int x, int y, int w, int h) {
        int pw = Math.min(w, 340);
        int px = x + (w - pw) / 2;
        int stepH = Math.max(20, Math.min(26, (h - 44) / 3));
        int ph = 22 + 3 * stepH + 18;
        int py = y + Math.max(0, (h - ph) / 2);
        panel("<col class=\"panel-warn\"></col>", px, py, pw, ph);
        text("line-gold", "Vincule sua conta do site para ver a ficha aqui", 7, px + 10, py + 6, pw - 20, 10);
        int sy = py + 20;
        int textX = px + 26;
        boolean sending = linkSentAt > 0;
        String linkUrl = linkPageUrl();

        // 1 — abrir o site
        int b1 = buttonW("Abrir o site", 6, true, false);
        step(1, sending, px + 10, sy);
        lines("line", "No site, entre com a sua conta e abra Perfil → RPG → No jogo.", 6, textX, sy + 1, pw - 36 - b1 - 6, 2, 8);
        button("site_vincular", linkUrl.isEmpty() ? "b-off" : "b", "Abrir o site", true, px + pw - 10 - b1, sy, b1, 13,
                linkUrl.isEmpty() ? null : () -> openUrl(linkUrl),
                linkUrl.isEmpty() ? "O servidor não mandou o endereço do site." : "Abrir " + linkUrl + " (pede confirmação)");
        sy += stepH;

        // 2 — gerar o código
        step(2, sending, px + 10, sy);
        lines("line", "Clique em “Vincular minha conta Minecraft” e copie o código que aparecer.", 6, textX, sy + 1, pw - 36, 2, 8);
        sy += stepH;

        // 3 — colar e vincular
        step(3, false, px + 10, sy);
        int b3 = buttonW(sending ? "Conferindo…" : "Vincular", 6, true, true);
        int fieldW = Math.max(60, Math.min(120, pw - 36 - b3 - 6 - 70));
        text("line", "Cole o código:", 6, textX, sy + 3, 64, 9);
        int fx = textX + 66;
        panel("<row class=\"cell\"><input id=\"codigo\" class=\"input\" placeholder=\"código do site\" maxlength=\"32\" value=\""
                + FichaUi.esc(field("codigo")) + "\"/></row>", fx, sy, fieldW, 14);
        tip(fx, sy, fieldW, 14, "O código que o site mostra em Perfil → RPG → No jogo.\nCtrl+V cola; Enter vincula.");
        button("vincular_conta", sending ? "b-off" : "b-gold", sending ? "Conferindo…" : "Vincular", true, fx + fieldW + 4, sy, b3, 14,
                sending ? null : this::claimLink, sending ? "Esperando o site confirmar o código…"
                        : "Manda o código ao site por você (o mesmo que digitar /vincular CÓDIGO)");
        sy += stepH;

        // Situação: conferindo (com os pontinhos), erro, ou a dica do que acontece depois.
        String note = !linkNote.isEmpty() ? linkNote : "Depois de vincular, a ficha abre sozinha nesta tela.";
        String cls = linkNote.isEmpty() ? "hint" : "status-" + linkNoteKind;
        int noteX = sending ? px + 22 : px + 10;
        text(cls, note, 6, noteX, sy + 1, px + pw - 10 - noteX, 9);
        if (sending) {
            int dotY = sy + 4;
            draw(g -> FichaUi.dots(g, px + 15, dotY, Util.getMillis(), FichaUi.WARN));
        }
    }

    /** O número do passo num quadradinho dourado (verde quando já ficou para trás). */
    private void step(int n, boolean done, int x, int y) {
        panel("<col class=\"" + (done ? "step-done" : "step") + "\"></col>", x, y + 1, 11, 11);
        String digit = FACES[n];
        int color = done ? FichaUi.OK : FichaUi.GOLD_HI;
        draw(g -> FichaUi.textCenter(g, font, digit, x + 5.5f, y + 3.5f, color, 0.72f, false));
    }

    // ── Aba Ficha ───────────────────────────────────────────────────────────────

    private void fichaBody(int x, int y, int w, int h) {
        FichaSheet s = sheet;
        if (s == null) return;
        if (!s.hasBuild() || s.derived() == null) {
            noBuildBody(s, x, y, w, h);
            return;
        }
        FichaSheet.Derived d = s.derived();
        FichaSheet.Resources r = s.resources();
        int lw = (w - GAP) * 46 / 100;
        int rw = w - lw - GAP;
        int rx = x + lw + GAP;

        // Recursos: o nome na cor do recurso, o número em vermelho quando está em 1/4 ou menos.
        panel("<col class=\"panel\"></col>", x, y, lw, h);
        panel("<col class=\"cell\"><label class=\"section\">Recursos</label></col>", x + 7, y + 5, lw - 14, 7);
        int[][] bars = {
                {r.vida(), d.vidaMax(), r.tempHp(), FichaUi.VIDA, FichaUi.VIDA_HI},
                {r.stamina(), d.staminaMax(), 0, FichaUi.STAMINA, FichaUi.STAMINA_HI},
                {r.sanidade(), d.sanidadeMax(), 0, FichaUi.SANIDADE, FichaUi.SANIDADE_HI},
                {r.pe(), d.peMax(), 0, FichaUi.PE, FichaUi.PE_HI}};
        String[] names = {"VIDA", "STAMINA", "SANIDADE", "P.E."};
        String[] about = {
                "Pontos de vida atuais / máximos.",
                "Stamina atual / máxima.",
                "Sanidade atual / máxima. Em zero, o personagem perde a decisão normal.",
                "P.E. atuais / máximos. O máximo vem da Alma (" + s.soul() + ")."};
        int barW = lw - 14;
        int rowH = Math.max(15, Math.min(19, (h - 70) / 4));
        int barH = rowH >= 18 ? 7 : 6;
        for (int i = 0; i < bars.length; i++) {
            int[] b = bars[i];
            int by = y + 15 + i * rowH;
            String name = names[i];
            String value = b[0] + " / " + b[1] + (b[2] > 0 ? "  +" + b[2] : "");
            boolean low = b[1] > 0 && b[0] * 4 <= b[1];
            String tooltip = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT) + " " + b[0] + " / " + b[1]
                    + "\n" + about[i]
                    + (b[2] > 0 ? "\n+" + b[2] + " de Vida temporária: some primeiro quando o dano chega." : "")
                    + (low ? "\nUm quarto ou menos do máximo." : "");
            // A moldura do hover cobre a linha toda; a barra anda do valor antigo para o novo.
            panel("<col class=\"res\"></col>", x + 4, by - 2, lw - 8, rowH);
            tip(x + 4, by - 2, lw - 8, rowH, tooltip);
            int index = i;
            draw(g -> {
                FichaUi.text(g, font, name, x + 7, by, b[4], 0.72f, false);
                FichaUi.textRight(g, font, value, x + 7 + barW, by, low ? FichaUi.BAD : FichaUi.INK, 0.72f, false);
                FichaUi.bar(g, x + 7, by + 7, barW, barH, Math.round(barShown(index, Util.getMillis())), b[1], b[2], b[3], b[4]);
            });
        }
        int cy = y + 15 + 4 * rowH + 2;
        String vital = r.vital();
        if (!vital.isEmpty() && !"consciente".equals(vital)) {
            String label = switch (vital) {
                case "queda" -> "EM QUEDA · sucessos " + r.deathSuccesses() + "/3 · falhas " + r.deathFailures() + "/3";
                case "estabilizado" -> "ESTABILIZADO";
                case "morto" -> "MORTO EM COMBATE";
                default -> Catalog.vitalLabel(vital).toUpperCase(Locale.ROOT);
            };
            text("line-bad", label, 6, x + 7, cy, lw - 14, 9);
            if ("queda".equals(vital)) tip(x + 7, cy, lw - 14, 9, "Em queda\nTrês sucessos estabilizam; três falhas, morte.");
            cy += 10;
        }
        panel("<col class=\"cell\"><label class=\"section\">Condições</label></col>", x + 7, cy, lw - 14, 7);
        cy += 9;
        List<String[]> conditions = new ArrayList<>();
        for (String id : r.conditions()) {
            Catalog.Condition c = Catalog.condition(id);
            String severity = c.severity().isEmpty() || "estado".equals(c.severity()) ? "" : " (" + c.severity() + ")";
            // Nome (gravidade), o efeito e como sair — os textos do motor (Catalog), um parágrafo cada.
            conditions.add(new String[]{severityClass(c.severity()), c.label(), c.label() + severity
                    + (c.summary().isEmpty() ? "" : "\n" + c.summary())
                    + (c.removal().isEmpty() ? "" : "\nComo sair: " + c.removal())});
        }
        if (r.exhaustion() > 0) conditions.add(new String[]{"chip-warn", "Exaustão " + r.exhaustion(),
                "Exaustão nível " + r.exhaustion() + "\n" + Catalog.exhaustionSummary(r.exhaustion())});
        List<String[]> extras = new ArrayList<>();
        if (r.ultimateAvailable()) extras.add(new String[]{"chip-gold", "Ultimate pronta", "A técnica suprema pode ser usada."});
        if (d.markCharges() > 0) {
            int left = Math.max(0, d.markCharges() - r.chargesUsed());
            extras.add(new String[]{"chip-cyan", "Cargas " + left + "/" + d.markCharges(), "Cargas da Marca: " + left + " de " + d.markCharges() + " restantes."});
        }
        if (!r.initialized()) extras.add(new String[]{"chip-faint", "Recursos não iniciados", "O site ainda não registrou os recursos desta ficha."});
        // Os selos extras ficam presos à base; as condições usam o que sobra (e cortam com "+N").
        int bottom = extras.isEmpty() ? y + h - 4 : y + h - 17;
        chipRows(conditions, x + 7, cy, lw - 14, bottom - cy, "Nenhuma condição ativa.");
        if (!extras.isEmpty()) chipRows(extras, x + 7, y + h - 15, lw - 14, 11, "");

        // Combate: os blocos de número encolhem antes dos atributos (que são o alvo do clique).
        panel("<col class=\"panel\"></col>", rx, y, rw, h);
        panel("<col class=\"cell\"><label class=\"section\">Combate</label></col>", rx + 7, y + 5, rw - 14, 7);
        int inner = rw - 14;
        int tileH = Math.max(20, Math.min(26, (h - 41 - 2 * 24) / 2));
        int attrH = Math.max(22, Math.min(34, (h - 41 - 2 * tileH) / 2));
        boolean narrow = (inner - 9) / 4 < 46;
        String[][] stats = {
                {String.valueOf(d.defesa()), narrow ? "Def." : "Defesa", statTip("defesa", s, d)},
                {String.valueOf(d.rd()), "RD", statTip("rd", s, d)},
                {d.movimentoM() + "m", narrow ? "Mov." : "Movimento", statTip("movimento", s, d)},
                {Texts.signed(d.iniciativaBonus()), narrow ? "Inic." : "Iniciativa", statTip("iniciativa", s, d)}};
        String[][] more = {
                {String.valueOf(d.fortitude()), narrow ? "Fort." : "Fortitude", statTip("fortitude", s, d)},
                {Texts.signed(d.resonanceB()), narrow ? "Resson." : "Ressonância", statTip("ressonancia", s, d)},
                {String.valueOf(s.soul()), "Alma", statTip("alma", s, d)}};
        statRow(stats, rx + 7, y + 14, inner, tileH);
        statRow(more, rx + 7, y + 14 + tileH + 3, inner, tileH);

        // Atributos
        int ay = y + 14 + 2 * tileH + 3 + 5;
        text("section", "Atributos" + (isPeek() ? "" : " · clique para testar"), 5, rx + 7, ay, inner, 7);
        ay += 10;
        int cols = 3;
        int attrW = (inner - (cols - 1) * 3) / cols;
        List<FichaSheet.Attribute> attributes = s.attributes();
        for (int i = 0; i < attributes.size() && i < 6; i++) {
            FichaSheet.Attribute a = attributes.get(i);
            int ax = rx + 7 + (i % cols) * (attrW + 3);
            int ayy = ay + (i / cols) * (attrH + 3);
            String label = a.label().isEmpty() ? Catalog.attributeLabel(a.id()) : a.label();
            boolean changed = a.base() != a.effective();
            String tooltip = label + " " + a.effective()
                    + (changed ? "\nBase " + a.base() + ", efetivo " + a.effective() + " (Marca, condições ou efeitos)." : "")
                    + (isPeek() ? "\nSó leitura." : "\nClique para um teste: 1d20 " + Texts.signed(a.effective()) + ", com CD opcional.");
            // O bloco inteiro é o alvo do clique (col com onclick); os textos são desenhados por cima.
            if (isPeek()) {
                panel("<col class=\"attr-off\"></col>", ax, ayy, attrW, attrH);
            } else {
                panel("<col class=\"attr\" onclick=\"" + on("attr_" + a.id(), () -> openAttribute(a.id(), label, a.effective()))
                        + "\"></col>", ax, ayy, attrW, attrH);
                layer.hits.add(new int[]{ax, ayy, attrW, attrH});
            }
            tip(ax, ayy, attrW, attrH, tooltip);
            String abbr = Catalog.attributeAbbreviation(a.id());
            String value = String.valueOf(a.effective());
            String foot = changed ? "base " + a.base() : label;
            int fx = ax, fy = ayy, fw = attrW, fh = attrH;
            int valueColor = changed ? FichaUi.GOLD_HI : FichaUi.INK;
            if (fh >= 32) {
                // Alto: sigla, número grande e o nome embaixo, centrados.
                String footFit = FichaUi.fitScaled(font, foot, fw - 4, 0.62f);
                draw(g -> {
                    FichaUi.textCenter(g, font, abbr, fx + fw / 2f, fy + 3, 0xFFC59AFF, 0.72f, false);
                    FichaUi.textCenter(g, font, value, fx + fw / 2f, fy + fh / 2f - 6, valueColor, 1.8f, true);
                    FichaUi.textCenter(g, font, footFit, fx + fw / 2f, fy + fh - 7, FichaUi.FAINT, 0.62f, false);
                });
            } else {
                // Baixo (telas pequenas): sigla e nome à esquerda, número à direita.
                float valueW = font.width(value) * 1.5f;
                String footFit = FichaUi.fitScaled(font, foot, fw - 12 - valueW, 0.55f);
                draw(g -> {
                    FichaUi.text(g, font, abbr, fx + 5, fy + fh / 2f - 6, 0xFFC59AFF, 0.72f, false);
                    FichaUi.text(g, font, footFit, fx + 5, fy + fh / 2f + 1.5f, FichaUi.FAINT, 0.55f, false);
                    FichaUi.textRight(g, font, value, fx + fw - 5, fy + fh / 2f - 6, valueColor, 1.5f, true);
                });
            }
        }
    }

    private static int effective(FichaSheet s, String id) {
        FichaSheet.Attribute a = s.attribute(id);
        return a == null ? 0 : a.effective();
    }

    /** De onde vem cada número de combate (as fórmulas do motor, rules/build.go e ruleset.go). */
    private static String statTip(String id, FichaSheet s, FichaSheet.Derived d) {
        int des = effective(s, "des");
        return switch (id) {
            case "defesa" -> {
                int gear = d.defesa() - 10 - des;
                yield "Defesa " + d.defesa() + "\nA dificuldade para acertar você.\n10 + Destreza " + des
                        + (gear != 0 ? " + equipamento " + Texts.signed(gear) : "") + ".";
            }
            case "rd" -> "Redução de dano " + d.rd() + "\nSai de cada dano recebido. Vem do equipamento.";
            case "movimento" -> "Movimento " + d.movimentoM() + " m por turno"
                    + (d.movimentoM() == Math.min(13, 6 + des) ? "\n6 + Destreza " + des + ", até 13 m." : "");
            case "iniciativa" -> {
                FichaSheet.Skill intent = s.skill("intencao");
                int trained = intent == null ? 0 : intent.value();
                yield "Iniciativa " + Texts.signed(d.iniciativaBonus())
                        + (d.iniciativaBonus() == des + trained ? "\nDestreza " + des + " + treino em Intenção " + trained + "." : "");
            }
            case "fortitude" -> {
                int sab = effective(s, "sab"), vit = effective(s, "vit");
                yield "Fortitude " + d.fortitude()
                        + (d.fortitude() == (sab + vit + 1) / 2 ? "\nMetade de Sabedoria " + sab + " + Vitral " + vit + ", arredondada para cima." : "");
            }
            case "ressonancia" -> "Bônus de Ressonância " + Texts.signed(d.resonanceB()) + "\nVem da escala da Alma (" + s.soul() + ").";
            default -> "Alma " + s.soul() + "\nO grau de poder da ficha: define a escala dos números e o máximo de P.E.";
        };
    }

    /** Uma fileira de blocos de número (Defesa, RD…): moldura do Tessera, número e rótulo desenhados por cima. */
    private void statRow(String[][] stats, int x, int y, int w, int h) {
        int n = stats.length;
        int tileW = (w - (n - 1) * 3) / n;
        float vs = h >= 25 ? 1.3f : 1.1f;
        for (int i = 0; i < n; i++) {
            String[] st = stats[i];
            int tx = x + i * (tileW + 3);
            panel("<col class=\"stat\"></col>", tx, y, tileW, h);
            tip(tx, y, tileW, h, st[2]);
            float valueY = y + (h - (8 * vs + 7)) / 2f;
            String label = FichaUi.fitScaled(font, st[1].toUpperCase(Locale.ROOT), tileW - 4, 0.55f);
            draw(g -> {
                FichaUi.textCenter(g, font, st[0], tx + tileW / 2f, valueY, FichaUi.INK, vs, true);
                FichaUi.textCenter(g, font, label, tx + tileW / 2f, valueY + 8 * vs + 2.5f, FichaUi.FAINT, 0.55f, false);
            });
        }
    }

    private void noBuildBody(FichaSheet s, int x, int y, int w, int h) {
        // Dois casos bem diferentes para o jogador: já enviou e espera a equipe, ou ainda nem atualizou
        // a ficha para o sistema novo (aí o próximo passo é dele, no site).
        boolean waiting = s.pendingReview();
        boolean peek = isPeek();
        int bw = Math.max(buttonW("Atualizar no site", 6, true, false), buttonW("Rolagem livre", 6, true, false));
        boolean buttons = !peek && w - 20 - bw - 8 >= 120;
        int textW = w - 20 - (buttons ? bw + 8 : 0);
        panel("<col class=\"panel-warn\"></col>", x, y, w, 42);
        String title = peek ? (waiting ? "Construção enviada — aguardando aprovação da equipe"
                : "Esta ficha ainda não foi atualizada para o sistema novo")
                : waiting ? "Sua construção foi enviada — falta a equipe aprovar"
                : "Sua ficha ainda não foi atualizada no site";
        String body = peek ? "Sem atributos e perícias até a construção nova ser aprovada no site."
                : waiting ? "Nada a fazer por enquanto: atributos, perícias e testes aparecem aqui sozinhos quando aprovarem. "
                        + "A rolagem livre já funciona."
                : "Abra a ficha no site, monte a construção nova (atributos e perícias) e envie para a equipe aprovar. "
                        + "Depois disso os testes liberam aqui sozinhos; a rolagem livre já funciona.";
        text("line-gold", title, 7, x + 10, y + 5, textW, 10);
        lines("line-soft", body, 6, x + 10, y + 17, textW, 3, 8);
        if (buttons) {
            int bx = x + w - 10 - bw;
            if (!waiting) {
                button("site_construcao", siteUrl.isEmpty() ? "b-off" : "b-gold", "Atualizar no site", true, bx, y + 6, bw, 13,
                        siteUrl.isEmpty() ? null : this::openSite, siteUrl.isEmpty() ? "O servidor não mandou o endereço do site."
                                : "Abrir esta ficha no site para montar a construção nova (pede confirmação).");
            }
            button("livre_construcao", "b", "Rolagem livre", true, bx, y + (waiting ? 6 : 23), bw, 13, this::openFree,
                    "Rolar dados à mão (2d6+3, 1d20+5…). Não usa atributos nem perícias.");
        }
        loreColumns(s, x, y + 48, w, h - 48);
    }

    /**
     * Selos de condição em linhas, com o resumo no tooltip. O que não cabe em {@code h} vira um selo
     * "+N" no fim, com os nomes que faltaram no tooltip. Devolve o y depois da última linha.
     */
    private int chipRows(List<String[]> chips, int x, int y, int w, int h, String empty) {
        if (chips.isEmpty()) {
            if (!empty.isEmpty()) panel("<col class=\"cell\"><label class=\"hint\">" + FichaUi.esc(empty) + "</label></col>", x, y, w, 9);
            return y + 10;
        }
        int maxRows = Math.max(1, (h + 1) / 11);
        List<List<String[]>> rows = new ArrayList<>();
        List<String[]> row = new ArrayList<>();
        int used = 0;
        for (String[] chip : chips) {
            int cw = chipW(chip[1]) + 3;
            if (!row.isEmpty() && used + cw > w + 3) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            row.add(chip);
            used += cw;
        }
        rows.add(row);
        if (rows.size() > maxRows) {
            rows = new ArrayList<>(rows.subList(0, maxRows));
            List<String[]> last = new ArrayList<>(rows.get(maxRows - 1));
            rows.set(maxRows - 1, last);
            int shown = 0;
            for (List<String[]> r : rows) shown += r.size();
            int width = 0;
            for (String[] chip : last) width += chipW(chip[1]) + 3;
            while (last.size() > 1 && width + chipW("+" + (chips.size() - shown)) > w) {
                width -= chipW(last.remove(last.size() - 1)[1]) + 3;
                shown--;
            }
            List<String> hidden = new ArrayList<>();
            for (String[] chip : chips.subList(shown, chips.size())) hidden.add(chip[1]);
            last.add(new String[]{"chip-faint", "+" + hidden.size(), "Também: " + String.join(", ", hidden)});
        }
        int rowY = y;
        for (List<String[]> r : rows) {
            int cx = x;
            for (String[] chip : r) cx += chip(chip[0], chip[1], cx, rowY, chip[2]) + 3;
            rowY += 11;
        }
        return rowY + 1;
    }

    private static String severityClass(String severity) {
        return switch (severity) {
            case "leve" -> "chip-warn";
            case "moderada", "grave" -> "chip-bad";
            case "estado" -> "chip-cyan";
            default -> "chip";
        };
    }

    // ── Barras ──────────────────────────────────────────────────────────────────

    /** Recursos da ficha nova: na primeira vez as barras enchem a partir de zero; depois andam do valor visto. */
    private void updateBars(@Nullable FichaSheet before, boolean first, long now) {
        FichaSheet s = sheet;
        if (s == null || s.derived() == null) {
            barAt = 0;
            return;
        }
        FichaSheet.Resources r = s.resources();
        int[] next = {r.vida(), r.stamina(), r.sanidade(), r.pe()};
        boolean same = before != null && before.subjectId() == s.subjectId() && before.subjectType().equals(s.subjectType());
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            float shown = first || !same ? 0f : barShown(i, now);
            barFrom[i] = shown;
            moved |= Math.round(shown) != next[i];
            barTo[i] = next[i];
        }
        barAt = moved ? now : 0;
    }

    private float barShown(int i, long now) {
        if (barAt == 0) return barTo[i];
        float t = FichaUi.easeOut((now - barAt) / (float) BAR_ANIM_MS);
        if (t >= 1f) return barTo[i];
        return barFrom[i] + (barTo[i] - barFrom[i]) * t;
    }

    // ── Aba Perícias ────────────────────────────────────────────────────────────

    /** Uma linha da aba Perícias: cabeçalho de atributo ({@code continued}: repetido no topo da coluna) ou perícia. */
    private record SkillRow(@Nullable String attribute, @Nullable FichaSheet.Skill skill, boolean continued) {
    }

    /** As perícias que passam no filtro, por atributo; o nome do atributo no filtro traz o grupo inteiro. */
    private Map<String, List<FichaSheet.Skill>> filteredSkills(FichaSheet s) {
        Map<String, List<FichaSheet.Skill>> all = s.skillsByAttribute();
        if (filter.isBlank()) return all;
        String wanted = Catalog.normalize(filter);
        Map<String, List<FichaSheet.Skill>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<FichaSheet.Skill>> group : all.entrySet()) {
            FichaSheet.Attribute a = s.attribute(group.getKey());
            String attrName = a != null && !a.label().isEmpty() ? a.label() : Catalog.attributeLabel(group.getKey());
            if (Catalog.normalize(attrName).contains(wanted)) {
                out.put(group.getKey(), group.getValue());
                continue;
            }
            List<FichaSheet.Skill> hit = new ArrayList<>();
            for (FichaSheet.Skill skill : group.getValue()) if (Catalog.normalize(skill.label()).contains(wanted)) hit.add(skill);
            if (!hit.isEmpty()) out.put(group.getKey(), hit);
        }
        return out;
    }

    @Nullable
    private FichaSheet.Skill firstFiltered() {
        FichaSheet s = sheet;
        if (s == null) return null;
        for (List<FichaSheet.Skill> group : filteredSkills(s).values()) if (!group.isEmpty()) return group.get(0);
        return null;
    }

    private void skillsBody(int x, int y, int w, int h) {
        FichaSheet s = sheet;
        if (s == null) return;
        if (s.skills().isEmpty()) {
            panel("<col class=\"panel\"></col>", x, y, w, 30);
            text("line-soft", s.hasBuild() ? "Esta ficha não lista perícias."
                    : "As perícias aparecem quando a construção da ficha for aprovada no site.", 6, x + 10, y + 11, w - 20, 9);
            return;
        }
        Map<String, List<FichaSheet.Skill>> groups = filteredSkills(s);
        panel("<col class=\"panel\"></col>", x, y, w, h);
        if (groups.isEmpty()) {
            text("line-soft", "Nenhuma perícia com “" + filter.strip() + "”.", 6, x + 10, y + 10, w - 20, 9);
            text("hint", "Backspace apaga uma letra; Esc limpa o filtro.", 6, x + 10, y + 21, w - 20, 9);
            return;
        }
        int cols = Math.max(1, Math.min(3, (w + GAP) / (128 + GAP)));
        int colW = (w - (cols - 1) * GAP) / cols;
        // Treino: quantas bolinhas cada linha reserva (o maior treino da ficha, de 3 a 5), para as
        // bolinhas e o bônus ficarem em colunas alinhadas de cima a baixo.
        int slots = 3;
        for (FichaSheet.Skill skill : s.skills()) slots = Math.max(slots, Math.min(5, skill.value()));
        // Primeiro: grupos inteiros em colunas de altura parecida, na linha mais alta (até 14px) que caiba.
        int rowH = 11;
        List<List<List<SkillRow>>> pages = null;
        boolean balanced = false;
        for (int candidate = 14; candidate >= 10 && pages == null; candidate--) {
            List<List<SkillRow>> columns = balanceSkills(groups, cols, h - 8, candidate);
            if (columns != null) {
                rowH = candidate;
                pages = List.of(columns);
                balanced = true;
            }
        }
        // Não cabe nem com 10px: colunas em sequência, com o grupo continuando na próxima, paginado
        // (a última linha fica para o "‹ 1/2 ›").
        if (pages == null) pages = packSkills(groups, cols, Math.max(3, (h - 20) / rowH));
        pageCount = pages.size();
        page = Math.min(page, pages.size() - 1);

        List<List<SkillRow>> shown = pages.get(Math.max(0, page));
        for (int c = 0; c < shown.size(); c++) {
            int cx = x + 4 + c * (colW + GAP);
            int cw = colW - 8;
            List<SkillRow> rows = shown.get(c);
            int ry = y + 4;
            for (int i = 0; i < rows.size(); i++) {
                SkillRow row = rows.get(i);
                // Um respiro antes de cada grupo que não abre a coluna: o cabeçalho lê como título.
                if (balanced && i > 0 && row.attribute() != null) ry += SKILL_GROUP_GAP;
                if (row.attribute() != null) skillGroup(s, row.attribute(), cx, ry, cw, rowH - 1, row.continued());
                else if (row.skill() != null) skillLine(s, row.skill(), cx, ry, cw, rowH - 1, slots);
                ry += rowH;
            }
        }
        if (pages.size() > 1) pager(x + w - 4, y + h - 14, pages.size());
    }

    private static final int SKILL_GROUP_GAP = 4;

    /**
     * Os grupos (atributo + perícias) inteiros em até {@code cols} colunas de altura parecida: o maior
     * grupo vai para a coluna mais baixa (LPT). Dentro de cada coluna e entre as colunas volta a ordem
     * dos atributos (com as 6 de sempre sai Força/Sabedoria · Destreza/Intelecto · Vigor/Vitral).
     * {@code null} se a coluna mais alta passar de {@code room} px.
     */
    @Nullable
    private static List<List<SkillRow>> balanceSkills(Map<String, List<FichaSheet.Skill>> groups, int cols, int room, int rowH) {
        List<String> order = new ArrayList<>(groups.keySet());
        List<String> bySize = new ArrayList<>(order);
        bySize.sort((a, b) -> Integer.compare(groups.get(b).size(), groups.get(a).size())); // estável: empate fica na ordem
        int n = Math.max(1, Math.min(cols, order.size()));
        List<List<String>> columns = new ArrayList<>(n);
        int[] heights = new int[n];
        for (int c = 0; c < n; c++) columns.add(new ArrayList<>());
        for (String attribute : bySize) {
            int best = 0;
            for (int c = 1; c < n; c++) if (heights[c] < heights[best]) best = c;
            heights[best] += (columns.get(best).isEmpty() ? 0 : SKILL_GROUP_GAP) + (1 + groups.get(attribute).size()) * rowH;
            if (heights[best] > room) return null;
            columns.get(best).add(attribute);
        }
        for (List<String> column : columns) column.sort((a, b) -> Integer.compare(order.indexOf(a), order.indexOf(b)));
        columns.removeIf(List::isEmpty);
        columns.sort((a, b) -> Integer.compare(order.indexOf(a.get(0)), order.indexOf(b.get(0))));
        List<List<SkillRow>> out = new ArrayList<>(columns.size());
        for (List<String> column : columns) {
            List<SkillRow> rows = new ArrayList<>();
            for (String attribute : column) {
                rows.add(new SkillRow(attribute, null, false));
                for (FichaSheet.Skill skill : groups.get(attribute)) rows.add(new SkillRow(null, skill, false));
            }
            out.add(rows);
        }
        return out;
    }

    /**
     * Distribui os grupos (atributo + perícias) em colunas de {@code perCol} linhas e as colunas em
     * páginas de {@code cols}. Grupo que não cabe inteiro no resto da coluna começa na próxima; grupo
     * maior que uma coluna continua na seguinte com o cabeçalho repetido ("cont.").
     */
    private static List<List<List<SkillRow>>> packSkills(Map<String, List<FichaSheet.Skill>> groups, int cols, int perCol) {
        List<List<List<SkillRow>>> pages = new ArrayList<>();
        List<List<SkillRow>> columns = new ArrayList<>();
        List<SkillRow> column = new ArrayList<>();
        for (Map.Entry<String, List<FichaSheet.Skill>> group : groups.entrySet()) {
            int size = 1 + group.getValue().size();
            if (!column.isEmpty() && column.size() + Math.min(size, perCol) > perCol) {
                columns.add(column);
                column = new ArrayList<>();
                if (columns.size() == cols) {
                    pages.add(columns);
                    columns = new ArrayList<>();
                }
            }
            column.add(new SkillRow(group.getKey(), null, false));
            for (FichaSheet.Skill skill : group.getValue()) {
                if (column.size() >= perCol) {
                    columns.add(column);
                    column = new ArrayList<>();
                    if (columns.size() == cols) {
                        pages.add(columns);
                        columns = new ArrayList<>();
                    }
                    column.add(new SkillRow(group.getKey(), null, true));
                }
                column.add(new SkillRow(null, skill, false));
            }
        }
        if (!column.isEmpty()) columns.add(column);
        if (!columns.isEmpty()) pages.add(columns);
        return pages;
    }

    private void skillGroup(FichaSheet s, String attribute, int x, int y, int w, int h, boolean continued) {
        FichaSheet.Attribute a = s.attribute(attribute);
        String label = a != null && !a.label().isEmpty() ? a.label() : Catalog.attributeLabel(attribute);
        String name = label + (continued ? " (cont.)" : "");
        int value = a == null ? 0 : a.effective();
        String abbr = Catalog.attributeAbbreviation(attribute) + " " + value;
        int abbrW = Math.round(font.width(abbr) * 0.72f) + 6;
        String shown = FichaUi.fit(font, name.toUpperCase(Locale.ROOT), w - 8 - abbrW, 5);
        String html = "<label class=\"group-t\">" + FichaUi.esc(shown) + "</label>";
        if (isPeek() || a == null) {
            panel("<row class=\"group-off\">" + html + "</row>", x, y, w, h);
            tip(x, y, w, h, label + " " + value);
        } else {
            panel("<row class=\"group\" onclick=\"" + on("grupo_" + attribute + (continued ? "_c" : ""), () -> openAttribute(attribute, label, value))
                    + "\">" + html + "</row>", x, y, w, h);
            layer.hits.add(new int[]{x, y, w, h});
            tip(x, y, w, h, "Teste de " + label + ": 1d20 " + Texts.signed(value) + "\nClique para rolar o atributo.");
        }
        draw(g -> FichaUi.textRight(g, font, abbr, x + w - 4, y + (h - 6) / 2f, FichaUi.GOLD, 0.72f, false));
    }

    private void skillLine(FichaSheet s, FichaSheet.Skill skill, int x, int y, int w, int h, int slots) {
        FichaSheet.Attribute a = s.attribute(skill.attribute());
        String attr = a != null && !a.label().isEmpty() ? a.label() : Catalog.attributeLabel(skill.attribute());
        String bonus = Texts.signed(skill.bonus());
        StringBuilder tooltip = new StringBuilder(skill.label()).append(' ').append(bonus);
        if (a != null) {
            // bônus = atributo efetivo + treino (Build.TestBonus); o que sobrar vem de outra fonte (Marca, efeito).
            int other = skill.bonus() - a.effective() - skill.value();
            tooltip.append('\n').append(attr).append(' ').append(a.effective()).append(" + treino ").append(skill.value())
                    .append(other != 0 ? " " + Texts.signed(other) + " de outras fontes" : "").append(" = ").append(bonus);
        } else {
            tooltip.append("\nTreino ").append(skill.value()).append("; bônus total ").append(bonus).append('.');
        }
        if (!isPeek()) tooltip.append("\nClique para rolar 1d20 ").append(bonus).append(", com CD opcional.");
        // Colunas fixas à direita: o bônus (largura de "+10") e, antes dele, as bolinhas de treino.
        int bonusColW = Math.round(font.width("+10") * 0.86f);
        int pipsW = slots * 4;
        String label = FichaUi.fit(font, skill.label(), w - 8 - bonusColW - 6 - pipsW - 4, 6);
        // A linha inteira é clicável (row com onclick), com hover; bônus e treino são desenhados por cima.
        String text = "<label class=\"skill-t\">" + FichaUi.esc(label) + "</label>";
        if (isPeek()) panel("<row class=\"skill-off\">" + text + "</row>", x, y, w, h);
        else {
            panel("<row class=\"skill\" onclick=\"" + on("pericia_" + skill.id(), () -> openSkill(skill)) + "\">" + text + "</row>", x, y, w, h);
            layer.hits.add(new int[]{x, y, w, h});
        }
        tip(x, y, w, h, tooltip.toString());
        int pips = Math.max(0, Math.min(slots, skill.value()));
        float ty = y + (h - 6) / 2f;
        int bonusColor = skill.bonus() > 0 ? FichaUi.GOLD_HI : FichaUi.SOFT;
        int px = x + w - 4 - bonusColW - 6 - pipsW + 1;
        int py = y + (h - 2) / 2;
        draw(g -> {
            FichaUi.textRight(g, font, bonus, x + w - 4, ty, bonusColor, 0.86f, false);
            // Treino: as bolinhas cheias da esquerda para a direita; as que faltam, apagadas.
            for (int i = 0; i < slots; i++) g.fill(px + i * 4, py, px + i * 4 + 2, py + 2, i < pips ? 0xFFA78BFA : 0x38A78BFA);
        });
    }

    // ── Aba Técnicas ────────────────────────────────────────────────────────────

    private void abilitiesBody(int x, int y, int w, int h) {
        FichaSheet s = sheet;
        if (s == null) return;
        String first = "Técnicas · " + s.abilities().size();
        int w1 = buttonW(first, 6, false, false), w2 = buttonW("História", 6, false, false);
        button("ver_tecnicas", lore ? "b-mini" : "b-mini-on", first, false, x, y, w1, 12, lore ? () -> {
            lore = false;
            page = 0;
            rebuildMain();
        } : null, "As técnicas da ficha (as equipadas em dourado)");
        button("ver_lore", lore ? "b-mini-on" : "b-mini", "História", false, x + w1 + 3, y, w2, 12, lore ? null : () -> {
            lore = true;
            page = 0;
            rebuildMain();
        }, "Idade, origem, alinhamento, personalidade e aparência");
        int top = y + 15;
        int bodyH = h - 15;
        if (lore) {
            loreColumns(s, x, top, w, bodyH);
            return;
        }
        if (s.abilities().isEmpty()) {
            text("hint", "Nenhuma técnica nesta ficha.", 6, x + 4, top + 4, w - 8, 9);
            return;
        }
        int cols = w >= 300 ? 2 : 1;
        int colW = (w - (cols - 1) * GAP) / cols;
        // Cada cartão na altura do próprio texto (até 4 linhas; o resto vai com "…" e no tooltip),
        // empilhados na coluna mais baixa; o que não cabe vira outra página.
        List<FichaSheet.Ability> all = s.abilities();
        int[] heights = new int[all.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = abilityHeight(all.get(i), colW);
        int[][] placed = placeCards(heights, cols, bodyH);
        int pages = placed.length == 0 ? 1 : placed[placed.length - 1][0] + 1;
        if (pages > 1) {
            placed = placeCards(heights, cols, bodyH - 15); // a última faixa fica para o "‹ 1/2 ›"
            pages = placed[placed.length - 1][0] + 1;
        }
        pageCount = pages;
        page = Math.min(page, pages - 1);
        for (int i = 0; i < all.size(); i++) {
            if (placed[i][0] != page) continue;
            abilityCard(all.get(i), x + placed[i][1] * (colW + GAP), top + placed[i][2], colW, heights[i]);
        }
        if (pages > 1) pager(x + w - 4, y + h - 13, pages);
    }

    private static final int ABILITY_LINES = 4;

    /** Nome (10) + selos (9) + a descrição em linhas de 8px + margem: a altura que o cartão pede. */
    private int abilityHeight(FichaSheet.Ability a, int w) {
        String description = a.shortDescription().isEmpty() ? "Sem descrição." : a.shortDescription();
        int lines = Math.max(1, Math.min(ABILITY_LINES, FichaUi.wrap(font, description, w - 14, 6, ABILITY_LINES).size()));
        return 28 + lines * 8 + 4;
    }

    /**
     * Cartões de alturas diferentes em {@code cols} colunas: cada um na coluna mais baixa; quando nem
     * nela cabe em {@code room}, abre a página seguinte. Devolve, por cartão, {página, coluna, y}.
     */
    private static int[][] placeCards(int[] heights, int cols, int room) {
        int[][] out = new int[heights.length][];
        int[] colY = new int[cols];
        int pageAt = 0;
        for (int i = 0; i < heights.length; i++) {
            int best = 0;
            for (int c = 1; c < cols; c++) if (colY[c] < colY[best]) best = c;
            if (colY[best] > 0 && colY[best] + heights[i] > room) {
                pageAt++;
                java.util.Arrays.fill(colY, 0);
                best = 0;
            }
            out[i] = new int[]{pageAt, best, colY[best]};
            colY[best] += heights[i] + GAP;
        }
        return out;
    }

    private void abilityCard(FichaSheet.Ability a, int x, int y, int w, int h) {
        // Equipada em âmbar e dourado; a não equipada tem o mesmo cartão, mais apagado (nome claro, texto esmaecido).
        boolean on = a.equipped();
        panel("<col class=\"" + (on ? "ability-on" : "ability") + "\"></col>", x, y, w, h);
        // Raridade em tier (o modelo manda "★★★"): pontos dourados desenhados no canto, sem depender do
        // glifo da estrela na fonte. Raridade em texto (rótulo pronto) vira selo.
        String rarity = a.rarity();
        int stars = !rarity.isEmpty() && rarity.chars().allMatch(c -> c == '★') ? Math.min(5, rarity.length()) : 0;
        int starsW = stars > 0 ? stars * 6 + 2 : 0;
        String name = a.name().isEmpty() ? "Técnica" : a.name();
        text(on ? "ab-name" : "ab-name-off", name, 7, x + 7, y + 5, w - 14 - starsW, 10);
        if (stars > 0) {
            int sx = x + w - 7 - stars * 6 + 1, sy = y + 7;
            int starColor = on ? FichaUi.GOLD : 0xFF9C7A45;
            draw(g -> {
                for (int i = 0; i < stars; i++) diamond(g, sx + i * 6, sy, starColor);
            });
        }
        List<String[]> chips = new ArrayList<>();
        if (!a.element().isEmpty()) chips.add(new String[]{"el-" + Catalog.elementClass(a.element()), Catalog.elementLabel(a.element()),
                "Elemento: " + Catalog.elementLabel(a.element())});
        if (stars == 0 && !rarity.isEmpty()) chips.add(new String[]{"chip", rarity, "Raridade: " + rarity});
        if (a.abilityLevel() > 0) chips.add(new String[]{"chip-faint", "Nível " + a.abilityLevel(), "Nível da técnica"});
        if (a.equipped()) chips.add(new String[]{"chip-gold", "Equipada", "Equipada: pronta para usar"});
        chipLine(chips, x + 7, y + 16, w - 14);
        String description = a.shortDescription().isEmpty() ? "Sem descrição." : a.shortDescription();
        lines(on ? "ab-desc" : "ab-desc-off", description, 6, x + 7, y + 28, w - 14, ABILITY_LINES, 8);
        // O cartão inteiro explica: nome, raridade e a descrição completa (os selos e o nome têm o próprio tooltip por cima).
        tip(x, y, w, h, name + (stars > 0 ? " · raridade " + stars + " de 5" : "") + (a.equipped() ? " · equipada" : "")
                + "\n" + description);
    }

    /** Losango de 5×5 px (as "estrelas" da raridade). */
    private static void diamond(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 3, y + 5, color);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x, y + 2, x + 5, y + 3, color);
    }

    private void loreColumns(FichaSheet s, int x, int y, int w, int h) {
        FichaSheet.Lore l = s.lore();
        String[][] fields = {{"Idade", l.idade()}, {"Origem", l.origem()}, {"Alinhamento", l.alinhamento()},
                {"Personalidade", l.personalidade()}, {"Aparência", l.aparencia()}};
        if (l.isEmpty()) {
            text("hint", "A história deste personagem ainda não foi escrita no site.", 6, x + 4, y + 4, w - 8, 9);
            return;
        }
        int cols = w >= 300 ? 2 : 1;
        int colW = (w - (cols - 1) * GAP) / cols;
        int[] colY = new int[cols];
        for (int c = 0; c < cols; c++) colY[c] = y;
        for (String[] field : fields) {
            if (field[1].isEmpty()) continue;
            int c = cols == 1 || colY[0] <= colY[1] ? 0 : 1;
            int fx = x + c * (colW + GAP);
            int room = (y + h - colY[c] - 22) / 8;
            if (room < 1) continue;
            List<String> all = FichaUi.wrap(font, field[1], colW - 14, 6, Math.min(6, room) + 1);
            int shown = Math.min(all.size(), Math.min(6, room));
            int ph = 16 + shown * 8;
            panel("<col class=\"panel\"></col>", fx, colY[c], colW, ph);
            panel("<col class=\"cell\"><label class=\"section\">" + FichaUi.esc(field[0]) + "</label></col>", fx + 7, colY[c] + 4, colW - 14, 7);
            lines("line", field[1], 6, fx + 7, colY[c] + 12, colW - 14, shown, 8);
            // Cortado: o painel inteiro mostra o texto completo no tooltip.
            if (all.size() > shown) tip(fx, colY[c], colW, ph, field[0] + "\n" + field[1]);
            colY[c] += ph + GAP;
        }
    }

    // ── Aba Habilidades ─────────────────────────────────────────────────────────

    /** Uma faixa da aba Habilidades: o título de um grupo ({@code itens} nulo) ou uma fileira de cartões dele. */
    private record HabRow(Habilidades.Grupo grupo, @Nullable List<Habilidades.Item> itens, boolean continued) {
    }

    private static final int HAB_CARD_H = 26;
    private static final int HAB_HEAD_H = 13;
    private static final int HAB_GROUP_GAP = 4;

    /**
     * O que o personagem já tem dentro do jogo, por grupo (magias, profissão, Marca…): título na cor do
     * grupo e cartões com o ícone do item, nome, selo de estado e nível/detalhe ou a barra de progresso.
     * Não depende do site (aparece até sem ficha); vazio vira uma explicação.
     */
    private void habilidadesBody(int x, int y, int w, int h) {
        Habilidades hab = habilidades;
        if (hab.isEmpty()) {
            habilidadesEmpty(x, y, w, h);
            return;
        }
        int cols = w >= 480 ? 3 : w >= 240 ? 2 : 1;
        int colW = (w - (cols - 1) * GAP) / cols;
        List<HabRow> rows = new ArrayList<>();
        for (Habilidades.Grupo g : hab.grupos()) {
            if (g.itens().isEmpty()) continue;
            rows.add(new HabRow(g, null, false));
            for (int i = 0; i < g.itens().size(); i += cols) {
                rows.add(new HabRow(g, g.itens().subList(i, Math.min(g.itens().size(), i + cols)), false));
            }
        }
        List<List<HabRow>> pages = packHab(rows, h);
        if (pages.size() > 1) pages = packHab(rows, h - 15); // a última faixa fica para o "‹ 1/2 ›"
        pageCount = pages.size();
        page = Math.min(page, pages.size() - 1);
        int ry = y;
        for (HabRow row : pages.get(Math.max(0, page))) {
            if (row.itens() == null) {
                if (ry > y) ry += HAB_GROUP_GAP;
                habHeader(row.grupo(), x, ry, w, row.continued());
                ry += HAB_HEAD_H;
            } else {
                for (int i = 0; i < row.itens().size(); i++) habCard(row.grupo(), row.itens().get(i), x + i * (colW + GAP), ry, colW, HAB_CARD_H);
                ry += HAB_CARD_H + 3;
            }
        }
        if (pages.size() > 1) pager(x + w - 4, y + h - 13, pages.size());
    }

    /**
     * Faixas em páginas de {@code room} px. Um título nunca fica sozinho no pé da página (leva junto a
     * primeira fileira); fileira que passa para a página seguinte ganha o título de novo, "(cont.)".
     */
    private static List<List<HabRow>> packHab(List<HabRow> rows, int room) {
        List<List<HabRow>> pages = new ArrayList<>();
        List<HabRow> current = new ArrayList<>();
        int used = 0;
        for (HabRow row : rows) {
            boolean header = row.itens() == null;
            int need = header ? (current.isEmpty() ? 0 : HAB_GROUP_GAP) + HAB_HEAD_H + HAB_CARD_H + 3 : HAB_CARD_H + 3;
            if (!current.isEmpty() && used + need > room) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
                if (!header) {
                    current.add(new HabRow(row.grupo(), null, true));
                    used = HAB_HEAD_H;
                }
            }
            used += header ? (current.isEmpty() ? 0 : HAB_GROUP_GAP) + HAB_HEAD_H : HAB_CARD_H + 3;
            current.add(row);
        }
        if (!current.isEmpty() || pages.isEmpty()) pages.add(current);
        return pages;
    }

    /** O título do grupo na cor dele, com quantas já estão liberadas e o fio colorido embaixo. */
    private void habHeader(Habilidades.Grupo grupo, int x, int y, int w, boolean continued) {
        int color = FichaUi.hexColor(grupo.cor(), FichaUi.GOLD);
        int line = FichaUi.alpha(color, 0.28f);
        String title = (grupo.titulo().isEmpty() ? "Habilidades" : grupo.titulo()) + (continued ? " (cont.)" : "");
        int open = 0;
        for (Habilidades.Item item : grupo.itens()) if (!"bloqueada".equals(item.estado())) open++;
        String count = open + " de " + grupo.itens().size();
        int countW = Math.round(font.width(count) * 0.62f);
        String titleFit = FichaUi.fitScaled(font, title.toUpperCase(Locale.ROOT), w - 12 - countW - 6, 0.72f);
        draw(g -> {
            diamond(g, x, y + 1, color);
            FichaUi.text(g, font, titleFit, x + 8, y + 1.5f, color, 0.72f, false);
            FichaUi.textRight(g, font, count, x + w, y + 2, FichaUi.FAINT, 0.62f, false);
            g.fill(x, y + 9, x + w, y + 10, line);
        });
        tip(x, y, w, 10, title + "\n" + open + " de " + grupo.itens().size() + " liberadas"
                + (open < grupo.itens().size() ? "; as bloqueadas dizem o que falta." : "."));
    }

    /** Um cartão de habilidade: ícone, nome, selo de estado e a segunda linha (barra ou nível/detalhe). */
    private void habCard(Habilidades.Grupo grupo, Habilidades.Item item, int x, int y, int w, int h) {
        String estado = item.estado();
        boolean locked = "bloqueada".equals(estado);
        String[] state = habState(estado);
        String name = item.nome().isEmpty() ? "Habilidade" : item.nome();
        int pct = item.progresso();
        panel("<col class=\"" + ("ativa".equals(estado) ? "hab-on" : locked ? "hab-off" : "hab") + "\"></col>", x, y, w, h);
        // O cartão inteiro explica (registrado antes: o selo e o nome cortado ficam com o tooltip deles por cima).
        tip(x, y, w, h, name + (item.nivel().isEmpty() ? "" : " · " + item.nivel())
                + "\n" + state[2] + (pct >= 0 ? " (" + pct + "%)" : "")
                + (item.detalhe().isEmpty() ? "" : "\n" + item.detalhe()));

        // Ícone do item num nicho escuro; sem ícone (ou item de mod ausente), a inicial na cor do grupo.
        int color = FichaUi.hexColor(grupo.cor(), FichaUi.GOLD);
        panel("<col class=\"slot\"></col>", x + 4, y + 4, 18, 18);
        ItemStack icon = FichaUi.icon(item.icone());
        if (!icon.isEmpty()) {
            draw(g -> {
                g.renderItem(icon, x + 5, y + 5);
                if (locked) {
                    // O item desenha a z 150+: o véu da bloqueada vai acima dele.
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(x + 5, y + 5, x + 21, y + 21, 0xA00C0618);
                    g.pose().popPose();
                }
            });
        } else {
            String letter = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            int letterColor = locked ? FichaUi.FAINT : color;
            draw(g -> FichaUi.textCenter(g, font, letter, x + 13, y + 9, letterColor, 1f, false));
        }

        int chipW = chipW(state[1]);
        int tx = x + 27;
        text(locked ? "hab-name-off" : "hab-name", name, 7, tx, y + 4, x + w - 5 - chipW - 4 - tx, 10);
        chip(state[0], state[1], x + w - 5 - chipW, y + 4, state[2]);

        int lineW = x + w - 6 - tx;
        if (pct >= 0) {
            // Progresso: a barra na cor do grupo e a porcentagem à direita.
            String pctText = pct + "%";
            int pctW = Math.round(font.width("100%") * 0.62f);
            int barW = Math.max(10, lineW - pctW - 4);
            int hi = lighten(color);
            draw(g -> {
                FichaUi.bar(g, tx, y + 16, barW, 5, pct, 100, 0, color, hi);
                FichaUi.textRight(g, font, pctText, tx + lineW, y + 16, FichaUi.SOFT, 0.62f, false);
            });
        } else {
            List<String> parts = new ArrayList<>(2);
            if (!item.nivel().isEmpty()) parts.add(item.nivel());
            if (!item.detalhe().isEmpty()) parts.add(item.detalhe());
            String second = parts.isEmpty() ? state[2] : String.join(" · ", parts);
            text(locked ? "line-warn" : "line-faint", second, 6, tx, y + 15, lineW, 9);
        }
    }

    /** O selo de cada estado: classe, texto e a explicação do tooltip (cores distintas: verde, dourado, ciano, cinza). */
    private static String[] habState(String estado) {
        return switch (estado) {
            case "ativa" -> new String[]{"chip-ok", "Ativa", "Ativa: em uso agora."};
            case "progresso" -> new String[]{"chip-cyan", "Em progresso", "Em progresso: ainda liberando."};
            case "bloqueada" -> new String[]{"chip-faint", "Bloqueada", "Bloqueada: ainda não liberou."};
            case "liberada" -> new String[]{"chip-gold", "Liberada", "Liberada: disponível para usar."};
            default -> new String[]{"chip", estado, estado};
        };
    }

    /** A cor misturada com branco (o brilho da borda de cima da barra). */
    private static int lighten(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        return 0xFF000000 | (r + (255 - r) * 2 / 5) << 16 | (g + (255 - g) * 2 / 5) << 8 | (b + (255 - b) * 2 / 5);
    }

    /** Nada liberado: o porquê, sem parecer erro. */
    private void habilidadesEmpty(int x, int y, int w, int h) {
        int pw = Math.min(w, 300);
        int px = x + (w - pw) / 2;
        String body = isPeek() ? (peekName.isEmpty() ? "O jogador" : peekName)
                + " ainda não liberou nada no servidor. Magias, profissão e Marca aparecem aqui conforme ele avança."
                : "Magias, profissão e Marca aparecem aqui conforme você avança no servidor.";
        int textW = pw - 50;
        int bodyH = Math.max(1, FichaUi.wrap(font, body, textW, 6, 3).size()) * 9;
        int ph = Math.max(36, 21 + bodyH + 6);
        int py = y + Math.max(0, (h - ph) / 2 - 10);
        panel("<col class=\"panel\"></col>", px, py, pw, ph);
        int sy = py + (ph - 22) / 2;
        panel("<col class=\"slot\"></col>", px + 10, sy, 22, 22);
        ItemStack book = FichaUi.icon("minecraft:book");
        if (!book.isEmpty()) draw(g -> g.renderItem(book, px + 13, sy + 3));
        text("line-gold", "Nada liberado ainda", 7, px + 40, py + 8, textW, 10);
        lines("line-soft", body, 6, px + 40, py + 20, textW, 3, 9);
    }

    // ── Aba Vínculo ─────────────────────────────────────────────────────────────

    private void bindingBody(int x, int y, int w, int h) {
        boolean alt = (flags & FichaPayloads.ALT) != 0;
        boolean explicit = binding != null && binding.explicit();
        boolean busy = !bindingKey.isEmpty();
        int headH = 40;
        boolean stale = binding != null && binding.stale();
        panel("<col class=\"" + (stale ? "panel-bad" : "panel") + "\"></col>", x, y, w, headH);
        boolean armed = clearArmedAt > 0;
        String clearLabel = armed ? "Confirmar?" : "Padrão";
        int refreshW = buttonW("Atualizar", 6, true, false);
        int clearW = explicit ? Math.max(buttonW("Padrão", 6, true, false), buttonW("Confirmar?", 6, true, false)) : 0;
        int buttonsW = explicit ? refreshW + 3 + clearW : refreshW;
        int textW = w - 14 - buttonsW - 8;
        text("kicker-gold", "Este perfil" + (profileName.isEmpty() ? "" : " · " + profileName)
                + (alt ? " · personagem alternativo" : " · conta principal"), 5, x + 7, y + 5, textW, 7);
        String using;
        if (sheet != null) {
            using = "Usa: " + (sheet.name().isEmpty() ? "ficha sem nome" : sheet.name())
                    + (explicit ? " (escolhida para este perfil)" : " (padrão: sua ficha ativa)");
        } else {
            using = switch (code) {
                case "needs_binding" -> "Ainda não usa nenhuma ficha: escolha uma abaixo.";
                case "no_sheet" -> "Você ainda não tem ficha ativa no site.";
                case "not_linked" -> "A conta do site não está vinculada.";
                default -> "Sem ficha carregada.";
            };
        }
        text("line-gold", using, 7, x + 7, y + 14, textW, 10);
        String hint;
        if (stale) hint = "O vínculo escolhido não vale mais (a ficha ou o NPC saiu do seu controle). Escolha outra.";
        else if (sheet != null && !sheet.hasBuild()) hint = sheet.pendingReview()
                ? "Esta ficha está aguardando aprovação no site: os testes liberam depois."
                : "Esta ficha ainda não foi atualizada para o sistema novo no site: os testes liberam depois da aprovação.";
        else if (alt) hint = "Alts usam a ficha ou o NPC que você escolher aqui; a conta principal usa sua ficha ativa.";
        else hint = "A conta principal usa sua ficha ativa; escolha outra só se este perfil interpretar um NPC.";
        text(stale ? "line-bad" : sheet != null && !sheet.hasBuild() ? "line-warn" : "line-faint", hint, 6, x + 7, y + 27, textW, 9);
        int bx = x + w - 7 - buttonsW;
        button("atualizar", candidatesLoading ? "b-off" : "b", "Atualizar", true, bx, y + 13, refreshW, 14,
                candidatesLoading ? null : this::refreshCandidates,
                candidatesLoading ? "Buscando a lista no site…" : "Buscar de novo no site o que este perfil pode usar");
        if (explicit) {
            String clearTip = busy ? "Espere o vínculo em andamento."
                    : armed ? "Clique de novo para confirmar (Esc cancela)."
                    : alt ? "Remove a escolha: este perfil fica sem ficha até você escolher outra." : "Remove a escolha: volta a usar sua ficha ativa.";
            String clearClass = busy ? "b-off" : armed ? "b-confirm" : "b-danger";
            if (!busy && armed && confirmAnimated != clearArmedAt) {
                confirmAnimated = clearArmedAt;
                clearClass = "b-confirm confirm-in";
            }
            button("limpar", clearClass, clearLabel, true, bx + refreshW + 3, y + 13, clearW, 14,
                    busy ? null : this::clearBinding, clearTip);
        }

        int listY = y + headH + GAP;
        int listH = h - headH - GAP;
        if (candidatesLoading && candidates.isEmpty()) {
            text("status-espera", "Carregando as fichas que este perfil pode usar…", 6, x + 16, listY + 6, w - 20, 9);
            draw(g -> FichaUi.dots(g, x + 9, listY + 9, Util.getMillis(), FichaUi.WARN));
            return;
        }
        if (candidates.isEmpty()) {
            if (!candidatesLoaded) {
                text("hint", "Clique em Atualizar para ver as fichas.", 6, x + 4, listY + 6, w - 8, 9);
                return;
            }
            // Nada para escolher: o caminho é o site.
            panel("<col class=\"panel\"></col>", x, listY, w, 34);
            text("line-soft", "Nenhuma ficha disponível para este perfil.", 6, x + 8, listY + 5, w - 16, 9);
            text("hint", "Crie ou ative uma ficha no site (Perfil → RPG) e clique em Atualizar.", 6, x + 8, listY + 15, w - 16 - 70, 9);
            if (!siteUrl.isEmpty()) {
                int bw = buttonW("Abrir o site", 6, true, false);
                button("site_vazio", "b", "Abrir o site", true, x + w - 8 - bw, listY + 11, bw, 13, this::openSite, "Abrir o site (pede confirmação)");
            }
            return;
        }
        int cols = w >= 320 ? 2 : 1;
        int cardH = 32;
        int colW = (w - (cols - 1) * GAP) / cols;
        int rows = Math.max(1, (listH - 14 + GAP) / (cardH + GAP));
        int perPage = rows * cols;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        pageCount = pages;
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= candidates.size()) break;
            Candidate c = candidates.get(index);
            candidateCard(c, x + (i % cols) * (colW + GAP), listY + (i / cols) * (cardH + GAP), colW, cardH);
        }
        boolean anyFree = candidates.stream().anyMatch(Candidate::staffOnly);
        int pagerW = pages > 1 ? 60 : 0;
        if (pages > 1) pager(x + w - 4, y + h - 13, pages);
        if (anyFree) text("hint", "NPC livre (staff): usar liga o NPC à sua conta no site.", 6, x + 4, y + h - 10, w - 8 - pagerW, 9);
    }

    private void candidateCard(Candidate c, int x, int y, int w, int h) {
        boolean current = sheet != null && c.subjectType().equals(sheet.subjectType()) && c.subjectId() == sheet.subjectId()
                && (binding == null || !binding.stale());
        String key = c.subjectType() + "_" + c.subjectId();
        boolean mine = key.equals(bindingKey);
        panel("<col class=\"" + (current ? "cand-on" : "cand") + "\"></col>", x, y, w, h);
        String initial = c.name().isEmpty() ? "?" : c.name().substring(0, c.name().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        panel("<col class=\"" + (c.npc() ? "monogram-npc" : "monogram") + "\"><label class=\"mono-t\">" + FichaUi.esc(initial)
                + "</label></col>", x + 5, y + 5, 22, 22);
        int useW = 46;
        int textW = w - 32 - useW - 6;
        text("line-gold", c.name().isEmpty() ? "Sem nome" : c.name(), 7, x + 32, y + 4, textW, 10);
        String kind = c.staffOnly() ? "NPC livre" : c.npc() ? "NPC" : "Ficha";
        String kindClass = c.staffOnly() ? "chip-warn" : c.npc() ? "chip-gold" : "chip";
        int chipW = chip(kindClass, kind, x + 32, y + 14, c.staffOnly() ? "NPC sem dono: usar o liga à sua conta no site (só staff)."
                : c.npc() ? "Um NPC ligado à sua conta." : "Uma ficha sua.");
        if (!c.title().isEmpty()) text("line-faint", c.title(), 6, x + 32 + chipW + 3, y + 14, Math.max(10, textW - chipW - 3), 9);
        if (!c.boundProfiles().isEmpty()) {
            text("hint", "também em: " + String.join(", ", c.boundProfiles()), 6, x + 32, y + 23, textW, 8);
        }
        int ux = x + w - useW - 6;
        if (current) {
            panel("<row class=\"use-on\"><label>EM USO</label></row>", ux, y + 10, useW, 12);
            tip(ux, y + 10, useW, 12, "Este perfil usa esta ficha agora.");
        } else if (mine) {
            button("usar_" + key, "b-off", "…", false, ux, y + 10, useW, 12, null, "Vinculando…");
            draw(g -> FichaUi.dots(g, ux + useW / 2 + 1, y + 15, Util.getMillis(), FichaUi.GOLD_HI));
        } else {
            boolean busy = !bindingKey.isEmpty();
            String tooltip = busy ? "Espere o vínculo em andamento."
                    : c.staffOnly() ? "Liga este NPC livre à sua conta no site e o usa neste perfil" : "Usar esta ficha neste perfil";
            button("usar_" + key, busy ? "b-off" : "b-gold", "Usar", true, ux, y + 10, useW, 12, busy ? null : () -> useCandidate(c), tooltip);
        }
    }

    // ── Rodapé e paginação ──────────────────────────────────────────────────────

    private void footer(int x, int y, int w) {
        boolean dice = !isPeek() && sheet != null;
        String closeLabel = parent != null ? "Voltar" : "Fechar";
        int closeW = buttonW(closeLabel, 6, true, false);
        int diceW = dice ? buttonW("Dados", 6, true, false) : 0;
        int buttonsW = dice ? diceW + 3 + closeW : closeW;
        // Pedido do mestre pendente: Rolar e Recusar a um clique daqui, sem fechar a ficha. As teclas de
        // pedido não têm padrão; se o jogador as atribuiu, o tooltip diz quais.
        FichaHud.Ask ask = askId.isEmpty() ? null : FichaHud.currentAsk();
        if (ask != null && ask.id().equals(askId)) {
            // Rolado daqui ou já pela roda/HUD antes de abrir a ficha: o mesmo "Rolando…".
            boolean answering = ask.id().equals(askAnswering) || FichaClient.isAnswering(ask.id());
            String rollKey = FichaUi.keyName(FichaKeys.ROLL_REQUEST), declineKey = FichaUi.keyName(FichaKeys.DECLINE_REQUEST);
            String about = (ask.title().isEmpty() ? "O mestre pede" : ask.title()) + ": " + lowerFirst(Texts.askHeadline(ask.test()))
                    + (ask.gmName().isEmpty() ? "" : " (" + ask.gmName() + ")") + (ask.detail().isEmpty() ? "" : "\n" + ask.detail());
            int declineW = answering ? 0 : buttonW("Recusar", 6, true, false);
            String label = answering ? "Rolando o pedido…" : "Rolar " + ask.test();
            int room = Math.max(60, (w - buttonsW) / 2 - (declineW > 0 ? declineW + 3 : 0));
            int askW = Math.min(buttonW(label, 6, false, true), room);
            String shown = FichaUi.fit(font, label, askW - 14, 6);
            int ax = x + w - buttonsW - 4 - askW - (declineW > 0 ? declineW + 3 : 0);
            button("pedido", answering ? "b-off" : "b-gold", shown, false, ax, y + 1, askW, FOOT_H - 2,
                    answering ? null : () -> answerAsk(true),
                    answering ? "Rolando o pedido do mestre: o resultado aparece aqui e no HUD." : about
                            + "\nRola agora" + (rollKey.isEmpty() ? "." : " (atalho: " + rollKey + ")."));
            if (declineW > 0) {
                button("recusar_pedido", "b", "Recusar", true, ax + askW + 3, y + 1, declineW, FOOT_H - 2, () -> answerAsk(false),
                        "Recusar o pedido" + (ask.gmName().isEmpty() ? "" : " de " + ask.gmName())
                                + ": a mesa do mestre fica sabendo" + (declineKey.isEmpty() ? "." : " (atalho: " + declineKey + ")."));
            }
            buttonsW += askW + 4 + (declineW > 0 ? declineW + 3 : 0);
        }
        String text = statusText.isEmpty() ? hint() : statusText;
        String cls = statusText.isEmpty() ? "status-info" : "status-" + statusKind;
        text(cls, text, 6, x + 2, y + 4, w - buttonsW - 8, 9);
        int bx = x + w - (dice ? diceW + 3 + closeW : closeW);
        if (dice) {
            button("dados", "b", "Dados", true, bx, y + 1, diceW, FOOT_H - 2, this::openFree,
                    "Rolagem livre pelo motor do site: 2d6+3, 1d20+5, 4d6kh3…");
            bx += diceW + 3;
        }
        button("fechar_rodape", "b", closeLabel, true, bx, y + 1, closeW, FOOT_H - 2, this::onClose,
                parent != null ? "Voltar à mesa do mestre (Esc)" : "Fechar a ficha (Esc)");
    }

    /** A dica do rodapé quando não há mensagem: o que dá para fazer na aba aberta, com os atalhos. */
    private String hint() {
        if (tab == Tab.HABILIDADES) return habilidades.isEmpty() ? "Vem do jogo, não do site: atualiza conforme você avança."
                : "Passe o mouse num cartão para ler o detalhe.";
        if (isPeek()) return "Ficha vista pelo mestre: só leitura. 1–4 trocam a aba.";
        if (sheet == null && tab != Tab.VINCULO) return "Veja a aba Vínculo ou abra a ficha no site.";
        return switch (tab) {
            case FICHA -> sheet != null && sheet.hasBuild() ? "Clique num atributo para testar · 1–5 trocam a aba"
                    : "Os testes liberam quando a construção for aprovada.";
            case PERICIAS -> !filter.isEmpty() ? "Enter rola a primeira · Backspace apaga · Esc limpa o filtro"
                    : pageCount > 1 ? "Digite para filtrar · roda ou ← → para as outras · clique para rolar"
                    : "Digite para filtrar · clique numa perícia para rolar, com CD opcional";
            case TECNICAS -> lore ? "Passe o mouse num campo cortado para ler o texto inteiro."
                    : "Passe o mouse sobre a técnica para ler a descrição inteira.";
            case VINCULO -> "Qual ficha este perfil usa no jogo. Também se escolhe no site, em Perfil → RPG.";
            case HABILIDADES -> ""; // respondida acima (vale até sem ficha)
        };
    }

    /** "‹ 1/3 ›" alinhado pela direita em {@code right}. */
    private void pager(int right, int y, int pages) {
        String label = (page + 1) + "/" + pages;
        int arrowW = 13;
        int labelW = FichaUi.width(font, label, 6) + 6;
        int x = right - (arrowW * 2 + labelW);
        boolean prev = page > 0, next = page < pages - 1;
        button("anterior", prev ? "b-page" : "b-off", "<", false, x, y, arrowW, 12, prev ? () -> turnPage(-1) : null,
                prev ? "Página anterior (← ou roda do mouse)" : "");
        int lx = x + arrowW + labelW / 2;
        draw(g -> FichaUi.textCenter(g, font, label, lx, y + 3, FichaUi.FAINT, 6 / 7f, false));
        button("proxima", next ? "b-page" : "b-off", ">", false, x + arrowW + labelW, y, arrowW, 12, next ? () -> turnPage(1) : null,
                next ? "Próxima página (→ ou roda do mouse)" : "");
    }

    // ── Diálogo de rolagem ──────────────────────────────────────────────────────

    private void dialogPanels() {
        Dialog d = dialog;
        if (d == null) return;
        boolean free = "free".equals(d.kind());
        dw = Math.min(260, width - 24);
        dh = 170;
        dx = (width - dw) / 2;
        dy = (height - dh) / 2;
        panel("<col class=\"" + (dialogFresh ? "modal modal-in" : "modal") + "\"></col>", dx, dy, dw, dh);
        dialogFresh = false;
        int ix = dx + 10, iw = dw - 20;

        // Cabeçalho: o tipo de teste, o nome grande e a conta; um fio dourado separa dos controles.
        text("kicker-gold", d.kicker(), 5, ix, dy + 8, iw - 18, 7);
        button("fechar_x", "b-icon", "×", false, dx + dw - 18, dy + 4, 14, 12, this::closeDialog, "Fechar (Esc)");
        text("name", d.title(), 10, ix, dy + 17, iw, 13);
        text("subtitle", d.subtitle(), 6, ix, dy + 31, iw, 8);
        draw(g -> g.fill(ix, dy + 42, ix + iw, dy + 43, 0x30D7A857));

        int fy = dy + 47;
        int cy = fy + 9;
        // Secreto: a moldura é um <row onclick>; caixinha e rótulo são do Java (alinhados em qualquer escala).
        int secretTextW = Math.round(font.width("Secreto") * 6 / 7f);
        int secretW = 6 + 6 + 4 + secretTextW + 7;
        int sx = ix + iw - secretW;
        panel("<row class=\"" + (secret ? "b-toggle-on" : "b-toggle") + "\" onclick=\"" + on("secreto", this::toggleSecret) + "\"></row>",
                sx, cy, secretW, 14);
        layer.hits.add(new int[]{sx, cy, secretW, 14});
        tip(sx, cy, secretW, 14, "Rolagem secreta: só você e os mestres online veem o resultado.\nAtalho: Tab" + (free ? "" : " ou S"));
        boolean on = secret;
        draw(g -> {
            int bx = sx + 6, by = cy + 4;
            g.fill(bx, by, bx + 6, by + 6, on ? 0xFFC59AFF : 0xFF6D5A92);
            g.fill(bx + 1, by + 1, bx + 5, by + 5, on ? 0xFFFFE9A7 : 0xFF120822);
            FichaUi.text(g, font, "Secreto", bx + 10, cy + 4, on ? FichaUi.GOLD_HI : FichaUi.SOFT, 6 / 7f, false);
        });

        if (free) {
            panel("<col class=\"cell\"><label class=\"field-label\">Fórmula</label></col>", ix, fy, 60, 7);
            int fw = sx - 6 - ix;
            panel("<row class=\"cell\"><input id=\"formula\" class=\"input\" placeholder=\"2d6+3\" maxlength=\"40\" value=\""
                    + FichaUi.esc(field("formula")) + "\"/></row>", ix, cy, fw, 14);
            tip(ix, cy, fw, 14, "Qualquer fórmula que o motor do site aceite\n2d6+3, 1d20+5, 4d6kh3 (os 3 maiores de 4d6)…");
        } else {
            int dc = dcValue();
            panel("<col class=\"cell\"><label class=\"field-label\">CD (opcional)</label></col>", ix, fy, 70, 7);
            // Ao lado do rótulo, o que a CD escolhida quer dizer (vazia: só o total, sem sucesso/falha).
            boolean valid = dc >= 1 && dc <= 99;
            String meaning = dcText.isEmpty() ? "sem CD · mostra só o total"
                    : valid ? "sucesso com " + dc + " ou mais" + presetName(dc) : "a CD vai de 1 a 99";
            int meaningColor = dcText.isEmpty() ? FichaUi.FAINT : valid ? FichaUi.GOLD : FichaUi.BAD;
            int mx = ix + FichaUi.width(font, "CD (OPCIONAL)", 5) + 6;
            String meaningFit = FichaUi.fitScaled(font, meaning, sx - 6 - mx, 5 / 7f);
            draw(g -> FichaUi.text(g, font, meaningFit, mx, fy + 1, meaningColor, 5 / 7f, false));

            // [−] [ valor ] [+]: vazia mostra um travessão apagado, nunca um ponto solto.
            button("cd_menos", dc > 0 ? "b-mini" : "b-off", "-", false, ix, cy, 12, 14, dc > 0 ? () -> stepDc(-1) : null,
                    dc > 0 ? "Baixar a CD (↓ ou roda; Shift: de 5 em 5)" : "Sem CD para baixar.");
            panel("<col class=\"" + (dcText.isEmpty() ? "dc" : "dc-on") + "\"></col>", ix + 14, cy, 24, 14);
            tip(ix + 14, cy, 24, 14, "CD — classe de dificuldade\nO total precisa igualar ou passar dela para dar sucesso. "
                    + "Vazia: rola sem sucesso/falha.\nDigite o número, ou ↑/↓ e a roda do mouse; Delete limpa.");
            String shown = dcText.isEmpty() ? "—" : dcText;
            int color = dcText.isEmpty() ? FichaUi.FAINT : FichaUi.GOLD_HI;
            int vx = ix + 26, vy = cy + 3;
            draw(g -> FichaUi.textCenter(g, font, shown, vx, vy, color, 1f, false));
            button("cd_mais", "b-mini", "+", false, ix + 40, cy, 12, 14, () -> stepDc(1), "Subir a CD (↑ ou roda; Shift: de 5 em 5)");
            int px = ix + 58;
            for (int preset : DC_PRESETS) {
                String label = FACES.length > preset ? FACES[preset] : String.valueOf(preset);
                int pw = 18;
                if (px + pw > sx - 6) break;
                boolean picked = dc == preset;
                button("cd_" + preset, picked ? "b-preset-on" : "b-preset", label, false, px, cy, pw, 14, () -> setDc(picked ? 0 : preset),
                        picked ? "Tirar a CD" : "CD " + preset + presetName(preset));
                px += pw + 2;
            }
        }

        int ry = dy + RESULT_Y;
        String resultClass = "result";
        if (result != null && !D20.spinning(Util.getMillis() - resultAt)) {
            String outcome = Texts.outcome(result.roll());
            resultClass = outcome.startsWith("CRÍTICO") ? "result-crit" : outcome.contains("FALHA") ? "result-bad"
                    : outcome.equals("SUCESSO") ? "result-ok" : "result";
            // O brilho do crítico pulsa uma vez por rolagem: mexer na CD ou no secreto depois não o refaz.
            if ("result-crit".equals(resultClass) && critAnimated != resultAt) {
                critAnimated = resultAt;
                resultClass = "result-crit crit-in";
            }
        }
        panel("<col class=\"" + resultClass + "\"></col>", ix, ry, iw, 50);
        boolean otherRoll = waiting && !d.equals(waitingFor);
        if (result == null) {
            String idle;
            String cls;
            if (waiting) {
                idle = otherRoll ? "Esperando a rolagem anterior voltar do site…" : "Rolando pelo motor do site…";
                cls = "status-espera";
            } else if (!dialogError.isEmpty()) {
                idle = dialogError;
                cls = "status-erro";
            } else {
                idle = free ? "Qualquer fórmula que o site aceite. Enter rola."
                        : "Rola 1d20 com o bônus da ficha" + (secret ? " — em segredo: só você e os mestres veem."
                        : ". Quem estiver por perto vê o resultado.");
                cls = "hint";
            }
            List<String> wrapped = FichaUi.wrap(font, idle, iw - 62, 6, 3);
            int th = wrapped.size() * 9;
            lines(cls, idle, 6, ix + 50, ry + 25 - th / 2, iw - 62, 3, 9);
        }

        // Atalhos numa linha própria, inteira (o erro de uma rolagem com resultado na tela entra no lugar deles).
        int ky = dy + 129;
        if (result != null && !dialogError.isEmpty()) {
            text("status-erro", dialogError, 6, ix, ky - 1, iw, 9);
        } else {
            String keys = free ? "Enter rola · Tab secreto · Esc fecha" : "Enter rola · ↑↓ ou roda mudam a CD · Tab secreto · Esc fecha";
            String keysFit = FichaUi.fitScaled(font, keys, iw, 5 / 7f);
            int kcx = ix + iw / 2;
            draw(g -> FichaUi.textCenter(g, font, keysFit, kcx, ky, FichaUi.FAINT, 5 / 7f, false));
        }

        // Botões à direita: Rolar é o primário (dourado, o mais largo); Fechar ao lado, discreto.
        int byy = dy + dh - 24;
        boolean locked = waiting;
        String rollLabel = locked ? "Rolando…" : result != null ? "Rolar de novo" : "Rolar";
        int rollW = Math.max(78, buttonW(rollLabel, 6, true, true) + 8);
        int closeW = buttonW("Fechar", 6, true, false);
        int rollX = ix + iw - rollW;
        button("rolar", locked ? "b-off" : "b-gold", rollLabel, true, rollX, byy, rollW, 16, locked ? null : this::roll,
                locked ? (otherRoll ? "Uma rolagem de cada vez: a anterior ainda não voltou." : "Esperando o resultado do site…") : "Rolar (Enter)");
        button("fechar_dialogo", "b", "Fechar", true, rollX - 4 - closeW, byy, closeW, 16, this::closeDialog,
                waiting ? "Fechar (Esc). A rolagem continua: o resultado aparece no HUD." : "Fechar (Esc)");
    }

    private static String presetName(int dc) {
        return switch (dc) {
            case 10 -> " (fácil)";
            case 15 -> " (média)";
            case 20 -> " (difícil)";
            case 25 -> " (muito difícil)";
            default -> "";
        };
    }

    /** O resultado no diálogo: d20 girando e pousando, total e o que deu. Por frame, mas só aqui. */
    private void renderResult(GuiGraphics g, long now) {
        if (dialog == null) return;
        int ix = dx + 10, iw = dw - 20, ry = dy + RESULT_Y;
        RollCard card = result;
        if (card == null) {
            // Esperando: o dado "chacoalha" com números trocando; parado: o dado apagado com "?".
            if (waiting) {
                long step = now / 85;
                int face = 1 + (int) Math.floorMod(step * 7 + 3, 20L);
                int lift = (int) Math.round(Math.abs(Math.sin(now / 140.0)) * 3);
                D20.draw(g, font, ix + 26, ry + 25 - lift, 17, WAIT_PALETTE, FACES[face], 1f, step % 2 == 0, 1f);
            } else {
                D20.draw(g, font, ix + 26, ry + 25, 17, D20.dim(), "?", 1f, false, 0.9f);
            }
            return;
        }
        RollResult roll = card.roll();
        long age = now - resultAt;
        boolean landed = !D20.spinning(age);
        int color = FichaUi.outcomeColor(roll);
        // Ao pousar, um clarão rápido da cor do resultado dentro da caixa.
        long after = age - D20.SPIN_MS;
        if (after >= 0 && after < 280) {
            g.fill(ix + 1, ry + 1, ix + iw - 1, ry + 49, FichaUi.alpha(color, 0.22f * (1f - after / 280f)));
        }
        D20.drawRoll(g, font, roll, ix + 26, ry + 25, 19, age, resultAt, 1f);
        int tx = ix + 56;
        if (!landed) {
            FichaUi.text(g, font, "rolando…", tx, ry + 21, FichaUi.SOFT, 1f, false);
            return;
        }
        prepareResultText(card, iw);
        float pop = D20.pop(age);
        FichaUi.text(g, font, resultTotal, tx, ry + 8 - (pop - 1) * 8, color, resultScale * pop, true);
        int afterTotal = tx + Math.round(font.width(resultTotal) * resultScale) + 8;
        if (!resultOutcome.isEmpty()) FichaUi.text(g, font, resultOutcome, afterTotal, ry + 10, color, 0.86f, false);
        if (!resultMode.isEmpty()) FichaUi.text(g, font, resultMode, afterTotal, ry + 21, FichaUi.VIOLET, 0.72f, false);
        FichaUi.text(g, font, resultLine, tx, ry + 36, FichaUi.SOFT, 0.8f, false);
    }

    /** Total, resultado, modo e a conta já cortados para a caixa: uma vez por rolagem (e por largura). */
    private void prepareResultText(RollCard card, int iw) {
        if (card == textFor && iw == textW) return;
        textFor = card;
        textW = iw;
        RollResult roll = card.roll();
        resultTotal = roll.repeated() ? Texts.totals(roll) : D20.num(roll.total());
        resultScale = roll.repeated() ? 1.4f : 2.2f;
        // Mesma conta do desenho: o texto começa em ix + 56 e o resultado depois do total em 2,2x.
        float room = iw - 56 - 6 - Math.round(font.width(resultTotal) * resultScale) - 8;
        resultOutcome = FichaUi.fitScaled(font, Texts.outcome(roll), room, 0.86f);
        String mode = Texts.modeLabel(roll.mode());
        RollResult.Die dropped = D20.dropped(roll);
        resultMode = mode.isEmpty() ? "" : FichaUi.fitScaled(font, mode + (dropped != null ? " · descartou " + dropped.value() : ""), room, 0.72f);
        String line = Texts.breakdown(roll, card.bonusHidden(), card.dcHidden()) + (card.secret() ? " · secreta" : "");
        resultLine = FichaUi.fitScaled(font, line, iw - 56 - 6, 0.8f);
    }

    // ── Utilitários ─────────────────────────────────────────────────────────────

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static TesseraInputState fresh() {
        TesseraInputState state = new TesseraInputState();
        if (state.text == null) state.text = "";
        return state;
    }

    private String field(String key) {
        TesseraInputState state = inputs.get(key);
        return state == null || state.text == null ? "" : state.text;
    }
}
