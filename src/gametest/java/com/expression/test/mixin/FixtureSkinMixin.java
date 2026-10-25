package com.expression.test.mixin;

import com.expression.render.ExpressionRenderState;
import com.expression.skin.FaceRuntime;
import com.expression.test.PreviewSkin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the synthetic fixture or an explicitly selected local skin. No account or network access. */
// TAIL callbacks from the production renderer must finish before the preview override.
@Mixin(value = PlayerEntityRenderer.class, priority = 1100)
public class FixtureSkinMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void fixture(PlayerLikeEntity entity, PlayerEntityRenderState state, float delta, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        if (com.expression.test.LegacyFaceGameTest.body() != null) return; // Exercise the production renderer unchanged.
        boolean preview = PreviewSkin.body() != null;
        var body = preview ? PreviewSkin.body()
                : new AssetInfo.TextureAssetInfo(Identifier.of("expressionmod_test", "fixture"));
        state.skinTextures = new SkinTextures(body, null, null, state.skinTextures.model(), true);
        if (preview) {
            // The fabricated remote player has no server settings packet. Show the original outer clothing on both.
            state.hatVisible = state.jacketVisible = state.leftPantsLegVisible = state.rightPantsLegVisible = true;
            state.leftSleeveVisible = state.rightSleeveVisible = true;
        }
        ((ExpressionRenderState) state).expressionmod$setFrame(preview && !PreviewSkin.expressions() ? null
                : PreviewSkin.sample(FaceRuntime.snapshot(player, body.texturePath())));
        state.displayName = null;
    }
}
