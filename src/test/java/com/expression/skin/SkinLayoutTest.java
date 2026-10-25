package com.expression.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinLayoutTest {
    @Test void narrowRobotLightsAreMeasuredSeparatelyFromTheirBlackBackground() {
        var layout = new SkinLayout(24, 0, 3, 2, false);
        assertEquals(new SkinLayout.EyeSpan(2, 3), layout.eyeSpan(0,
                (x, y) -> x == 2 && y != 0 && y != 3 ? 0xFF00BDFF : 0xFF000000));
        assertEquals(new SkinLayout.EyeSpan(5, 6), layout.eyeSpan(1,
                (x, y) -> x == 5 && y != 0 && y != 3 ? 0xFF00BDFF : 0xFF000000));
    }

    @Test void gazeArtworkInTheOtherColumnIsIncludedInTheMaterialSpan() {
        var layout = new SkinLayout(0, 0, 4, 2, false);
        assertEquals(new SkinLayout.EyeSpan(1, 3), layout.eyeSpan(0,
                (x, y) -> (x == 2 && y == 1) || (x == 1 && y == 4) ? 0xFF00BDFF : 0xFF000000));
    }

    @Test void oldCyanMarkersSelectTheCorrectThreeFormats() {
        var standard = SkinLayout.detect(64, 64, (x, y) -> x == 63 && y == 0 ? 0xFF00FFFF : 0).orElseThrow();
        assertEquals(new SkinLayout(0, 0, 4, 2, false), standard);
        var alternate = SkinLayout.detect(64, 64, (x, y) -> x == 63 && y == 1 ? 0xFF00FFFF : 0).orElseThrow();
        assertEquals(new SkinLayout(24, 0, 3, 2, false), alternate);
        var narrow = SkinLayout.detect(64, 64, (x, y) -> x == 63 && y == 2 ? 0xFF00FFFF : 0).orElseThrow();
        assertEquals(new SkinLayout(56, 16, 4, 1, true), narrow);
        assertEquals(5, narrow.eyeRow(1));
        assertEquals(8, narrow.eyeRow(-1));
        assertEquals(4, narrow.lashRow(1));
        assertEquals(7, narrow.lashRow(-1));
    }

    @Test void unmarkedWrongSizedAndTransparentMarkersDoNotEnableRendering() {
        assertTrue(SkinLayout.detect(64, 64, (x, y) -> 0xFF223344).isEmpty());
        assertTrue(SkinLayout.detect(64, 32, (x, y) -> { fail("must check dimensions first"); return 0; }).isEmpty());
        assertTrue(SkinLayout.detect(128, 128, (x, y) -> 0xFF00FFFF).isEmpty());
        assertTrue(SkinLayout.detect(64, 64, (x, y) -> 0x0000FFFF).isEmpty());
    }
}
