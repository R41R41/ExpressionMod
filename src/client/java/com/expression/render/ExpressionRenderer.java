package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * プレイヤーの表情をレンダリングするためのユーティリティクラス
 * まばたきアニメーション + 表情差分を統合
 */
public class ExpressionRenderer {

    public static boolean hasExpressionFeatures(Player player, Identifier skinTexture) {
        EyelidTextureData data = EyelidTextureData.getOrCreate(player.getUUID(), skinTexture);
        return data != null && data.hasFeatures;
    }

    public static float getBlinkProgress(Player player, float tickDelta) {
        return BlinkController.getBlinkProgress(player.getUUID(), tickDelta);
    }

    public static float getEyelidOffset(Player player, float tickDelta) {
        return BlinkController.getEyelidOffset(player.getUUID(), tickDelta);
    }

    public static float[] getEyeRotation(UUID playerUuid) {
        ExpressionState state = ExpressionStateManager.getInstance().get(playerUuid);
        if (state == null) {
            return new float[] { 0, 0 };
        }
        return new float[] { state.getEyeYaw(), state.getEyePitch() };
    }

    public static float[] getHeadOffset(UUID playerUuid) {
        ExpressionState state = ExpressionStateManager.getInstance().get(playerUuid);
        if (state == null) {
            return new float[] { 0, 0 };
        }
        return new float[] { state.getHeadYawOffset(), state.getHeadPitchOffset() };
    }

    public static void clearPlayerCache(UUID playerUuid) {
        EyelidTextureData.clearCache(playerUuid);
        BlinkController.resetState(playerUuid);
    }

    public static void clearAllCaches() {
        EyelidTextureData.clearCache();
        BlinkController.clearAllStates();
    }
}
