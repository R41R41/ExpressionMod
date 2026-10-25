package com.expression.render;

import com.expression.skin.FaceSkinData;
import com.expression.state.FacePose;
import java.util.UUID;

/** Per-entity immutable snapshot for Minecraft's deferred render queue. */
public interface ExpressionRenderState {
    record Frame(UUID player, FaceSkinData skin, FacePose pose, float blink, float gazeX, float gazeY,
            boolean headMovement, double circuitSeconds, double eyeGradientSeconds) {}
    Frame expressionmod$getFrame();
    void expressionmod$setFrame(Frame frame);
}
