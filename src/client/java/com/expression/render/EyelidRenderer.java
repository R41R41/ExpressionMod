package com.expression.render;

import com.expression.skin.BlinkController;
import com.expression.skin.EyelidTextureData;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * まぶた（まつ毛）のレンダリングを担当
 * 
 * プレイヤーの顔にまつ毛テクスチャを動的にオーバーレイし、
 * Y座標をアニメーションさせることでスムーズなまばたきを実現
 */
public class EyelidRenderer {

    private static final float HEAD_SIZE = 8.0f / 16.0f;
    private static final float PIXEL_SIZE = 1.0f / 16.0f;

    public static void render(Player player, PoseStack poseStack,
            SubmitNodeCollector collector, int light, float tickDelta) {
        UUID playerUuid = player.getUUID();

        EyelidTextureData data = EyelidTextureData.get(playerUuid);
        if (data == null || !data.hasFeatures) {
            return;
        }

        BlinkController.update(playerUuid, player.isSleeping(),
                player.hasEffect(MobEffects.BLINDNESS));

        float eyelidOffset = BlinkController.getEyelidOffset(playerUuid, tickDelta);

        float visibleEyeHeight = Math.max(0, 2.0f - eyelidOffset);

        poseStack.pushPose();

        poseStack.translate(0, 0, HEAD_SIZE / 2 + 0.001f);

        int eyeY = data.eyeYPosition;
        float eyeYOffset = (eyeY - 1) * PIXEL_SIZE;

        if (data.skinTexture != null && eyelidOffset > 0) {
            renderSkinBackground(poseStack, collector, light, data.skinTexture,
                    eyeYOffset, eyelidOffset);
        }

        if (data.eyeTexture != null && visibleEyeHeight > 0) {
            renderEye(poseStack, collector, light, data.eyeTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE, visibleEyeHeight);
        }

        if (data.eyelashTexture != null) {
            renderEyelash(poseStack, collector, light, data.eyelashTexture,
                    eyeYOffset + eyelidOffset * PIXEL_SIZE);
        }

        poseStack.popPose();
    }

    private static void renderSkinBackground(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float yOffset, float height) {
        float width = 8.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - height * PIXEL_SIZE;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            consumer.addVertex(matrix, x1, y1, 0).setColor(255, 255, 255, 255).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x1, y2, 0).setColor(255, 255, 255, 255).setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y2, 0).setColor(255, 255, 255, 255).setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y1, 0).setColor(255, 255, 255, 255).setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        });
    }

    private static void renderEye(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float yOffset, float visibleHeight) {
        float width = 8.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - visibleHeight * PIXEL_SIZE;
        float uvTop = 1.0f - (visibleHeight / 2.0f);

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            consumer.addVertex(matrix, x1, y1, 0).setColor(255, 255, 255, 255).setUv(0, uvTop)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x1, y2, 0).setColor(255, 255, 255, 255).setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y2, 0).setColor(255, 255, 255, 255).setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y1, 0).setColor(255, 255, 255, 255).setUv(1, uvTop)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        });
    }

    private static void renderEyelash(PoseStack poseStack, SubmitNodeCollector collector,
            int light, Identifier texture, float yOffset) {
        float width = 8.0f * PIXEL_SIZE;
        float height = 1.0f * PIXEL_SIZE;
        float x1 = -width / 2;
        float x2 = width / 2;
        float y1 = HEAD_SIZE / 2 - yOffset;
        float y2 = y1 - height;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            consumer.addVertex(matrix, x1, y1, 0).setColor(255, 255, 255, 255).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x1, y2, 0).setColor(255, 255, 255, 255).setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y2, 0).setColor(255, 255, 255, 255).setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            consumer.addVertex(matrix, x2, y1, 0).setColor(255, 255, 255, 255).setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        });
    }

    public static void initializePlayer(Player player, Identifier skinTexture) {
        EyelidTextureData.getOrCreate(player.getUUID(), skinTexture);
    }

    public static void clearPlayer(UUID playerUuid) {
        EyelidTextureData.clearCache(playerUuid);
        BlinkController.resetState(playerUuid);
    }

    public static void clearAll() {
        EyelidTextureData.clearAllCaches();
        BlinkController.clearAllStates();
    }
}
