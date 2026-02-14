package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.skin.SkinRegions;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * まぶた（まつ毛）のアニメーションを描画するFeatureRenderer
 * 
 * プレイヤーの顔にまつ毛・目・肌色をオーバーレイし、
 * まつ毛のY座標をアニメーションさせることでスムーズなまばたきを実現
 */
public class EyelidFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // 顔のサイズ（ブロック単位）
    private static final float HEAD_SIZE = 8.0f / 16.0f; // 0.5ブロック
    private static final float PIXEL_SIZE = 1.0f / 16.0f; // 1ピクセル = 1/16ブロック
    private static final float LAYER_OFFSET = 0.001f; // レイヤー間のオフセット（Z-fighting防止）

    public EyelidFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Identifier skinTexture = state.skinTextures.body().texturePath();

        // スキンテクスチャからEyelidTextureDataを検索
        EyelidTextureData data = findDataBySkinTexture(skinTexture);
        if (data == null || !data.hasFeatures) {
            return;
        }

        UUID playerUuid = data.playerUuid;
        float tickDelta = 0.0f;

        // まつ毛のオフセットを取得
        float eyelidOffset = BlinkController.getEyelidOffset(playerUuid, tickDelta);

        // 目の可視高さを計算 (2px - offset)
        float visibleEyeHeight = Math.max(0, SkinRegions.EYE_HEIGHT - eyelidOffset);

        matrices.push();

        // 顔の前面に移動
        matrices.translate(0, 0, HEAD_SIZE / 2 + LAYER_OFFSET);

        // 目の位置を計算
        int eyeY = data.eyeYPosition;
        float eyeYOffset = eyeY * PIXEL_SIZE;

        // レンダリング
        if (data.skinTexture != null && eyelidOffset > 0) {
            // 肌色背景を描画（まつ毛が下がった時に見える部分）
            renderQuad(matrices, queue, light, data.skinTexture,
                    eyeYOffset, eyelidOffset * PIXEL_SIZE,
                    SkinRegions.SKIN_WIDTH * PIXEL_SIZE, LAYER_OFFSET);
        }

        if (data.eyeTexture != null && visibleEyeHeight > 0) {
            // 目を描画（可視部分のみ）
            renderEye(matrices, queue, light, data.eyeTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, visibleEyeHeight,
                    SkinRegions.EYE_WIDTH * PIXEL_SIZE, LAYER_OFFSET * 2);
        }

        if (data.eyelashTexture != null) {
            // まつ毛を描画（移動後の位置）
            renderQuad(matrices, queue, light, data.eyelashTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, PIXEL_SIZE,
                    SkinRegions.EYELASH_WIDTH * PIXEL_SIZE, LAYER_OFFSET * 3);
        }

        matrices.pop();
    }

    /**
     * スキンテクスチャからEyelidTextureDataを検索
     */
    private EyelidTextureData findDataBySkinTexture(Identifier skinTexture) {
        return EyelidTextureData.getBySkinTexture(skinTexture);
    }

    /**
     * 四角形を描画（submitCustom使用）
     */
    private void renderQuad(MatrixStack matrices, OrderedRenderCommandQueue queue,
            int light, Identifier texture, float yOffset, float height,
            float width, float zOffset) {
        final float x1 = -width / 2;
        final float x2 = width / 2;
        final float y1 = HEAD_SIZE / 2 - yOffset;
        final float y2 = y1 - height;
        final float z = zOffset;
        final int lightFinal = light;

        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(texture),
                (entry, buffer) -> {
                    Matrix4f matrix = entry.getPositionMatrix();
                    buffer.vertex(matrix, x1, y1, z).color(255, 255, 255, 255)
                            .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x1, y2, z).color(255, 255, 255, 255)
                            .texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x2, y2, z).color(255, 255, 255, 255)
                            .texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x2, y1, z).color(255, 255, 255, 255)
                            .texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                });
    }

    /**
     * 目を描画（上部がカットされる）
     */
    private void renderEye(MatrixStack matrices, OrderedRenderCommandQueue queue,
            int light, Identifier texture, float yOffset, float visibleHeight,
            float width, float zOffset) {
        final float x1 = -width / 2;
        final float x2 = width / 2;
        final float y1 = HEAD_SIZE / 2 - yOffset;
        final float y2 = y1 - visibleHeight * PIXEL_SIZE;
        final float z = zOffset;
        final float uvTop = 1.0f - (visibleHeight / SkinRegions.EYE_HEIGHT);
        final int lightFinal = light;

        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(texture),
                (entry, buffer) -> {
                    Matrix4f matrix = entry.getPositionMatrix();
                    buffer.vertex(matrix, x1, y1, z).color(255, 255, 255, 255)
                            .texture(0, uvTop).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x1, y2, z).color(255, 255, 255, 255)
                            .texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x2, y2, z).color(255, 255, 255, 255)
                            .texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                    buffer.vertex(matrix, x2, y1, z).color(255, 255, 255, 255)
                            .texture(1, uvTop).overlay(OverlayTexture.DEFAULT_UV).light(lightFinal).normal(entry, 0, 0, 1);
                });
    }
}
