package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * まぶた（まつ毛）のレンダリングを担当
 * 
 * プレイヤーの顔にまつ毛テクスチャを動的にオーバーレイし、
 * Y座標をアニメーションさせることでスムーズなまばたきを実現
 */
public class EyelidRenderer {

    // 顔のサイズ（ブロック単位）
    private static final float HEAD_SIZE = 8.0f / 16.0f; // 0.5ブロック
    private static final float PIXEL_SIZE = 1.0f / 16.0f; // 1ピクセル = 1/16ブロック

    /**
     * プレイヤーのまぶたをレンダリング
     * 
     * @param player          プレイヤー
     * @param matrices        変換行列
     * @param vertexConsumers バッファ
     * @param light           ライト値
     * @param tickDelta       フレーム補間値
     */
    public static void render(PlayerEntity player, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        UUID playerUuid = player.getUuid();

        // テクスチャデータを取得
        EyelidTextureData data = EyelidTextureData.get(playerUuid);
        if (data == null || !data.hasFeatures) {
            return;
        }

        // まばたき状態を更新
        BlinkController.update(playerUuid, player.isSleeping(),
                player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS));

        // まつ毛のオフセットを取得
        float eyelidOffset = BlinkController.getEyelidOffset(playerUuid, tickDelta);

        // 目の可視高さを計算 (2px - offset)
        float visibleEyeHeight = Math.max(0, 2.0f - eyelidOffset);

        matrices.push();

        // 顔の位置に移動（頭の前面）
        // 頭の中心から前面へ
        matrices.translate(0, 0, HEAD_SIZE / 2 + 0.001f); // 少し前に出してz-fighting防止

        // 目の位置を計算
        int eyeY = data.eyeYPosition;
        float eyeYOffset = (eyeY - 1) * PIXEL_SIZE; // 顔の上端からのオフセット

        // まず肌色を描画（目の領域全体を覆う）
        if (data.skinTexture != null && eyelidOffset > 0) {
            renderSkinBackground(matrices, vertexConsumers, light, data.skinTexture,
                    eyeYOffset, eyelidOffset);
        }

        // 目を描画（可視部分のみ）
        if (data.eyeTexture != null && visibleEyeHeight > 0) {
            renderEye(matrices, vertexConsumers, light, data.eyeTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, visibleEyeHeight);
        }

        // まつ毛を描画
        if (data.eyelashTexture != null) {
            renderEyelash(matrices, vertexConsumers, light, data.eyelashTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE);
        }

        matrices.pop();
    }

    /**
     * 肌色背景を描画（まつ毛が下がった時に見える部分）
     */
    private static void renderSkinBackground(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float yOffset, float height) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayers.entityCutout(texture));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float width = 8.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - height * PIXEL_SIZE;

        // 単純な四角形
        buffer.vertex(matrix, x1, y1, 0).color(255, 255, 255, 255).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x1, y2, 0).color(255, 255, 255, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y2, 0).color(255, 255, 255, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y1, 0).color(255, 255, 255, 255).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
    }

    /**
     * 目を描画
     */
    private static void renderEye(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float yOffset, float visibleHeight) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayers.entityCutout(texture));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float width = 8.0f * PIXEL_SIZE;
        float fullHeight = 2.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - visibleHeight * PIXEL_SIZE;

        // UVの下部分のみ表示（上部がまつ毛で隠れる）
        float uvTop = 1.0f - (visibleHeight / 2.0f);

        buffer.vertex(matrix, x1, y1, 0).color(255, 255, 255, 255).texture(0, uvTop).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x1, y2, 0).color(255, 255, 255, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y2, 0).color(255, 255, 255, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y1, 0).color(255, 255, 255, 255).texture(1, uvTop).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
    }

    /**
     * まつ毛を描画
     */
    private static void renderEyelash(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float yOffset) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayers.entityCutout(texture));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float width = 8.0f * PIXEL_SIZE;
        float height = 1.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - height;

        buffer.vertex(matrix, x1, y1, 0).color(255, 255, 255, 255).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x1, y2, 0).color(255, 255, 255, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y2, 0).color(255, 255, 255, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x2, y1, 0).color(255, 255, 255, 255).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 0, 1);
    }

    /**
     * プレイヤーのスキンからまぶたデータを初期化
     */
    public static void initializePlayer(PlayerEntity player, Identifier skinTexture) {
        EyelidTextureData.getOrCreate(player.getUuid(), skinTexture);
    }

    /**
     * プレイヤーのデータをクリア
     */
    public static void clearPlayer(UUID playerUuid) {
        EyelidTextureData.clearCache(playerUuid);
        BlinkController.resetState(playerUuid);
    }

    /**
     * 全データをクリア
     */
    public static void clearAll() {
        EyelidTextureData.clearAllCaches();
        BlinkController.clearAllStates();
    }
}
