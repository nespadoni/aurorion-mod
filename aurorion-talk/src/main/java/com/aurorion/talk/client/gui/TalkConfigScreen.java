package com.aurorion.talk.client.gui;

import com.aurorion.talk.config.TalkConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Preferencias pessoais do cliente: nada aqui muda o que os outros jogadores veem — isso fica na
 * tela de personalizacao do balao ({@link BalloonCustomizationScreen}).
 */
public class TalkConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 280;

    private final @Nullable Screen parent;
    private int nextY;

    public TalkConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.aurorion_talk.preferences"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.nextY = this.height / 2 - 90;

        toggle("hidePlayerChat", TalkConfig.HIDE_PLAYER_CHAT.get(), TalkConfig.HIDE_PLAYER_CHAT::set);
        toggle("hideOnlyWhenBalloonShown", TalkConfig.HIDE_ONLY_WHEN_BALLOON_SHOWN.get(), TalkConfig.HIDE_ONLY_WHEN_BALLOON_SHOWN::set);
        toggle("showOwnBalloon", TalkConfig.SHOW_OWN_BALLOON.get(), TalkConfig.SHOW_OWN_BALLOON::set);

        slider("balloonAgeSeconds", TalkConfig.BALLOON_AGE_SECONDS.get(), 1, 120,
                TalkConfig.BALLOON_AGE_SECONDS::set, v -> v + "s");
        slider("maxBalloons", TalkConfig.MAX_BALLOONS.get(), 2, 20, TalkConfig.MAX_BALLOONS::set, String::valueOf);
        slider("minBalloonWidth", TalkConfig.MIN_BALLOON_WIDTH.get(), 5, 120, TalkConfig.MIN_BALLOON_WIDTH::set, v -> v + "px");
        slider("maxBalloonWidth", TalkConfig.MAX_BALLOON_WIDTH.get(), 40, 400, TalkConfig.MAX_BALLOON_WIDTH::set, v -> v + "px");
        slider("distanceBetweenBalloons", TalkConfig.DISTANCE_BETWEEN_BALLOONS.get(), 0, 32,
                TalkConfig.DISTANCE_BETWEEN_BALLOONS::set, v -> v + "px");

        // Altura e um double; representamos em decimos de bloco (-40..80 -> -4.0..8.0) so na UI.
        int initialTenths = (int) Math.round(TalkConfig.HEIGHT_OFFSET.get() * 10.0);
        addRenderableWidget(new IntSliderWidget(
                this.width / 2 - ROW_WIDTH / 2, nextY, ROW_WIDTH, 20, -40, 80,
                () -> initialTenths, tenths -> TalkConfig.HEIGHT_OFFSET.set(tenths / 10.0),
                v -> Component.translatable("options.generic_value",
                        Component.translatable(optionKey("heightOffset")), String.format("%.1f", v / 10.0))
        ));
        nextY += ROW_HEIGHT;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 32, 150, 20)
                .build());
    }

    private void toggle(String key, boolean initial, java.util.function.Consumer<Boolean> set) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
                .create(this.width / 2 - ROW_WIDTH / 2, nextY, ROW_WIDTH, 20,
                        Component.translatable(optionKey(key)), (button, value) -> set.accept(value)));
        nextY += ROW_HEIGHT;
    }

    private void slider(String key, int initial, int min, int max, java.util.function.IntConsumer set,
                        java.util.function.IntFunction<String> format) {
        Component label = Component.translatable(optionKey(key));
        addRenderableWidget(new IntSliderWidget(
                this.width / 2 - ROW_WIDTH / 2, nextY, ROW_WIDTH, 20, min, max,
                () -> initial, set,
                v -> Component.translatable("options.generic_value", label, format.apply(v))
        ));
        nextY += ROW_HEIGHT;
    }

    private static String optionKey(String name) {
        return "options.aurorion_talk." + name;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        TalkConfig.SPEC.save();
        this.minecraft.setScreen(parent);
    }
}
