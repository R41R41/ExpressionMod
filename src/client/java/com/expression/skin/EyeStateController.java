package com.expression.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;

/**
 * 目の状態を管理するコントローラー
 * - まばたき（自動）
 * - アイトラッキング（head_yaw/head_pitch）
 * - 睡眠/盲目
 */
public class EyeStateController {

    private static BlinkTextureManager.EyeState currentState = BlinkTextureManager.EyeState.NORMAL;
    private static BlinkTextureManager.EyePosition currentPosition = BlinkTextureManager.EyePosition.CENTER;

    private static long lastBlinkTime = 0;
    private static long nextBlinkInterval = 3000;
    private static long blinkDuration = 150;
    private static boolean isBlinking = false;
    private static long blinkStartTime = 0;

    private static float lastHeadYaw = 0;
    private static float lastHeadPitch = 0;
    private static long lastHeadMoveTime = 0;
    private static float headYawVelocity = 0;
    private static float headPitchVelocity = 0;

    private static final float YAW_THRESHOLD = 5.0f;
    private static final float PITCH_DOWN_THRESHOLD = 25.0f;
    private static final float PITCH_UP_THRESHOLD = -20.0f;
    private static final float VELOCITY_THRESHOLD = 5.0f;

    private static final long EYE_RETURN_DELAY = 200;

    /**
     * 毎ティック更新
     */
    public static void update() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return;

        long currentTime = System.currentTimeMillis();

        if (checkSleepingOrBlind(client.player)) {
            currentState = BlinkTextureManager.EyeState.CLOSED;
            currentPosition = BlinkTextureManager.EyePosition.CENTER;
            return;
        }

        updateEyeTracking(client.player, currentTime);
        updateBlink(currentTime);
    }

    private static boolean checkSleepingOrBlind(AbstractClientPlayer player) {
        if (player.getPose() == Pose.SLEEPING) {
            return true;
        }

        if (player.hasEffect(MobEffects.BLINDNESS)) {
            return true;
        }

        return false;
    }

    private static void updateBlink(long currentTime) {
        if (isBlinking) {
            long elapsed = currentTime - blinkStartTime;

            if (elapsed < blinkDuration / 3) {
                currentState = BlinkTextureManager.EyeState.HALF;
            } else if (elapsed < blinkDuration * 2 / 3) {
                currentState = BlinkTextureManager.EyeState.CLOSED;
            } else if (elapsed < blinkDuration) {
                currentState = BlinkTextureManager.EyeState.HALF;
            } else {
                isBlinking = false;
                currentState = BlinkTextureManager.EyeState.NORMAL;
                lastBlinkTime = currentTime;
                scheduleNextBlink();
            }
            return;
        }

        if (currentTime - lastBlinkTime >= nextBlinkInterval) {
            startBlink(currentTime);
        }
    }

    private static void startBlink(long currentTime) {
        isBlinking = true;
        blinkStartTime = currentTime;
        currentState = BlinkTextureManager.EyeState.HALF;
    }

    private static void scheduleNextBlink() {
        nextBlinkInterval = 3000 + (long) (Math.random() * 3000);
    }

    private static int debugCounter = 0;

    private static float trackedBodyYaw = 0;
    private static boolean bodyYawInitialized = false;
    private static final float BODY_YAW_SPEED = 0.1f;

    private static void updateEyeTracking(AbstractClientPlayer player, long currentTime) {
        float headYaw = player.getYHeadRot();
        float headPitch = player.getXRot();

        if (!bodyYawInitialized) {
            trackedBodyYaw = headYaw;
            bodyYawInitialized = true;
        } else {
            float diff = headYaw - trackedBodyYaw;
            while (diff > 180)
                diff -= 360;
            while (diff < -180)
                diff += 360;

            if (Math.abs(diff) > 45) {
                trackedBodyYaw += diff * 0.3f;
            } else {
                trackedBodyYaw += diff * BODY_YAW_SPEED;
            }
        }

        float relativeYaw = headYaw - trackedBodyYaw;

        while (relativeYaw > 180)
            relativeYaw -= 360;
        while (relativeYaw < -180)
            relativeYaw += 360;

        debugCounter++;
        if (debugCounter % 100 == 0) {
            com.expression.ExpressionMod.LOGGER.info("[EyeStateController] DEBUG: headYaw=" + headYaw +
                    ", trackedBodyYaw=" + trackedBodyYaw + ", relativeYaw=" + relativeYaw + ", threshold="
                    + YAW_THRESHOLD);
        }

        BlinkTextureManager.EyePosition newPosition;
        if (relativeYaw > YAW_THRESHOLD) {
            newPosition = BlinkTextureManager.EyePosition.RIGHT;
        } else if (relativeYaw < -YAW_THRESHOLD) {
            newPosition = BlinkTextureManager.EyePosition.LEFT;
        } else {
            newPosition = BlinkTextureManager.EyePosition.CENTER;
        }

        if (newPosition != currentPosition) {
            com.expression.ExpressionMod.LOGGER.info("[EyeStateController] Position changed: " + currentPosition
                    + " -> " + newPosition + " (relativeYaw=" + relativeYaw + ")");
        }
        currentPosition = newPosition;

        if (!isBlinking) {
            if (headPitch > PITCH_DOWN_THRESHOLD) {
                currentState = BlinkTextureManager.EyeState.HALF;
            } else if (headPitch < PITCH_UP_THRESHOLD) {
                currentState = BlinkTextureManager.EyeState.WIDE;
            } else {
                currentState = BlinkTextureManager.EyeState.NORMAL;
            }
        }

        lastHeadYaw = headYaw;
        lastHeadPitch = headPitch;
    }

    public static BlinkTextureManager.EyeState getCurrentState() {
        return currentState;
    }

    public static BlinkTextureManager.EyePosition getCurrentPosition() {
        return currentPosition;
    }

    public static void reset() {
        currentState = BlinkTextureManager.EyeState.NORMAL;
        currentPosition = BlinkTextureManager.EyePosition.CENTER;
        isBlinking = false;
        lastBlinkTime = System.currentTimeMillis();
        bodyYawInitialized = false;
        scheduleNextBlink();
    }
}
