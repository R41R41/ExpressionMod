package com.expression.render;

import com.expression.skin.FaceSkinData;
import com.expression.skin.ShannonSkinProfile;
import com.expression.state.CircuitPulse;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import java.util.ArrayList;

/** Samples vanilla's actual cuboid UVs, including slim arms and the child clothing layers. */
public final class BodyCircuitFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier WHITE = Identifier.of("expressionmod", "textures/white.png");
    private record Vertex(Vector3f point, int color) {}
    private record Patch(Vertex a, Vertex b, Vertex c, Vertex d, Vector3f normal) {}
    public BodyCircuitFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

    @Override public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        var frame = ((ExpressionRenderState) state).expressionmod$getFrame();
        if (frame == null || frame.circuitSeconds() < 0 || !frame.skin().usesWaterlightGradient()
                || state.invisible || state.spectator) return;
        var model = getContextModel();
        // The torso's cyan pixels belong to T-shirt fabric, including the jacket overlay.
        // Animate the hardware on the limbs; leave both torso layers in their original colors.
        var parts = frame.skin().isSoftRobotV5()
                ? new ModelPart[]{model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg}
                : new ModelPart[]{model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        for (var part : parts) {
            if (!part.visible || part.hidden) continue;
            part.forEachCuboid(matrices, (entry, path, index, cuboid) -> {
                // forEachCuboid includes children even when their clothing visibility is disabled.
                if (!path.isEmpty()) {
                    ModelPart child = part;
                    for (String name : path.split("/")) if (!name.isEmpty()) {
                        child = child.getChild(name);
                        if (!child.visible || child.hidden) return;
                    }
                }
                var patches = patches(cuboid, frame.skin(), frame.circuitSeconds(), part);
                if (patches.isEmpty()) return;
                // Vanilla has already applied the parent and child transformations here.
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(WHITE), (snapshot, buffer) -> {
                    for (var patch : patches) for (var vertex : new Vertex[]{patch.a, patch.b, patch.c, patch.d}) {
                        var p = vertex.point;
                        buffer.vertex(snapshot, p.x, p.y, p.z).color(vertex.color).texture(0, 0)
                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                .normal(snapshot, patch.normal.x, patch.normal.y, patch.normal.z);
                    }
                });
            });
        }
    }

    private static ArrayList<Patch> patches(ModelPart.Cuboid cuboid, FaceSkinData skin, double seconds, ModelPart part) {
        var patches = new ArrayList<Patch>();
        for (var side : cuboid.sides) {
            var vertices = side.vertices();
            float minU = 1, minV = 1, maxU = 0, maxV = 0;
            for (var vertex : vertices) {
                minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v());
            }
            if (maxU <= minU || maxV <= minV) continue;
            ModelPart.Vertex origin = null, endU = null, endV = null;
            for (var vertex : vertices) {
                if (vertex.u() == minU && vertex.v() == minV) origin = vertex;
                if (vertex.u() == maxU && vertex.v() == minV) endU = vertex;
                if (vertex.u() == minU && vertex.v() == maxV) endV = vertex;
            }
            if (origin == null || endU == null || endV == null) continue;
            var o = new Vector3f(origin.worldX(), origin.worldY(), origin.worldZ());
            var du = new Vector3f(endU.worldX(), endU.worldY(), endU.worldZ()).sub(o);
            var dv = new Vector3f(endV.worldX(), endV.worldY(), endV.worldZ()).sub(o);
            var normal = new Vector3f(side.direction());
            o.add(new Vector3f(normal).mul(.025f / 16));
            boolean reverse = new Vector3f(du).cross(dv).dot(normal) > 0;
            for (int y = Math.round(minV * 64); y < Math.round(maxV * 64); y++)
                for (int x = Math.round(minU * 64); x < Math.round(maxU * 64); x++) {
                    if (skin.isSoftRobotV5()) {
                        if (!ShannonSkinProfile.isSoftRobotV5BodyCircuitPixel(x, y, skin.pixel(x, y))) continue;
                    } else if (ShannonSkinProfile.isUnderarmFabricPixel(x, y)
                            || !CircuitPulse.isTrace(skin.pixel(x, y))) continue;
                    float u1 = (x / 64f - minU) / (maxU - minU), u2 = ((x + 1) / 64f - minU) / (maxU - minU);
                    for (int step = 0; step < 4; step++) {
                        float v1 = ((y + step / 4f) / 64 - minV) / (maxV - minV);
                        float v2 = ((y + (step + 1) / 4f) / 64 - minV) / (maxV - minV);
                        var a = vertex(o, du, dv, u1, v1, seconds, part);
                        var b = vertex(o, du, dv, u1, v2, seconds, part);
                        var c = vertex(o, du, dv, u2, v2, seconds, part);
                        var d = vertex(o, du, dv, u2, v1, seconds, part);
                        patches.add(reverse ? new Patch(d, c, b, a, normal) : new Patch(a, b, c, d, normal));
                    }
                }
        }
        return patches;
    }
    private static Vertex vertex(Vector3f o, Vector3f du, Vector3f dv, float u, float v, double seconds, ModelPart part) {
        var p = new Vector3f(o).fma(u, du).fma(v, dv);
        // A diagonal current also travels along horizontal arm traces instead of pulsing them uniformly.
        double distance = -(p.y * 16 + part.originY + .7 * (p.x * 16 + part.originX) + .35 * (p.z * 16 + part.originZ));
        return new Vertex(p, CircuitPulse.color(distance, seconds));
    }
}
