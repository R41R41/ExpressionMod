package com.expression.mixin;

import com.expression.ExpressionMod;
import com.expression.skin.BlinkTextureManager;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * RenderTypesでスキンテクスチャを目の状態に応じたテクスチャに差し替えるMixin
 * 26.1: RenderType → RenderTypes (net.minecraft.client.renderer.rendertype.RenderTypes)
 */
@Mixin(RenderTypes.class)
public class PlayerSkinTextureMixin {

    @Unique
    private static boolean loggedSkinOnce = false;

    @Unique
    private static Identifier lastEyeTexture = null;

    @ModifyVariable(
            method = "entityTranslucent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Identifier modifyEntityTranslucentTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Translucent");
    }

    @ModifyVariable(
            method = "entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Identifier modifyEntitySolidTexture(Identifier texture) {
        return replaceWithEyeTexture(texture, "Solid");
    }

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
