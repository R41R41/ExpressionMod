package com.expression.render;

import com.expression.ExpressionMod;
import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.skin.SkinRegions;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * プレイヤーの顔にまぶたオーバーレイを描画するレンダラー
 * 頭部の回転に追従するように実装
 */
public class EyelidOverlayRenderer {

    // サイズ定数（ブロック単位）
    private static final float PIXEL_SIZE = 1.0f / 16.0f; // 1ピクセル = 1/16ブロック
    private static final float HEAD_SIZE = 8.0f * PIXEL_SIZE; // 頭のサイズ = 0.5ブロック

    private static boolean loggedOnce = false;
    private static int renderCount = 0;

    /**
     * 頭部の変換を適用してレンダリング
     * PlayerEntityRendererから呼び出される
     */
    public static void renderWithHeadTransform(PlayerEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, ModelPart headPart) {

        Identifier skinTexture = state.skinTextures.texture();
        EyelidTextureData data = EyelidTextureData.getBySkinTexture(skinTexture);

        if (!loggedOnce) {
            ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] renderWithHeadTransform called");
            ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] Skin texture: " + skinTexture);
            ExpressionMod.LOGGER
                    .info("[EyelidOverlayRenderer] EyelidTextureData: " + (data != null ? "found" : "null"));
            if (data != null) {
                ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] hasFeatures: " + data.hasFeatures);
                ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] eyelashTexture: " + data.eyelashTexture);
            }
            loggedOnce = true;
        }

        if (data == null || !data.hasFeatures) {
            return;
        }

        if (data.eyelashTexture == null && data.eyeTexture == null) {
            return;
        }

        renderCount++;

        float tickDelta = 0.0f;
        float eyelidOffset = BlinkController.getEyelidOffset(data.playerUuid, tickDelta);
        float visibleEyeHeight = Math.max(0, SkinRegions.EYE_HEIGHT - eyelidOffset);

        matrices.push();

        // 頭部のModelPartの変換を適用
        // これにより頭の回転・傾きに追従する
        headPart.rotate(matrices);

        // 顔の前面に移動（頭の中心から前方へ）
        // 頭のサイズは8ピクセル、その半分 + 少しオフセット
        matrices.translate(0, 0, -(HEAD_SIZE / 2 + 0.01f));

        // 目のY位置（顔の上端からの距離）
        // headPartの中心は頭の中心なので、上端は+4ピクセル
        float eyeYFromCenter = (HEAD_SIZE / 2) - (data.eyeYPosition * PIXEL_SIZE);

        // まつ毛と目を描画
        float baseY = eyeYFromCenter;

        if (eyelidOffset > 0 && data.skinTexture != null) {
            renderSkinBackground(matrices, vertexConsumers, light, data.skinTexture,
                    baseY, eyelidOffset, 0.001f);
        }

        if (visibleEyeHeight > 0 && data.eyeTexture != null) {
            renderEye(matrices, vertexConsumers, light, data.eyeTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, visibleEyeHeight, 0.002f);
        }

        if (data.eyelashTexture != null) {
            renderEyelash(matrices, vertexConsumers, light, data.eyelashTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, 0.003f);
        }

        matrices.pop();
    }

    /**
     * 状態とyaw/pitchから直接レンダリング（Mixin用のフォールバック）
     */
    public static void renderWithRotation(PlayerEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {

        Identifier skinTexture = state.skinTextures.texture();

        // スキンIDで検索
        EyelidTextureData data = EyelidTextureData.getBySkinTexture(skinTexture);

        // 見つからない場合は新規作成を試みる
        // state.nameからUUIDを推測するか、ランダムUUIDを使用
        if (data == null) {
            // レンダリング時にgetOrCreateを呼び出して、このスキンIDで登録
            java.util.UUID playerUuid = java.util.UUID.nameUUIDFromBytes(
                    ("player_" + skinTexture.toString()).getBytes());
            data = EyelidTextureData.getOrCreate(playerUuid, skinTexture);
        }

        // デバッグログ
        renderCount++;
        if (renderCount % 1000 == 1) {
            ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] renderWithRotation called, count: " + renderCount);
            ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] skinTexture: " + skinTexture);
            ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] data: " + (data != null ? "found" : "null"));
            if (data != null) {
                ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] hasFeatures: " + data.hasFeatures);
                ExpressionMod.LOGGER.info("[EyelidOverlayRenderer] eyelashTexture: " + data.eyelashTexture);
            }
        }

        if (data == null || !data.hasFeatures) {
            return;
        }

        if (data.eyelashTexture == null && data.eyeTexture == null) {
            return;
        }

        float tickDelta = 0.0f;
        float eyelidOffset = BlinkController.getEyelidOffset(data.playerUuid, tickDelta);
        float visibleEyeHeight = Math.max(0, SkinRegions.EYE_HEIGHT - eyelidOffset);

        matrices.push();

        // 頭部の位置に移動（足元から）
        // プレイヤーモデルの頭部は足元から約1.5ブロック上
        matrices.translate(0, 1.5f, 0);

        // 頭部の回転を適用（PlayerEntityRenderStateから取得）
        float yaw = state.yawDegrees;
        float pitch = state.pitch;

        // まずY軸回転（左右を向く）
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        // 次にX軸回転（上下を向く）
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        // 顔の前面に移動
        matrices.translate(0, 0, -(HEAD_SIZE / 2 + 0.01f));

        // 目のY位置
        float eyeYFromCenter = (HEAD_SIZE / 2) - (data.eyeYPosition * PIXEL_SIZE);
        float baseY = eyeYFromCenter;

        if (eyelidOffset > 0 && data.skinTexture != null) {
            renderSkinBackground(matrices, vertexConsumers, light, data.skinTexture,
                    baseY, eyelidOffset, 0.001f);
        }

        if (visibleEyeHeight > 0 && data.eyeTexture != null) {
            renderEye(matrices, vertexConsumers, light, data.eyeTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, visibleEyeHeight, 0.002f);
        }

        if (data.eyelashTexture != null) {
            renderEyelash(matrices, vertexConsumers, light, data.eyelashTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, 0.003f);
        }

        matrices.pop();
    }

    /**
     * 肌色背景を描画
     */
    private static void renderSkinBackground(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float topY, float height, float zOffset) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        float width = SkinRegions.SKIN_WIDTH * PIXEL_SIZE;
        float h = height * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - h;

        renderQuad(buffer, posMatrix, x1, y1, x2, y2, zOffset, light);
    }

    /**
     * 目を描画
     */
    private static void renderEye(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float topY, float visibleHeight, float zOffset) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        float width = SkinRegions.EYE_WIDTH * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - visibleHeight * PIXEL_SIZE;

        float uvTop = 1.0f - (visibleHeight / SkinRegions.EYE_HEIGHT);

        renderQuadWithUV(buffer, posMatrix, x1, y1, x2, y2, zOffset, light, 0, uvTop, 1, 1);
    }

    /**
     * まつ毛を描画
     */
    private static void renderEyelash(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Identifier texture, float topY, float zOffset) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        float width = SkinRegions.EYELASH_WIDTH * PIXEL_SIZE;
        float height = SkinRegions.EYELASH_HEIGHT * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - height;

        renderQuad(buffer, posMatrix, x1, y1, x2, y2, zOffset, light);
    }

    /**
     * 四角形を描画（UV 0-1）
     */
    private static void renderQuad(VertexConsumer buffer, Matrix4f posMatrix,
            float x1, float y1, float x2, float y2, float z, int light) {
        renderQuadWithUV(buffer, posMatrix, x1, y1, x2, y2, z, light, 0, 0, 1, 1);
    }

    /**
     * 四角形を描画（UV指定）
     */
    private static void renderQuadWithUV(VertexConsumer buffer, Matrix4f posMatrix,
            float x1, float y1, float x2, float y2, float z,
            int light, float u1, float v1, float u2, float v2) {
        // 頂点を追加
        buffer.vertex(posMatrix, x1, y1, z)
                .color(255, 255, 255, 255)
                .texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0, 0, 1);

        buffer.vertex(posMatrix, x2, y1, z)
                .color(255, 255, 255, 255)
                .texture(u2, v1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0, 0, 1);

        buffer.vertex(posMatrix, x2, y2, z)
                .color(255, 255, 255, 255)
                .texture(u2, v2)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0, 0, 1);

        buffer.vertex(posMatrix, x1, y2, z)
                .color(255, 255, 255, 255)
                .texture(u1, v2)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0, 0, 1);
    }

    public static void resetLogFlag() {
        loggedOnce = false;
        renderCount = 0;
    }
}
