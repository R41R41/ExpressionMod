package com.expression.state;

/** A slow, smooth current along the robot's authored cyan traces. No blinking or added glow. */
public final class CircuitPulse {
    public static final double PERIOD_SECONDS = 5.6;
    public static final int PALE = 0xFF91DCEC, DENSE = 0xFF279EBF;

    public static boolean isTrace(int argb) {
        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        return (argb >>> 24) == 255 && g > r + 25 && b > g && b > 200;
    }

    public static int color(double distance, double seconds) {
        double phase = distance / 12 + (seconds % PERIOD_SECONDS) / PERIOD_SECONDS;
        double wave = (1 + Math.cos(phase * 2 * Math.PI)) / 2;
        wave = wave * wave * wave;
        wave *= wave;
        // A short, denser cyan packet moves across an even pale base. Authored dark pixels
        // must not stay stationary underneath it, which would read as breathing brightness.
        return WaterlightGradient.blend(PALE, DENSE, (float) (.80 * wave));
    }
    private CircuitPulse() {}
}
