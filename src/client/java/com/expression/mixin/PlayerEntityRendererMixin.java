package com.expression.mixin;

import com.expression.skin.BlinkTextureManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerEntityRendererにフックして、レンダリング中のプレイヤーを追跡する
 * 1.21.11: PlayerLikeEntity 型パラメータに対応
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta,
            CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            BlinkTextureManager.setCurrentRenderingPlayer(player);
        }
    }
}
