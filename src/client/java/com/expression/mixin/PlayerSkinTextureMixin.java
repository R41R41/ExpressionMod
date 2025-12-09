package com.expression.mixin;

import com.expression.ExpressionMod;
import com.expression.skin.BlinkTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * RenderLayerでスキンテクスチャを目の状態に応じたテクスチャに差し替えるMixin
 */
@Mixin(RenderLayer.class)
public class PlayerSkinTextureMixin {

    @Unique
    private static int callCount = 0;

    @Unique
    private static boolean loggedSkinOnce = false;

    /**
     * getEntityTranslucentでテクスチャIDを差し替え
     */
    @ModifyArg(method = "getEntityTranslucent(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getEntityTranslucent(Lnet/minecraft/util/Identifier;Z)Lnet/minecraft/client/render/RenderLayer;"), index = 0)
    private static Identifier modifyEntityTranslucentTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Translucent");
    }

    /**
     * getEntitySolidでテクスチャIDを差し替え
     */
    @ModifyArg(method = "getEntitySolid(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getEntitySolid(Lnet/minecraft/util/Identifier;Z)Lnet/minecraft/client/render/RenderLayer;"), index = 0)
    private static Identifier modifyEntitySolidTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Solid");
    }

    @Unique
    private static Identifier lastEyeTexture = null;

    /**
     * スキンテクスチャを目の状態に応じたテクスチャに差し替える
     */
    private static Identifier replaceWithEyeTexture(Identifier texture, String type) {
        String path = texture.getPath();

        // スキンテクスチャの場合のみ処理
        if (path.startsWith("skins/") || path.contains("player")) {
            if (!loggedSkinOnce) {
                ExpressionMod.LOGGER
                        .info("[PlayerSkinTextureMixin] Skin texture detected: " + texture + " (type: " + type + ")");
                loggedSkinOnce = true;
            }

            // 目の状態に応じたテクスチャを取得
            Identifier eyeTexture = BlinkTextureManager.getCurrentEyeTexture(texture);
            if (eyeTexture != null) {
                callCount++;
                // テクスチャが変わった時にログ出力
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
