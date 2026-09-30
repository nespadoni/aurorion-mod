package com.aurorion.servicos.client;

import com.aurorion.servicos.data.Categorias;
import com.aurorion.servicos.data.ServicosRules;
import com.aurorion.servicos.network.ServicosPagePayload;
import com.aurorion.servicos.network.ServicosPagePayload.Row;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.aurorion.servicos.network.ServicosPagePayload.*;

/**
 * O app "Servicos" dentro do celular: mesma moldura, mesma capinha, mesmo tema claro/escuro e o mesmo
 * tamanho (170 x 285, escala 3) dos apps do proprio telefone.
 *
 * <p>Quatro abas embaixo:
 * <ul>
 *   <li><b>Buscar</b>: anuncios por area, quem esta trabalhando primeiro. Tocar abre o anuncio, de onde
 *       sai o pedido direto ou a conversa.</li>
 *   <li><b>Pedidos</b>: os que eu fiz e os que chegaram para mim (ou para a minha area). Aceitar leva
 *       direto para a conversa com o cliente.</li>
 *   <li><b>Vagas</b>: vagas de trabalho por area; candidatar-se abre a conversa com quem contrata.</li>
 *   <li><b>Perfil</b>: meus anuncios e minhas vagas, com os candidatos de cada uma.</li>
 * </ul>
 *
 * <p>O botao "Trabalhando" fica no topo para quem tem anuncio. A tela nao decide nada: o servidor
 * manda cada linha ja com os botoes que ela pode ter ({@link Row#flags()}).
 */
public final class ServicosScreen extends Screen implements AurorionPhoneScreen {
    private static final int W = 170;
    private static final int H = 285;
    private static final int ROW_H = 38;
    private static final int GREEN = 0xFF22A55A;
    private static final int YELLOW = 0xFFE3A008;
    private static final int RED = 0xFFE5484D;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    enum Tab {
        BUSCAR("buscar", "Buscar"), PEDIDOS("pedidos", "Pedidos"), VAGAS("vagas", "Vagas"), PERFIL("perfil", "Perfil");

        final String id;
        final String label;

        Tab(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Tab of(String id) {
            for (Tab tab : values()) if (tab.id.equals(id)) return tab;
            return BUSCAR;
        }
    }

    enum View { LIST, DETAIL, FORM_AD, FORM_DIRECT, FORM_OPEN, FORM_JOB }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Action(String label, int color, Runnable onClick) {
    }

    private Tab tab;
    private String filter;
    private View view = View.LIST;
    @Nullable
    private ServicosPagePayload page;
    @Nullable
    private Row selected;
    private int scroll;
    private int listHeight;
    private int contentHeight;
    private String toast = "";
    private long toastUntil;
    private boolean submitting;

    private String formCategory = Categorias.all().getFirst().id();
    private long formId;
    private String formTitle = "";
    private String formPrice = "";
    private String formText = "";
    @Nullable
    private EditBox titleBox;
    @Nullable
    private EditBox priceBox;
    @Nullable
    private MultiLineEditBox textBox;

    private final List<Hit> hits = new ArrayList<>();
    private PhoneBridge.Theme theme = PhoneBridge.theme();
    private int px;
    private int py;

    public ServicosScreen(String tab, @Nullable String filter) {
        super(Component.literal(ServicosClient.APP_NAME));
        this.tab = Tab.of(tab);
        this.filter = filter != null ? filter : defaultFilter(this.tab);
    }

    // ---- ciclo da tela -------------------------------------------------------------------------

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        titleBox = null;
        priceBox = null;
        textBox = null;
        switch (view) {
            case FORM_AD, FORM_JOB -> {
                titleBox = field(py + 86, ServicosRules.TITLE, formTitle, "Título", value -> formTitle = value);
                priceBox = field(py + 112, ServicosRules.PRICE, formPrice, view == View.FORM_AD ? "Ex.: 50 óbolos" : "Ex.: 30 óbolos/dia",
                        value -> formPrice = value);
                textBox = area(py + 138, 58, ServicosRules.DESCRIPTION, view == View.FORM_AD ? "O que você faz, onde atende..." : "O que a pessoa vai fazer...");
            }
            case FORM_DIRECT -> textBox = area(py + 100, 76, ServicosRules.ORDER_TEXT, "Conte o que você precisa");
            case FORM_OPEN -> textBox = area(py + 92, 76, ServicosRules.ORDER_TEXT, "Conte o que você precisa");
            default -> {
            }
        }
        if (page == null) request();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        theme = PhoneBridge.theme();
        hits.clear();
        PhoneBridge.drawShell(graphics, px, py, W, H, theme.app());
        drawStatusBar(graphics);
        drawHeader(graphics, mouseX, mouseY);
        switch (view) {
            case LIST -> drawList(graphics, mouseX, mouseY);
            case DETAIL -> drawDetail(graphics, mouseX, mouseY);
            default -> drawForm(graphics, mouseX, mouseY);
        }
        drawTabBar(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawToast(graphics);
        int overlay = PhoneBridge.brightnessOverlay();
        if (overlay != 0) graphics.fill(px, py, px + W, py + H, overlay);
        graphics.fill(px + 55, py + H - 12, px + W - 55, py + H - 9, theme.homeIndicator());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (hit.inside(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view != View.LIST) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = clamp(scroll - (int) Math.round(scrollY * 18), 0, Math.max(0, contentHeight - listHeight));
        return true;
    }

    // ---- servidor ------------------------------------------------------------------------------

    void accept(ServicosPagePayload payload) {
        if (!payload.tab().equals(tab.id) || !payload.filter().equals(filter)) return;
        page = payload;
        if (!payload.message().isEmpty()) showToast(payload.message());
        if (submitting) {
            submitting = false;
            if (payload.ok()) {
                PhoneBridge.click();
                if (view == View.FORM_DIRECT) selected = null;
                clearForm();
                show(View.LIST);
            } else {
                PhoneBridge.error();
            }
        }
        if (view == View.DETAIL) {
            selected = selected == null ? null : find(selected);
            if (selected == null) show(View.LIST);
        }
        scroll = clamp(scroll, 0, Math.max(0, contentHeight - listHeight));
    }

    void refresh() {
        request();
    }

    private void request() {
        ServicosClient.query(tab.id, filter);
    }

    private void act(String action, long id, String category, String title, String text, String price) {
        submitting = view != View.LIST && view != View.DETAIL;
        ServicosClient.action(action, id, category, title, text, price, tab.id, filter);
    }

    @Nullable
    private Row find(Row old) {
        if (page == null) return null;
        for (Row row : page.rows()) {
            if (row.kind() == old.kind() && row.id() == old.id() && row.parent() == old.parent()) return row;
        }
        return null;
    }

    // ---- navegacao -----------------------------------------------------------------------------

    private void switchTab(Tab next) {
        if (next == tab && view == View.LIST) return;
        PhoneBridge.click();
        tab = next;
        filter = defaultFilter(next);
        page = null;
        selected = null;
        scroll = 0;
        clearForm();
        show(View.LIST);
        request();
    }

    private void setFilter(String next) {
        PhoneBridge.click();
        filter = next;
        page = null;
        scroll = 0;
        request();
    }

    private void back() {
        PhoneBridge.back();
        switch (view) {
            case LIST -> PhoneBridge.home();
            case FORM_DIRECT -> show(selected != null ? View.DETAIL : View.LIST);
            default -> {
                clearForm();
                show(View.LIST);
            }
        }
    }

    private void show(View next) {
        view = next;
        rebuildWidgets();
    }

    private void openForm(View form, long id, String category, String title, String price, String text) {
        PhoneBridge.click();
        formId = id;
        formCategory = Categorias.exists(category) ? category : Categorias.all().getFirst().id();
        formTitle = title;
        formPrice = price;
        formText = text;
        show(form);
    }

    private void clearForm() {
        formId = 0;
        formTitle = "";
        formPrice = "";
        formText = "";
    }

    private static String defaultFilter(Tab tab) {
        return tab == Tab.PEDIDOS ? "meus" : "";
    }

    // ---- desenho: topo e rodape ----------------------------------------------------------------

    private void drawStatusBar(GuiGraphics graphics) {
        graphics.fill(px, py, px + W, py + 22, theme.card());
        graphics.drawString(font, LocalTime.now().format(CLOCK), px + 8, py + 7, theme.text(), false);
        int notch = px + W / 2 - 18;
        graphics.fill(notch, py + 4, notch + 36, py + 14, theme.dark() ? 0xFF000000 : 0x22000000);
        String network = PhoneBridge.airplaneMode() ? "Avião" : "5G";
        graphics.drawString(font, network, px + W - 8 - font.width(network), py + 7, theme.text(), false);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = py + 22;
        graphics.fill(px, y, px + W, y + 28, theme.card());
        graphics.fill(px, y + 27, px + W, y + 28, theme.border());
        boolean backHover = inside(mouseX, mouseY, px + 4, y + 4, 20, 20);
        graphics.drawString(font, "<", px + 10, y + 10, backHover ? theme.accent() : theme.text(), false);
        hit(px + 4, y + 4, 20, 20, this::back);

        String title = switch (view) {
            case LIST -> ServicosClient.APP_NAME;
            case DETAIL -> selected == null ? ServicosClient.APP_NAME : switch (selected.kind()) {
                case KIND_ORDER -> "Pedido";
                case KIND_JOB -> "Vaga";
                default -> "Anúncio";
            };
            case FORM_AD -> formId > 0 ? "Editar anúncio" : "Novo anúncio";
            case FORM_DIRECT -> "Pedir serviço";
            case FORM_OPEN -> "Pedido aberto";
            case FORM_JOB -> "Nova vaga";
        };
        graphics.drawString(font, title, px + 24, y + 5, theme.text(), false);
        graphics.drawString(font, tab.label, px + 24, y + 16, theme.muted(), false);

        if (page != null && page.canWork()) {
            boolean working = page.working();
            int pillW = 62;
            int pillX = px + W - pillW - 6;
            int pillY = y + 7;
            graphics.fill(pillX, pillY, pillX + pillW, pillY + 14, working ? GREEN : theme.switchOff());
            String label = working ? "Trabalhando" : "Parado";
            graphics.drawString(font, label, pillX + (pillW - font.width(label)) / 2, pillY + 3, 0xFFFFFFFF, false);
            hit(pillX, pillY, pillW, 14, () -> {
                PhoneBridge.click();
                act("trabalhando", 0, "", "", working ? "0" : "1", "");
            });
        }
    }

    private void drawTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = py + H - 38;
        graphics.fill(px, top, px + W, top + 24, theme.card());
        graphics.fill(px, top, px + W, top + 1, theme.border());
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab each = tabs[i];
            int x0 = px + i * W / tabs.length;
            int x1 = px + (i + 1) * W / tabs.length;
            boolean current = each == tab;
            boolean hover = inside(mouseX, mouseY, x0, top, x1 - x0, 24);
            int color = current ? theme.accent() : hover ? theme.text() : theme.sub();
            graphics.drawString(font, each.label, x0 + (x1 - x0 - font.width(each.label)) / 2, top + 8, color, false);
            if (current) graphics.fill(x0 + 8, top + 20, x1 - 8, top + 22, theme.accent());
            if (each == Tab.PEDIDOS && ServicosClient.badge() > 0) {
                String count = ServicosClient.badge() > 9 ? "9+" : String.valueOf(ServicosClient.badge());
                int bx = x1 - 10;
                graphics.fill(bx - 1, top + 3, bx + font.width(count) + 3, top + 13, RED);
                graphics.drawString(font, count, bx + 1, top + 4, 0xFFFFFFFF, false);
            }
            hit(x0, top, x1 - x0, 24, () -> switchTab(each));
        }
    }

    private void drawToast(GuiGraphics graphics) {
        if (toast.isEmpty() || Util.getMillis() > toastUntil) return;
        List<FormattedCharSequence> lines = font.split(Component.literal(toast), W - 28);
        int count = Math.min(3, lines.size());
        int h = count * 10 + 8;
        int y = py + H - 44 - h;
        graphics.fill(px + 8, y, px + W - 8, y + h, 0xE6111827);
        for (int i = 0; i < count; i++) graphics.drawString(font, lines.get(i), px + 14, y + 5 + i * 10, 0xFFFFFFFF, false);
    }

    private void showToast(String message) {
        toast = message;
        toastUntil = Util.getMillis() + 3500L;
    }

    // ---- desenho: lista ------------------------------------------------------------------------

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        drawSubBar(graphics, mouseX, mouseY);
        List<Action> quick = quickActions();
        int top = py + 68;
        int bottom = py + H - 40 - (quick.isEmpty() ? 0 : 20);
        listHeight = bottom - top;

        if (!quick.isEmpty()) {
            int each = (W - 12 - 4 * (quick.size() - 1)) / quick.size();
            for (int i = 0; i < quick.size(); i++) {
                Action action = quick.get(i);
                button(graphics, mouseX, mouseY, px + 6 + i * (each + 4), bottom + 2, each, 16, action);
            }
        }

        if (page == null) {
            centered(graphics, "Carregando...", top + listHeight / 2 - 4, theme.muted());
            contentHeight = 0;
            return;
        }

        // Candidato so aparece no Perfil, logo abaixo da propria vaga.
        List<Row> rows = new ArrayList<>();
        for (Row row : page.rows()) if (row.kind() != KIND_CANDIDATE || tab == Tab.PERFIL) rows.add(row);
        if (rows.isEmpty()) {
            centered(graphics, emptyText(), top + listHeight / 2 - 4, theme.muted());
            contentHeight = 0;
            return;
        }

        graphics.enableScissor(px, top, px + W, bottom);
        int y = top + 2 - scroll;
        int previousKind = -1;
        for (Row row : rows) {
            if (row.kind() == KIND_CANDIDATE) {
                if (y + 18 > top && y < bottom) drawCandidateRow(graphics, row, y, mouseX, mouseY, top, bottom);
                y += 18;
                continue;
            }
            if (tab == Tab.PERFIL && row.kind() != previousKind) {
                String header = row.kind() == KIND_JOB ? "Minhas vagas" : "Meus anúncios";
                graphics.drawString(font, header, px + 8, y + 2, theme.muted(), false);
                y += 12;
                previousKind = row.kind();
            }
            if (y + ROW_H > top && y < bottom) drawRow(graphics, row, y, mouseX, mouseY, top, bottom);
            y += ROW_H;
        }
        graphics.disableScissor();
        contentHeight = y + scroll - top;

        if (contentHeight > listHeight) {
            int barH = Math.max(12, listHeight * listHeight / contentHeight);
            int barY = top + (listHeight - barH) * scroll / Math.max(1, contentHeight - listHeight);
            graphics.fill(px + W - 3, barY, px + W - 1, barY + barH, theme.border());
        }
    }

    private void drawSubBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = py + 51;
        switch (tab) {
            case BUSCAR, VAGAS -> selector(graphics, mouseX, mouseY, y,
                    filter.isEmpty() ? "Todas as áreas" : Categorias.label(filter), true, this::setFilter, filter);
            case PEDIDOS -> {
                int half = (W - 12) / 2;
                segment(graphics, mouseX, mouseY, px + 6, y, half, "Meus pedidos", "meus".equals(filter), () -> setFilter("meus"));
                String received = ServicosClient.badge() > 0 ? "Recebidos (" + ServicosClient.badge() + ")" : "Recebidos";
                segment(graphics, mouseX, mouseY, px + 6 + half, y, half, received, "recebidos".equals(filter), () -> setFilter("recebidos"));
            }
            case PERFIL -> {
                int ads = 0;
                int jobs = 0;
                if (page != null) {
                    for (Row row : page.rows()) {
                        if (row.kind() == KIND_AD) ads++;
                        if (row.kind() == KIND_JOB) jobs++;
                    }
                }
                graphics.drawString(font, "Anúncios " + ads + "/" + ServicosRules.MAX_ADS + "  ·  Vagas " + jobs + "/" + ServicosRules.MAX_JOBS,
                        px + 8, y + 4, theme.sub(), false);
            }
        }
    }

    private List<Action> quickActions() {
        return switch (tab) {
            case BUSCAR -> List.of(new Action("Não achou? Pedido aberto", theme.accent(),
                    () -> openForm(View.FORM_OPEN, 0, filter, "", "", "")));
            case PEDIDOS -> List.of(new Action("+ Novo pedido aberto", theme.accent(),
                    () -> openForm(View.FORM_OPEN, 0, "", "", "", "")));
            case VAGAS -> List.of(new Action("+ Publicar vaga", theme.accent(),
                    () -> openForm(View.FORM_JOB, 0, filter, "", "", "")));
            case PERFIL -> List.of(
                    new Action("+ Anúncio", theme.accent(), () -> openForm(View.FORM_AD, 0, "", "", "", "")),
                    new Action("+ Vaga", theme.accent(), () -> openForm(View.FORM_JOB, 0, "", "", "", "")));
        };
    }

    private String emptyText() {
        return switch (tab) {
            case BUSCAR -> "Ninguém anunciando aqui ainda.";
            case PEDIDOS -> "recebidos".equals(filter) ? "Nenhum pedido para você." : "Você não fez pedidos.";
            case VAGAS -> "Nenhuma vaga aberta.";
            case PERFIL -> "Anuncie o que você faz.";
        };
    }

    private void drawRow(GuiGraphics graphics, Row row, int y, int mouseX, int mouseY, int top, int bottom) {
        int x0 = px + 6;
        int x1 = px + W - 6;
        boolean hover = mouseY >= top && mouseY < bottom && inside(mouseX, mouseY, x0, y, x1 - x0, ROW_H - 3);
        graphics.fill(x0, y, x1, y + ROW_H - 3, hover ? theme.secondaryCard() : theme.card());
        int tx = x0 + 6;
        int width = x1 - x0 - 12;

        switch (row.kind()) {
            case KIND_ORDER -> {
                String kind = row.has(DIRECT) ? " · direto" : " · aberto";
                String status = row.subtitle();
                int statusW = font.width(status);
                graphics.drawString(font, trim(row.title() + kind, width - statusW - 6), tx, y + 4, theme.text(), false);
                graphics.drawString(font, status, x1 - 6 - statusW, y + 4, statusColor(status), false);
                graphics.drawString(font, trim(row.detail(), width), tx, y + 14, theme.sub(), false);
                String who = (row.has(INCOMING) ? "De " : "Com ") + row.person();
                String when = ago(row.time());
                graphics.drawString(font, trim(who, width - font.width(when) - 6), tx, y + 24, theme.muted(), false);
                graphics.drawString(font, when, x1 - 6 - font.width(when), y + 24, theme.muted(), false);
            }
            case KIND_JOB -> {
                graphics.drawString(font, trim(row.title(), width - 10), tx, y + 4, theme.text(), false);
                dot(graphics, x1 - 9, y + 6, row);
                graphics.drawString(font, trim(row.person(), width), tx, y + 14, theme.sub(), false);
                String right = row.has(MINE) ? row.count() + " candidato(s)" : row.has(APPLIED) ? "Candidatado" : row.subtitle();
                graphics.drawString(font, trim(row.price(), width - font.width(right) - 6), tx, y + 24, theme.accent(), false);
                graphics.drawString(font, right, x1 - 6 - font.width(right), y + 24, theme.muted(), false);
            }
            default -> {
                String name = row.person() + (row.has(REGISTERED) ? " ✔" : "");
                graphics.drawString(font, trim(name, width - 10), tx, y + 4, theme.text(), false);
                dot(graphics, x1 - 9, y + 6, row);
                graphics.drawString(font, trim(row.title(), width), tx, y + 14, theme.sub(), false);
                String area = row.subtitle();
                graphics.drawString(font, trim(row.price(), width - font.width(area) - 6), tx, y + 24, theme.accent(), false);
                graphics.drawString(font, area, x1 - 6 - font.width(area), y + 24, theme.muted(), false);
            }
        }
        if (y >= top - ROW_H && y < bottom) {
            int hy = Math.max(y, top);
            int hh = Math.min(y + ROW_H - 3, bottom) - hy;
            if (hh > 0) hit(x0, hy, x1 - x0, hh, () -> {
                PhoneBridge.click();
                selected = row;
                show(View.DETAIL);
            });
        }
    }

    /** Um candidato, recuado sob a vaga, com o botao de conversa. */
    private void drawCandidateRow(GuiGraphics graphics, Row row, int y, int mouseX, int mouseY, int top, int bottom) {
        int x0 = px + 16;
        int x1 = px + W - 6;
        graphics.fill(x0, y, x1, y + 15, theme.secondaryCard());
        graphics.drawString(font, trim(row.person() + onlineSuffix(row), x1 - x0 - 58), x0 + 4, y + 4, theme.text(), false);
        if (!row.has(CAN_CHAT)) return;
        int bx = x1 - 52;
        boolean visible = y >= top && y + 15 <= bottom;
        boolean hover = visible && inside(mouseX, mouseY, bx, y + 1, 50, 13);
        graphics.fill(bx, y + 1, bx + 50, y + 14, hover ? brighten(theme.accent()) : theme.accent());
        String label = "Conversar";
        graphics.drawString(font, trim(label, 46), bx + (50 - font.width(trim(label, 46))) / 2, y + 4, 0xFFFFFFFF, false);
        if (visible) hit(bx, y + 1, 50, 13, chat(row, "").onClick());
    }

    // ---- desenho: detalhe ----------------------------------------------------------------------

    private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        Row row = selected;
        if (row == null) return;
        int x = px + 10;
        int w = W - 20;
        int y = py + 56;
        List<Action> actions = new ArrayList<>();

        switch (row.kind()) {
            case KIND_ORDER -> {
                y = line(graphics, row.title() + (row.has(DIRECT) ? " · pedido direto" : " · pedido aberto"), x, y, w, theme.text());
                y = line(graphics, "Situação: " + row.subtitle(), x, y, w, statusColor(row.subtitle()));
                String who;
                if (row.has(INCOMING)) who = "Cliente: " + row.person() + onlineSuffix(row);
                else if (row.nick().isEmpty()) who = "Esperando alguém da área aceitar";
                else who = "Profissional: " + row.person() + onlineSuffix(row);
                y = line(graphics, who, x, y, w, theme.sub());
                y = paragraph(graphics, "\"" + row.detail() + "\"", x, y + 4, w, 8, theme.text());
                if (row.has(CAN_ACCEPT)) actions.add(new Action("Aceitar e conversar", GREEN, () -> act("aceitar", row.id(), "", "", "", "")));
                if (row.has(CAN_REFUSE)) actions.add(new Action("Recusar", RED, () -> act("recusar", row.id(), "", "", "", "")));
                if (row.has(CAN_COMPLETE)) actions.add(new Action("Marcar como concluído", theme.accent(), () -> act("concluir", row.id(), "", "", "", "")));
                if (row.has(CAN_CHAT)) actions.add(chat(row, ""));
                if (row.has(CAN_CANCEL)) actions.add(new Action("Cancelar pedido", RED, () -> act("cancelar", row.id(), "", "", "", "")));
            }
            case KIND_JOB -> {
                y = line(graphics, row.title(), x, y, w, theme.text());
                y = line(graphics, row.subtitle() + " · por " + row.person(), x, y, w, theme.sub());
                y = line(graphics, "Salário: " + row.price(), x, y, w, theme.accent());
                y = paragraph(graphics, row.detail(), x, y + 4, w, 5, theme.text());
                if (row.has(MINE)) {
                    // Os candidatos so vem na pagina do Perfil; na aba Vagas o numero aponta para la.
                    boolean elsewhere = row.count() > 3 || (row.count() > 0 && tab != Tab.PERFIL);
                    y = line(graphics, row.count() + " candidato(s)" + (elsewhere ? " · veja no Perfil" : ""),
                            x, y + 4, w, theme.muted());
                    drawCandidates(graphics, mouseX, mouseY, row, x, y, w);
                }
                if (row.has(CAN_APPLY)) actions.add(new Action("Candidatar-se", GREEN, () -> act("candidatar", row.id(), "", "", "", "")));
                if (row.has(APPLIED) && row.has(CAN_CHAT)) actions.add(chat(row, ""));
                if (row.has(CAN_REMOVE)) actions.add(new Action(row.has(MINE) ? "Encerrar vaga" : "Remover (staff)", RED,
                        () -> act("fechar_vaga", row.id(), "", "", "", "")));
            }
            default -> {
                y = line(graphics, row.person() + onlineSuffix(row), x, y, w, theme.text());
                if (row.has(REGISTERED)) y = line(graphics, "✔ Profissional registrado", x, y, w, theme.accent());
                y = line(graphics, row.title(), x, y + 2, w, theme.text());
                y = line(graphics, row.subtitle() + " · " + row.price(), x, y, w, theme.accent());
                y = paragraph(graphics, row.detail(), x, y + 4, w, 7, theme.sub());
                if (row.has(CAN_ORDER)) actions.add(new Action("Pedir serviço", GREEN,
                        () -> openForm(View.FORM_DIRECT, row.id(), row.category(), "", "", "")));
                if (row.has(CAN_CHAT)) actions.add(chat(row, ""));
                if (row.has(CAN_EDIT)) actions.add(new Action("Editar", theme.accent(),
                        () -> openForm(View.FORM_AD, row.id(), row.category(), row.title(), row.price(), row.detail())));
                if (row.has(CAN_REMOVE)) actions.add(new Action(row.has(MINE) ? "Remover anúncio" : "Remover (staff)", RED,
                        () -> act("remover_anuncio", row.id(), "", "", "", "")));
            }
        }
        stack(graphics, mouseX, mouseY, actions);
    }

    private int drawCandidates(GuiGraphics graphics, int mouseX, int mouseY, Row job, int x, int y, int w) {
        if (page == null) return y;
        int shown = 0;
        for (Row candidate : page.rows()) {
            if (candidate.kind() != KIND_CANDIDATE || candidate.parent() != job.id() || shown >= 3) continue;
            graphics.fill(x, y, x + w, y + 14, theme.card());
            graphics.drawString(font, trim(candidate.person() + onlineSuffix(candidate), w - 56), x + 4, y + 3, theme.text(), false);
            if (candidate.has(CAN_CHAT)) {
                button(graphics, mouseX, mouseY, x + w - 50, y + 1, 48, 12, chat(candidate, ""));
            }
            y += 16;
            shown++;
        }
        return y;
    }

    private Action chat(Row row, String draft) {
        return new Action("Conversar", theme.accent(), () -> {
            PhoneBridge.click();
            if (!PhoneBridge.openChat(row.nick(), draft)) showToast("Não consegui abrir a conversa.");
        });
    }

    private String onlineSuffix(Row row) {
        if (row.has(WORKING)) return " · trabalhando";
        return row.has(ONLINE) ? " · online" : " · offline";
    }

    // ---- desenho: formularios ------------------------------------------------------------------

    private void drawForm(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = px + 10;
        int w = W - 20;
        List<Action> actions = new ArrayList<>();
        switch (view) {
            case FORM_AD, FORM_JOB -> {
                label(graphics, "Área", x, py + 56);
                selector(graphics, mouseX, mouseY, py + 64, Categorias.label(formCategory), false, value -> formCategory = value, formCategory);
                label(graphics, "Título", x, py + 76);
                fieldBackground(graphics, py + 86);
                label(graphics, view == View.FORM_AD ? "Preço" : "Salário", x, py + 102);
                fieldBackground(graphics, py + 112);
                label(graphics, "Descrição", x, py + 128);
                boolean ad = view == View.FORM_AD;
                actions.add(new Action(ad ? (formId > 0 ? "Salvar anúncio" : "Publicar anúncio") : "Publicar vaga", GREEN,
                        () -> act(ad ? "anunciar" : "vaga", formId, formCategory, formTitle, currentText(), formPrice)));
            }
            case FORM_DIRECT -> {
                Row ad = selected;
                int y = py + 56;
                if (ad != null) {
                    y = line(graphics, "Para " + ad.person(), x, y, w, theme.text());
                    y = line(graphics, ad.title() + " · " + ad.price(), x, y, w, theme.sub());
                }
                label(graphics, "O que você precisa?", x, py + 90);
                graphics.drawString(font, trim("Você cai na conversa com a pessoa.", w), x, py + 180, theme.muted(), false);
                actions.add(new Action("Enviar pedido", GREEN,
                        () -> act("pedir", formId, "", "", currentText(), "")));
            }
            case FORM_OPEN -> {
                label(graphics, "Área", x, py + 56);
                selector(graphics, mouseX, mouseY, py + 64, Categorias.label(formCategory), false, value -> formCategory = value, formCategory);
                label(graphics, "O que você precisa?", x, py + 82);
                paragraph(graphics, "Quem está trabalhando nessa área recebe o pedido no celular. O primeiro que aceitar fala com você.",
                        x, py + 172, w, 3, theme.muted());
                actions.add(new Action("Publicar pedido", GREEN,
                        () -> act("pedido_aberto", 0, formCategory, "", currentText(), "")));
            }
            default -> {
            }
        }
        stack(graphics, mouseX, mouseY, actions);
    }

    private String currentText() {
        if (textBox != null) formText = textBox.getValue();
        return formText;
    }

    private EditBox field(int y, int max, String value, String hint, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, px + 14, y + 3, W - 28, 10, Component.literal(hint));
        box.setMaxLength(max);
        box.setBordered(false);
        box.setTextColor(theme.text());
        box.setHint(Component.literal(hint));
        box.setValue(value);
        box.setResponder(responder);
        return addRenderableWidget(box);
    }

    private MultiLineEditBox area(int y, int height, int max, String placeholder) {
        MultiLineEditBox box = new MultiLineEditBox(font, px + 10, y, W - 20, height, Component.literal(placeholder),
                Component.literal(placeholder));
        box.setCharacterLimit(max);
        box.setValue(formText);
        box.setValueListener(value -> formText = value);
        return addRenderableWidget(box);
    }

    private void fieldBackground(GuiGraphics graphics, int y) {
        graphics.fill(px + 10, y, px + W - 10, y + 15, theme.input());
        graphics.fill(px + 10, y + 14, px + W - 10, y + 15, theme.border());
    }

    private void label(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawString(font, text, x, y, theme.muted(), false);
    }

    // ---- pecas ---------------------------------------------------------------------------------

    /** "< Area >": as setas trocam; com {@code all}, a primeira opcao e "todas". */
    private void selector(GuiGraphics graphics, int mouseX, int mouseY, int y, String label, boolean all,
                          java.util.function.Consumer<String> onChange, String current) {
        int x0 = px + 6;
        int x1 = px + W - 6;
        graphics.fill(x0, y, x1, y + 14, theme.input());
        graphics.drawString(font, "<", x0 + 5, y + 3, inside(mouseX, mouseY, x0, y, 18, 14) ? theme.accent() : theme.text(), false);
        graphics.drawString(font, ">", x1 - 10, y + 3, inside(mouseX, mouseY, x1 - 18, y, 18, 14) ? theme.accent() : theme.text(), false);
        String shown = trim(label, x1 - x0 - 36);
        graphics.drawString(font, shown, x0 + (x1 - x0 - font.width(shown)) / 2, y + 3, theme.text(), false);
        hit(x0, y, 18, 14, () -> onChange.accept(cycle(current, all, -1)));
        hit(x1 - 18, y, 18, 14, () -> onChange.accept(cycle(current, all, 1)));
    }

    private static String cycle(String current, boolean all, int step) {
        List<String> ids = new ArrayList<>();
        if (all) ids.add("");
        for (Categorias.Categoria categoria : Categorias.all()) ids.add(categoria.id());
        int index = Math.max(0, ids.indexOf(current));
        return ids.get(Math.floorMod(index + step, ids.size()));
    }

    private void segment(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String label, boolean on, Runnable action) {
        graphics.fill(x, y, x + w, y + 14, on ? theme.accent() : theme.input());
        String shown = trim(label, w - 6);
        int color = on ? 0xFFFFFFFF : inside(mouseX, mouseY, x, y, w, 14) ? theme.text() : theme.sub();
        graphics.drawString(font, shown, x + (w - font.width(shown)) / 2, y + 3, color, false);
        hit(x, y, w, 14, action);
    }

    /** Botoes empilhados de baixo para cima, logo acima das abas. */
    private void stack(GuiGraphics graphics, int mouseX, int mouseY, List<Action> actions) {
        int y = py + H - 42 - actions.size() * 18;
        for (Action action : actions) {
            button(graphics, mouseX, mouseY, px + 8, y, W - 16, 16, action);
            y += 18;
        }
    }

    private void button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Action action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int color = hover ? brighten(action.color()) : action.color();
        graphics.fill(x, y, x + w, y + h, color);
        String shown = trim(action.label(), w - 6);
        graphics.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        hit(x, y, w, h, action.onClick());
    }

    private void dot(GuiGraphics graphics, int x, int y, Row row) {
        int color = row.has(WORKING) ? GREEN : row.has(ONLINE) ? YELLOW : theme.muted();
        graphics.fill(x, y, x + 5, y + 5, color);
    }

    private int line(GuiGraphics graphics, String text, int x, int y, int w, int color) {
        graphics.drawString(font, trim(text, w), x, y, color, false);
        return y + 11;
    }

    private int paragraph(GuiGraphics graphics, String text, int x, int y, int w, int maxLines, int color) {
        if (text == null || text.isBlank()) return y;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), w);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void centered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, px + (W - font.width(text)) / 2, y, color, false);
    }

    private int statusColor(String status) {
        return switch (status) {
            case "Aceito" -> GREEN;
            case "Aguardando" -> YELLOW;
            case "Concluído" -> theme.accent();
            default -> theme.muted();
        };
    }

    private String trim(String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int brighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 24);
        int b = Math.min(255, (color & 0xFF) + 24);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String ago(long time) {
        long minutes = Math.max(0, System.currentTimeMillis() - time) / 60_000L;
        if (minutes < 1) return "agora";
        if (minutes < 60) return minutes + " min";
        if (minutes < 1440) return minutes / 60 + " h";
        return minutes / 1440 + " d";
    }
}
