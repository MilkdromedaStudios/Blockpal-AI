package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A labelled slider for a numeric setting with a fixed min/max range. The live
 * value is read on demand with {@link #current()}; integer sliders snap to whole
 * numbers, doubles to one decimal place.
 */
public class OptionSlider extends AbstractSliderButton {

    private final String label;
    private final double min;
    private final double max;
    private final boolean integer;

    public OptionSlider(int x, int y, int width, int height, String label,
                        double min, double max, double initial, boolean integer) {
        super(x, y, width, height, Component.empty(), fraction(initial, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.integer = integer;
        updateMessage();
    }

    private static double fraction(double value, double min, double max) {
        if (max <= min) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    /** Programmatically set the slider to a real-unit value (clamps to range). */
    public void setCurrent(double v) {
        this.value = fraction(v, min, max);
        updateMessage();
    }

    /** The slider's current value in real units (not the 0–1 internal fraction). */
    public double current() {
        double v = min + this.value * (max - min);
        return integer ? Math.round(v) : Math.round(v * 10.0) / 10.0;
    }

    @Override
    protected void updateMessage() {
        String shown = integer ? String.valueOf((int) current()) : String.valueOf(current());
        setMessage(Component.literal(label + ": " + shown));
    }

    @Override
    protected void applyValue() {
        // Read lazily via current() — nothing to store on each drag.
    }
}
