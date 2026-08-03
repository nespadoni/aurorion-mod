package com.aurorion.talk.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Slider inteiro generico. O vanilla so fala em {@code double} 0..1 internamente
 * ({@link AbstractSliderButton}); aqui a gente so cuida da conversao para o intervalo real.
 */
public class IntSliderWidget extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final IntSupplier get;
    private final IntConsumer set;
    private final java.util.function.IntFunction<Component> label;

    public IntSliderWidget(int x, int y, int width, int height, int min, int max,
                           IntSupplier get, IntConsumer set, java.util.function.IntFunction<Component> label) {
        super(x, y, width, height, Component.empty(), normalize(get.getAsInt(), min, max));
        this.min = min;
        this.max = max;
        this.get = get;
        this.set = set;
        this.label = label;
        updateMessage();
    }

    private static double normalize(int current, int min, int max) {
        return max == min ? 0.0 : (current - min) / (double) (max - min);
    }

    private int currentInt() {
        return (int) Math.round(min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(currentInt()));
    }

    @Override
    protected void applyValue() {
        set.accept(currentInt());
    }

    /** Reflete uma mudanca feita fora do slider (ex.: reset), sem disparar {@link #applyValue()}. */
    public void refresh() {
        this.value = normalize(get.getAsInt(), min, max);
        updateMessage();
    }
}
