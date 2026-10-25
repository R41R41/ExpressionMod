package com.expression.state;

import java.util.function.LongSupplier;

/** A renewable expression lease, with frame-time interpolation and continuous interruption. */
public class EmoteState {
    public static final long DEFAULT_DURATION_MS = 3000;
    public static final long MAX_DURATION_MS = 30_000;
    public static final long TRANSITION_DURATION_MS = 450;
    private final LongSupplier clock;
    private EmoteType currentEmote = EmoteType.NEUTRAL;
    private EmoteType previousEmote = EmoteType.NEUTRAL;
    private FacePose startPose = FacePose.NEUTRAL;
    private long transitionStart;
    private long endTime;

    public EmoteState() { this(() -> System.nanoTime() / 1_000_000); }
    public EmoteState(LongSupplier clock) {
        this.clock = clock;
        transitionStart = clock.getAsLong() - TRANSITION_DURATION_MS;
    }
    public static long boundedDuration(long durationMs) {
        return durationMs <= 0 ? DEFAULT_DURATION_MS : Math.min(durationMs, MAX_DURATION_MS);
    }
    public void setEmote(EmoteType emote, long durationMs) {
        long now = clock.getAsLong();
        expire(now);
        if (emote != currentEmote) transition(emote, now);
        endTime = emote == EmoteType.NEUTRAL ? 0 : now + boundedDuration(durationMs);
    }
    private void transition(EmoteType emote, long now) {
        startPose = sampleAt(now);
        previousEmote = currentEmote;
        currentEmote = emote;
        transitionStart = now;
    }
    private void expire(long now) {
        if (currentEmote != EmoteType.NEUTRAL && now >= endTime) {
            // Start at the deadline, even if this player was not rendered at that instant.
            long deadline = endTime;
            transition(EmoteType.NEUTRAL, deadline);
            endTime = 0;
        }
    }
    private FacePose sampleAt(long now) {
        return startPose.interpolate(FacePose.of(currentEmote), (float) (now - transitionStart) / TRANSITION_DURATION_MS);
    }
    public FacePose sampleFace() {
        long now = clock.getAsLong();
        expire(now);
        return sampleAt(now);
    }
    public void tick() { expire(clock.getAsLong()); }
    public EmoteType getCurrentEmote() { tick(); return currentEmote; }
    public EmoteType getPreviousEmote() { return previousEmote; }
    public boolean isNeutral() { return getCurrentEmote() == EmoteType.NEUTRAL; }
    public float getTransitionProgress() {
        tick();
        return Math.clamp((float) (clock.getAsLong() - transitionStart) / TRANSITION_DURATION_MS, 0, 1);
    }
    public boolean isTransitioning() { return getTransitionProgress() < 1; }
    public long getRemainingTimeMs() {
        tick();
        return endTime == 0 ? 0 : Math.max(0, endTime - clock.getAsLong());
    }
    public void reset() {
        currentEmote = previousEmote = EmoteType.NEUTRAL;
        startPose = FacePose.NEUTRAL;
        endTime = 0;
        transitionStart = clock.getAsLong() - TRANSITION_DURATION_MS;
    }
}
