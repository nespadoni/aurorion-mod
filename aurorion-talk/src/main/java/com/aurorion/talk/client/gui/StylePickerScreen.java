package com.aurorion.talk.client.gui;

import com.aurorion.talk.client.BalloonCatalog;
import com.aurorion.talk.style.BalloonStyle;
import com.aurorion.talk.style.BalloonTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Grade de skins de balao e enfeites disponiveis. O catalogo vem de
 * {@link BalloonCatalog}, que escaneia as pastas {@code skin/} e {@code deco/} — arte nova aparece
 * aqui so de soltar o PNG na pasta certa e reexportar, sem mexer em codigo.
 */
public class StylePickerScreen extends Screen {
    private static final int CELL = 36;
    private static final int ICON = 24;

    private final Screen parent;
    private final Consumer<BalloonStyle> onDone;

    private BalloonStyle pending;

    public StylePickerScreen(Screen parent, BalloonStyle initial, Consumer<BalloonStyle> onDone) {
        super(Component.translatable("screen.aurorion_talk.select_style"));
        this.parent = parent;
        this.pending = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        List<ResourceLocation> skins = BalloonCatalog.skins();
        List<ResourceLocation> decorations = BalloonCatalog.decorations();

        int columns = Math.max(1, (this.width - 40) / CELL);

        int skinsTop = 36;
        layoutGrid(skins.size(), columns, skinsTop, i -> selectSkin(skins.get(i)),
                i -> pending.skin().equals(skins.get(i)),
                i -> BalloonTextures.displayName(skins.get(i)));

        int skinRows = (skins.size() + columns - 1) / columns;
        int decosTop = skinsTop + skinRows * CELL + 28;

        // Primeira celula da fileira de enfeites e sempre "nenhum".
        addRenderableWidget(iconButton(40, decosTop, Optional.empty(), pending.decoration().isEmpty(),
                b -> selectDecoration(Optional.empty()), Component.translatable("screen.aurorion_talk.no_decoration")));

        for (int i = 0; i < decorations.size(); i++) {
            ResourceLocation deco = decorations.get(i);
            int col = (i + 1) % columns;
            int row = (i + 1) / columns;
            int x = 40 + col * CELL;
            int y = decosTop + row * CELL;

            addRenderableWidget(iconButton(x, y, Optional.of(deco), pending.decoration().equals(Optional.of(deco)),
                    b -> selectDecoration(Optional.of(deco)), Component.literal(BalloonTextures.displayName(deco))));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> done())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
                .build());
    }

    private void layoutGrid(int count, int columns, int top, java.util.function.IntConsumer onPick,
                            java.util.function.IntPredicate isSelected, java.util.function.IntFunction<String> nameOf) {
        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            int x = 40 + col * CELL;
            int y = top + row * CELL;
            int index = i;

            addRenderableWidget(iconButton(x, y, Optional.empty(), isSelected.test(index),
                    b -> onPick.accept(index), Component.literal(nameOf.apply(index))));
        }
    }

    /** Botao quadrado que desenha uma miniatura da textura, ou um "X" para a opcao "nenhum". */
    private Button iconButton(int x, int y, Optional<ResourceLocation> texture, boolean selected,
                              Button.OnPress onPress, Component name) {
        return new Button(x, y, CELL - 4, CELL - 4, name, onPress, Button.DEFAULT_NARRATION) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height,
                        selected ? 0xFF3A3A44 : 0xFF202024);

                int iconX = getX() + (width - ICON) / 2;
                int iconY = getY() + (height - ICON) / 2;

                texture.ifPresentOrElse(
                        id -> graphics.blit(id, iconX, iconY, 0, 0, ICON, ICON, ICON, ICON),
                        () -> graphics.drawCenteredString(font, "-", getX() + width / 2, getY() + height / 2 - 4, 0xA0A0A0)
                );

                if (selected) {
                    graphics.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
                } else if (isHoveredOrFocused()) {
                    graphics.renderOutline(getX(), getY(), width, height, 0x80FFFFFF);
                }
            }
        };
    }

    private void selectSkin(ResourceLocation skin) {
        pending = pending.withSkin(skin);
        rebuild();
    }

    private void selectDecoration(Optional<ResourceLocation> decoration) {
        pending = pending.withDecoration(decoration);
        rebuild();
    }

    /** Reabre a propria tela para redesenhar as selecoes — mais simples que atualizar cada botao. */
    private void rebuild() {
        this.minecraft.setScreen(new StylePickerScreen(parent, pending, onDone));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        done();
    }

    private void done() {
        onDone.accept(pending);
    }
}
