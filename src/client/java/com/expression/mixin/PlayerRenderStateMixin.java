package com.expression.mixin;

import com.expression.render.ExpressionRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerRenderStateMixin implements ExpressionRenderState {
    @Unique private Frame expressionmod$frame;
    @Override public Frame expressionmod$getFrame() { return expressionmod$frame; }
    @Override public void expressionmod$setFrame(Frame frame) { expressionmod$frame = frame; }
}
