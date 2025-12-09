package com.expression.state;

/**
 * まばたき（ブリンク）の状態を管理するクラス
 */
public class BlinkState {
    private boolean isBlinking = false;
    private long blinkStartTime = 0;
    private long nextBlinkTime = 0;
    private float blinkProgress = 0; // 0=開, 1=閉

    // 定数
    private static final long BLINK_INTERVAL_MIN = 3000; // 最小間隔（3秒）
    private static final long BLINK_INTERVAL_MAX = 6000; // 最大間隔（6秒）
    private static final long BLINK_DURATION_MS = 150; // まばたき時間（150ms）
    private static final long BLINK_CLOSE_TIME = 50; // 閉じるまでの時間
    private static final long BLINK_OPEN_TIME = 100; // 開くまでの時間

    public BlinkState() {
        scheduleNextBlink();
    }

    /**
     * 強制的にまばたきを開始
     */
    public void triggerBlink() {
        if (!isBlinking) {
            isBlinking = true;
            blinkStartTime = System.currentTimeMillis();
        }
    }

    /**
     * 毎tick更新処理
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();

        if (isBlinking) {
            // まばたき中の進行
            long elapsed = currentTime - blinkStartTime;

            if (elapsed < BLINK_CLOSE_TIME) {
                // 閉じる途中
                blinkProgress = (float) elapsed / BLINK_CLOSE_TIME;
            } else if (elapsed < BLINK_CLOSE_TIME + BLINK_OPEN_TIME) {
                // 開く途中
                blinkProgress = 1.0f - ((float) (elapsed - BLINK_CLOSE_TIME) / BLINK_OPEN_TIME);
            } else {
                // まばたき終了
                isBlinking = false;
                blinkProgress = 0;
                scheduleNextBlink();
            }
        } else {
            // 次のまばたき時間をチェック
            if (currentTime >= nextBlinkTime) {
                triggerBlink();
            }
        }
    }

    /**
     * 大きな動きに合わせてまばたきを誘発
     * （視線の大きな移動時などに呼ぶ）
     */
    public void triggerOnMovement() {
        // 次のまばたきが近い場合（1秒以内）は即座にまばたき
        if (!isBlinking && System.currentTimeMillis() >= nextBlinkTime - 1000) {
            triggerBlink();
        }
    }

    private void scheduleNextBlink() {
        nextBlinkTime = System.currentTimeMillis() +
                BLINK_INTERVAL_MIN +
                (long) (Math.random() * (BLINK_INTERVAL_MAX - BLINK_INTERVAL_MIN));
    }

    // Getters
    public boolean isBlinking() {
        return isBlinking;
    }

    public float getBlinkProgress() {
        return blinkProgress;
    }

    /**
     * 目が閉じているかどうか
     */
    public boolean isEyeClosed() {
        return blinkProgress > 0.8f;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        isBlinking = false;
        blinkProgress = 0;
        scheduleNextBlink();
    }
}
