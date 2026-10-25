package com.expression.state;

import java.util.function.LongSupplier;

/** Renewable transport lease. Repeating a heartbeat must not restart the motion. */
public final class DialogueState {
    public static final long MAX_DURATION_MS = 5000;
    private final LongSupplier clock;
    private DialogueActivity activity = DialogueActivity.IDLE;
    private long until;
    private boolean target;
    private float yaw, pitch;
    public DialogueState() { this(() -> System.nanoTime() / 1_000_000); }
    public DialogueState(LongSupplier clock) { this.clock = clock; }
    public void update(DialogueActivity value, long duration, boolean hasTarget, float yaw, float pitch) {
        expire();
        long now = clock.getAsLong();
        activity = value;
        until = value == DialogueActivity.IDLE ? 0 : now + Math.clamp(duration, 250, MAX_DURATION_MS);
        target = value != DialogueActivity.IDLE && hasTarget && Float.isFinite(yaw) && Float.isFinite(pitch);
        this.yaw = target ? Math.clamp(yaw, -30, 30) : 0;
        this.pitch = target ? Math.clamp(pitch, -30, 30) : 0;
    }
    private void expire() {
        if (activity != DialogueActivity.IDLE && clock.getAsLong() >= until) {
            activity = DialogueActivity.IDLE; target = false;
        }
    }
    public DialogueActivity activity() { expire(); return activity; }
    public boolean hasTarget() { expire(); return target; }
    public float yaw() { expire(); return yaw; }
    public float pitch() { expire(); return pitch; }
    public long remainingMs() { expire(); return activity == DialogueActivity.IDLE ? 0 : Math.max(0, until - clock.getAsLong()); }
}
