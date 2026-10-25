package com.expression.skin;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

/** The existing three cyan-marker formats. Extraction is pure and can be tested without a GPU. */
public record SkinLayout(int sourceX, int sourceY, int eyeY, int eyeWidth, boolean directionalLashes) {
    public static Optional<SkinLayout> detect(int width, int height, IntBinaryOperator pixel) {
        if (width != 64 || height != 64) return Optional.empty();
        if (cyan(pixel.applyAsInt(63, 0))) return Optional.of(new SkinLayout(0, 0, 4, 2, false));
        if (cyan(pixel.applyAsInt(63, 1))) return Optional.of(new SkinLayout(24, 0, 3, 2, false));
        if (cyan(pixel.applyAsInt(63, 2))) return Optional.of(new SkinLayout(56, 16, 4, 1, true));
        return Optional.empty();
    }
    public static boolean cyan(int argb) {
        return (argb >>> 24) > 200 && ((argb >>> 16) & 255) < 20 && ((argb >>> 8) & 255) > 230 && (argb & 255) > 230;
    }
    public int eyeRow(int direction) {
        return directionalLashes ? (direction == 0 ? 1 : direction > 0 ? 5 : 8)
                : (direction == 0 ? 1 : direction > 0 ? 4 : 6);
    }
    public int lashRow(int direction) { return directionalLashes ? (direction == 0 ? 0 : direction > 0 ? 4 : 7) : 0; }
    public int eyeStart(int side) { return side == 0 ? 1 : 5; }

    public record EyeSpan(int left, int right) {}
    /** Include all authored gaze directions; a one-column light on a black face stays one column. */
    public EyeSpan eyeSpan(int side, IntBinaryOperator material) {
        int start = eyeStart(side), left = start + 2, right = start;
        for (int x = start; x < start + 2; x++) {
            int background = material.applyAsInt(x, 3);
            for (int direction = -1; direction <= 1; direction++) for (int row = 0; row < 2; row++) {
                int color = material.applyAsInt(x, eyeRow(direction) + row);
                if ((color >>> 24) != 0 && color != background) {
                    left = Math.min(left, x);
                    right = Math.max(right, x + 1);
                }
            }
        }
        return left < right ? new EyeSpan(left, right) : new EyeSpan(start, start + 2);
    }
}
