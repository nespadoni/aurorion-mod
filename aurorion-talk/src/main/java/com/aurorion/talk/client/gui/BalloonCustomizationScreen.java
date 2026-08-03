package com.aurorion.talk.client.gui;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.client.render.BalloonGuiRenderer;
import com.aurorion.talk.config.TalkConfig;
import com.aurorion.talk.network.SetStylePayload;
import com.aurorion.talk.style.BalloonPalette;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tela principal de personalizacao do balao — o que no QSMP chama de "Speech Bubble Customization".
 * Abre com a tecla configurada em {@link com.aurorion.talk.client.TalkClientEvents#OPEN_CONFIG}.
 *
 * <p>Tudo aqui e social: a escolha so vira definitiva (e visivel para os outros) quando a tela
 * fecha e manda {@link SetStylePayload} pro servidor. Ate la, as mudancas ficam so no preview
 * local.</p>
 */
public class BalloonCustomizationScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int SWATCH_SIZE = 16;
    private static final int SWATCH_GAP = 4;

    private static final String SHORT_SAMPLE = "options.aurorion_talk.preview.short";
    private static final String LONG_SAMPLE = "options.aurorion_talk.preview.long";

    private final BalloonStyle original;
    private BalloonStyle pending;

    private final List<ColorSwatchButton> balloonSwatches = new ArrayList<>();
    private final List<ColorSwatchButton> textSwatches = new ArrayList<>();

    public BalloonCustomizationScreen() {
        super(Component.translatable("screen.aurorion_talk.customization"));
        this.original = currentOwnStyle();
        this.pending = original;
    }

    private static BalloonStyle currentOwnStyle() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return BalloonStyle.DEFAULT;
        return com.aurorion.talk.client.ClientStyles.get(player.getUUID());
    }

    @Override
    protected void init() {
        balloonSwatches.clear();
        textSwatches.clear();

        int panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        int swatchesY = this.height / 2 + 20;

        int swatchesPerRow = (PANEL_WIDTH + SWATCH_GAP) / (SWATCH_SIZE + SWATCH_GAP);
        layoutPalette(BalloonPalette.BALLOON_COLORS, panelLeft, swatchesY, swatchesPerRow, true);
        layoutPalette(BalloonPalette.TEXT_COLORS, panelLeft, swatchesY + SWATCH_SIZE + SWATCH_GAP + 10, swatchesPerRow, false);

        addRenderableWidget(Button.builder(Component.translatable("screen.aurorion_talk.select_style"),
                        b -> openPicker())
                .bounds(panelLeft, swatchesY + 2 * (SWATCH_SIZE + SWATCH_GAP) + 24, PANEL_WIDTH, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.aurorion_talk.preferences"),
                        b -> this.minecraft.setScreen(new TalkConfigScreen(this)))
                .bounds(panelLeft, swatchesY + 2 * (SWATCH_SIZE + SWATCH_GAP) + 48, PANEL_WIDTH, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(panelLeft, swatchesY + 2 * (SWATCH_SIZE + SWATCH_GAP) + 76, PANEL_WIDTH, 20)
                .build());
    }

    private void layoutPalette(int[] colors, int left, int y, int perRow, boolean isBalloonColor) {
        List<ColorSwatchButton> target = isBalloonColor ? balloonSwatches : textSwatches;

        for (int i = 0; i < colors.length; i++) {
            int col = i % perRow;
            int row = i / perRow;
            int x = left + col * (SWATCH_SIZE + SWATCH_GAP);
            int swatchY = y + row * (SWATCH_SIZE + SWATCH_GAP);
            int color = colors[i];

            ColorSwatchButton swatch = new ColorSwatchButton(x, swatchY, SWATCH_SIZE, color,
                    isSelected(color, isBalloonColor), b -> selectColor(color, isBalloonColor));

            target.add(swatch);
            addRenderableWidget(swatch);
        }
    }

    private boolean isSelected(int color, boolean isBalloonColor) {
        return isBalloonColor ? pending.color() == color : pending.textColor() == color;
    }

    private void selectColor(int color, boolean isBalloonColor) {
        pending = isBalloonColor ? pending.withColor(color) : pending.withTextColor(color);

        for (ColorSwatchButton swatch : balloonSwatches) swatch.setSelected(swatch.color() == pending.color());
        for (ColorSwatchButton swatch : textSwatches) swatch.setSelected(swatch.color() == pending.textColor());
    }

    private void openPicker() {
        this.minecraft.setScreen(new StylePickerScreen(this, pending, chosen -> {
            pending = chosen;
            this.minecraft.setScreen(this);
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        drawPreviews(graphics);

        int swatchesY = this.height / 2 + 20;
        graphics.drawCenteredString(this.font, Component.translatable("screen.aurorion_talk.balloon_color"),
                this.width / 2, swatchesY - 10, 0xA0A0A0);
        graphics.drawCenteredString(this.font, Component.translatable("screen.aurorion_talk.text_color"),
                this.width / 2, swatchesY + SWATCH_SIZE + SWATCH_GAP, 0xA0A0A0);
    }

    private void drawPreviews(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int shortY = this.height / 2 - 60;
        int longY = this.height / 2 - 20;

        List<FormattedCharSequence> shortLines = BalloonGuiRenderer.wrap(
                this.font, Component.translatable(SHORT_SAMPLE).getString(), TalkConfig.MAX_BALLOON_WIDTH.get());
        List<FormattedCharSequence> longLines = BalloonGuiRenderer.wrap(
                this.font, Component.translatable(LONG_SAMPLE).getString(), TalkConfig.MAX_BALLOON_WIDTH.get());

        BalloonGuiRenderer.draw(graphics, this.font, pending.skin(), pending.decoration(),
                pending.color(), pending.textColor(), shortLines,
                TalkConfig.MIN_BALLOON_WIDTH.get(), TalkConfig.MAX_BALLOON_WIDTH.get(), centerX, shortY);

        BalloonGuiRenderer.draw(graphics, this.font, pending.skin(), pending.decoration(),
                pending.color(), pending.textColor(), longLines,
                TalkConfig.MIN_BALLOON_WIDTH.get(), TalkConfig.MAX_BALLOON_WIDTH.get(), centerX, longY);
    }

    @Override
    public void onClose() {
        if (!Objects.equals(pending, original) && pending.isWellFormed()) {
            PacketDistributor.sendToServer(new SetStylePayload(pending));
        } else if (!pending.isWellFormed()) {
            AurorionTalk.LOGGER.warn("Estilo montado na GUI ficou mal formado, nao enviando: {}", pending);
        }

        this.minecraft.setScreen(null);
    }
}
