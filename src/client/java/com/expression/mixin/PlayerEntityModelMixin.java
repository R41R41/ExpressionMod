package com.expression.mixin;

import com.expression.render.ExpressionRenderState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void expressionmod$headPose(PlayerEntityRenderState state, CallbackInfo ci) {
        var frame = ((ExpressionRenderState) state).expressionmod$getFrame();
        if (frame == null || !frame.headMovement()) return;
        var model = (PlayerEntityModel) (Object) this;
        model.head.yaw += (float) Math.toRadians(frame.pose().headYaw());
        model.head.pitch += (float) Math.toRadians(frame.pose().headPitch());
    }
}
