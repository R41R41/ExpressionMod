package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import com.expression.skin.SkinRegions;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * まぶた（まつ毛）のアニメーションを描画するFeatureRenderer
 * 
 * プレイヤーの顔にまつ毛・目・肌色をオーバーレイし、
 * まつ毛のY座標をアニメーションさせることでスムーズなまばたきを実現
 */
public class EyelidFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final float HEAD_SIZE = 8.0f / 16.0f;
    private static final float PIXEL_SIZE = 1.0f / 16.0f;
    private static final float LAYER_OFFSET = 0.001f;

    public EyelidFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
            AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        Identifier skinTexture = state.skin.body().texturePath();

        EyelidTextureData data = findDataBySkinTexture(skinTexture);
        if (data == null || !data.hasFeatures) {
            return;
        }

        UUID playerUuid = data.playerUuid;
        float tickDelta = 0.0f;

        float eyelidOffset = BlinkController.getEyelidOffset(playerUuid, tickDelta);

        float visibleEyeHeight = Math.max(0, SkinRegions.EYE_HEIGHT - eyelidOffset);

        poseStack.pushPose();

        poseStack.translate(0, 0, HEAD_SIZE / 2 + LAYER_OFFSET);

        int eyeY = data.eyeYPosition;
        float eyeYOffset = eyeY * PIXEL_SIZE;

        if (data.skinTexture != null && eyelidOffset > 0) {
            renderQuad(poseStack, collector, light, data.skinTexture,
                    eyeYOffset, eyelidOffset * PIXEL_SIZE,
                    SkinRegions.SKIN_WIDTH * PIXEL_SIZE, LAYER_OFFSET);
        }

        if (data.eyeTexture != null && visibleEyeHeight > 0) {
            renderEye(poseStack, collector, light, data.eyeTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, visibleEyeHeight,
                    SkinRegions.EYE_WIDTH * PIXEL_SIZE, LAYER_OFFSET * 2);
        }

        if (data.eyelashTexture != null) {
            renderQuad(poseStack, collector, light, data.eyelashTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, PIXEL_SIZE,
                    SkinRegions.EYELASH_WIDTH * PIXEL_SIZE, LAYER_OFFSET * 3);
        }

        poseStack.popPose();
    }

    private EyelidTextureData findDataBySkinTexture(Identifier skinTexture) {
        return EyelidTextureData.getBySkinTexture(skinTexture);
    }

    private void renderQuad(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float yOffset, float height,
            float width, float zOffset) {
        final float x1 = -width / 2;
        final float x2 = width / 2;
        final float y1 = HEAD_SIZE / 2 - yOffset;
        final float y2 = y1 - height;
        final float z = zOffset;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, consumer) -> {
                    Matrix4f matrix = pose.pose();
                    consumer.addVertex(matrix, x1, y1, z).setColor(255, 255, 255, 255)
                            .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x1, y2, z).setColor(255, 255, 255, 255)
                            .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x2, y2, z).setColor(255, 255, 255, 255)
                            .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x2, y1, z).setColor(255, 255, 255, 255)
                            .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                });
    }

    private void renderEye(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float yOffset, float visibleHeight,
            float width, float zOffset) {
        final float x1 = -width / 2;
        final float x2 = width / 2;
        final float y1 = HEAD_SIZE / 2 - yOffset;
        final float y2 = y1 - visibleHeight * PIXEL_SIZE;
        final float z = zOffset;
        final float uvTop = 1.0f - (visibleHeight / SkinRegions.EYE_HEIGHT);

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, consumer) -> {
                    Matrix4f matrix = pose.pose();
                    consumer.addVertex(matrix, x1, y1, z).setColor(255, 255, 255, 255)
                            .setUv(0, uvTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x1, y2, z).setColor(255, 255, 255, 255)
                            .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x2, y2, z).setColor(255, 255, 255, 255)
                            .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                    consumer.addVertex(matrix, x2, y1, z).setColor(255, 255, 255, 255)
                            .setUv(1, uvTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
                });
    }
}
