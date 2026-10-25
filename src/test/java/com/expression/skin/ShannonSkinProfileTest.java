package com.expression.skin;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShannonSkinProfileTest {
    @Test void minimalTenBodyLinesAreSelectedWithoutChangingTheV5FaceOrHousing() throws IOException {
        var base = ImageIO.read(getClass().getResourceAsStream("/shannon-soft-robot-v5.png"));
        var lines = ImageIO.read(getClass().getResourceAsStream("/shannon-lines-10-minimal.png"));
        assertTrue(ShannonSkinProfile.isSoftRobotV5(lines::getRGB));
        for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++)
            if ((base.getRGB(x,y) >>> 24) != 0 || (lines.getRGB(x,y) >>> 24) != 0)
                assertEquals(base.getRGB(x,y), lines.getRGB(x,y), "head UV " + x + "," + y);
        int added = 0;
        for (int y = 16; y < 64; y++) for (int x = 0; x < 64; x++) {
            int rgb = lines.getRGB(x,y);
            if ((rgb >>> 24) != 255) continue;
            if (rgb != base.getRGB(x,y)) {
                assertTrue(ShannonSkinProfile.isSoftRobotV5BodyCircuitPixel(x,y,rgb), "added line UV " + x + "," + y);
                added++;
            }
            if ((rgb & 0xFFFFFF) == 0xE5EAF0 || (rgb & 0xFFFFFF) == 0x2D405A)
                assertFalse(ShannonSkinProfile.isSoftRobotV5BodyCircuitPixel(x,y,rgb));
        }
        assertEquals(32, added);
    }

    @Test void softRobotV5KeepsItsScreenAndSingleColumnEyes() throws IOException {
        var image = ImageIO.read(getClass().getResourceAsStream("/shannon-soft-robot-v5.png"));
        assertTrue(ShannonSkinProfile.matches(64, 64, image::getRGB));
        assertTrue(ShannonSkinProfile.isSoftRobotV5(image::getRGB));
        assertFalse(ShannonSkinProfile.matches(64, 64,
                (x, y) -> image.getRGB(x, y) ^ (x == 10 && y == 12 ? 1 : 0)));
        var palette = ShannonSkinProfile.palette(image::getRGB);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++)
            assertEquals(image.getRGB(x, y), palette[y * 64 + x]);
        var layout = ShannonSkinProfile.SOFT_ROBOT_V5_LAYOUT;
        assertEquals(new SkinLayout.EyeSpan(2, 3), layout.eyeSpan(0, (x,y) -> palette[y * 64 + 24 + x]));
        assertEquals(new SkinLayout.EyeSpan(5, 6), layout.eyeSpan(1, (x,y) -> palette[y * 64 + 24 + x]));
        assertEquals(ShannonSkinProfile.LAYOUT.eyeY(), layout.eyeY());
    }

    @Test void softRobotBodySelectsInsetPanelsAndLeavesWhiteHousingAlone() throws IOException {
        var image = ImageIO.read(getClass().getResourceAsStream("/shannon-soft-robot-v5.png"));
        for (int y : new int[]{25,26,41,42}) for (int x : new int[]{23,24,35,36}) {
            assertTrue(ShannonSkinProfile.isSoftRobotV5BodyCircuitPixel(x, y, image.getRGB(x,y)));
            int rgb = image.getRGB(x, y);
            assertTrue((rgb & 255) > ((rgb >>> 16) & 255));
        }
        for (int[] uv : new int[][]{{22,25},{25,25},{23,24},{23,27},{24,27},{23,43},{24,43},
                {35,27},{36,27},{35,43},{36,43},{23,28},{20,23},{47,36},{52,53}})
            assertFalse(ShannonSkinProfile.isSoftRobotV5BodyCircuitPixel(uv[0], uv[1], image.getRGB(uv[0],uv[1])));
    }

    @Test void underarmClothIsExcludedButShoulderAndForearmCircuitsRemain() throws IOException {
        var image = ImageIO.read(getClass().getResourceAsStream("/shannon-current.png"));
        int clothPixels = 0;
        for (int y = 32; y < 64; y++) for (int x = 40; x < 64; x++) {
            // This exact pale fabric color occurs only on the sleeve folds in these UV regions.
            if ((image.getRGB(x, y) == 0xFFADD5E7) && (y < 48 && x < 56 || y >= 48 && x >= 48)) {
                assertTrue(ShannonSkinProfile.isUnderarmFabricPixel(x, y), "fabric UV " + x + "," + y);
                clothPixels++;
            }
        }
        assertEquals(24, clothPixels);
        for (int[] uv : new int[][]{{45,37}, {54,37}, {45,42}, {54,45},
                {54,53}, {61,53}, {54,58}, {61,61}}) {
            assertTrue(com.expression.state.CircuitPulse.isTrace(image.getRGB(uv[0], uv[1])));
            assertFalse(ShannonSkinProfile.isUnderarmFabricPixel(uv[0], uv[1]));
        }
    }

    @Test void exactFaceIsRequiredAndOriginalPixelsArePreserved() throws IOException {
        var image = ImageIO.read(getClass().getResourceAsStream("/shannon-current.png"));
        assertTrue(ShannonSkinProfile.matches(64, 64, image::getRGB));
        var palette = ShannonSkinProfile.palette(image::getRGB);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) {
            assertEquals(image.getRGB(x, y), palette[y * 64 + x]);
            final int changedX = x, changedY = y;
            assertFalse(ShannonSkinProfile.matches(64, 64,
                    (px, py) -> image.getRGB(px, py) ^ (px == changedX && py == changedY ? 1 : 0)));
        }
        assertFalse(ShannonSkinProfile.matches(64, 32, image::getRGB));
        assertFalse(ShannonSkinProfile.matches(64, 64, (x, y) -> 0xFF000000));
        var span = ShannonSkinProfile.LAYOUT.eyeSpan(0, (x, y) -> palette[y * 64 + 24 + x]);
        assertEquals(new SkinLayout.EyeSpan(2, 3), span);
    }

    @Test void shannonNeedsNoSettingAndOthersNeedToBeListed() {
        // 1.3.2: every observer sees Shannon's face (multiplayer); the face fingerprint is still checked by matches().
        var shannon = UUID.fromString(ShannonSkinProfile.PLAYER_UUID);
        assertTrue(ShannonSkinProfile.allowedPlayer(shannon, null));
        assertTrue(ShannonSkinProfile.allowedPlayer(shannon, new String[0]));
        assertTrue(ShannonSkinProfile.allowedPlayer(shannon, new String[]{"invalid"}));
        var friend = UUID.randomUUID();
        assertFalse(ShannonSkinProfile.allowedPlayer(friend, new String[]{ShannonSkinProfile.PLAYER_UUID}));
        assertTrue(ShannonSkinProfile.allowedPlayer(friend, new String[]{friend.toString().toUpperCase()}));
        assertFalse(ShannonSkinProfile.allowedPlayer(null, new String[]{ShannonSkinProfile.PLAYER_UUID}));
    }
}
