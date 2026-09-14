package com.aurorion.limbo.client;

import com.aurorion.core.text.TimeFormat;
import com.aurorion.limbo.network.BeginRescuePayload;
import com.aurorion.limbo.network.OpenOraclePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A tela do Oraculo: quem esta no Limbo, e quanto custa ir buscar.
 *
 * <h2>A tela nao decide nada</h2>
 *
 * <p>Ela desabilita o botao de quem nao tem vida suficiente, mas isso e <b>cortesia</b>, nao regra —
 * a regra mora no servidor e e reconferida quando o pacote chega. Um cliente modificado que force o
 * clique nao consegue nada: o {@code RescueManager} conta as vidas de novo, confere a distancia ate o
 * Oraculo de novo, e confere se o alvo ainda esta no Limbo de novo.
 *
 * <p>O que a tela faz e traduzir. Ela recebe milissegundos e nomes, e monta a leitura — nenhum calculo
 * de regra acontece aqui.
 */
public class OracleScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int PANEL_WIDTH = 320;
    private static final int MAX_VISIBLE = 8;

    private final OpenOraclePayload data;
    private int scroll;

    public OracleScreen(OpenOraclePayload data) {
        super(Component.translatable("aurorion_limbo.oraculo.titulo"));
        this.data = data;
    }

    @Override
    protected void init() {
        int visible = Math.min(data.exiles().size(), MAX_VISIBLE);
        int top = height / 2 - (visible * ROW_HEIGHT) / 2 + 6;
        int x = (width - PANEL_WIDTH) / 2;

        boolean canAfford = data.viewerLives() >= data.minLives();

        for (int i = 0; i < visible; i++) {
            OpenOraclePayload.Entry entry = data.exiles().get(Mth.clamp(scroll + i, 0, data.exiles().size() - 1));

            Button button = Button.builder(
                            Component.translatable("aurorion_limbo.oraculo.abrir"),
                            b -> choose(entry))
                    .bounds(x + PANEL_WIDTH - 96, top + i * ROW_HEIGHT, 88, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("aurorion_limbo.oraculo.custo", data.cost())))
                    .build();

            button.active = canAfford;
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, Math.min(height - 28, top + visible * ROW_HEIGHT + 12), 100, 20)
                .build());
    }

    private void choose(OpenOraclePayload.Entry entry) {
        PacketDistributor.sendToServer(new BeginRescuePayload(entry.id()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        int visible = Math.min(data.exiles().size(), MAX_VISIBLE);
        int top = height / 2 - (visible * ROW_HEIGHT) / 2 + 6;
        int x = (width - PANEL_WIDTH) / 2;

        g.drawCenteredString(font, title, width / 2, top - 34, 0xFFE8DCC8);

        Component wallet = Component.translatable("aurorion_limbo.oraculo.suas_vidas",
                data.viewerLives(), data.minLives());
        g.drawCenteredString(font, wallet, width / 2, top - 20,
                data.viewerLives() >= data.minLives() ? 0xFF9BBFD6 : 0xFFD99891);

        if (data.exiles().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("aurorion_limbo.oraculo.vazio"),
                    width / 2, top + 8, 0xFF8B9CAD);
            return;
        }

        for (int i = 0; i < visible; i++) {
            OpenOraclePayload.Entry entry = data.exiles().get(Mth.clamp(scroll + i, 0, data.exiles().size() - 1));
            int y = top + i * ROW_HEIGHT;

            g.fill(x, y - 2, x + PANEL_WIDTH, y + ROW_HEIGHT - 6, 0x40101925);
            g.drawString(font, entry.name(), x + 10, y + 1, 0xFFF1E8D8, false);

            // Prazo curto em ferrugem: a linha de cima da tela ja diz quem esta perto de sumir.
            int color = entry.remainingMillis() < 6L * 3_600_000L ? 0xFFD99891 : 0xFF8B9CAD;
            g.drawString(font, Component.translatable("aurorion_limbo.oraculo.prazo",
                            TimeFormat.duration(entry.remainingMillis())),
                    x + 10, y + 12, color, false);

            if (entry.attempted()) {
                g.drawString(font, Component.translatable("aurorion_limbo.oraculo.procurado"),
                        x + 150, y + 12, 0xFF6E7686, false);
            }
            if (!entry.online()) {
                g.drawString(font, Component.translatable("aurorion_limbo.oraculo.offline"),
                        x + 150, y + 1, 0xFF6E7686, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = Math.max(0, data.exiles().size() - MAX_VISIBLE);
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, max);
        if (next != scroll) {
            scroll = next;
            // Os botoes carregam o alvo de cada linha, entao rolar tem que reconstrui-los — senao o
            // botao da linha 1 continuaria resgatando quem estava ali antes da rolagem.
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
