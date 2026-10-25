package com.expression.state;

/** Independent rectangular eye dimensions, tilt in degrees, gaze and head controls. */
public record FacePose(float leftOpen, float rightOpen, float leftTilt, float rightTilt,
        float gazeX, float gazeY, float headYaw, float headPitch, float blush,
        float leftWidth, float rightWidth, float energy) {
    public static final FacePose NEUTRAL = new FacePose(1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, .56f);
    public static FacePose of(EmoteType emote) {
        return switch (emote) {
            case NEUTRAL -> NEUTRAL;
            // Waterlight-inspired proportions, adapted to a small rectangular Minecraft display.
            case SMILE -> pair(.32f, .32f, 4, 1, 6, .06f, .62f);
            case EXCITED -> pair(.37f, .60f, -9, 2, -11, 0, .86f);
            case AMUSED -> rig(.31f, .14f, 15, .22f, .48f, -7, 7, 5, 0, .66f);
            case LAUGHING -> pair(.39f, .23f, 7, 0, 12, .15f, .82f);
            case BASHFUL -> rig(.26f, .34f, 3, .24f, .29f, -2, -10, 5, .3f, .42f);
            case SHY -> pair(.19f, .39f, -8, -17, -12, .35f, .38f);
            case RELIEVED -> pair(.30f, .25f, 0, 0, 6, .03f, .28f);
            case SURPRISED -> pair(.43f, .49f, 0, 0, -8, 0, .72f);
            case CURIOUS -> rig(.25f, .55f, -7, .20f, .43f, -7, 17, -8, 0, .65f);
            case THINK -> rig(.21f, .46f, -12, .20f, .39f, -12, -17, -13, 0, .44f);
            case CONFUSED -> rig(.21f, .50f, -18, .30f, .16f, 15, -10, 1, 0, .52f);
            case DETERMINED -> pair(.33f, .16f, 28, 0, 8, 0, .58f);
            case TENDER -> pair(.24f, .43f, 7, 0, 7, .05f, .34f);
            case CALM -> pair(.25f, .40f, 0, 0, 0, 0, .22f);
            case JEALOUS -> rig(.22f, .38f, -9, .28f, .14f, -4, 20, 3, 0, .46f);
            case WRY -> rig(.31f, .14f, 14, .22f, .42f, -10, 12, 7, 0, .42f);
            case EXASPERATED -> rig(.31f, .13f, 3, .28f, .11f, -3, 19, 2, 0, .28f);
            case ANGRY -> pair(.34f, .15f, 27, 8, 8, 0, .44f);
            case CONCERNED -> rig(.24f, .45f, -10, .25f, .37f, 8, -2, 3, 0, .34f);
            case SAD -> pair(.28f, .25f, -12, 0, 14, 0, .24f);
            case SLEEPY -> pair(.28f, .105f * .62f, 2, -4, -5, 0, .14f);
        };
    }
    private static FacePose pair(float width, float height, float tilt, float yaw, float pitch, float blush, float energy) {
        return rig(width, height, tilt, width, height, -tilt, yaw, pitch, blush, energy);
    }
    private static FacePose rig(float lw, float lh, float lt, float rw, float rh, float rt,
            float yaw, float pitch, float blush, float energy) {
        return new FacePose(lh / .54f, rh / .51f, lt, rt, yaw / 80, pitch / 100, yaw * .25f, pitch * .3f,
                blush, lw / .23f, rw / .22f, energy);
    }
    public FacePose interpolate(FacePose target, float progress) {
        if (progress <= 0) return this;
        if (progress >= 1) return target;
        float t = easeOutQuint(progress);
        return new FacePose(mix(leftOpen, target.leftOpen, t), mix(rightOpen, target.rightOpen, t),
                mix(leftTilt, target.leftTilt, t), mix(rightTilt, target.rightTilt, t),
                mix(gazeX, target.gazeX, t), mix(gazeY, target.gazeY, t),
                mix(headYaw, target.headYaw, t), mix(headPitch, target.headPitch, t), mix(blush, target.blush, t),
                mix(leftWidth, target.leftWidth, t), mix(rightWidth, target.rightWidth, t), mix(energy, target.energy, t));
    }
    public static float easeOutQuint(float t) {
        float remaining = 1 - Math.clamp(t, 0, 1);
        return 1 - remaining * remaining * remaining * remaining * remaining;
    }
    public static float smoothstep(float t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
