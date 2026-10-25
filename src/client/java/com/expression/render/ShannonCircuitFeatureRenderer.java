package com.expression.render;

import com.expression.state.CircuitPulse;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.util.ArrayList;

/** Optional experiment: current flows through existing front/side traces, leaving the eyes dominant. */
public final class ShannonCircuitFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier WHITE = Identifier.of("expressionmod", "textures/white.png");
    private record Strip(int face, float x1, float x2, float y1, float y2, int c1, int c2) {}
    public ShannonCircuitFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

    @Override public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        var frame = ((ExpressionRenderState) state).expressionmod$getFrame();
        if (frame == null || frame.circuitSeconds() < 0 || !frame.skin().usesWaterlightGradient()
                || state.invisible || state.spectator) return;
        var strips = new ArrayList<Strip>();
        for (int face = 0; face < 3; face++) for (int y = 4; y <= 5; y++) for (int x = 0; x < 8; x++) {
            if (face == 1 && x != 0 && x != 7) continue; // Face-center cyan pixels are eyes, never circuit traces.
            int original = frame.skin().isSoftRobotV5() ? frame.skin().headHatPixel(face, x, y)
                    : frame.skin().headPixel(face, x, y);
            if (!CircuitPulse.isTrace(original)) continue;
            for (int step = 0; step < 4; step++) {
                float u1 = x + step / 4f, u2 = x + (step + 1) / 4f;
                double d1 = distance(face, u1), d2 = distance(face, u2);
                strips.add(new Strip(face, u1, u2, y, y + 1,
                        CircuitPulse.color(d1, frame.circuitSeconds()),
                        CircuitPulse.color(d2, frame.circuitSeconds())));
            }
        }
        matrices.push();
        getContextModel().head.applyTransform(matrices);
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(WHITE), (entry, vertices) -> {
            for (var strip : strips) {
                vertex(vertices, entry, strip.face, strip.x1, strip.y1, strip.c1, light, 0, 0, frame.skin().isSoftRobotV5());
                vertex(vertices, entry, strip.face, strip.x1, strip.y2, strip.c1, light, 0, 1, frame.skin().isSoftRobotV5());
                vertex(vertices, entry, strip.face, strip.x2, strip.y2, strip.c2, light, 1, 1, frame.skin().isSoftRobotV5());
                vertex(vertices, entry, strip.face, strip.x2, strip.y1, strip.c2, light, 1, 0, frame.skin().isSoftRobotV5());
            }
        });
        matrices.pop();
    }
    private static double distance(int face, float u) {
        // Continue past the side/front seam into the face instead of reversing at that corner.
        return switch (face) { case 0 -> 8 - u; case 2 -> u; default -> u < 4 ? -u : u - 8; };
    }
    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, int face, float u, float v,
            int color, int light, float texU, float texV, boolean outerHat) {
        float x, z, nx, nz;
        float scale = outerHat ? 1.125f : 1, extent = outerHat ? 4.525f : 4.025f;
        if (face == 0) { x = -extent; z = (4 - u) * scale; nx = -1; nz = 0; }
        else if (face == 2) { x = extent; z = (u - 4) * scale; nx = 1; nz = 0; }
        else { x = (u - 4) * scale; z = -extent; nx = 0; nz = -1; }
        // Ordinary scene lighting: this accessory never gets the eye display's full-bright light.
        vertices.vertex(entry, x / 16, ((v - 4) * scale - 4) / 16, z / 16).color(color).texture(texU, texV)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
    }
}
