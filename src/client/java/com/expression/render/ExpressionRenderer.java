package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * プレイヤーの表情をレンダリングするためのユーティリティクラス
 * まばたきアニメーション + 表情差分を統合
 */
public class ExpressionRenderer {

    /**
     * プレイヤーの表情機能が有効かどうかを確認
     */
    public static boolean hasExpressionFeatures(PlayerEntity player, Identifier skinTexture) {
        EyelidTextureData data = EyelidTextureData.getOrCreate(player.getUuid(), skinTexture);
        return data != null && data.hasFeatures;
    }

    /**
     * まばたきの進行度を取得
     * 
     * @param player    プレイヤーエンティティ
     * @param tickDelta フレーム補間値
     * @return 進行度（0.0 = 開いている, 1.0 = 閉じている）
     */
    public static float getBlinkProgress(PlayerEntity player, float tickDelta) {
        return BlinkController.getBlinkProgress(player.getUuid(), tickDelta);
    }

    /**
     * まつ毛のYオフセットを取得（ピクセル単位）
     * 
     * @param player    プレイヤーエンティティ
     * @param tickDelta フレーム補間値
     * @return Yオフセット（0.0 = 開いている, 2.0 = 完全に閉じている）
     */
    public static float getEyelidOffset(PlayerEntity player, float tickDelta) {
        return BlinkController.getEyelidOffset(player.getUuid(), tickDelta);
    }

    /**
     * 目の回転角度を取得
     */
    public static float[] getEyeRotation(UUID playerUuid) {
        ExpressionState state = ExpressionStateManager.getInstance().get(playerUuid);
        if (state == null) {
            return new float[] { 0, 0 };
        }
        return new float[] { state.getEyeYaw(), state.getEyePitch() };
    }

    /**
     * 頭部の追加回転を取得
     */
    public static float[] getHeadOffset(UUID playerUuid) {
        ExpressionState state = ExpressionStateManager.getInstance().get(playerUuid);
        if (state == null) {
            return new float[] { 0, 0 };
        }
        return new float[] { state.getHeadYawOffset(), state.getHeadPitchOffset() };
    }

    /**
     * プレイヤーのスキンキャッシュをクリア
     */
    public static void clearPlayerCache(UUID playerUuid) {
        EyelidTextureData.clearCache(playerUuid);
        BlinkController.resetState(playerUuid);
    }

    /**
     * 全キャッシュをクリア
     */
    public static void clearAllCaches() {
        EyelidTextureData.clearCache();
        BlinkController.clearAllStates();
    }
}
