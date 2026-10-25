package com.expression.skin;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.IntBinaryOperator;

/** Explicit adapter for the inspected, unmarked 64x64 robot face. Never edits the uploaded skin. */
public final class ShannonSkinProfile {
    public static final String PLAYER_UUID = "accf3442-b95f-414b-95fd-f70bbd2c9764";
    public static final String FACE_SHA256 = "5ac2311cb9bddd4420b112b60e5deed0684a010cdf0803eb350191b3053a6f12";
    public static final String SOFT_ROBOT_V5_FACE_SHA256 = "1f3232f33356a1c61e5089ec5b8e6a87838b6b993e9522f1c0c054a172030577";
    public static final SkinLayout LAYOUT = new SkinLayout(24, 0, 3, 2, false);
    public static final SkinLayout SOFT_ROBOT_V5_LAYOUT = new SkinLayout(24, 0, 3, 1, false);

    public static boolean isSoftRobotV5(IntBinaryOperator pixel) {
        return SOFT_ROBOT_V5_FACE_SHA256.equals(faceHash(pixel));
    }

    /** Cyan hardware on the v5 metal body, including authored body-line variants. */
    public static boolean isSoftRobotV5BodyCircuitPixel(int x, int y, int argb) {
        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        // The darker cyan stop is below the head trace threshold. Navy frames are not circuitry.
        return x >= 0 && x < 64 && y >= 16 && y < 64
                && (argb >>> 24) == 255 && g > r + 60 && b > g && b >= 160;
    }

    /** Inspected T-shirt folds on the inner upper arms, in original 64x64 skin UVs. */
    public static boolean isUnderarmFabricPixel(int x, int y) {
        return (x == 47 && y >= 32 && y < 40) // Right sleeve front edge and shoulder fold.
                || (x == 52 && y >= 36 && y < 40) // Right sleeve back edge.
                || (y == 36 && x >= 48 && x < 52) // Connecting inner face.
                || (x == 52 && y >= 48 && y < 56) // Left sleeve front edge and shoulder fold.
                || (x == 63 && y >= 52 && y < 56) // Left sleeve back edge.
                || (y == 52 && x >= 48 && x < 52) // Connecting inner face.
                || (x == 47 && y == 20) || (x == 36 && y == 52); // Base arm layer.
    }

    public static boolean matches(int width, int height, IntBinaryOperator pixel) {
        if (width != 64 || height != 64) return false;
        String hash = faceHash(pixel);
        return FACE_SHA256.equals(hash) || SOFT_ROBOT_V5_FACE_SHA256.equals(hash);
    }

    private static String faceHash(IntBinaryOperator pixel) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) {
                int argb = pixel.applyAsInt(x, y);
                for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (argb >>> shift));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static boolean allowedPlayer(UUID player, boolean enabled, String[] allowed) {
        if (!enabled || player == null || allowed == null) return false;
        for (String id : allowed) if (player.toString().equalsIgnoreCase(id)) return true;
        return false;
    }

    public static int[] palette(IntBinaryOperator original) {
        boolean softRobot = isSoftRobotV5(original);
        int[] colors = new int[64 * 64];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) colors[y * 64 + x] = original.applyAsInt(x, y);
        for (int column : new int[]{1, 2, 5, 6}) {
            colors[24 + column] = original.applyAsInt(8 + column, 11);
            colors[3 * 64 + 24 + column] = original.applyAsInt(8 + column, 14);
            for (int row : new int[]{1, 4, 6}) {
                // v5 has a shaded screen: inactive columns must compare to the same material
                // background, otherwise a shade change is mistaken for a second eye column.
                boolean inactive = softRobot && (column == 1 || column == 6);
                colors[row * 64 + 24 + column] = original.applyAsInt(8 + column, inactive ? 14 : 12);
                colors[(row + 1) * 64 + 24 + column] = original.applyAsInt(8 + column, inactive ? 14 : 13);
            }
        }
        return colors;
    }

    private ShannonSkinProfile() {}
}
