package com.expression.state;

import net.minecraft.util.math.Vec3d;

/**
 * 頭部の状態を管理するクラス
 * 目に遅れてターゲット方向へ追従する
 */
public class HeadState {
    // 現在の頭の角度オフセット（度）- 元の向きからの差分
    private float yawOffset = 0;
    private float pitchOffset = 0;

    // ターゲット角度オフセット
    private float targetYawOffset = 0;
    private float targetPitchOffset = 0;

    // エモートによる補正
    private float emotePitchOffset = 0;
    private float emoteYawOffset = 0;

    // 定数
    private static final float HEAD_SPEED = 0.08f; // 頭の回転速度（目の20-40%）
    private static final float DELAY_FACTOR = 0.6f; // 目に対する遅延係数

    // 頭の回転制限
    public static final float MAX_YAW_OFFSET = 30.0f;
    public static final float MAX_PITCH_OFFSET = 25.0f;

    /**
     * EyeStateに基づいて頭部のターゲットを更新
     */
    public void updateFromEyeState(EyeState eyeState) {
        // 目のターゲットに対して、遅延＋小さめの角度で追従
        targetYawOffset = eyeState.getTargetYaw() * DELAY_FACTOR;
        targetPitchOffset = eyeState.getTargetPitch() * DELAY_FACTOR;

        // 制限適用
        targetYawOffset = clamp(targetYawOffset, -MAX_YAW_OFFSET, MAX_YAW_OFFSET);
        targetPitchOffset = clamp(targetPitchOffset, -MAX_PITCH_OFFSET, MAX_PITCH_OFFSET);
    }

    /**
     * エモートによる頭部補正を設定
     */
    public void setEmoteOffset(EmoteType emote) {
        switch (emote) {
            case SHY:
                // 恥じらい：少し俯く＋横を向く
                emotePitchOffset = 10.0f;
                emoteYawOffset = 15.0f;
                break;
            case THINK:
                // 考え込み：やや上を見る
                emotePitchOffset = -10.0f;
                emoteYawOffset = 5.0f;
                break;
            case SAD:
                // 悲しみ：下を向く
                emotePitchOffset = 8.0f;
                emoteYawOffset = 0;
                break;
            case SURPRISED:
                // 驚き：少し後ろに引く感じ
                emotePitchOffset = -5.0f;
                emoteYawOffset = 0;
                break;
            default:
                emotePitchOffset = 0;
                emoteYawOffset = 0;
                break;
        }
    }

    /**
     * 毎tick更新処理
     */
    public void tick() {
        // 目標角度（エモート補正込み）
        float finalTargetYaw = targetYawOffset + emoteYawOffset;
        float finalTargetPitch = targetPitchOffset + emotePitchOffset;

        // ゆっくり補間
        yawOffset = lerp(yawOffset, finalTargetYaw, HEAD_SPEED);
        pitchOffset = lerp(pitchOffset, finalTargetPitch, HEAD_SPEED);

        // 制限適用
        yawOffset = clamp(yawOffset, -MAX_YAW_OFFSET, MAX_YAW_OFFSET);
        pitchOffset = clamp(pitchOffset, -MAX_PITCH_OFFSET, MAX_PITCH_OFFSET);
    }

    private float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // Getters
    public float getYawOffset() {
        return yawOffset;
    }

    public float getPitchOffset() {
        return pitchOffset;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        yawOffset = 0;
        pitchOffset = 0;
        targetYawOffset = 0;
        targetPitchOffset = 0;
        emotePitchOffset = 0;
        emoteYawOffset = 0;
    }
}
