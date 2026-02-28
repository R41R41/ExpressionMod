package com.expression.mixin;

import com.expression.ExpressionMod;
import com.expression.skin.BlinkTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * RenderLayersでスキンテクスチャを目の状態に応じたテクスチャに差し替えるMixin
 * 1.21.11: RenderLayer → RenderLayers に移動対応
 */
@Mixin(RenderLayers.class)
public class PlayerSkinTextureMixin {

    @Unique
    private static boolean loggedSkinOnce = false;

    @Unique
    private static Identifier lastEyeTexture = null;

    /**
     * entityTranslucentでテクスチャIDを差し替え
     */
    @ModifyVariable(
            method = "entityTranslucent(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Identifier modifyEntityTranslucentTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Translucent");
    }

    /**
     * entitySolidでテクスチャIDを差し替え
     */
    @ModifyVariable(
            method = "entitySolid(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Identifier modifyEntitySolidTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Solid");
    }

    /**
     * スキンテクスチャを目の状態に応じたテクスチャに差し替える
     */
    @Unique
    private static Identifier replaceWithEyeTexture(Identifier texture, String type) {
        String path = texture.getPath();

        if (path.startsWith("skins/") || path.contains("player")) {
            if (!loggedSkinOnce) {
                ExpressionMod.LOGGER
                        .info("[PlayerSkinTextureMixin] Skin texture detected: " + texture + " (type: " + type + ")");
                loggedSkinOnce = true;
            }

            Identifier eyeTexture = BlinkTextureManager.getCurrentEyeTexture(texture);
            if (eyeTexture != null) {
                if (lastEyeTexture == null || !lastEyeTexture.equals(eyeTexture)) {
                    ExpressionMod.LOGGER.info("[PlayerSkinTextureMixin] Texture changed: " + eyeTexture);
                    lastEyeTexture = eyeTexture;
                }
                return eyeTexture;
            }
        }

        return texture;
    }
}
