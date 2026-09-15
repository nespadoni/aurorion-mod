package com.aurorion.limbo.client;

import com.aurorion.core.client.gui.AurorionButton;
import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.core.text.TimeFormat;
import com.aurorion.limbo.network.BeginRescuePayload;
import com.aurorion.limbo.network.OpenOraclePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lista viva do Oraculo. O ADM conduz a conversa; esta tela mostra somente os dados que mudam.
 *
 * <p>A interface desabilita escolhas impossiveis por cortesia. O servidor reconfere vidas,
 * distancia, alvo e exilio quando recebe {@link BeginRescuePayload}.
 */
public final class OracleScreen extends NpcPanelScreen {
    private static final int IDEAL_WIDTH = 366;
    private static final int ROW_HEIGHT = 34;
    private static final int HEADER_HEIGHT = 60;
    private static final int FOOTER_HEIGHT = 46;
    private static final int EMPTY_BODY_HEIGHT = 60;
    private static final int MAX_VISIBLE = 8;
    private static final int OPEN_BUTTON_WIDTH = 116;
    private static final int PAD = 8;
    private static final long URGENT_MILLIS = 6L * 3_600_000L;

    private final OpenOraclePayload data;
    private int scroll;
    private int visible;

    public OracleScreen(OpenOraclePayload data) {
        super(Component.translatable("aurorion_limbo.oraculo.titulo"),
                Component.translatable("aurorion_limbo.oraculo.eyebrow"), LimboNpcThemes.ORACLE);
        this.data = data;
    }

    @Override
    protected PanelLayout resolveLayout() {
        int room = Math.max(1, (height - HEADER_HEIGHT - FOOTER_HEIGHT - 36) / ROW_HEIGHT);
        visible = Math.min(data.exiles().size(), Math.min(MAX_VISIBLE, room));
        scroll = Mth.clamp(scroll, 0, Math.max(0, data.exiles().size() - visible));
        int body = data.exiles().isEmpty() ? EMPTY_BODY_HEIGHT : visible * ROW_HEIGHT;
        return new PanelLayout(IDEAL_WIDTH, HEADER_HEIGHT, body, FOOTER_HEIGHT);
    }

    @Override
    protected void initPanel() {
        boolean canAfford = canAfford();
        Component reason = canAfford
                ? Component.translatable("aurorion_limbo.oraculo.custo", data.cost())
                : Component.translatable("aurorion_limbo.oraculo.sem_vidas", data.minLives());

        for (int i = 0; i < visible; i++) {
            OpenOraclePayload.Entry entry = entryAt(i);
            int rowY = bodyTop() + i * ROW_HEIGHT;
            AurorionButton open = panelButton(
                    panelRight() - PAD - OPEN_BUTTON_WIDTH, rowY + 7,
                    OPEN_BUTTON_WIDTH, 20,
                    Component.translatable("aurorion_limbo.oraculo.abrir"), () -> choose(entry));
            open.active = canAfford;
            open.setTooltip(Tooltip.create(reason));
            addRenderableWidget(open);
        }

        addRenderableWidget(panelButton(width / 2 - 62, footerTop() + 12, 124, 20,
                Component.translatable("aurorion_limbo.oraculo.fechar"), this::onClose));
    }

    @Override
    protected Component headerSubtitle() {
        return Component.translatable("aurorion_limbo.oraculo.suas_vidas",
                data.viewerLives(), data.minLives());
    }

    @Override
    protected int headerSubtitleColor() {
        return canAfford() ? theme.text().accent() : theme.text().warning();
    }

    @Override
    protected void renderPanelBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (data.exiles().isEmpty()) return;
        for (int i = 0; i < visible; i++) {
            OpenOraclePayload.Entry entry = entryAt(i);
            int y = bodyTop() + i * ROW_HEIGHT + 3;
            boolean hovered = mouseX >= panelX + 7 && mouseX <= panelRight() - 7
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 6;
            drawRow(graphics, y, ROW_HEIGHT - 6, hovered, i % 2 != 0,
                    urgent(entry) ? theme.text().warning() : theme.text().accent());
        }
        drawScrollbar(graphics, scroll, visible, data.exiles().size());
    }

    @Override
    protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int center = panelX + panelWidth / 2;
        if (data.exiles().isEmpty()) {
            graphics.drawCenteredString(font, theme.display(Component.translatable(
                            "aurorion_limbo.oraculo.vazio")),
                    center, bodyTop() + 15, theme.text().body());
            graphics.drawCenteredString(font, Component.translatable(
                            "aurorion_limbo.oraculo.vazio.dica"),
                    center, bodyTop() + 34, theme.text().muted());
            return;
        }

        int tagRight = panelRight() - PAD - OPEN_BUTTON_WIDTH - 8;
        for (int i = 0; i < visible; i++) {
            OpenOraclePayload.Entry entry = entryAt(i);
            int rowY = bodyTop() + i * ROW_HEIGHT;
            Component name = theme.display(Component.literal(entry.name()));
            graphics.drawString(font, name, panelX + 16, rowY + 7, theme.text().body(), false);

            Component deadline = Component.translatable("aurorion_limbo.oraculo.prazo",
                    TimeFormat.duration(entry.remainingMillis()));
            graphics.drawString(font, deadline, panelX + 16, rowY + 20,
                    urgent(entry) ? theme.text().warning() : theme.text().accent(), false);

            int right = tagRight;
            if (!entry.online()) {
                right = drawTag(graphics, Component.translatable("aurorion_limbo.oraculo.offline"),
                        right, rowY + 11, theme.text().muted());
            }
            if (entry.attempted()) {
                drawTag(graphics, Component.translatable("aurorion_limbo.oraculo.procurado"),
                        right, rowY + 11, theme.text().muted());
            }
        }
    }

    private boolean canAfford() {
        return data.viewerLives() >= data.minLives();
    }

    private boolean urgent(OpenOraclePayload.Entry entry) {
        return entry.remainingMillis() < URGENT_MILLIS;
    }

    private OpenOraclePayload.Entry entryAt(int index) {
        return data.exiles().get(Mth.clamp(scroll + index, 0, data.exiles().size() - 1));
    }

    private void choose(OpenOraclePayload.Entry entry) {
        PacketDistributor.sendToServer(new BeginRescuePayload(entry.id()));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0,
                Math.max(0, data.exiles().size() - visible));
        if (next != scroll) {
            scroll = next;
            // Cada botao captura o UUID da linha. Recriar evita que uma linha rolada mantenha o alvo
            // que ocupava aquela posicao antes.
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
