package com.expression.render;

import com.expression.skin.FaceSkinData;
import com.expression.state.EyeGeometry;
import com.expression.state.WaterlightGradient;
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
import java.util.List;

/** Rectangular eye meshes: continuous scale, rotation, gaze, blink and vertex-color gradients. */
public final class ContinuousFaceFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier WHITE = Identifier.of("expressionmod", "textures/white.png");
    private static final float FRONT = -4.02f / 16;
    private static final int DISPLAY_LIGHT = 0xF000F0;
    private record Quad(EyeGeometry.Point a, EyeGeometry.Point b, EyeGeometry.Point c, EyeGeometry.Point d,
            int ca, int cb, int cc, int cd, float depth, boolean emissive) {}

    public ContinuousFaceFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

    @Override public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        var frame = ((ExpressionRenderState) state).expressionmod$getFrame();
        if (frame == null || state.invisible || state.spectator) return;
        List<Quad> quads = new ArrayList<>();
        for (int side = 0; side < 2; side++) eye(quads, frame, side);
        cheeks(quads, frame);
        matrices.push();
        getContextModel().head.applyTransform(matrices);
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(WHITE), (entry, vertices) -> {
            for (var quad : quads) emit(vertices, entry, quad, light);
        });
        matrices.pop();
    }

    private static void cheeks(List<Quad> quads, ExpressionRenderState.Frame frame) {
        boolean shannon = frame.skin().usesWaterlightGradient();
        // The robot has a faint permanent blush; embarrassment strengthens it continuously.
        float opacity = shannon ? .18f + .62f * frame.pose().blush() : frame.pose().blush();
        if (opacity <= 0) return;
        int color = (Math.round(Math.clamp(opacity, 0, 1) * 255) << 24) | (shannon ? 0xE89AAE : 0xF8C4CD);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? (shannon ? .35f : .5f) : (shannon ? 5.25f : 5.5f);
            float y = shannon ? 6.2f : 6.8f;
            float width = shannon ? 2.4f : 2;
            float height = shannon ? .55f : .5f;
            // Deliberately extend into the white bezel, while staying on the head's front face.
            var a = new EyeGeometry.Point(x, y);
            var b = new EyeGeometry.Point(x, y + height);
            var c = new EyeGeometry.Point(x + width, y + height);
            var d = new EyeGeometry.Point(x + width, y);
            quads.add(new Quad(a, b, c, d, color, color, color, color,
                    frame.skin().isSoftRobotV5() ? .032f : .0002f, shannon));
        }
    }

    private static void eye(List<Quad> quads, ExpressionRenderState.Frame frame, int side) {
        FaceSkinData skin = frame.skin();
        var layout = skin.layout;
        var pose = frame.pose();
        int start = layout.eyeStart(side), y = layout.eyeY();
        // Cover only the original two eye columns; all other skin pixels and the outer layer stay intact.
        for (int column = start; column < start + 2; column++) {
            if (skin.isSoftRobotV5()) {
                // Preserve each row of the v5 shaded screen while erasing the original pupil.
                for (int row = y - 1; row < y + 3; row++)
                    rectangle(quads, column, row, column + 1, row + 1, skin.face(side == 0 ? 3 : 4, row), 0);
            } else rectangle(quads, column, y - 1, column + 1, y + 3, skin.sample(column, 3), 0);
        }
        var span = skin.eyeSpan(side);
        var eye = EyeGeometry.rectangle(start, y, span.left(), span.right(),
                side == 0 ? pose.leftOpen() : pose.rightOpen(), side == 0 ? pose.leftWidth() : pose.rightWidth(),
                side == 0 ? pose.leftTilt() : pose.rightTilt(), frame.blink(), frame.gazeX(), frame.gazeY());
        if (eye.area() <= .00001f) return;
        if (skin.usesWaterlightGradient()) {
            int rows = frame.eyeGradientSeconds() < 0 ? 2 : 6;
            int columns = frame.eyeGradientSeconds() < 0 ? 1 : 2;
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                float v1 = row / (float) rows, v2 = (row + 1) / (float) rows;
                float u1 = column / (float) columns, u2 = (column + 1) / (float) columns;
                double time = frame.eyeGradientSeconds();
                quads.add(new Quad(eye.at(u1, v1), eye.at(u1, v2), eye.at(u2, v2), eye.at(u2, v1),
                        WaterlightGradient.at(u1, v1, time, pose.energy()), WaterlightGradient.at(u1, v2, time, pose.energy()),
                        WaterlightGradient.at(u2, v2, time, pose.energy()), WaterlightGradient.at(u2, v1, time, pose.energy()), .0001f, true));
            }
        } else {
            // Marker skins retain their authored colors, including continuous directional-material blending.
            float weight = Math.clamp(Math.abs(frame.gazeX()) / .4f, 0, 1);
            int direction = frame.gazeX() >= 0 ? 1 : -1;
            for (int column = span.left(); column < span.right(); column++) for (int row = 0; row < 2; row++) {
                float u1 = (column - span.left()) / (float) (span.right() - span.left());
                float u2 = (column + 1 - span.left()) / (float) (span.right() - span.left());
                float v1 = row / 2f, v2 = (row + 1) / 2f;
                int color = WaterlightGradient.blend(skin.sample(column, layout.eyeRow(0) + row),
                        skin.sample(column, layout.eyeRow(direction) + row), weight);
                quads.add(new Quad(eye.at(u1, v1), eye.at(u1, v2), eye.at(u2, v2), eye.at(u2, v1),
                        color, color, color, color, .0001f, false));
            }
        }
    }

    private static void rectangle(List<Quad> quads, float x1, float y1, float x2, float y2, int color, float depth) {
        quads.add(new Quad(new EyeGeometry.Point(x1, y1), new EyeGeometry.Point(x1, y2),
                new EyeGeometry.Point(x2, y2), new EyeGeometry.Point(x2, y1), color, color, color, color, depth, false));
    }
    private static void emit(VertexConsumer buffer, MatrixStack.Entry entry, Quad quad, int light) {
        if (((quad.ca | quad.cb | quad.cc | quad.cd) >>> 24) == 0) return;
        float z = FRONT - quad.depth;
        int illumination = quad.emissive ? DISPLAY_LIGHT : light;
        vertex(buffer, entry, quad.a, z, quad.ca, illumination, 0, 0);
        vertex(buffer, entry, quad.b, z, quad.cb, illumination, 0, 1);
        vertex(buffer, entry, quad.c, z, quad.cc, illumination, 1, 1);
        vertex(buffer, entry, quad.d, z, quad.cd, illumination, 1, 0);
    }
    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, EyeGeometry.Point point, float z,
            int color, int light, float u, float v) {
        buffer.vertex(entry, (point.x() - 4) / 16, (point.y() - 8) / 16, z).color(color).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, -1);
    }
}
