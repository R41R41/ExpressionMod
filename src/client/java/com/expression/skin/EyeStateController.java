package com.expression.skin;

import com.expression.ExpressionMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EntityPose;

/**
 * 目の状態を管理するコントローラー
 * - まばたき（自動）
 * - アイトラッキング（head_yaw/head_pitch）
 * - 睡眠/盲目
 */
public class EyeStateController {

    // 現在の目の状態
    private static BlinkTextureManager.EyeState currentState = BlinkTextureManager.EyeState.NORMAL;
    private static BlinkTextureManager.EyePosition currentPosition = BlinkTextureManager.EyePosition.CENTER;

    // まばたき関連
    private static long lastBlinkTime = 0;
    private static long nextBlinkInterval = 3000; // ミリ秒
    private static long blinkDuration = 150; // まばたきの長さ（ミリ秒）
    private static boolean isBlinking = false;
    private static long blinkStartTime = 0;

    // アイトラッキング関連
    private static float lastHeadYaw = 0;
    private static float lastHeadPitch = 0;
    private static long lastHeadMoveTime = 0;
    private static float headYawVelocity = 0;
    private static float headPitchVelocity = 0;

    // しきい値
    private static final float YAW_THRESHOLD = 5.0f; // 左右の目移動のしきい値（度）
    private static final float PITCH_DOWN_THRESHOLD = 25.0f; // 下向きで半目になるしきい値
    private static final float PITCH_UP_THRESHOLD = -20.0f; // 上向きで見開きになるしきい値
    private static final float VELOCITY_THRESHOLD = 5.0f; // 頭の動きの速度しきい値

    // 目の位置が正面に戻るまでの時間（ミリ秒）
    private static final long EYE_RETURN_DELAY = 200;

    /**
     * 毎ティック更新
     */
    public static void update() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;

        long currentTime = System.currentTimeMillis();

        // 睡眠/盲目チェック（最優先）
        if (checkSleepingOrBlind(client.player)) {
            currentState = BlinkTextureManager.EyeState.CLOSED;
            currentPosition = BlinkTextureManager.EyePosition.CENTER;
            return;
        }

        // アイトラッキング処理（まばたき中でもpositionは更新）
        updateEyeTracking(client.player, currentTime);

        // まばたき処理（stateを上書きする可能性あり）
        updateBlink(currentTime);
    }

    /**
     * 睡眠/盲目チェック
     */
    private static boolean checkSleepingOrBlind(AbstractClientPlayerEntity player) {
        // 睡眠中
        if (player.getPose() == EntityPose.SLEEPING) {
            return true;
        }

        // 盲目エフェクト
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return true;
        }

        return false;
    }

    /**
     * まばたき更新
     */
    private static void updateBlink(long currentTime) {
        // まばたき中の処理
        if (isBlinking) {
            long elapsed = currentTime - blinkStartTime;

            if (elapsed < blinkDuration / 3) {
                // 閉じる途中
                currentState = BlinkTextureManager.EyeState.HALF;
            } else if (elapsed < blinkDuration * 2 / 3) {
                // 完全に閉じる
                currentState = BlinkTextureManager.EyeState.CLOSED;
            } else if (elapsed < blinkDuration) {
                // 開く途中
                currentState = BlinkTextureManager.EyeState.HALF;
            } else {
                // まばたき終了
                isBlinking = false;
                currentState = BlinkTextureManager.EyeState.NORMAL;
                lastBlinkTime = currentTime;
                scheduleNextBlink();
            }
            return;
        }

        // 次のまばたきをチェック
        if (currentTime - lastBlinkTime >= nextBlinkInterval) {
            startBlink(currentTime);
        }
    }

    /**
     * まばたき開始
     */
    private static void startBlink(long currentTime) {
        isBlinking = true;
        blinkStartTime = currentTime;
        currentState = BlinkTextureManager.EyeState.HALF;
    }

    /**
     * 次のまばたき間隔を設定
     */
    private static void scheduleNextBlink() {
        // 3〜6秒のランダム間隔
        nextBlinkInterval = 3000 + (long) (Math.random() * 3000);
    }

    // デバッグ用カウンター
    private static int debugCounter = 0;

    // 体のYawを追跡（ゆっくり頭に追従）
    private static float trackedBodyYaw = 0;
    private static boolean bodyYawInitialized = false;
    private static final float BODY_YAW_SPEED = 0.1f; // 体が頭に追従する速度

    /**
     * アイトラッキング更新
     * 頭の向きに応じて目の位置と状態を決定
     */
    private static void updateEyeTracking(AbstractClientPlayerEntity player, long currentTime) {
        float headYaw = player.getHeadYaw();
        float headPitch = player.getPitch();

        // 体のYawを自分で追跡（ゆっくり頭に追従させる）
        if (!bodyYawInitialized) {
            trackedBodyYaw = headYaw;
            bodyYawInitialized = true;
        } else {
            // 体は頭の方向にゆっくり追従
            float diff = headYaw - trackedBodyYaw;
            // -180〜180の範囲に正規化
            while (diff > 180)
                diff -= 360;
            while (diff < -180)
                diff += 360;

            // 体を頭の方向に少しずつ動かす（ただし最大角度を制限）
            if (Math.abs(diff) > 45) {
                // 45度以上離れたら体も追従を早める
                trackedBodyYaw += diff * 0.3f;
            } else {
                trackedBodyYaw += diff * BODY_YAW_SPEED;
            }
        }

        float relativeYaw = headYaw - trackedBodyYaw;

        // -180〜180の範囲に正規化
        while (relativeYaw > 180)
            relativeYaw -= 360;
        while (relativeYaw < -180)
            relativeYaw += 360;

        // デバッグ: 100tickごとにログ
        debugCounter++;
        if (debugCounter % 100 == 0) {
            com.expression.ExpressionMod.LOGGER.info("[EyeStateController] DEBUG: headYaw=" + headYaw +
                    ", trackedBodyYaw=" + trackedBodyYaw + ", relativeYaw=" + relativeYaw + ", threshold="
                    + YAW_THRESHOLD);
        }

        // 左右の目の位置を決定（頭の向きに合わせてずっと維持）
        BlinkTextureManager.EyePosition newPosition;
        if (relativeYaw > YAW_THRESHOLD) {
            newPosition = BlinkTextureManager.EyePosition.RIGHT;
        } else if (relativeYaw < -YAW_THRESHOLD) {
            newPosition = BlinkTextureManager.EyePosition.LEFT;
        } else {
            newPosition = BlinkTextureManager.EyePosition.CENTER;
        }

        // デバッグログ（位置が変わった時のみ）
        if (newPosition != currentPosition) {
            com.expression.ExpressionMod.LOGGER.info("[EyeStateController] Position changed: " + currentPosition
                    + " -> " + newPosition + " (relativeYaw=" + relativeYaw + ")");
        }
        currentPosition = newPosition;

        // 上下の目の状態を決定（まばたき中でなければ）
        if (!isBlinking) {
            if (headPitch > PITCH_DOWN_THRESHOLD) {
                currentState = BlinkTextureManager.EyeState.HALF; // 下向き→半目
            } else if (headPitch < PITCH_UP_THRESHOLD) {
                currentState = BlinkTextureManager.EyeState.WIDE; // 上向き→見開き
            } else {
                currentState = BlinkTextureManager.EyeState.NORMAL;
            }
        }

        lastHeadYaw = headYaw;
        lastHeadPitch = headPitch;
    }

    /**
     * 現在の目の状態を取得
     */
    public static BlinkTextureManager.EyeState getCurrentState() {
        return currentState;
    }

    /**
     * 現在の目の位置を取得
     */
    public static BlinkTextureManager.EyePosition getCurrentPosition() {
        return currentPosition;
    }

    /**
     * 状態をリセット
     */
    public static void reset() {
        currentState = BlinkTextureManager.EyeState.NORMAL;
        currentPosition = BlinkTextureManager.EyePosition.CENTER;
        isBlinking = false;
        lastBlinkTime = System.currentTimeMillis();
        bodyYawInitialized = false;
        scheduleNextBlink();
    }
}
