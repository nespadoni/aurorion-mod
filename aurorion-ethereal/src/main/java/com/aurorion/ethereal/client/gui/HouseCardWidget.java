package com.aurorion.ethereal.client.gui;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseOption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * Um card de casa na tela do altar.
 *
 * <p>Todo o trabalho caro e feito no construtor, uma vez por abertura de tela: quebra de linha do
 * lema e da descricao ({@code Font#split}) e o {@link ItemStack} do icone. Essa e a mesma licao do
 * {@code aurorion-talk} (SDD §4.2) — refazer quebra de texto ou instanciar stack a 60 fps e
 * desperdicio puro, ainda mais com o cliente rodando o modpack inteiro por tras da tela.
 */
public class HouseCardWidget extends AbstractButton {
    private static final int PADDING = 6;
    private static final int ICON_SIZE = 16;
    private static final int LINE_HEIGHT = 10;

    private static final int BACKGROUND = 0xC0101014;
    private static final int BACKGROUND_DISABLED = 0xC0181818;
    private static final int TEXT_MUTED = 0xFFA0A0A0;
    private static final int TEXT_MOTTO = 0xFFC8C8D8;
    private static final int TEXT_FULL = 0xFFFF6B6B;

    private final HouseOption option;
    private final Font font;
    private final ItemStack icon;
    private final List<FormattedCharSequence> motto;
    private final List<FormattedCharSequence> description;
    private final Consumer<HouseOption> onPick;

    private final boolean isCurrent;
    private boolean selected;

    public HouseCardWidget(int x, int y, int width, int height, Font font, HouseOption option,
                           boolean isCurrent, boolean selectable, Consumer<HouseOption> onPick) {
        super(x, y, width, height, option.house().name());
        this.option = option;
        this.font = font;
        this.onPick = onPick;
        this.isCurrent = isCurrent;
        this.icon = resolveIcon(option.house());
        this.motto = font.split(option.house().motto(), width - 2 * PADDING);
        this.description = font.split(option.house().description(), width - 2 * PADDING);
        setEnabled(selectable);
    }

    /** Icone invalido (mod removido, id errado no JSON) vira um item generico em vez de crashar a tela. */
    private static ItemStack resolveIcon(House house) {
        return house.icon()
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.PAPER));
    }

    public HouseOption option() {
        return option;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** Casa lotada nunca fica clicavel, mesmo quando a tela como um todo esta liberada. */
    public void setEnabled(boolean enabled) {
        this.active = enabled && !option.isFull();
    }

    @Override
    public void onPress() {
        onPick.accept(option);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = getX();
        int top = getY();
        int houseColor = option.house().argb();

        graphics.fill(left, top, left + width, top + height, active ? BACKGROUND : BACKGROUND_DISABLED);
        graphics.renderOutline(left, top, width, height, houseColor);
        if (selected) {
            graphics.renderOutline(left + 1, top + 1, width - 2, height - 2, 0xFFFFFFFF);
        } else if (isHoveredOrFocused() && active) {
            graphics.renderOutline(left + 1, top + 1, width - 2, height - 2, 0x80FFFFFF);
        }

        int centerX = left + width / 2;
        int y = top + PADDING;

        graphics.renderItem(icon, centerX - ICON_SIZE / 2, y);
        y += ICON_SIZE + 4;

        graphics.drawCenteredString(font, option.house().name(), centerX, y, houseColor);
        y += LINE_HEIGHT + 1;

        // Lema e descricao param na borda do card em vez de vazar por baixo: os dois vem de datapack,
        // e um texto longo demais nao pode empurrar o resto da tela.
        int bottom = top + height - PADDING;
        for (FormattedCharSequence line : motto) {
            if (y + LINE_HEIGHT > bottom) {
                break;
            }
            graphics.drawString(font, line, centerX - font.width(line) / 2, y, TEXT_MOTTO, false);
            y += LINE_HEIGHT;
        }
        y += 2;

        if (isCurrent) {
            graphics.drawCenteredString(font, Component.translatable("gui.aurorion_ethereal.house.your_house"),
                    centerX, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        if (option.house().hasCapacity()) {
            Component members = Component.translatable("gui.aurorion_ethereal.house.members",
                    option.members(), option.house().capacity());
            graphics.drawCenteredString(font, members, centerX, y, option.isFull() ? TEXT_FULL : TEXT_MUTED);
            y += LINE_HEIGHT;
        }

        y += 2;
        for (FormattedCharSequence line : description) {
            if (y + LINE_HEIGHT > bottom) {
                break;
            }
            graphics.drawString(font, line, left + PADDING, y, TEXT_MUTED, false);
            y += LINE_HEIGHT;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
