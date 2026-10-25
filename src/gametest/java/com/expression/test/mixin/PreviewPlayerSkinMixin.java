package com.expression.test.mixin;

import com.expression.test.PreviewSkin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the public profile's classic arm model, instead of the offline test player's default model. */
@Mixin(AbstractClientPlayerEntity.class)
public class PreviewPlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void preview(CallbackInfoReturnable<SkinTextures> ci) {
        if (com.expression.test.LegacyFaceGameTest.body() != null) {
            ci.setReturnValue(new SkinTextures(com.expression.test.LegacyFaceGameTest.body(), null, null, PlayerSkinType.SLIM, false));
            return;
        }
        if (PreviewSkin.body() != null)
            ci.setReturnValue(new SkinTextures(PreviewSkin.body(), null, null, PlayerSkinType.WIDE, false));
    }
}
