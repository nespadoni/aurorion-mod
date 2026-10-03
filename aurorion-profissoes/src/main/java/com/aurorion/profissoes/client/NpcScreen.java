package com.aurorion.profissoes.client;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.network.NpcActionPayload;
import com.aurorion.profissoes.network.NpcScreenPayload;
import com.tesseraui.TesseraFocusManager;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import static com.aurorion.profissoes.network.NpcScreenPayload.*;

/**
 * Tela de um NPC de oficio, em HTML/CSS do TesseraUI (o mesmo do diario): cabecalho com o nome, o
 * saldo da carteira e a saudacao; abas de loja, servicos e conversa; linhas com o item de verdade
 * desenhado por cima; rodape com o aviso da ultima escolha e a paginacao.
 *
 * <h2>Uma tela, varias respostas</h2>
 * O pacote ja traz tudo e as abas trocam sem falar com o servidor. Cada escolha viaja como indice
 * (mais o nome digitado, na etiqueta); a resposta chega e {@link #update} troca os dados <b>na mesma
 * tela</b>: rolagem e texto digitado ficam. Enquanto a resposta nao chega os botoes ficam desligados,
 * para um clique duplo nao virar duas compras.
 *
 * <h2>Regioes absolutas</h2>
 * Cada linha e um painel Tessera proprio, posicionado aqui. Assim a posicao do icone e exata e o item
 * real (com encantamento, pocao, nome) e desenhado com {@code renderItem}, em vez do {@code item-slot}
 * do Tessera, que so conhece o id. O HTML e remontado apenas quando algo muda — nunca por frame.
 */
public final class NpcScreen extends TesseraScreen {
    private static final int MAX_W = 400, MAX_H = 300, PAD = 6, GAP = 4;
    private static final int HEAD_H = 46, TABS_H = 18, FOOT_H = 22;
    // Cada linha de texto do Tessera ocupa ~13px de GUI (fonte + respiro): 2 linhas na loja, 3 nos servicos.
    private static final int TRADE_H = 32, SERVICE_H = 48, ROW_GAP = 3, ICON = 24, LINE = 10;
    private static final int BUTTON_W = 64, INPUT_W = 120;
    private static String css = "";

    private record Icon(ItemStack stack, int x, int y) {}

    private NpcScreenPayload data;
    private int tab, scroll, visible, total, waitTicks;
    private boolean waiting, closed;
    private TesseraPanel root = TesseraPanel.column(0, 0, 1, 1);
    private final Map<String, TesseraInputState> inputs = new HashMap<>();
    private final List<Icon> icons = new ArrayList<>();

    public NpcScreen(NpcScreenPayload data) {
        super(Component.literal(data.title()));
        this.data = data;
        this.tab = resolveTab(data.tab());
    }

    /**
     * A resposta do servidor para a ultima escolha. Na mesma aba a rolagem fica (comprar o 5o item nao
     * volta a lista ao topo). Etiqueta gravada esvazia o campo; recusada, o nome fica para tentar de novo.
     */
    public void update(NpcScreenPayload next) {
        int nextTab;
        data = next;
        nextTab = resolveTab(next.tab());
        if (nextTab != tab) scroll = 0;
        tab = nextTab;
        waiting = false;
        if (!next.notice().isEmpty() && !next.noticeError()) inputs.replaceAll((key, old) -> fresh());
        rebuild();
    }

    public boolean closed() { return closed; }

    // --- ciclo de vida -------------------------------------------------------------------------

    @Override protected void init() {
        if (css.isEmpty()) css = resource("ui/npc.css");
        rebuild();
    }

    @Override protected TesseraPanel tesseraRoot() { return root; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // O renderBackground do Tessera nao escurece o mundo; aqui sim, para o texto ler sobre qualquer cena.
        graphics.fill(0, 0, width, height, 0x90000000);
        root.render(graphics, mouseX, mouseY);
        ItemStack hovered = ItemStack.EMPTY;
        for (var icon : icons) {
            // So o item: a quantidade ja esta no texto ("16× Tocha"), o numero por cima so repetia.
            graphics.renderItem(icon.stack, icon.x, icon.y);
            if (mouseX >= icon.x && mouseX < icon.x + 16 && mouseY >= icon.y && mouseY < icon.y + 16) hovered = icon.stack;
        }
        renderTesseraOverlays(graphics, mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Sem resposta em 2 s (sessao expirada, NPC longe), a tela volta a aceitar cliques. */
    @Override public void tick() {
        super.tick();
        if (waiting && ++waitTicks > 40) { waiting = false; rebuild(); }
    }

    @Override public void onClose() {
        if (!closed) PacketDistributor.sendToServer(new NpcActionPayload(data.token(), NpcActionPayload.CLOSE, 0));
        closed = true;
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    // --- entrada -------------------------------------------------------------------------------

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicar fora do campo tira o foco dele; clicar no campo devolve.
        TesseraFocusManager.clear();
        inputs.values().forEach(state -> state.focused = false);
        if (root.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (TesseraFocusManager.focused() != null) return root.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == TAB_HOME) return false;
        page(-(int) Math.signum(scrollY));
        return true;
    }

    // --- montagem ------------------------------------------------------------------------------

    private void rebuild() {
        if (width <= 0 || height <= 0) return; // ainda sem init: o init monta tudo
        int w = Math.min(width - 16, MAX_W), h = Math.min(height - 16, MAX_H);
        int x = (width - w) / 2, y = (height - h) / 2;
        int innerX = x + PAD, innerW = w - 2 * PAD;
        Map<String, Runnable> clicks = new HashMap<>();
        Map<String, Consumer<String>> submits = new HashMap<>();
        icons.clear();

        TesseraPanel next = TesseraPanel.column(0, 0, width, height);
        add(next, "<col class=\"frame-" + theme() + "\"></col>", clicks, submits, x, y, w, h);
        head(next, clicks, submits, innerX, y + PAD, innerW);
        int tabsY = y + PAD + HEAD_H + GAP;
        add(next, tabsHtml(clicks), clicks, submits, innerX, tabsY, innerW, TABS_H);
        int bodyY = tabsY + TABS_H + GAP, footY = y + h - PAD - FOOT_H;
        body(next, clicks, submits, innerX, bodyY, innerW, Math.max(LINE, footY - GAP - bodyY));
        add(next, footHtml(clicks, innerW), clicks, submits, innerX, footY, innerW, FOOT_H);
        next.layout();
        root = next;
    }

    private void add(TesseraPanel parent, String html, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits,
                     int x, int y, int w, int h) {
        TesseraPanel panel = TesseraTemplateRenderer.build(TesseraTemplate.fromString(html, css), TesseraModel.EMPTY,
                clicks, submits, inputs, x, y, w, h);
        panel.layout();
        // TesseraUI 1.1: addAbsolute(widget, top, left, right, bottom); MIN_VALUE deixa right/bottom sem ancora.
        parent.addAbsolute(panel, y, x, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /**
     * Cabecalho em pecas com posicao fixa: o Tessera 1.1 nao soma a altura de uma coluna dentro de
     * uma linha (o nome caia por cima da saudacao) nem respeita o espacador ao lado de texto longo
     * (o saldo saia da janela). Cada texto num painel proprio nao depende de nenhum dos dois.
     */
    private void head(TesseraPanel parent, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits,
                      int x, int y, int w) {
        String balance = data.balance();
        int balanceW = font.width(balance) * 7 / 9 + 16;
        int textW = w - 16 - balanceW - 8;
        add(parent, "<col class=\"head\"></col>", clicks, submits, x, y, w, HEAD_H);
        add(parent, "<col class=\"cell\"><label class=\"kicker-" + theme() + "\">" + esc(fit(data.eyebrow(), textW, 6))
                + "</label></col>", clicks, submits, x + 8, y + 5, textW, 8);
        add(parent, "<col class=\"cell\"><label class=\"name\">" + esc(fit(data.title(), textW, 10))
                + "</label></col>", clicks, submits, x + 8, y + 15, textW, 12);
        add(parent, "<col class=\"cell\">" + label("greet", data.greeting(), w - 16, 7) + "</col>",
                clicks, submits, x + 8, y + 32, w - 16, 10);
        add(parent, "<row class=\"saldo\"><label class=\"saldo-t\">" + esc(balance) + "</label></row>",
                clicks, submits, x + w - 8 - balanceW, y + 8, balanceW, 14);
    }

    private String tabsHtml(Map<String, Runnable> clicks) {
        var html = new StringBuilder("<row class=\"tabs\">");
        if (!data.trades().isEmpty()) html.append(tabButton(clicks, TAB_SHOP, "Loja • " + data.trades().size()));
        if (!data.services().isEmpty()) html.append(tabButton(clicks, TAB_SERVICES, "Serviços • " + data.services().size()));
        if (data.admDialogue() || !data.dialogue().isEmpty()) html.append(tabButton(clicks, TAB_TALK, "Conversar"));
        return html.append("</row>").toString();
    }

    private String tabButton(Map<String, Runnable> clicks, int target, String label) {
        if (target == tab) return "<button class=\"tab-on-" + theme() + "\">" + esc(label) + "</button>";
        String handler = "aba_" + target;
        // Com o ADM, "Conversar" abre a arvore de dialogo no servidor; sem ele, as falas do JSON.
        clicks.put(handler, target == TAB_TALK && data.admDialogue() ? () -> send(NpcActionPayload.TALK, 0, "") : () -> switchTab(target));
        return "<button class=\"tab\" onclick=\"" + handler + "\">" + esc(label) + "</button>";
    }

    private void body(TesseraPanel parent, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits,
                      int x, int y, int w, int h) {
        switch (tab) {
            case TAB_SHOP -> {
                layoutRows(data.trades().size(), TRADE_H, h);
                if (total == 0) { empty(parent, clicks, submits, "Nada à venda.", x, y, w); return; }
                for (int i = 0; i < visible; i++) {
                    int index = scroll + i, rowY = y + i * (TRADE_H + ROW_GAP);
                    var row = data.trades().get(index);
                    icon(parent, clicks, submits, row.result(), x, rowY, TRADE_H);
                    add(parent, tradeHtml(clicks, row, index, w - ICON - 3), clicks, submits, x + ICON + 3, rowY, w - ICON - 3, TRADE_H);
                }
            }
            case TAB_SERVICES -> {
                layoutRows(data.services().size(), SERVICE_H, h);
                if (total == 0) { empty(parent, clicks, submits, "Nenhum serviço disponível.", x, y, w); return; }
                for (int i = 0; i < visible; i++) {
                    int index = scroll + i, rowY = y + i * (SERVICE_H + ROW_GAP);
                    add(parent, serviceHtml(clicks, submits, data.services().get(index), index, w), clicks, submits, x, rowY, w, SERVICE_H);
                }
            }
            case TAB_TALK -> {
                var lines = new ArrayList<String>();
                int wrap = Math.max(40, (w - 16) * 9 / 7);
                for (String said : data.dialogue()) {
                    for (FormattedCharSequence line : font.split(Component.literal(said), wrap)) lines.add(plain(line));
                    lines.add("");
                }
                if (!lines.isEmpty()) lines.removeLast();
                total = lines.size();
                visible = Math.max(1, Math.min(total, (h - 10) / LINE));
                scroll = Mth.clamp(scroll, 0, Math.max(0, total - visible));
                if (total == 0) { empty(parent, clicks, submits, "Não há nada a conversar.", x, y, w); return; }
                var html = new StringBuilder("<col class=\"talk\">");
                for (int i = 0; i < visible; i++)
                    html.append("<label class=\"line\">").append(esc(lines.get(scroll + i))).append("</label>");
                add(parent, html.append("</col>").toString(), clicks, submits, x, y, w, h);
            }
            default -> {
                total = visible = 0;
                empty(parent, clicks, submits, "Este NPC não tem nada a oferecer agora.", x, y, w);
            }
        }
    }

    private void layoutRows(int count, int rowH, int h) {
        total = count;
        visible = Math.max(1, Math.min(count, (h + ROW_GAP) / (rowH + ROW_GAP)));
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - visible));
        if (count == 0) visible = 0;
    }

    private void icon(TesseraPanel parent, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits,
                      ItemStack stack, int x, int y, int rowH) {
        add(parent, "<col class=\"slot\"></col>", clicks, submits, x, y, ICON, rowH);
        icons.add(new Icon(stack, x + (ICON - 16) / 2, y + (rowH - 16) / 2));
    }

    private String tradeHtml(Map<String, Runnable> clicks, TradeRow row, int index, int w) {
        String handler = "comprar_" + index;
        boolean on = row.enabled() && !waiting;
        if (on) clicks.put(handler, () -> send(NpcActionPayload.TRADE, index, ""));
        int textW = w - BUTTON_W - 16;
        String name = row.amount() + "× " + row.result().getHoverName().getString();
        String detail = row.enabled() ? row.price() + (row.stock() >= 0 ? "  •  estoque " + row.stock() : "") : row.reason();
        return "<row class=\"row\">"
                + "<col class=\"txt\">"
                + label("t", name, textW, 8)
                + label(row.enabled() ? "price" : "why", detail, textW, 7)
                + "</col>"
                + button(on, handler, waiting ? "…" : "Comprar", row.enabled() ? "Preço: " + row.price() : row.reason())
                + "</row>";
    }

    private String serviceHtml(Map<String, Runnable> clicks, Map<String, Consumer<String>> submits, ServiceRow row, int index, int w) {
        String handler = "servico_" + index;
        boolean on = row.enabled() && !waiting;
        boolean typed = !row.input().isEmpty();
        String key = "nome_" + index;
        if (typed) inputs.computeIfAbsent(key, k -> fresh());
        if (on) {
            clicks.put(handler, () -> send(NpcActionPayload.SERVICE, index, typed ? text(key) : ""));
            if (typed) submits.put(handler, value -> send(NpcActionPayload.SERVICE, index, text(key)));
        }
        int textW = w - BUTTON_W - 16 - (typed ? INPUT_W + 4 : 0);
        String status = row.enabled() ? "Custo: " + row.cost() : row.reason();
        var html = new StringBuilder("<row class=\"row\">")
                .append("<col class=\"txt\">")
                .append(label("t", row.title(), textW, 8))
                .append(label("d", row.detail(), textW, 6))
                .append(label(row.enabled() ? "price" : "why", status, textW, 7))
                .append("</col>");
        if (typed) {
            html.append("<input id=\"").append(key).append("\" class=\"field\" placeholder=\"").append(esc(row.input()))
                    .append("\" maxlength=\"50\" value=\"").append(esc(text(key))).append("\"")
                    .append(on ? " onsubmit=\"" + handler + "\"" : " disabled=\"true\"").append("/>");
        }
        html.append(button(on, handler, waiting ? "…" : typed ? "Gravar" : "Solicitar", row.enabled() ? "Custo: " + row.cost() : row.reason()));
        return html.append("</row>").toString();
    }

    private String footHtml(Map<String, Runnable> clicks, int w) {
        clicks.put("fechar", this::onClose);
        var html = new StringBuilder("<row class=\"foot\">");
        String notice = data.notice().isEmpty() ? hint() : data.notice();
        String noticeClass = data.notice().isEmpty() ? "hint" : data.noticeError() ? "notice-err" : "notice-ok";
        boolean pages = total > visible && visible > 0;
        int noticeW = w - 70 - (pages ? 90 : 0);
        html.append(label(noticeClass, notice, noticeW, 7)).append("<label class=\"spacer\"></label>");
        if (pages) {
            clicks.put("anterior", () -> page(-visible));
            clicks.put("proxima", () -> page(visible));
            int last = Math.min(total, scroll + visible);
            html.append("<button class=\"page\" onclick=\"anterior\">‹</button>")
                    .append("<label class=\"page-label\">").append(scroll + 1).append("–").append(last).append(" de ").append(total).append("</label>")
                    .append("<button class=\"page\" onclick=\"proxima\">›</button>");
        }
        return html.append("<button class=\"btn\" onclick=\"fechar\">Fechar</button></row>").toString();
    }

    private String hint() {
        return switch (tab) {
            case TAB_SHOP -> "O pagamento sai da sua carteira.";
            case TAB_SERVICES -> "Atendimento de plantão: um profissional jogador cobra menos.";
            case TAB_TALK -> "";
            default -> "";
        };
    }

    private void empty(TesseraPanel parent, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits, String text, int x, int y, int w) {
        add(parent, "<col class=\"empty\"><label class=\"hint\">" + esc(text) + "</label></col>", clicks, submits, x, y, w, 24);
    }

    /** Um texto de linha. O tooltip so existe quando o texto foi cortado: repetir o que ja se le e ruido. */
    private String label(String cls, String text, int width, int size) {
        String shown = fit(text, width, size);
        String tooltip = shown.equals(text) ? "" : " tooltip=\"" + esc(text) + "\"";
        return "<label class=\"" + cls + "\"" + tooltip + ">" + esc(shown) + "</label>";
    }

    /** Botao ligado nao tem tooltip (o preco ja esta na linha); desligado explica o motivo. */
    private static String button(boolean on, String handler, String label, String reason) {
        return on
                ? "<button class=\"buy\" onclick=\"" + handler + "\">" + esc(label) + "</button>"
                : "<button class=\"buy-off\" tooltip=\"" + esc(reason) + "\">" + esc(label) + "</button>";
    }

    // --- interacao -----------------------------------------------------------------------------

    private void switchTab(int next) {
        tab = next;
        scroll = 0;
        rebuild();
    }

    private void page(int delta) {
        int next = Mth.clamp(scroll + delta, 0, Math.max(0, total - visible));
        if (next != scroll) { scroll = next; rebuild(); }
    }

    private void send(String kind, int index, String text) {
        if (waiting) return;
        waiting = true;
        waitTicks = 0;
        String clipped = text.length() > NpcActionPayload.MAX_TEXT ? text.substring(0, NpcActionPayload.MAX_TEXT) : text;
        PacketDistributor.sendToServer(new NpcActionPayload(data.token(), kind, index, clipped));
        rebuild();
    }

    /** A aba pedida, se existir; senao a primeira que tem conteudo (loja, servicos, conversa). */
    private int resolveTab(int requested) {
        boolean shop = !data.trades().isEmpty(), services = !data.services().isEmpty(), talk = !data.dialogue().isEmpty();
        if (requested == TAB_SHOP && shop || requested == TAB_SERVICES && services || requested == TAB_TALK && talk) return requested;
        return shop ? TAB_SHOP : services ? TAB_SERVICES : talk ? TAB_TALK : TAB_HOME;
    }

    /** O tema segue o oficio: cada um tem a sua cor de destaque no CSS. */
    private String theme() {
        return switch (data.profession()) {
            case "medico", "ferreiro", "cozinheiro", "arcanista", "corretor" -> data.profession();
            default -> "mercador";
        };
    }

    // --- utilitarios ---------------------------------------------------------------------------

    private static TesseraInputState fresh() {
        var state = new TesseraInputState();
        if (state.text == null) state.text = "";
        return state;
    }

    private String text(String key) {
        var state = inputs.get(key);
        return state == null || state.text == null ? "" : state.text;
    }

    /**
     * Corta o texto para caber em {@code width} pixels de tela com a fonte do Tessera em {@code size}px.
     * A fonte do Minecraft tem 9px de linha: um texto em 7px ocupa 7/9 da largura medida.
     */
    private String fit(String text, int width, int size) {
        int room = Math.max(1, width * 9 / Math.max(1, size));
        if (font.width(text) <= room) return text;
        return font.plainSubstrByWidth(text, Math.max(1, room - font.width("…"))) + "…";
    }

    private static String plain(FormattedCharSequence sequence) {
        var out = new StringBuilder();
        sequence.accept((index, style, codePoint) -> { out.appendCodePoint(codePoint); return true; });
        return out.toString();
    }

    /** Escapa texto vindo do servidor/JSON antes de entrar no HTML do Tessera (inclusive {{ }} de binding). */
    static String esc(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '{' -> out.append("&#123;");
                case '}' -> out.append("&#125;");
                default -> { if (!Character.isISOControl(c)) out.append(c); }
            }
        }
        return out.toString();
    }

    private static String resource(String path) {
        var location = ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, path);
        return Minecraft.getInstance().getResourceManager().getResource(location).map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException error) {
                AurorionProfissoes.LOGGER.warn("NPCs: nao consegui ler {}", location, error);
                return "";
            }
        }).orElse("");
    }
}
