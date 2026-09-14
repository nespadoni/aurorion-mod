package com.aurorion.talk.client.gui;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.client.render.BalloonGuiRenderer;
import com.aurorion.talk.config.TalkConfig;
import com.aurorion.talk.network.SetStylePayload;
import com.aurorion.talk.style.BalloonPalette;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tela principal de personalizacao do balao.
 *
 * <h2>Uma grade, nao duas</h2>
 *
 * <p>Havia aqui duas paletas empilhadas — dezesseis cores de balao e dezesseis de texto — porque as
 * listas eram diferentes uma da outra. Agora a lista e a mesma ({@link BalloonPalette#GRID}), entao
 * duas grades seriam a mesma imagem desenhada duas vezes ocupando o dobro da tela. Ficou uma grade e
 * um par de botoes dizendo <b>o que</b> ela esta pintando.
 *
 * <p>Ao lado da grade tem um campo de hexadecimal, que e o ponto: a grade e para escolher rapido, o
 * campo e para acertar a cor exata da sua casa ou do seu clã.
 *
 * <h2>O aviso de contraste</h2>
 *
 * <p>A tela avisa quando a combinacao escolhida nao da para ler, mas <b>nao impede</b>. Quem decide e
 * o servidor, que corrige o texto ao gravar — a tela apenas mostra antes o que vai acontecer, para a
 * correcao nao parecer um bug.
 *
 * <p>Tudo e social: a escolha so vira definitiva quando a tela fecha e manda o
 * {@link SetStylePayload}. Ate la, as mudancas ficam so no preview local.
 */
public class BalloonCustomizationScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int SWATCH = 18;
    private static final int GAP = 3;
    private static final int ROWS = 3;

    private static final String SHORT_SAMPLE = "options.aurorion_talk.preview.short";
    private static final String LONG_SAMPLE = "options.aurorion_talk.preview.long";

    private final BalloonStyle original;
    private BalloonStyle pending;

    /** O que a grade e o campo estao pintando agora. */
    private boolean paintingBalloon = true;

    private final List<ColorSwatchButton> swatches = new ArrayList<>();
    private Button balloonTab;
    private Button textTab;
    private EditBox hex;
    private int gridTop;

    public BalloonCustomizationScreen() {
        super(Component.translatable("screen.aurorion_talk.customization"));
        this.original = currentOwnStyle();
        this.pending = original;
    }

    private static BalloonStyle currentOwnStyle() {
        var player = Minecraft.getInstance().player;
        if (player == null) return BalloonStyle.DEFAULT;
        return com.aurorion.talk.client.ClientStyles.get(player.getUUID());
    }

    @Override
    protected void init() {
        swatches.clear();

        int left = width / 2 - PANEL_WIDTH / 2;
        int top = Math.max(8, height / 2 - 150);
        int tabsY = top + 104;

        balloonTab = addRenderableWidget(Button.builder(
                        Component.translatable("screen.aurorion_talk.balloon_color"), b -> paint(true))
                .bounds(left, tabsY, PANEL_WIDTH / 2 - 2, 20).build());
        textTab = addRenderableWidget(Button.builder(
                        Component.translatable("screen.aurorion_talk.text_color"), b -> paint(false))
                .bounds(left + PANEL_WIDTH / 2 + 2, tabsY, PANEL_WIDTH / 2 - 2, 20).build());

        gridTop = tabsY + 26;
        int gridWidth = BalloonPalette.COLUMNS * (SWATCH + GAP) - GAP;
        int gridLeft = width / 2 - gridWidth / 2;

        for (int i = 0; i < BalloonPalette.GRID.length; i++) {
            int color = BalloonPalette.GRID[i];
            int x = gridLeft + (i % BalloonPalette.COLUMNS) * (SWATCH + GAP);
            int y = gridTop + (i / BalloonPalette.COLUMNS) * (SWATCH + GAP);

            ColorSwatchButton swatch = new ColorSwatchButton(x, y, SWATCH, color,
                    color == active(), b -> choose(color));
            swatches.add(swatch);
            addRenderableWidget(swatch);
        }

        int hexY = gridTop + ROWS * (SWATCH + GAP) + 8;
        hex = new EditBox(font, width / 2 - 34, hexY, 68, 18,
                Component.translatable("screen.aurorion_talk.hex"));
        hex.setMaxLength(7);
        hex.setFilter(BalloonCustomizationScreen::looksLikeHex);
        hex.setResponder(this::typedHex);
        hex.setValue(BalloonPalette.toHex(active()));
        addRenderableWidget(hex);

        int buttonsY = hexY + 30;
        addRenderableWidget(Button.builder(Component.translatable("screen.aurorion_talk.select_style"),
                        b -> openPicker())
                .bounds(left, buttonsY, PANEL_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aurorion_talk.preferences"),
                        b -> this.minecraft.setScreen(new TalkConfigScreen(this)))
                .bounds(left, buttonsY + 24, PANEL_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left, buttonsY + 48, PANEL_WIDTH, 20).build());

        refreshTabs();
    }

    // --- Escolha ------------------------------------------------------------------------------

    private int active() {
        return paintingBalloon ? pending.color() : pending.textColor();
    }

    private void paint(boolean balloon) {
        paintingBalloon = balloon;
        hex.setValue(BalloonPalette.toHex(active()));
        refreshTabs();
    }

    private void choose(int color) {
        apply(color);
        hex.setValue(BalloonPalette.toHex(color));
    }

    /**
     * Trocar a cor do balao leva junto um palpite de cor de texto — mas so quando o que estava la
     * deixou de dar para ler. Reescrever sempre tiraria da pessoa uma escolha que continuava valida.
     */
    private void apply(int color) {
        if (paintingBalloon) {
            pending = pending.withColor(color);
            if (!BalloonPalette.readable(color, pending.textColor())) {
                pending = pending.withTextColor(BalloonPalette.suggestedTextColor(color));
            }
        } else {
            pending = pending.withTextColor(color);
        }
        refreshTabs();
    }

    private void typedHex(String raw) {
        int color = BalloonPalette.parseHex(raw);
        if (color >= 0) apply(color);
    }

    private static boolean looksLikeHex(String value) {
        if (value.length() > 7) return false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = i == 0 && c == '#'
                    || c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
            if (!ok) return false;
        }
        return true;
    }

    private void refreshTabs() {
        int current = active();
        for (int i = 0; i < swatches.size(); i++) {
            swatches.get(i).setSelected(swatches.get(i).color() == current);
        }
        balloonTab.active = !paintingBalloon;
        textTab.active = paintingBalloon;
    }

    private void openPicker() {
        this.minecraft.setScreen(new StylePickerScreen(this, pending, chosen -> {
            pending = chosen;
            this.minecraft.setScreen(this);
        }));
    }

    // --- Desenho ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        drawPreviews(graphics);

        int hexY = gridTop + ROWS * (SWATCH + GAP) + 8;
        // Amostra do que esta sendo pintado, colada no campo: confirma a cor sem procurar na grade.
        graphics.fill(width / 2 - 56, hexY, width / 2 - 38, hexY + 18, 0xFF000000 | active());
        graphics.fill(width / 2 - 57, hexY - 1, width / 2 - 37, hexY, 0xFF6B6B76);
        graphics.fill(width / 2 - 57, hexY + 18, width / 2 - 37, hexY + 19, 0xFF6B6B76);

        if (!BalloonPalette.readable(pending.color(), pending.textColor())) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.aurorion_talk.low_contrast").withStyle(ChatFormatting.YELLOW),
                    width / 2, hexY + 22, 0xFFE9A13B);
        }
    }

    private void drawPreviews(GuiGraphics graphics) {
        int centerX = width / 2;
        int top = Math.max(8, height / 2 - 150);

        List<FormattedCharSequence> shortLines = BalloonGuiRenderer.wrap(
                font, Component.translatable(SHORT_SAMPLE).getString(), TalkConfig.MAX_BALLOON_WIDTH.get());
        List<FormattedCharSequence> longLines = BalloonGuiRenderer.wrap(
                font, Component.translatable(LONG_SAMPLE).getString(), TalkConfig.MAX_BALLOON_WIDTH.get());

        BalloonGuiRenderer.draw(graphics, font, pending.skin(), pending.decoration(),
                pending.color(), pending.textColor(), shortLines,
                TalkConfig.MIN_BALLOON_WIDTH.get(), TalkConfig.MAX_BALLOON_WIDTH.get(), centerX, top + 28);

        BalloonGuiRenderer.draw(graphics, font, pending.skin(), pending.decoration(),
                pending.color(), pending.textColor(), longLines,
                TalkConfig.MIN_BALLOON_WIDTH.get(), TalkConfig.MAX_BALLOON_WIDTH.get(), centerX, top + 64);
    }

    @Override
    public void onClose() {
        // Normaliza antes de enviar para o preview do proximo abrir ja mostrar o que o servidor gravou.
        BalloonStyle finished = pending.normalized();

        if (!Objects.equals(finished, original) && finished.isWellFormed()) {
            PacketDistributor.sendToServer(new SetStylePayload(finished));
        } else if (!finished.isWellFormed()) {
            AurorionTalk.LOGGER.warn("Estilo montado na GUI ficou mal formado, nao enviando: {}", finished);
        }

        this.minecraft.setScreen(null);
    }
}
