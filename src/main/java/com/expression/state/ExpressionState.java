package com.expression.state;

import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * プレイヤーの表情・視線・頭部状態を統合管理するクラス
 */
public class ExpressionState {
    private final UUID playerUuid;
    private final String playerName;

    private final EmoteState emoteState;
    private final EyeState eyeState;
    private final HeadState headState;
    private final BlinkState blinkState;

    // 最終更新時刻（同期用）
    private long lastUpdateTime;

    public ExpressionState(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.emoteState = new EmoteState();
        this.eyeState = new EyeState();
        this.headState = new HeadState();
        this.blinkState = new BlinkState();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 毎tick更新処理
     */
    public void tick() {
        // 状態更新
        emoteState.tick();
        eyeState.tick();
        headState.updateFromEyeState(eyeState);
        headState.setEmoteOffset(emoteState.getCurrentEmote());
        headState.tick();
        blinkState.tick();

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * エモートを設定
     */
    public void setEmote(EmoteType emote, long durationMs) {
        emoteState.setEmote(emote, durationMs);
    }

    /**
     * LookAtターゲットを設定（ワールド座標）
     */
    public void setLookAtTarget(Vec3d target) {
        eyeState.setLookAtTarget(target);
    }

    /**
     * 視線角度を直接設定
     */
    public void setEyeAngles(float yaw, float pitch) {
        // 大きな動きの場合はまばたきを誘発
        float deltaYaw = Math.abs(yaw - eyeState.getTargetYaw());
        float deltaPitch = Math.abs(pitch - eyeState.getTargetPitch());
        if (deltaYaw > 15 || deltaPitch > 15) {
            blinkState.triggerOnMovement();
        }

        eyeState.setTargetAngles(yaw, pitch);
    }

    /**
     * プレイヤーの位置と向きからターゲット座標を計算し、視線を設定
     */
    public void lookAt(Vec3d playerEyePos, Vec3d target) {
        if (target == null) {
            eyeState.setLookAtTarget(null);
            return;
        }

        Vec3d direction = target.subtract(playerEyePos).normalize();

        // 方向ベクトルから角度を計算
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(-Math.asin(direction.y));

        setEyeAngles(yaw, pitch);
        eyeState.setLookAtTarget(target);
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        emoteState.reset();
        eyeState.reset();
        headState.reset();
        blinkState.reset();
    }

    // Getters
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public EmoteState getEmoteState() {
        return emoteState;
    }

    public EyeState getEyeState() {
        return eyeState;
    }

    public HeadState getHeadState() {
        return headState;
    }

    public BlinkState getBlinkState() {
        return blinkState;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    // 便利メソッド
    public EmoteType getCurrentEmote() {
        return emoteState.getCurrentEmote();
    }

    public float getEyeYaw() {
        return eyeState.getYaw();
    }

    public float getEyePitch() {
        return eyeState.getPitch();
    }

    public float getHeadYawOffset() {
        return headState.getYawOffset();
    }

    public float getHeadPitchOffset() {
        return headState.getPitchOffset();
    }

    public boolean isEyeClosed() {
        return blinkState.isEyeClosed();
    }

    public float getBlinkProgress() {
        return blinkState.getBlinkProgress();
    }
}
