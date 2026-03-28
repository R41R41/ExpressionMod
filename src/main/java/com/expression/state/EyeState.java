package com.expression.state;

import net.minecraft.world.phys.Vec3;

/**
 * 視線（目）の状態を管理するクラス
 * サッケード、オーバーシュート、マイクロサッケード、視線逸らしを実装
 */
public class EyeState {
    // 現在の目の角度（度）
    private float yaw = 0; // 左右
    private float pitch = 0; // 上下

    // ターゲット角度
    private float targetYaw = 0;
    private float targetPitch = 0;

    // オーバーシュート用
    private float overshootYaw = 0;
    private float overshootPitch = 0;
    private boolean isOvershooting = false;
    private long overshootEndTime = 0;

    // マイクロサッケード用
    private float microSaccadeOffsetYaw = 0;
    private float microSaccadeOffsetPitch = 0;
    private long nextMicroSaccadeTime = 0;

    // 視線逸らし用
    private boolean isAverting = false;
    private long gazeStartTime = 0;
    private long nextAversionCheckTime = 0;
    private float aversionOffsetYaw = 0;
    private float aversionOffsetPitch = 0;

    // LookAtターゲット座標（ワールド座標）
    private Vec3 lookAtTarget = null;

    // 定数
    private static final float SACCADE_SPEED = 0.3f; // サッケード速度（1tick = 約20°移動）
    private static final float OVERSHOOT_AMOUNT = 0.15f; // オーバーシュート量（目標の15%）
    private static final long OVERSHOOT_DURATION_MS = 50; // オーバーシュート持続時間
    private static final float MICRO_SACCADE_RANGE = 2.0f; // マイクロサッケード範囲（±2°）
    private static final long MICRO_SACCADE_INTERVAL_MIN = 300; // マイクロサッケード最小間隔
    private static final long MICRO_SACCADE_INTERVAL_MAX = 800; // マイクロサッケード最大間隔
    private static final long GAZE_AVERSION_THRESHOLD = 5000; // 視線逸らし開始閾値（5秒）
    private static final long GAZE_AVERSION_MAX_TIME = 10000; // 視線逸らし最大時間（10秒）
    private static final float AVERSION_ANGLE = 20.0f; // 視線逸らし角度

    // 目の回転制限
    public static final float MAX_YAW = 40.0f;
    public static final float MAX_PITCH = 30.0f;

    public EyeState() {
        scheduleNextMicroSaccade();
        nextAversionCheckTime = System.currentTimeMillis() + GAZE_AVERSION_THRESHOLD;
    }

    /**
     * LookAtターゲットを設定
     */
    public void setLookAtTarget(Vec3 target) {
        if (target == null) {
            this.lookAtTarget = null;
            return;
        }

        // 新しいターゲットが設定された場合、視線逸らしをリセット
        if (this.lookAtTarget == null || !this.lookAtTarget.equals(target)) {
            this.gazeStartTime = System.currentTimeMillis();
            this.isAverting = false;
            this.aversionOffsetYaw = 0;
            this.aversionOffsetPitch = 0;
        }

        this.lookAtTarget = target;
    }

    /**
     * ターゲット角度を直接設定（サッケード開始）
     */
    public void setTargetAngles(float yaw, float pitch) {
        float clampedYaw = Math.max(-MAX_YAW, Math.min(MAX_YAW, yaw));
        float clampedPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));

        // 大きな移動の場合はオーバーシュートを設定
        float deltaYaw = Math.abs(clampedYaw - this.targetYaw);
        float deltaPitch = Math.abs(clampedPitch - this.targetPitch);

        if (deltaYaw > 5 || deltaPitch > 5) {
            // オーバーシュート計算
            float overshootDir = clampedYaw > this.targetYaw ? 1 : -1;
            this.overshootYaw = clampedYaw + (deltaYaw * OVERSHOOT_AMOUNT * overshootDir);

            overshootDir = clampedPitch > this.targetPitch ? 1 : -1;
            this.overshootPitch = clampedPitch + (deltaPitch * OVERSHOOT_AMOUNT * overshootDir);

            this.isOvershooting = true;
            this.overshootEndTime = System.currentTimeMillis() + OVERSHOOT_DURATION_MS;
        }

        this.targetYaw = clampedYaw;
        this.targetPitch = clampedPitch;

        // 視線変更時は視線逸らしタイマーリセット
        this.gazeStartTime = System.currentTimeMillis();
        this.isAverting = false;
    }

    /**
     * 毎tick更新処理
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();

        // 実際のターゲット（オーバーシュート考慮）
        float actualTargetYaw = isOvershooting ? overshootYaw : targetYaw;
        float actualTargetPitch = isOvershooting ? overshootPitch : targetPitch;

        // オーバーシュート終了チェック
        if (isOvershooting && currentTime >= overshootEndTime) {
            isOvershooting = false;
        }

        // 視線逸らしチェック
        updateGazeAversion(currentTime);

        // 視線逸らし中はオフセットを適用
        if (isAverting) {
            actualTargetYaw += aversionOffsetYaw;
            actualTargetPitch += aversionOffsetPitch;
        }

        // マイクロサッケード更新
        updateMicroSaccade(currentTime);
        actualTargetYaw += microSaccadeOffsetYaw;
        actualTargetPitch += microSaccadeOffsetPitch;

        // 補間で目を移動
        yaw = lerp(yaw, actualTargetYaw, SACCADE_SPEED);
        pitch = lerp(pitch, actualTargetPitch, SACCADE_SPEED);

        // 制限適用
        yaw = Math.max(-MAX_YAW, Math.min(MAX_YAW, yaw));
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    /**
     * 視線逸らし更新
     */
    private void updateGazeAversion(long currentTime) {
        if (lookAtTarget == null) {
            isAverting = false;
            return;
        }

        long gazeDuration = currentTime - gazeStartTime;

        if (!isAverting && gazeDuration > GAZE_AVERSION_THRESHOLD && currentTime >= nextAversionCheckTime) {
            // 視線逸らし開始（ランダムに判定）
            if (Math.random() < 0.3) { // 30%の確率で視線逸らし
                isAverting = true;

                // ランダムな方向に視線を逸らす
                double angle = Math.random() * Math.PI * 2;
                aversionOffsetYaw = (float) (Math.cos(angle) * AVERSION_ANGLE);
                aversionOffsetPitch = (float) (Math.sin(angle) * AVERSION_ANGLE * 0.5); // 上下は控えめ

                // 一定時間後に戻る
                nextAversionCheckTime = currentTime + 500 + (long) (Math.random() * 1000);
            } else {
                nextAversionCheckTime = currentTime + 2000;
            }
        } else if (isAverting && currentTime >= nextAversionCheckTime) {
            // 視線逸らし終了
            isAverting = false;
            aversionOffsetYaw = 0;
            aversionOffsetPitch = 0;
            gazeStartTime = currentTime; // タイマーリセット
            nextAversionCheckTime = currentTime + GAZE_AVERSION_THRESHOLD;
        }
    }

    /**
     * マイクロサッケード更新
     */
    private void updateMicroSaccade(long currentTime) {
        if (currentTime >= nextMicroSaccadeTime) {
            // 新しいマイクロサッケード
            microSaccadeOffsetYaw = (float) ((Math.random() - 0.5) * 2 * MICRO_SACCADE_RANGE);
            microSaccadeOffsetPitch = (float) ((Math.random() - 0.5) * 2 * MICRO_SACCADE_RANGE);
            scheduleNextMicroSaccade();
        }
    }

    private void scheduleNextMicroSaccade() {
        nextMicroSaccadeTime = System.currentTimeMillis() +
                MICRO_SACCADE_INTERVAL_MIN +
                (long) (Math.random() * (MICRO_SACCADE_INTERVAL_MAX - MICRO_SACCADE_INTERVAL_MIN));
    }

    private float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    // Getters
    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getTargetYaw() {
        return targetYaw;
    }

    public float getTargetPitch() {
        return targetPitch;
    }

    public Vec3 getLookAtTarget() {
        return lookAtTarget;
    }

    public boolean isAverting() {
        return isAverting;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        yaw = 0;
        pitch = 0;
        targetYaw = 0;
        targetPitch = 0;
        isOvershooting = false;
        isAverting = false;
        lookAtTarget = null;
        microSaccadeOffsetYaw = 0;
        microSaccadeOffsetPitch = 0;
        aversionOffsetYaw = 0;
        aversionOffsetPitch = 0;
    }
}
