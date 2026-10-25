package com.expression.skin;

import com.expression.config.ModConfig;
import com.expression.render.ExpressionRenderState;
import com.expression.state.ExpressionStateManager;
import com.expression.state.FacePose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Animates each player separately; players with the same skin never share mood or blink state. */
public final class FaceRuntime {
    private static final Map<UUID, Gaze> GAZE = new HashMap<>();
    private static final class Gaze {
        float x, y, headYaw, headPitch;
        final com.expression.state.AliveMotion motion = new com.expression.state.AliveMotion();
        com.expression.state.DialogueActivity previousActivity = com.expression.state.DialogueActivity.IDLE;
        long at = System.nanoTime();
    }

    public static ExpressionRenderState.Frame snapshot(AbstractClientPlayerEntity player, Identifier texture) {
        FaceSkinData skin = FaceSkinData.getForPlayer(texture, player.getUuid());
        if (skin == null || !skin.usesWaterlightGradient()) return null;
        var config = ModConfig.getInstance();
        var state = ExpressionStateManager.getInstance().getOrCreate(player.getUuid(), player.getName().getString());
        FacePose pose = config.enableExpressions ? state.getEmoteState().sampleFace() : FacePose.NEUTRAL;
        boolean closed = player.isSleeping() || player.hasStatusEffect(StatusEffects.BLINDNESS);
        float yaw = state.hasEyeTarget() ? state.getEyeState().getTargetYaw()
                : MathHelper.wrapDegrees(player.getHeadYaw() - player.getBodyYaw());
        float pitch = state.hasEyeTarget() ? state.getEyeState().getTargetPitch() : player.getPitch();
        var dialogue = state.getDialogueState();
        boolean alive = skin.usesWaterlightGradient() && config.enableShannonDialogueMotion;
        var activity = alive ? dialogue.activity() : com.expression.state.DialogueActivity.IDLE;
        if (alive && dialogue.hasTarget()) { yaw = dialogue.yaw(); pitch = dialogue.pitch(); }
        float targetX = config.enableEyeMovement ? Math.clamp(yaw / 30, -1, 1) * .4f : 0;
        float targetY = config.enableEyeMovement ? Math.clamp(pitch / 60, -1, 1) * .2f : 0;
        if (config.enableExpressions && config.enableGazeAversion) {
            targetX += pose.gazeX();
            targetY += pose.gazeY();
        }
        long now = System.nanoTime();
        if (!alive && config.enableEyeMovement && config.enableMicroSaccades) {
            double phase = now / 1_000_000_000.0 + (player.getUuid().hashCode() & 255);
            targetX += (float) Math.sin(phase * 2.1) * .025f;
            targetY += (float) Math.sin(phase * 1.3) * .015f;
        }
        var gaze = GAZE.computeIfAbsent(player.getUuid(), id -> new Gaze());
        double elapsed = Math.clamp((now - gaze.at) / 1_000_000_000.0, 0, .25);
        float speed = Math.clamp(config.eyeMovementSpeed, .1f, 1);
        float blend = (float) (1 - Math.exp(-elapsed * (4 + speed * 32)));
        gaze.x += (Math.clamp(targetX, -.6f, .6f) - gaze.x) * blend;
        gaze.y += (Math.clamp(targetY, -.4f, .4f) - gaze.y) * blend;
        gaze.at = now;
        float headSpeed = Math.clamp(config.headMovementSpeed, .01f, .2f);
        float headBlend = (float) (1 - Math.exp(-elapsed * (4 + headSpeed * 64)));
        gaze.headYaw += (pose.headYaw() - gaze.headYaw) * headBlend;
        gaze.headPitch += (pose.headPitch() - gaze.headPitch) * headBlend;
        float pitchOpen = config.enableEyeMovement ? Math.clamp(1 - pitch / 100, .6f, 1.3f) : 1;
        float breath = !alive && config.enableMicroSaccades ? 1 + (float) Math.sin(now / 1_000_000_000.0 * 1.28) * .006f * pose.energy() : 1;
        pose = new FacePose(Math.min(1.5f, pose.leftOpen() * pitchOpen * breath), Math.min(1.5f, pose.rightOpen() * pitchOpen * breath),
                pose.leftTilt(), pose.rightTilt(), pose.gazeX(), pose.gazeY(), gaze.headYaw, gaze.headPitch, pose.blush(),
                pose.leftWidth() * breath, pose.rightWidth() * breath, pose.energy());
        float visibleX = gaze.x, visibleY = gaze.y;
        if (alive) {
            // Gaze aversion was already added to the target above. Do not apply it twice.
            var input = new FacePose(pose.leftOpen(), pose.rightOpen(), pose.leftTilt(), pose.rightTilt(), 0, 0,
                    pose.headYaw(), pose.headPitch(), pose.blush(), pose.leftWidth(), pose.rightWidth(), pose.energy());
            var sample = gaze.motion.sample(input, state.getCurrentEmote(), activity, gaze.x, gaze.y,
                    now / 1_000_000_000.0, player.getUuid().hashCode(), config.enableMicroSaccades);
            pose = sample.pose();
            if (config.enableEyeMovement) { visibleX = sample.gazeX(); visibleY = sample.gazeY(); }
            state.getBlinkState().configure((long) (config.blinkIntervalMin * sample.blinkIntervalScale()),
                    (long) (config.blinkIntervalMax * sample.blinkIntervalScale()));
            state.getBlinkState().openingScale(sample.blinkOpenScale());
            if (gaze.previousActivity == com.expression.state.DialogueActivity.SPEAKING
                    && activity == com.expression.state.DialogueActivity.IDLE) state.getBlinkState().triggerBlink();
            gaze.previousActivity = activity;
        } else {
            state.getBlinkState().configure(config.blinkIntervalMin, config.blinkIntervalMax);
            state.getBlinkState().openingScale(1);
        }
        float blink = closed ? 1 : config.enableBlinking ? state.getBlinkState().getBlinkProgress() : 0;
        double circuitSeconds = config.enableShannonCircuitFlow && skin.usesWaterlightGradient()
                ? now / 1_000_000_000.0 : -1;
        double eyeGradientSeconds = config.enableShannonEyeGradient && skin.usesWaterlightGradient()
                ? now / 1_000_000_000.0 : -1;
        return new ExpressionRenderState.Frame(player.getUuid(), skin, pose, blink, visibleX, visibleY,
                config.enableHeadMovement, circuitSeconds, eyeGradientSeconds);
    }

    public static void retain(Set<UUID> players) { GAZE.keySet().retainAll(players); }
    public static void clear() { GAZE.clear(); }
    private FaceRuntime() {}
}
