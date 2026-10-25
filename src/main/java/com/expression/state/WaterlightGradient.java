package com.expression.state;

/** App-owned Waterlight palette in sRGB; a continuous diagonal three-stop gradient. */
public final class WaterlightGradient {
    public static final int TOP = 0xFFC4FBFF, MIDDLE = 0xFF76E7FF, BOTTOM = 0xFF6397FF;
    public static final double PERIOD_SECONDS = 8.4;
    public static int at(float u, float v) {
        float progress = Math.clamp(.18f * u + .82f * v, 0, 1);
        return ramp(progress);
    }
    public static int at(float u, float v, double seconds, float energy) {
        if (seconds < 0) return at(u, v);
        double phase = (seconds % PERIOD_SECONDS) / PERIOD_SECONDS * 2 * Math.PI;
        double strength = .65 + .35 * Math.clamp(energy, 0, 1);
        float progress = (float) (.18 * u + .82 * v + .11 * strength * Math.sin(phase)
                + .045 * Math.cos(phase) * (u - .5));
        return ramp(Math.clamp(progress, 0, 1));
    }
    private static int ramp(float progress) {
        return progress <= .5f ? blend(TOP, MIDDLE, progress * 2) : blend(MIDDLE, BOTTOM, (progress - .5f) * 2);
    }
    public static int blend(int a, int b, float weight) {
        weight = Math.clamp(weight, 0, 1);
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            color |= Math.round(((a >>> shift) & 255) * (1 - weight) + ((b >>> shift) & 255) * weight) << shift;
        return color;
    }
    private WaterlightGradient() {}
}
