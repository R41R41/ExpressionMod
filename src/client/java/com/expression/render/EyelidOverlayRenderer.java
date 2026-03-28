package com.expression.render;

import com.expression.ExpressionMod;
import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.skin.SkinRegions;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/**
 * プレイヤーの顔にまぶたオーバーレイを描画するレンダラー
 * 頭部の回転に追従するように実装
 */
public class EyelidOverlayRenderer {

    private static final float PIXEL_SIZE = 1.0f / 16.0f;
    private static final float HEAD_SIZE = 8.0f * PIXEL_SIZE;

    private static boolean loggedOnce = false;
    private static int renderCount = 0;

    /**
     * 頭部の変換を適用してレンダリング
     * PlayerEntityRendererから呼び出される
     */
    public static void renderWithHeadTransform(AvatarRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, int light, ModelPart headPart) {

        Identifier skinTexture = state.skin.body().texturePath();
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

        poseStack.pushPose();

        poseStack.mulPose(Axis.XP.rotationDegrees(headPart.xRot * (180F / (float) Math.PI)));
        poseStack.mulPose(Axis.YP.rotationDegrees(headPart.yRot * (180F / (float) Math.PI)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(headPart.zRot * (180F / (float) Math.PI)));

        poseStack.translate(0, 0, -(HEAD_SIZE / 2 + 0.01f));

        float eyeYFromCenter = (HEAD_SIZE / 2) - (data.eyeYPosition * PIXEL_SIZE);

        float baseY = eyeYFromCenter;

        if (eyelidOffset > 0 && data.skinTexture != null) {
            renderSkinBackground(poseStack, collector, light, data.skinTexture,
                    baseY, eyelidOffset, 0.001f);
        }

        if (visibleEyeHeight > 0 && data.eyeTexture != null) {
            renderEye(poseStack, collector, light, data.eyeTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, visibleEyeHeight, 0.002f);
        }

        if (data.eyelashTexture != null) {
            renderEyelash(poseStack, collector, light, data.eyelashTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, 0.003f);
        }

        poseStack.popPose();
    }

    /**
     * 状態とyaw/pitchから直接レンダリング（Mixin用のフォールバック）
     */
    public static void renderWithRotation(AvatarRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, int light) {

        Identifier skinTexture = state.skin.body().texturePath();

        EyelidTextureData data = EyelidTextureData.getBySkinTexture(skinTexture);

        if (data == null) {
            java.util.UUID playerUuid = java.util.UUID.nameUUIDFromBytes(
                    ("player_" + skinTexture.toString()).getBytes());
            data = EyelidTextureData.getOrCreate(playerUuid, skinTexture);
        }

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

        poseStack.pushPose();

        poseStack.translate(0, 1.5f, 0);

        float yaw = state.bodyRot;
        float pitch = state.xRot;

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        poseStack.translate(0, 0, -(HEAD_SIZE / 2 + 0.01f));

        float eyeYFromCenter = (HEAD_SIZE / 2) - (data.eyeYPosition * PIXEL_SIZE);
        float baseY = eyeYFromCenter;

        if (eyelidOffset > 0 && data.skinTexture != null) {
            renderSkinBackground(poseStack, collector, light, data.skinTexture,
                    baseY, eyelidOffset, 0.001f);
        }

        if (visibleEyeHeight > 0 && data.eyeTexture != null) {
            renderEye(poseStack, collector, light, data.eyeTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, visibleEyeHeight, 0.002f);
        }

        if (data.eyelashTexture != null) {
            renderEyelash(poseStack, collector, light, data.eyelashTexture,
                    baseY - eyelidOffset * PIXEL_SIZE, 0.003f);
        }

        poseStack.popPose();
    }

    private static void renderSkinBackground(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float topY, float height, float zOffset) {
        float width = SkinRegions.SKIN_WIDTH * PIXEL_SIZE;
        float h = height * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - h;

        renderQuad(poseStack, collector, light, texture, x1, y1, x2, y2, zOffset);
    }

    private static void renderEye(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float topY, float visibleHeight, float zOffset) {
        float width = SkinRegions.EYE_WIDTH * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - visibleHeight * PIXEL_SIZE;

        float uvTop = 1.0f - (visibleHeight / SkinRegions.EYE_HEIGHT);

        renderQuadWithUV(poseStack, collector, light, texture, x1, y1, x2, y2, zOffset, 0, uvTop, 1, 1);
    }

    private static void renderEyelash(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float topY, float zOffset) {
        float width = SkinRegions.EYELASH_WIDTH * PIXEL_SIZE;
        float height = SkinRegions.EYELASH_HEIGHT * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = topY;
        float y2 = topY - height;

        renderQuad(poseStack, collector, light, texture, x1, y1, x2, y2, zOffset);
    }

    private static void renderQuad(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float x1, float y1, float x2, float y2, float z) {
        renderQuadWithUV(poseStack, collector, light, texture, x1, y1, x2, y2, z, 0, 0, 1, 1);
    }

    private static void renderQuadWithUV(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float x1, float y1, float x2, float y2, float z,
            float u1, float v1, float u2, float v2) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, consumer) -> {
            Matrix4f posMatrix = pose.pose();
            consumer.addVertex(posMatrix, x1, y1, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u1, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 0, 1);

            consumer.addVertex(posMatrix, x2, y1, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u2, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 0, 1);

            consumer.addVertex(posMatrix, x2, y2, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u2, v2)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 0, 1);

            consumer.addVertex(posMatrix, x1, y2, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u1, v2)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 0, 1);
        });
    }

    public static void resetLogFlag() {
        loggedOnce = false;
        renderCount = 0;
    }
}
