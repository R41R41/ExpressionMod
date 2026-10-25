package com.expression.state;

import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/** Analytic eyelid closure sampled at render time rather than discrete eye frames. */
public class BlinkState {
    public static final long CLOSE_MS = 55;
    public static final long HOLD_MS = 20;
    public static final long OPEN_MS = 110;
    private final LongSupplier clock;
    private final DoubleSupplier random;
    private long intervalMin = 3000;
    private long intervalMax = 6000;
    private long blinkStart = Long.MIN_VALUE;
    private long nextBlink;
    private float openingScale = 1;
    private float activeOpeningScale = 1;

    public BlinkState() { this(() -> System.nanoTime() / 1_000_000, Math::random); }
    public BlinkState(LongSupplier clock, DoubleSupplier random) {
        this.clock = clock;
        this.random = random;
        schedule(clock.getAsLong());
    }
    public void configure(long minimum, long maximum) {
        long min = Math.clamp(minimum, 250, 60_000);
        long max = Math.clamp(maximum, min, 60_000);
        if (min != intervalMin || max != intervalMax) {
            intervalMin = min;
            intervalMax = max;
            schedule(clock.getAsLong());
        }
    }
    public void openingScale(float scale) { openingScale = Float.isFinite(scale) ? Math.clamp(scale, 1, 2) : 1; }
    public void triggerBlink() { if (!isBlinking()) { blinkStart = clock.getAsLong(); activeOpeningScale = openingScale; } }
    public void tick() { getBlinkProgress(); }
    public float getBlinkProgress() {
        long now = clock.getAsLong();
        if (blinkStart == Long.MIN_VALUE && now >= nextBlink) { blinkStart = now; activeOpeningScale = openingScale; }
        if (blinkStart == Long.MIN_VALUE) return 0;
        long elapsed = now - blinkStart;
        if (elapsed < CLOSE_MS) return FacePose.smoothstep((float) elapsed / CLOSE_MS);
        if (elapsed < CLOSE_MS + HOLD_MS) return 1;
        if (elapsed < CLOSE_MS + HOLD_MS + OPEN_MS * activeOpeningScale)
            return 1 - FacePose.smoothstep((float) (elapsed - CLOSE_MS - HOLD_MS) / (OPEN_MS * activeOpeningScale));
        blinkStart = Long.MIN_VALUE;
        schedule(now);
        return 0;
    }
    public boolean isBlinking() { getBlinkProgress(); return blinkStart != Long.MIN_VALUE; }
    public boolean isEyeClosed() { return getBlinkProgress() > .8f; }
    public void triggerOnMovement() { if (clock.getAsLong() >= nextBlink - 1000) triggerBlink(); }
    private void schedule(long now) { nextBlink = now + intervalMin + (long) (random.getAsDouble() * (intervalMax - intervalMin)); }
    public void reset() { blinkStart = Long.MIN_VALUE; schedule(clock.getAsLong()); }
}
