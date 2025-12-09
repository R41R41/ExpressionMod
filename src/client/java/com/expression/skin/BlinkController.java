package com.expression.skin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * スムーズなまばたきを制御するクラス
 * 
 * まつ毛のY座標を連続的に変化させることで、
 * コマ送りではないスムーズなアニメーションを実現
 */
public class BlinkController {

    // まばたき設定
    private static final int BLINK_INTERVAL_MIN = 60; // 最小間隔 (3秒)
    private static final int BLINK_INTERVAL_MAX = 120; // 最大間隔 (6秒)
    private static final int BLINK_DURATION = 6; // まばたき全体の長さ (0.3秒)

    // まつ毛の最大移動量（ピクセル）
    // 目が2px高なので、まつ毛が2px下がると完全に閉じる
    public static final float MAX_EYELID_OFFSET = 2.0f;

    private static final Map<UUID, BlinkState> blinkStates = new HashMap<>();
    private static final Random random = new Random();

    /**
     * まばたき状態
     */
    public static class BlinkState {
        // 次のまばたきまでのtick
        public int nextBlinkIn;

        // 現在まばたき中か
        public boolean isBlinking = false;

        // まばたきの進行度 (0 = 開始, BLINK_DURATION = 終了)
        public int blinkTick = 0;

        // 強制的に目を閉じるか（睡眠、盲目）
        public boolean forceClose = false;

        public BlinkState() {
            this.nextBlinkIn = randomInterval();
        }
    }

    /**
     * ランダムなまばたき間隔を生成
     */
    private static int randomInterval() {
        return BLINK_INTERVAL_MIN + random.nextInt(BLINK_INTERVAL_MAX - BLINK_INTERVAL_MIN);
    }

    /**
     * プレイヤーのまばたき状態を取得または作成
     */
    public static BlinkState getOrCreateState(UUID playerUuid) {
        return blinkStates.computeIfAbsent(playerUuid, k -> new BlinkState());
    }

    /**
     * まばたきを更新（毎tick呼び出す）
     * 
     * @param playerUuid プレイヤーUUID
     * @param isSleeping 睡眠中か
     * @param isBlind    盲目か
     */
    public static void update(UUID playerUuid, boolean isSleeping, boolean isBlind) {
        BlinkState state = getOrCreateState(playerUuid);

        // 強制閉じ目
        state.forceClose = isSleeping || isBlind;

        if (state.forceClose) {
            state.isBlinking = false;
            return;
        }

        if (state.isBlinking) {
            // まばたき中
            state.blinkTick++;
            if (state.blinkTick >= BLINK_DURATION) {
                // まばたき終了
                state.isBlinking = false;
                state.blinkTick = 0;
                state.nextBlinkIn = randomInterval();
            }
        } else {
            // 待機中
            state.nextBlinkIn--;
            if (state.nextBlinkIn <= 0) {
                // まばたき開始
                state.isBlinking = true;
                state.blinkTick = 0;
            }
        }
    }

    /**
     * まつ毛のY座標オフセットを取得（スムーズ補間付き）
     * 
     * @param playerUuid プレイヤーUUID
     * @param tickDelta  フレーム補間値 (0.0-1.0)
     * @return まつ毛のYオフセット (0.0 = 開いている, MAX_EYELID_OFFSET = 閉じている)
     */
    public static float getEyelidOffset(UUID playerUuid, float tickDelta) {
        BlinkState state = blinkStates.get(playerUuid);
        if (state == null) {
            return 0.0f;
        }

        // 強制閉じ目
        if (state.forceClose) {
            return MAX_EYELID_OFFSET;
        }

        if (!state.isBlinking) {
            return 0.0f;
        }

        // まばたきの進行度を計算 (0.0 - 1.0 - 0.0)
        // 前半: 閉じる、後半: 開く
        float progress = (state.blinkTick + tickDelta) / BLINK_DURATION;

        // サイン波で滑らかに (0→1→0)
        float smoothProgress = (float) Math.sin(progress * Math.PI);

        return smoothProgress * MAX_EYELID_OFFSET;
    }

    /**
     * 目の可視高さを取得
     * 
     * @param playerUuid     プレイヤーUUID
     * @param tickDelta      フレーム補間値
     * @param totalEyeHeight 目の全体高さ（ピクセル）
     * @return 可視高さ（ピクセル、0 = 完全に閉じている）
     */
    public static float getVisibleEyeHeight(UUID playerUuid, float tickDelta, float totalEyeHeight) {
        float offset = getEyelidOffset(playerUuid, tickDelta);
        return Math.max(0, totalEyeHeight - offset);
    }

    /**
     * まばたきの進行度を取得 (0.0 = 開いている, 1.0 = 閉じている)
     */
    public static float getBlinkProgress(UUID playerUuid, float tickDelta) {
        return getEyelidOffset(playerUuid, tickDelta) / MAX_EYELID_OFFSET;
    }

    /**
     * 目が閉じているか
     */
    public static boolean isEyeClosed(UUID playerUuid) {
        BlinkState state = blinkStates.get(playerUuid);
        if (state == null)
            return false;

        if (state.forceClose)
            return true;

        if (!state.isBlinking)
            return false;

        // まばたきの真ん中あたりで閉じていると判定
        float progress = (float) state.blinkTick / BLINK_DURATION;
        return progress > 0.3f && progress < 0.7f;
    }

    /**
     * 強制的にまばたきを開始
     */
    public static void forceBlink(UUID playerUuid) {
        BlinkState state = getOrCreateState(playerUuid);
        state.isBlinking = true;
        state.blinkTick = 0;
    }

    /**
     * 特定プレイヤーの状態をリセット
     */
    public static void resetState(UUID playerUuid) {
        blinkStates.remove(playerUuid);
    }

    /**
     * 全状態をクリア
     */
    public static void clearAllStates() {
        blinkStates.clear();
    }

    // ========== グローバルまばたき（テクスチャ切り替え用） ==========

    private static int globalNextBlinkIn = randomInterval();
    private static boolean globalIsBlinking = false;
    private static int globalBlinkTick = 0;

    /**
     * グローバルまばたきを更新（全プレイヤー共通）
     */
    public static void updateGlobal() {
        if (globalIsBlinking) {
            globalBlinkTick++;
            if (globalBlinkTick >= BLINK_DURATION) {
                globalIsBlinking = false;
                globalBlinkTick = 0;
                globalNextBlinkIn = randomInterval();
            }
        } else {
            globalNextBlinkIn--;
            if (globalNextBlinkIn <= 0) {
                globalIsBlinking = true;
                globalBlinkTick = 0;
            }
        }
    }

    /**
     * テクスチャ切り替え用のフレーム番号を取得
     * 
     * @return -1=通常(開いている), 0-1=まばたきフレーム
     * @deprecated EyeStateControllerを使用してください
     */
    @Deprecated
    public static int getGlobalBlinkFrame() {
        if (!globalIsBlinking) {
            return -1; // 通常状態（元のスキン）
        }

        // まばたきの進行度を計算
        float progress = (float) globalBlinkTick / BLINK_DURATION;

        // 0-0.5: 閉じていく (0→1)
        // 0.5-1.0: 開いていく (1→0)
        int frame;
        if (progress < 0.5f) {
            // 閉じていく
            frame = (int) (progress * 2 * 2); // 2フレーム
        } else {
            // 開いていく
            frame = (int) ((1.0f - progress) * 2 * 2);
        }

        return Math.min(frame, 1);
    }

    /**
     * まばたき中か（グローバル）
     */
    public static boolean isGlobalBlinking() {
        return globalIsBlinking;
    }
}
