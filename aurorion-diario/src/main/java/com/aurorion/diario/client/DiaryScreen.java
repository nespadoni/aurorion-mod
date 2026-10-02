package com.aurorion.diario.client;

import com.aurorion.diario.AurorionDiario;
import com.aurorion.diario.network.DiaryPayloads;
import com.tesseraui.TesseraFocusManager;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A tela do {@code /diario}.
 *
 * <h2>Duas camadas</h2>
 * O visual é HTML/CSS do Tessera UI (cantos arredondados, transições, o tema do Aurorion), em
 * quatro regiões: lista de entradas, barra de título e ferramentas, moldura do editor e rodapé. A
 * escrita usa o {@link MultiLineEditBox} nativo por cima da moldura: rolagem, seleção, copiar e
 * colar que o Minecraft já resolve, em vez de um campo de texto refeito.
 *
 * <h2>Salvamento</h2>
 * Dois segundos depois da última tecla, o rascunho vai ao servidor, que o leva ao site. Cada
 * gravação leva a versão-base: se o site ou outra sessão salvou antes, chega um conflito e a pessoa
 * escolhe qual texto fica — nada é sobrescrito sem ela decidir. Se o site está fora, o servidor do
 * jogo guarda e avisa; sincroniza sozinho depois.
 *
 * <p>O HTML é montado aqui com tudo que vem de fora escapado; o CSS mora em
 * {@code assets/aurorion_diario/ui/diario.css}.
 */
public final class DiaryScreen extends TesseraScreen {
    private static final long AUTOSAVE_DELAY_MS = 2_000L;
    private static final int TOP_H = 64;
    private static final int FOOT_H = 24;
    private static final int ENTRY_H = 26;

    private final List<DiaryPayloads.Summary> entries = new ArrayList<>();
    @Nullable
    private final DiaryTestSession testSession;
    private String character;
    private String siteUrl;

    // Entrada aberta
    private long entryId;
    private int version;
    private int flags;
    private String draftKey = UUID.randomUUID().toString();
    private boolean opened;
    private boolean readOnly;
    private final TesseraInputState titleState = new TesseraInputState();
    private final TesseraInputState dateState = new TesseraInputState();
    private String markup = "";
    private String savedTitle = "";
    private String savedDate = "";
    private String savedMarkup = "";

    // Estado de salvamento
    private boolean dirty;
    private boolean saving;
    private boolean loading;
    private boolean publishAfterSave;
    private long lastEditAt;
    private String state = "pronto";
    private String stateText = "";
    @Nullable
    private DiaryPayloads.Conflict conflict;

    // Interface
    private boolean preview;
    @Nullable
    private DiaryPreview previewLayout;
    private int page;
    private TesseraPanel root = TesseraPanel.column(0, 0, 1, 1);
    @Nullable
    private MultiLineEditBox editor;
    private int ex, ey, ew, eh; // região do editor
    private static String css = "";

    public DiaryScreen(DiaryPayloads.Open payload) {
        this(payload, null);
    }

    private DiaryScreen(DiaryPayloads.Open payload, @Nullable DiaryTestSession testSession) {
        super(Component.literal("Diário"));
        this.testSession = testSession;
        this.character = payload.character();
        this.siteUrl = payload.siteUrl();
        this.entries.addAll(payload.entries());
        if (!entries.isEmpty()) request("abrir", entries.get(0).id());
        else startNew();
    }

    static DiaryScreen forTesting() {
        DiaryTestSession session = new DiaryTestSession();
        return new DiaryScreen(session.openPayload(), session);
    }

    boolean isTest() {
        return testSession != null;
    }

    // ── Ciclo de vida da tela ───────────────────────────────────────────────────

    @Override
    protected void init() {
        if (css.isEmpty()) css = resource("ui/diario.css");
        String keep = editor != null ? editor.getValue() : markup;
        clearWidgets();

        int[] f = frame();
        int x = f[0], y = f[1], w = f[2], h = f[3], side = f[4], mx = f[5], mw = f[6];
        ex = mx + 6;
        ey = y + TOP_H + 6;
        ew = mw - 12;
        eh = h - TOP_H - FOOT_H - 14;

        editor = new MultiLineEditBox(font, ex, ey, ew, eh,
                Component.literal("Escreva como o seu personagem… ## título, > citação, - lista, **negrito**, *itálico*"),
                Component.literal("Texto do diário")) {
            @Override
            protected void renderBackground(GuiGraphics graphics) {
                // A moldura Tessera já desenha o fundo e a borda do editor.
            }
        };
        editor.setCharacterLimit(DiaryPayloads.MARKUP_EDIT);
        loading = true;
        editor.setValue(keep);
        loading = false;
        editor.setValueListener(value -> {
            markup = value;
            if (!loading) touch();
        });
        editor.visible = opened && !preview;
        editor.active = !readOnly;
        addRenderableWidget(editor);

        rebuild(x, y, w, h, side, mx, mw);
    }

    private int[] frame() {
        int w = Math.min(width - 16, 600);
        int h = Math.min(height - 16, 340);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int side = Math.min(150, w / 3);
        return new int[]{x, y, w, h, side, x + side + 6, w - side - 6};
    }

    private void rebuild() {
        if (width <= 0 || height <= 0) return; // ainda sem init: o init monta tudo
        int[] f = frame();
        rebuild(f[0], f[1], f[2], f[3], f[4], f[5], f[6]);
    }

    /** Remonta as regiões do Tessera a partir do estado. Barato: só acontece quando algo muda. */
    private void rebuild(int x, int y, int w, int h, int side, int mx, int mw) {
        Map<String, Runnable> clicks = new HashMap<>();
        Map<String, TesseraInputState> inputs = Map.of("titulo", titleState, "data", dateState);
        TesseraPanel next = TesseraPanel.column(0, 0, width, height);
        // TesseraUI 1.1: addAbsolute(widget, top, left, right, bottom), não x/y/w/h.
        // O tamanho já é definido em build(); MIN_VALUE deixa right/bottom sem âncora.
        next.addAbsolute(build(sidebarHtml(clicks, side, h), clicks, inputs, x, y, side, h), y, x, Integer.MIN_VALUE, Integer.MIN_VALUE);
        next.addAbsolute(build(topHtml(clicks, mw), clicks, inputs, mx, y, mw, TOP_H), y, mx, Integer.MIN_VALUE, Integer.MIN_VALUE);
        next.addAbsolute(build("<col class=\"frame\"></col>", clicks, inputs, mx, y + TOP_H + 2, mw, h - TOP_H - FOOT_H - 4),
                y + TOP_H + 2, mx, Integer.MIN_VALUE, Integer.MIN_VALUE);
        next.addAbsolute(build(footerHtml(clicks), clicks, inputs, mx, y + h - FOOT_H, mw, FOOT_H), y + h - FOOT_H, mx, Integer.MIN_VALUE, Integer.MIN_VALUE);
        next.layout();
        root = next;
        if (editor != null) {
            editor.visible = opened && !preview;
            editor.active = !readOnly;
        }
    }

    private TesseraPanel build(String html, Map<String, Runnable> clicks, Map<String, TesseraInputState> inputs, int x, int y, int w, int h) {
        TesseraPanel panel = TesseraTemplateRenderer.build(TesseraTemplate.fromString(html, css), TesseraModel.EMPTY,
                clicks, Map.of(), inputs, x, y, w, h);
        panel.layout();
        return panel;
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        root.render(graphics, mouseX, mouseY);
        if (opened && preview && previewLayout != null) previewLayout.render(graphics, font, ex + 4, ey + 2, ew - 8, eh - 4);
        if (!opened) {
            graphics.drawCenteredString(font, Component.literal("Escolha uma entrada ou comece uma nova."), ex + ew / 2, ey + eh / 2 - 4, 0xFFBFB3D6);
        }
        renderTesseraOverlays(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        // Título e data são campos do Tessera: confere aqui se mudaram.
        if (!loading && opened && (!titleState.text.equals(savedTitle) || !dateState.text.equals(savedDate)) && !dirty && !saving) touch();
        if (dirty && !saving && conflict == null && !readOnly && System.currentTimeMillis() - lastEditAt >= AUTOSAVE_DELAY_MS) save();
    }

    @Override
    public void onClose() {
        if (dirty && !readOnly && conflict == null) save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Entrada de usuário ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor != null && editor.visible && editor.isMouseOver(mouseX, mouseY)) {
            TesseraFocusManager.clear();
            titleState.focused = false;
            dateState.focused = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        setFocused(null);
        if (editor != null) editor.setFocused(false);
        if (root.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (TesseraFocusManager.focused() != null) {
            boolean handled = root.charTyped(codePoint, modifiers);
            afterFieldEdit();
            return handled;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        afterFieldEdit();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (preview && previewLayout != null && mouseX >= ex && mouseX <= ex + ew && mouseY >= ey && mouseY <= ey + eh) {
            previewLayout.scroll(scrollY, eh - 4);
            return true;
        }
        if (root.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void afterFieldEdit() {
        if (opened && !loading && (!titleState.text.equals(savedTitle) || !dateState.text.equals(savedDate))) touch();
    }

    private void touch() {
        if (readOnly) return;
        dirty = true;
        lastEditAt = System.currentTimeMillis();
        if (!"pendente".equals(state) && !saving) setState("pendente", "Alterações não salvas…");
    }

    // ── Ações ───────────────────────────────────────────────────────────────────

    private void save() {
        if (!opened || readOnly || conflict != null) return;
        String title = titleState.text.strip();
        String text = editor != null ? editor.getValue() : markup;
        if (title.isEmpty() && text.isBlank()) {
            dirty = false;
            return;
        }
        saving = true;
        dirty = false;
        savedTitle = titleState.text;
        savedDate = dateState.text;
        savedMarkup = text;
        setState("salvando", "Salvando…");
        send(new DiaryPayloads.Save(draftKey, UUID.randomUUID().toString(), entryId, version,
                limit(title, DiaryPayloads.TITLE), limit(dateState.text.strip(), DiaryPayloads.LORE_DATE), text));
    }

    private void open(long id) {
        if (dirty && !readOnly) save();
        setState("carregando", "Abrindo…");
        request("abrir", id);
    }

    private void startNew() {
        if (dirty && !readOnly) save();
        entryId = 0;
        version = 0;
        flags = 0;
        readOnly = false;
        conflict = null;
        preview = false;
        draftKey = UUID.randomUUID().toString();
        fill("", "", "");
        opened = true;
        setState("pronto", isTest() ? "TESTE LOCAL — nova entrada; dados temporários." : "Nova entrada — será salva automaticamente.");
    }

    private void togglePreview() {
        preview = !preview;
        previewLayout = preview ? new DiaryPreview(font, editor != null ? editor.getValue() : markup, ew - 8) : null;
        rebuild();
    }

    private void publish(boolean publish) {
        if (entryId <= 0 && !publish) return;
        if (publish && (dirty || saving || entryId <= 0)) {
            publishAfterSave = true;
            if (dirty) save();
            return;
        }
        setState("salvando", publish ? "Publicando…" : "Retirando a publicação…");
        send(new DiaryPayloads.Publish(entryId, version, publish));
    }

    private void keepMine() {
        if (conflict == null) return;
        entryId = conflict.entryId();
        version = conflict.version(); // sobrescreve de propósito; o texto de lá fica no histórico do site
        conflict = null;
        dirty = true;
        save();
    }

    private void takeSite() {
        if (conflict == null) return;
        DiaryPayloads.Conflict current = conflict;
        conflict = null;
        entryId = current.entryId();
        version = current.version();
        flags &= ~DiaryPayloads.LOCAL;
        fill(current.title(), current.loreDate(), current.markup());
        // O texto recusado já está no histórico da entrada no site: a cópia do servidor pode sair.
        request("descartar", entryId);
        upsertSummary();
        setState("site", "Versão do site carregada.");
    }

    private void request(String action, long id) {
        send(new DiaryPayloads.Request(action, id));
    }

    private void send(CustomPacketPayload payload) {
        if (testSession != null) testSession.handle(this, payload);
        else PacketDistributor.sendToServer(payload);
    }

    // ── Respostas do servidor ───────────────────────────────────────────────────

    void refresh(DiaryPayloads.Open payload) {
        character = payload.character();
        siteUrl = payload.siteUrl();
        entries.clear();
        entries.addAll(payload.entries());
        rebuild();
    }

    void load(DiaryPayloads.Entry payload) {
        entryId = payload.id();
        version = payload.version();
        flags = payload.flags();
        readOnly = (flags & DiaryPayloads.READ_ONLY) != 0;
        conflict = null;
        preview = false;
        publishAfterSave = false;
        // Cópia guardada no servidor: a tela continua aquela sessão, e as próximas gravações a
        // substituem em vez de competir com ela.
        draftKey = payload.draftKey().isEmpty() ? UUID.randomUUID().toString() : payload.draftKey();
        fill(payload.title(), payload.loreDate(), payload.markup());
        opened = true;
        if (readOnly) {
            setState("leitura", "Entrada longa demais para editar no jogo — edite pelo site.");
        } else if ((flags & DiaryPayloads.LOCAL) != 0) {
            // O servidor já está mandando a cópia ao site; se o site a recusou, chega um conflito.
            setState("servidor", "Cópia guardada no servidor — sincroniza sozinha com o site.");
        } else {
            setState("site", isTest() ? "TESTE LOCAL — edite, salve e confira a prévia." : "Salvo no site");
        }
    }

    void onStatus(DiaryPayloads.Status payload) {
        boolean mine = payload.draftKey().isEmpty() ? payload.entryId() == entryId : payload.draftKey().equals(draftKey);
        if (!mine) return;
        switch (payload.state()) {
            case "site" -> {
                saving = false;
                entryId = payload.entryId();
                version = payload.version();
                flags = payload.flags();
                upsertSummary();
                setState(dirty ? "pendente" : "site", dirty ? "Alterações não salvas…"
                        : isTest() ? "TESTE LOCAL — salvo em memória." : "Salvo no site");
                if (publishAfterSave && !dirty) {
                    publishAfterSave = false;
                    publish(true);
                }
            }
            case "servidor" -> {
                saving = false;
                publishAfterSave = false;
                flags |= DiaryPayloads.LOCAL;
                setState("servidor", payload.message());
            }
            case "lento" -> {
                saving = false;
                dirty = true;
                lastEditAt = System.currentTimeMillis();
            }
            case "publicado", "retirado" -> {
                version = payload.version();
                flags = payload.flags();
                upsertSummary();
                String done = "publicado".equals(payload.state()) ? "Publicado" : "Publicação retirada";
                setState("site", payload.message().isEmpty() ? done : payload.message());
            }
            default -> {
                saving = false;
                publishAfterSave = false;
                setState("erro", payload.message());
            }
        }
    }

    void onConflict(DiaryPayloads.Conflict payload) {
        if (!payload.draftKey().equals(draftKey)) return;
        saving = false;
        publishAfterSave = false;
        conflict = payload;
        setState("conflito", "Esta entrada foi salva " + ("site".equals(payload.origin()) ? "no site" : "em outro lugar") + " enquanto você escrevia.");
    }

    private void fill(String title, String date, String text) {
        loading = true;
        titleState.text = title;
        titleState.cursor = title.length();
        dateState.text = date;
        dateState.cursor = date.length();
        markup = text;
        if (editor != null) editor.setValue(text);
        loading = false;
        savedTitle = title;
        savedDate = date;
        savedMarkup = text;
        dirty = false;
        saving = false;
    }

    private void upsertSummary() {
        String title = titleState.text.isBlank() ? "Sem título" : titleState.text.strip();
        DiaryPayloads.Summary summary = new DiaryPayloads.Summary(entryId, title, flags);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() == entryId) {
                entries.set(i, summary);
                return;
            }
        }
        entries.add(0, summary);
        page = 0;
    }

    private void setState(String kind, String text) {
        state = kind;
        stateText = text;
        rebuild();
    }

    // ── Formatação (insere a marcação no cursor do editor) ──────────────────────

    private void format(String before, String after, boolean ownLine) {
        if (editor == null || readOnly || preview || !opened) return;
        setFocused(editor);
        editor.setFocused(true);
        TesseraFocusManager.clear();
        if (ownLine && !editor.getValue().isEmpty()) editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        for (char c : before.toCharArray()) editor.charTyped(c, 0);
        for (char c : after.toCharArray()) editor.charTyped(c, 0);
        for (int i = 0; i < after.length(); i++) editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
    }

    // ── HTML das regiões ────────────────────────────────────────────────────────

    private String sidebarHtml(Map<String, Runnable> clicks, int width, int height) {
        clicks.put("nova", this::startNew);
        int perPage = Math.max(1, (height - 86) / ENTRY_H);
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        clicks.put("anterior", () -> { page = Math.max(0, page - 1); rebuild(); });
        clicks.put("proxima", () -> { page = Math.min(pages - 1, page + 1); rebuild(); });

        StringBuilder html = new StringBuilder("<col class=\"side\">")
                .append("<label class=\"kicker\">DIÁRIO DE</label>")
                .append("<label class=\"who\" tooltip=\"").append(esc(character)).append("\">")
                .append(esc(font.plainSubstrByWidth(character, Math.max(1, (width - 16) * 7 / 9)))).append("</label>")
                .append("<button class=\"new\" onclick=\"nova\">+ Nova entrada</button>")
                .append("<col class=\"list\">");
        int from = page * perPage;
        for (int i = from; i < Math.min(entries.size(), from + perPage); i++) {
            DiaryPayloads.Summary entry = entries.get(i);
            String handler = "abrir_" + entry.id();
            clicks.put(handler, () -> open(entry.id()));
            boolean active = entry.id() == entryId && opened;
            html.append("<col class=\"").append(active ? "entry-on" : "entry").append("\">")
                    .append("<button class=\"entry-title\" onclick=\"").append(handler).append("\">")
                    .append(esc(font.plainSubstrByWidth(entry.title().isBlank() ? "Sem título" : entry.title(), Math.max(1, width - 24)))).append("</button>")
                    .append(badge(entry.flags()))
                    .append("</col>");
        }
        if (entries.isEmpty()) html.append("<label class=\"hint\">Nenhuma entrada ainda.</label>");
        html.append("</col>");
        if (pages > 1) {
            html.append("<row class=\"pager\"><button class=\"page\" onclick=\"anterior\">‹</button>")
                    .append("<label class=\"page-label\">").append(page + 1).append("/").append(pages).append("</label>")
                    .append("<button class=\"page\" onclick=\"proxima\">›</button></row>");
        }
        return html.append("</col>").toString();
    }

    private static String badge(int flags) {
        if ((flags & DiaryPayloads.HIDDEN) != 0) return "<label class=\"badge-hidden\">OCULTA</label>";
        if ((flags & DiaryPayloads.LOCAL) != 0) return "<label class=\"badge-local\">NO SERVIDOR</label>";
        if ((flags & DiaryPayloads.CHANGES) != 0) return "<label class=\"badge-changes\">ALTERAÇÕES</label>";
        if ((flags & DiaryPayloads.PUBLISHED) != 0) return "<label class=\"badge-pub\">PUBLICADA</label>";
        return "<label class=\"badge-draft\">RASCUNHO</label>";
    }

    private String topHtml(Map<String, Runnable> clicks, int width) {
        clicks.put("fmt_negrito", () -> format("**", "**", false));
        clicks.put("fmt_italico", () -> format("*", "*", false));
        clicks.put("fmt_titulo", () -> format("## ", "", true));
        clicks.put("fmt_citacao", () -> format("> ", "", true));
        clicks.put("fmt_lista", () -> format("- ", "", true));
        clicks.put("fmt_separador", () -> format("---", "", true));
        boolean compact = width < 260;
        String disabled = readOnly || !opened ? " disabled=\"true\"" : "";
        return "<col class=\"top\">"
                + "<row class=\"fields\">"
                + "<input id=\"titulo\" class=\"title\" placeholder=\"Título da entrada\" maxlength=\"200\" value=\"" + esc(titleState.text) + "\"" + disabled + "/>"
                + "<input id=\"data\" class=\"date\" placeholder=\"Data na história\" maxlength=\"120\" value=\"" + esc(dateState.text) + "\"" + disabled + "/>"
                + "</row>"
                + "<row class=\"tools\">"
                + "<button class=\"tool\" onclick=\"fmt_negrito\" tooltip=\"Negrito\">N</button>"
                + "<button class=\"tool\" onclick=\"fmt_italico\" tooltip=\"Itálico\">I</button>"
                + "<button class=\"tool-wide\" onclick=\"fmt_titulo\" tooltip=\"Título\">" + (compact ? "T" : "Título") + "</button>"
                + "<button class=\"tool-wide\" onclick=\"fmt_citacao\" tooltip=\"Citação\">" + (compact ? "&gt;" : "Citação") + "</button>"
                + "<button class=\"tool-wide\" onclick=\"fmt_lista\" tooltip=\"Lista\">" + (compact ? "-" : "Lista") + "</button>"
                + "<button class=\"tool\" onclick=\"fmt_separador\" tooltip=\"Separador\">—</button>"
                + "</row>"
                + "<label class=\"status-" + esc(state) + "\" tooltip=\"" + esc(stateText) + "\">"
                + esc(font.plainSubstrByWidth(stateText, Math.max(1, (width - 16) * 7 / 6))) + "</label>"
                + "</col>";
    }

    private String footerHtml(Map<String, Runnable> clicks) {
        if (conflict != null) {
            clicks.put("manter", this::keepMine);
            clicks.put("usar_site", this::takeSite);
            return "<row class=\"foot-conflict\">"
                    // O aviso completo já está na barra de situação, com tooltip.
                    + "<button class=\"btn\" onclick=\"usar_site\">Usar a do site</button>"
                    + "<button class=\"btn-primary\" onclick=\"manter\">Manter a minha</button>"
                    + "</row>";
        }
        clicks.put("previa", this::togglePreview);
        clicks.put("publicar", () -> publish(true));
        clicks.put("retirar", () -> publish(false));
        clicks.put("fechar", this::onClose);
        boolean published = (flags & DiaryPayloads.PUBLISHED) != 0;
        boolean changes = (flags & DiaryPayloads.CHANGES) != 0 || dirty || saving;
        String publishLabel = !published ? "Publicar" : changes ? "Atualizar" : "Publicado";
        StringBuilder html = new StringBuilder("<row class=\"foot\">")
                .append("<button class=\"btn\" onclick=\"previa\">").append(preview ? "Editar" : "Prévia").append("</button>");
        if (published) html.append("<button class=\"btn\" onclick=\"retirar\">Retirar</button>");
        html.append("<label class=\"spacer\"></label>");
        if (opened && !readOnly && (flags & DiaryPayloads.HIDDEN) == 0) {
            html.append("<button class=\"btn-primary\" onclick=\"publicar\">").append(publishLabel).append("</button>");
        }
        return html.append("<button class=\"btn\" onclick=\"fechar\">Fechar</button></row>").toString();
    }

    // ── Utilitários ─────────────────────────────────────────────────────────────

    /** Escapa texto vindo de fora (títulos, nomes) antes de entrar no HTML do Tessera. */
    static String esc(String text) {
        StringBuilder out = new StringBuilder(text.length());
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
                default -> {
                    if (!Character.isISOControl(c)) out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String resource(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AurorionDiario.MOD_ID, path);
        return Minecraft.getInstance().getResourceManager().getResource(location).map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                AurorionDiario.LOGGER.warn("Diario: nao consegui ler {}", location, e);
                return "";
            }
        }).orElse("");
    }
}
