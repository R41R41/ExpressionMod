package com.expression.state;

/** Four straight edges at every expression and every transition sample. */
public final class EyeGeometry {
    public record Point(float x, float y) {}
    public record Rectangle(float centerX, float centerY, float width, float height, float cosine, float sine, float closure) {
        public Point at(float u, float v) {
            float x = (u - .5f) * width, y = (v - .5f) * height;
            return new Point(centerX + x * cosine - y * sine, centerY + (x * sine + y * cosine) * closure);
        }
        public float area() { return width * height * closure; }
    }
    public static Rectangle rectangle(float start, float eyeY, float materialLeft, float materialRight,
            float open, float widthScale, float tiltDegrees, float blink, float gazeX, float gazeY) {
        float width = Math.clamp((materialRight - materialLeft) * widthScale, .1f, 2);
        float height = 2 * Math.clamp(open, 0, 1.5f);
        double radians = Math.toRadians(Math.clamp(tiltDegrees, -45, 45));
        float cosine = (float) Math.cos(radians), sine = (float) Math.sin(radians);
        float closure = 1 - Math.clamp(blink, 0, 1);
        // Fit the rotated rectangle as a whole; clipping corners would turn it into a different shape.
        float halfX = (width * Math.abs(cosine) + height * Math.abs(sine)) / 2;
        float halfY = (width * Math.abs(sine) + height * Math.abs(cosine)) * closure / 2;
        float fit = Math.min(1, Math.min(1 / Math.max(.0001f, halfX), 2 / Math.max(.0001f, halfY)));
        width *= fit; height *= fit; halfX = Math.min(1, halfX * fit); halfY = Math.min(2, halfY * fit);
        float centerX = Math.clamp((materialLeft + materialRight) / 2 + gazeX * .3f, start + halfX, start + 2 - halfX);
        float centerY = Math.clamp(eyeY + 2 + gazeY, eyeY - 1 + halfY, eyeY + 3 - halfY);
        return new Rectangle(centerX, centerY, width, height, cosine, sine, closure);
    }
    private EyeGeometry() {}
}
