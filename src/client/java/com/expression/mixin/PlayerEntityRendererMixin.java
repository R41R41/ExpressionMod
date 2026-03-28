package com.expression.mixin;

import com.expression.skin.BlinkTextureManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "extractRenderState",
            at = @At("HEAD")
    )
    private void onExtractRenderState(Avatar entity, AvatarRenderState state, float tickDelta,
            CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            BlinkTextureManager.setCurrentRenderingPlayer(player);
        }
    }
}
