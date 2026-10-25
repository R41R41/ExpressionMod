package com.expression.state;

/** Small, frame-rate-independent movements layered over the approved expression shapes. */
public final class AliveMotion {
    public record Sample(FacePose pose, float gazeX, float gazeY, float blinkIntervalScale, float blinkOpenScale) {}
    private double last = Double.NaN;
    private float gazeX, gazeY, headX, headY, width = 1, open = 1;
    private float thinking, speaking;
    public Sample sample(FacePose base, EmoteType emote, DialogueActivity activity,
                         float targetX, float targetY, double seconds, int seed, boolean animate) {
        double elapsed = Double.isNaN(last) ? 0 : Math.clamp(seconds - last, 0, .25);
        last = seconds;
        float quick = (float) (1 - Math.exp(-elapsed * 12));
        float slow = (float) (1 - Math.exp(-elapsed * 3.5));
        thinking += ((activity == DialogueActivity.THINKING ? 1 : 0) - thinking) * quick;
        speaking += ((activity == DialogueActivity.SPEAKING ? 1 : 0) - speaking) * quick;
        double phase = seconds + (seed & 1023) * .017;
        // Different frequencies keep idle eyes from following a single repeated pendulum.
        float driftX = animate ? (float) (Math.sin(phase * 1.13) * .012 + Math.sin(phase * 2.73) * .007) : 0;
        float driftY = animate ? (float) (Math.sin(phase * .79) * .009 + Math.cos(phase * 1.91) * .004) : 0;
        float desiredX = targetX + base.gazeX() + thinking * -.14f + driftX;
        float desiredY = targetY + base.gazeY() + thinking * -.035f + driftY;
        gazeX += (Math.clamp(desiredX, -.6f, .6f) - gazeX) * quick;
        gazeY += (Math.clamp(desiredY, -.4f, .4f) - gazeY) * quick;
        // Eyes respond first. A slower, much smaller head movement follows the visible gaze.
        headX += (gazeX - headX) * slow;
        headY += (gazeY - headY) * slow;
        float speech = animate ? (float) (Math.sin(phase * 5.2) * .022 + Math.sin(phase * 3.7) * .011) : 0;
        float breath = animate ? (float) (Math.sin(phase * 1.28) * .003 + Math.sin(phase * .53) * .002) * base.energy() : 0;
        float desiredWidth = 1 + breath + speaking * speech;
        float desiredOpen = 1 + breath + (activity == DialogueActivity.LISTENING ? .025f : 0) + speaking * speech * .6f;
        width += (desiredWidth - width) * quick;
        open += (desiredOpen - open) * quick;
        var pose = new FacePose(base.leftOpen() * open, base.rightOpen() * open, base.leftTilt(), base.rightTilt(),
                base.gazeX(), base.gazeY(), base.headYaw() + headX * 5, base.headPitch() + headY * 4,
                base.blush(), base.leftWidth() * width, base.rightWidth() * width, base.energy());
        float interval = emote == EmoteType.SLEEPY ? 1.25f : emote == EmoteType.EXCITED || emote == EmoteType.SURPRISED ? 1.12f : 1;
        if (activity == DialogueActivity.THINKING || activity == DialogueActivity.LISTENING) interval *= 1.3f;
        float opening = emote == EmoteType.SLEEPY ? 1.9f : emote == EmoteType.CALM || emote == EmoteType.RELIEVED ? 1.25f : 1;
        return new Sample(pose, gazeX, gazeY, interval, opening);
    }
}
