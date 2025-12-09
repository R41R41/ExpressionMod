package com.expression.state;

/**
 * エモート（表情）の状態を管理するクラス
 * duration_ms経過後にneutralに自動復帰する
 */
public class EmoteState {
    private EmoteType currentEmote = EmoteType.NEUTRAL;
    private long endTimeMs = 0;
    private boolean isTransitioning = false;
    private float transitionProgress = 1.0f;
    private EmoteType previousEmote = EmoteType.NEUTRAL;

    // トランジション時間（ミリ秒）
    private static final long TRANSITION_DURATION_MS = 150;

    // デフォルトのエモート持続時間
    public static final long DEFAULT_DURATION_MS = 3000;

    /**
     * エモートを設定
     * 
     * @param emote      設定するエモート
     * @param durationMs 持続時間（0の場合はデフォルト値を使用）
     */
    public void setEmote(EmoteType emote, long durationMs) {
        if (emote == currentEmote && !isNeutral()) {
            // 同じエモートの場合は時間を延長
            this.endTimeMs = System.currentTimeMillis() + (durationMs > 0 ? durationMs : DEFAULT_DURATION_MS);
            return;
        }

        // トランジション開始
        this.previousEmote = this.currentEmote;
        this.currentEmote = emote;
        this.transitionProgress = 0.0f;
        this.isTransitioning = true;

        if (emote != EmoteType.NEUTRAL) {
            this.endTimeMs = System.currentTimeMillis() + (durationMs > 0 ? durationMs : DEFAULT_DURATION_MS);
        } else {
            this.endTimeMs = 0;
        }
    }

    /**
     * 毎tick更新処理
     */
    public void tick() {
        // トランジション更新
        if (isTransitioning) {
            transitionProgress += 1.0f / (TRANSITION_DURATION_MS / 50.0f); // 50ms/tick想定
            if (transitionProgress >= 1.0f) {
                transitionProgress = 1.0f;
                isTransitioning = false;
            }
        }

        // 持続時間チェック
        if (currentEmote != EmoteType.NEUTRAL && endTimeMs > 0) {
            if (System.currentTimeMillis() >= endTimeMs) {
                setEmote(EmoteType.NEUTRAL, 0);
            }
        }
    }

    public EmoteType getCurrentEmote() {
        return currentEmote;
    }

    public EmoteType getPreviousEmote() {
        return previousEmote;
    }

    public float getTransitionProgress() {
        return transitionProgress;
    }

    public boolean isTransitioning() {
        return isTransitioning;
    }

    public boolean isNeutral() {
        return currentEmote == EmoteType.NEUTRAL;
    }

    public long getRemainingTimeMs() {
        if (endTimeMs == 0)
            return 0;
        return Math.max(0, endTimeMs - System.currentTimeMillis());
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        this.currentEmote = EmoteType.NEUTRAL;
        this.previousEmote = EmoteType.NEUTRAL;
        this.endTimeMs = 0;
        this.isTransitioning = false;
        this.transitionProgress = 1.0f;
    }
}
