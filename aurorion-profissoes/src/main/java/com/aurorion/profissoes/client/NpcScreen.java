package com.aurorion.profissoes.client;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.profissoes.network.NpcActionPayload;
import com.aurorion.profissoes.network.NpcScreenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/**
 * Tela de um NPC de oficio: um menu inicial e tres abas (loja, servicos, conversa).
 *
 * <p>As abas trocam sem falar com o servidor — o pacote ja traz tudo. So a escolha viaja, e a
 * resposta chega como um pacote novo na mesma aba, com o aviso do que aconteceu e estoque/saldo
 * atualizados. Enquanto a resposta nao chega os botoes ficam desligados, para um clique duplo nao
 * virar duas compras.
 */
public final class NpcScreen extends NpcPanelScreen {
    private static final int WIDTH = 380, FOOTER = 34, MENU_ROW = 28, TRADE_ROW = 32, SERVICE_ROW = 44, LINE = 11;
    private static final int BUTTON_W = 72, PAD = 10;
    private record Option(String label, Runnable action) {}

    private final NpcScreenPayload data;
    private int tab, scroll, visible, waitTicks;
    private boolean waiting, closed;
    private List<FormattedCharSequence> intro = List.of(), notice = List.of(), talk = List.of();

    public NpcScreen(NpcScreenPayload data, int scroll) {
        super(Component.literal(data.title()), Component.literal(data.eyebrow()), ProfessionThemes.OFFICE);
        this.data = data;
        this.tab = data.tab();
        this.scroll = scroll;
    }

    public int tab() { return tab; }
    public int scroll() { return scroll; }

    // --- layout --------------------------------------------------------------------------------

    @Override protected PanelLayout resolveLayout() {
        int inner = Math.min(WIDTH, Math.max(240, width - 24)) - 2 * PAD - 12;
        String introText = switch (tab) {
            case NpcScreenPayload.TAB_SHOP -> "Loja • escolha uma oferta";
            case NpcScreenPayload.TAB_SERVICES -> "Serviços • o pagamento é cobrado ao solicitar";
            case NpcScreenPayload.TAB_TALK -> "Conversa";
            default -> data.greeting();
        };
        intro = limit(font.split(Component.literal(introText), inner), 3);
        notice = data.notice().isEmpty() ? List.of() : limit(font.split(Component.literal(data.notice()), inner), 2);
        talk = new ArrayList<>();
        for (String line : data.dialogue()) {
            talk.addAll(font.split(Component.literal(line), inner));
            talk.add(FormattedCharSequence.EMPTY);
        }
        if (!talk.isEmpty()) talk.removeLast();

        int header = Math.max(60, 46 + intro.size() * LINE + (notice.isEmpty() ? 0 : 4 + notice.size() * LINE) + 6);
        int room = Math.max(40, height - header - FOOTER - 30);
        int total = total(), row = rowHeight();
        visible = Math.max(1, Math.min(total, room / row));
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - visible));
        int body = switch (tab) {
            case NpcScreenPayload.TAB_HOME -> Math.max(1, options().size()) * MENU_ROW + 12;
            case NpcScreenPayload.TAB_TALK -> talk.isEmpty() ? 30 : visible * LINE + 16;
            default -> total == 0 ? 30 : visible * row;
        };
        return new PanelLayout(WIDTH, header, body, FOOTER);
    }

    private int rowHeight() {
        return switch (tab) {
            case NpcScreenPayload.TAB_SHOP -> TRADE_ROW;
            case NpcScreenPayload.TAB_SERVICES -> SERVICE_ROW;
            case NpcScreenPayload.TAB_TALK -> LINE;
            default -> MENU_ROW;
        };
    }

    private int total() {
        return switch (tab) {
            case NpcScreenPayload.TAB_SHOP -> data.trades().size();
            case NpcScreenPayload.TAB_SERVICES -> data.services().size();
            case NpcScreenPayload.TAB_TALK -> talk.size();
            default -> options().size();
        };
    }

    private List<Option> options() {
        var options = new ArrayList<Option>(3);
        if (!data.trades().isEmpty()) options.add(new Option("Comprar itens", () -> switchTab(NpcScreenPayload.TAB_SHOP)));
        if (!data.services().isEmpty()) options.add(new Option("Serviços e cuidados", () -> switchTab(NpcScreenPayload.TAB_SERVICES)));
        if (data.admDialogue()) options.add(new Option("Conversar", () -> send(NpcActionPayload.TALK, 0)));
        else if (!data.dialogue().isEmpty()) options.add(new Option("Conversar", () -> switchTab(NpcScreenPayload.TAB_TALK)));
        return options;
    }

    // --- widgets -------------------------------------------------------------------------------

    @Override protected void initPanel() {
        int buttonX = panelRight() - PAD - BUTTON_W;
        switch (tab) {
            case NpcScreenPayload.TAB_HOME -> {
                var options = options();
                for (int i = 0; i < options.size(); i++) {
                    var option = options.get(i);
                    var button = panelButton(panelX + panelWidth / 2 - 100, bodyTop() + 8 + i * MENU_ROW, 200, 22,
                            Component.literal(option.label()), option.action()::run);
                    button.active = !waiting;
                    addRenderableWidget(button);
                }
            }
            case NpcScreenPayload.TAB_SHOP -> {
                for (int i = 0; i < visible && scroll + i < data.trades().size(); i++) {
                    int index = scroll + i;
                    var row = data.trades().get(index);
                    var button = panelButton(buttonX, bodyTop() + i * TRADE_ROW + 6, BUTTON_W, 20,
                            Component.literal("Comprar"), () -> send(NpcActionPayload.TRADE, index));
                    button.active = row.enabled() && !waiting;
                    button.setTooltip(Tooltip.create(Component.literal(row.enabled() ? "Preço: " + row.price() : row.reason())));
                    addRenderableWidget(button);
                }
            }
            case NpcScreenPayload.TAB_SERVICES -> {
                for (int i = 0; i < visible && scroll + i < data.services().size(); i++) {
                    int index = scroll + i;
                    var row = data.services().get(index);
                    var button = panelButton(buttonX, bodyTop() + i * SERVICE_ROW + 12, BUTTON_W, 20,
                            Component.literal("Solicitar"), () -> send(NpcActionPayload.SERVICE, index));
                    button.active = row.enabled() && !waiting;
                    button.setTooltip(Tooltip.create(Component.literal(row.enabled() ? "Custo: " + row.cost() : row.reason())));
                    addRenderableWidget(button);
                }
            }
            default -> { }
        }
        if (tab == NpcScreenPayload.TAB_HOME) {
            addRenderableWidget(panelButton(panelX + panelWidth / 2 - 50, footerTop() + 6, 100, 22,
                    Component.literal("Fechar"), this::onClose));
        } else {
            addRenderableWidget(panelButton(panelX + PAD, footerTop() + 6, 100, 22,
                    Component.literal("Voltar"), () -> switchTab(NpcScreenPayload.TAB_HOME)));
            addRenderableWidget(panelButton(panelRight() - PAD - 100, footerTop() + 6, 100, 22,
                    Component.literal("Fechar"), this::onClose));
        }
    }

    // --- desenho -------------------------------------------------------------------------------

    @Override protected void renderPanelBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tab != NpcScreenPayload.TAB_SHOP && tab != NpcScreenPayload.TAB_SERVICES) return;
        int row = rowHeight();
        for (int i = 0; i < visible && scroll + i < total(); i++) {
            int y = bodyTop() + i * row + 2;
            boolean enabled = tab == NpcScreenPayload.TAB_SHOP ? data.trades().get(scroll + i).enabled()
                    : data.services().get(scroll + i).enabled();
            boolean hovered = mouseX >= panelX + 7 && mouseX < panelRight() - 7 && mouseY >= y && mouseY < y + row - 4;
            drawRow(graphics, y, row - 4, hovered, i % 2 != 0, enabled ? theme.text().accent() : theme.text().muted());
        }
        drawScrollbar(graphics, scroll, visible, total());
    }

    @Override protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int center = panelX + panelWidth / 2;
        int y = panelY + 44;
        for (var line : intro) { graphics.drawCenteredString(font, line, center, y, theme.text().body()); y += LINE; }
        if (!notice.isEmpty()) {
            y += 4;
            int color = data.noticeError() ? theme.text().warning() : theme.text().accent();
            for (var line : notice) { graphics.drawCenteredString(font, line, center, y, color); y += LINE; }
        }
        switch (tab) {
            case NpcScreenPayload.TAB_HOME -> {
                if (options().isEmpty()) empty(graphics, "Este NPC não tem nada a oferecer agora.");
            }
            case NpcScreenPayload.TAB_SHOP -> renderTrades(graphics, mouseX, mouseY);
            case NpcScreenPayload.TAB_SERVICES -> renderServices(graphics);
            case NpcScreenPayload.TAB_TALK -> {
                if (talk.isEmpty()) { empty(graphics, "Não há nada a conversar."); return; }
                for (int i = 0; i < visible && scroll + i < talk.size(); i++)
                    graphics.drawString(font, talk.get(scroll + i), panelX + PAD + 6, bodyTop() + 8 + i * LINE, theme.text().body(), false);
                drawScrollbar(graphics, scroll, visible, talk.size());
            }
            default -> { }
        }
    }

    private void renderTrades(GuiGraphics graphics, int mouseX, int mouseY) {
        if (data.trades().isEmpty()) { empty(graphics, "Nada à venda."); return; }
        int textX = panelX + PAD + 26, textWidth = panelWidth - 2 * PAD - 26 - BUTTON_W - 8;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < visible && scroll + i < data.trades().size(); i++) {
            var row = data.trades().get(scroll + i);
            int y = bodyTop() + i * TRADE_ROW;
            int iconX = panelX + PAD + 4, iconY = y + 8;
            graphics.renderItem(row.result(), iconX, iconY);
            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) hovered = row.result();

            String name = row.amount() + "× " + row.result().getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), textX, y + 6, theme.text().accent(), false);
            String detail = row.enabled()
                    ? row.price() + (row.stock() >= 0 ? " • estoque: " + row.stock() : "")
                    : row.reason();
            graphics.drawString(font, font.plainSubstrByWidth(detail, textWidth), textX, y + 18,
                    row.enabled() ? theme.text().body() : theme.text().warning(), false);
        }
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void renderServices(GuiGraphics graphics) {
        if (data.services().isEmpty()) { empty(graphics, "Nenhum serviço disponível."); return; }
        int textX = panelX + PAD + 6, textWidth = panelWidth - 2 * PAD - 6 - BUTTON_W - 8;
        for (int i = 0; i < visible && scroll + i < data.services().size(); i++) {
            var row = data.services().get(scroll + i);
            int y = bodyTop() + i * SERVICE_ROW;
            graphics.drawString(font, font.plainSubstrByWidth(row.title(), textWidth), textX, y + 6, theme.text().accent(), false);
            graphics.drawString(font, font.plainSubstrByWidth(row.detail(), textWidth), textX, y + 18, theme.text().muted(), false);
            String status = row.enabled() ? "Custo: " + row.cost() : row.reason();
            graphics.drawString(font, font.plainSubstrByWidth(status, textWidth), textX, y + 30,
                    row.enabled() ? theme.text().body() : theme.text().warning(), false);
        }
    }

    private void empty(GuiGraphics graphics, String text) {
        graphics.drawCenteredString(font, Component.literal(text), panelX + panelWidth / 2, bodyTop() + 11, theme.text().muted());
    }

    // --- interacao -----------------------------------------------------------------------------

    private void switchTab(int next) {
        tab = next;
        scroll = 0;
        rebuildWidgets();
    }

    private void send(String kind, int index) {
        if (waiting) return;
        waiting = true;
        waitTicks = 0;
        PacketDistributor.sendToServer(new NpcActionPayload(data.token(), kind, index));
        rebuildWidgets();
    }

    /** Sem resposta em 2 s (sessao expirada, NPC longe), a tela volta a aceitar cliques. */
    @Override public void tick() {
        super.tick();
        if (waiting && ++waitTicks > 40) { waiting = false; rebuildWidgets(); }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (tab == NpcScreenPayload.TAB_HOME) return false;
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, Math.max(0, total() - visible));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override public void onClose() {
        if (!closed) PacketDistributor.sendToServer(new NpcActionPayload(data.token(), NpcActionPayload.CLOSE, 0));
        closed = true;
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private static <T> List<T> limit(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }
}
