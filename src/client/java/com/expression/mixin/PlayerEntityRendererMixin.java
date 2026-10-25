package com.expression.mixin;

import com.expression.render.ContinuousFaceFeatureRenderer;
import com.expression.render.ExpressionRenderState;
import com.expression.skin.FaceRuntime;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<PlayerLikeEntity, PlayerEntityRenderState, PlayerEntityModel> {
    protected PlayerEntityRendererMixin(EntityRendererFactory.Context context, PlayerEntityModel model, float shadow) {
        super(context, model, shadow);
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void expressionmod$features(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        addFeature(new ContinuousFaceFeatureRenderer(this));
        addFeature(new com.expression.render.ShannonCircuitFeatureRenderer(this));
        addFeature(new com.expression.render.BodyCircuitFeatureRenderer(this));
    }
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void expressionmod$snapshot(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        var frame = entity instanceof AbstractClientPlayerEntity player
                ? FaceRuntime.snapshot(player, state.skinTextures.body().texturePath()) : null;
        ((ExpressionRenderState) state).expressionmod$setFrame(frame);
        if (frame == null && entity instanceof AbstractClientPlayerEntity player) {
            var original=state.skinTextures;
            var body=com.expression.skin.LegacySkinTextures.select(player,original.body());
            if(body!=original.body()) state.skinTextures=new net.minecraft.entity.player.SkinTextures(
                    body,original.cape(),original.elytra(),original.model(),original.secure());
        }
    }
}
