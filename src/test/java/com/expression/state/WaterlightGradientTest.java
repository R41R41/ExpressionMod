package com.expression.state;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WaterlightGradientTest {
    @Test void movingGradientLoopsWithoutChangingThePaletteOrFlashing() {
        assertEquals(WaterlightGradient.at(.4f, .6f), WaterlightGradient.at(.4f, .6f, -1, .56f));
        assertEquals(WaterlightGradient.at(.4f, .6f, .7, .56f),
                WaterlightGradient.at(.4f, .6f, .7 + WaterlightGradient.PERIOD_SECONDS, .56f));
        assertNotEquals(WaterlightGradient.at(.5f, .5f, 0, .56f), WaterlightGradient.at(.5f, .5f, 2.1, .56f));
        int previous = WaterlightGradient.at(.5f, .5f, 0, .56f);
        for (int index = 1; index <= 840; index++) {
            int color = WaterlightGradient.at(.5f, .5f, index * .01, .56f);
            assertEquals(255, color >>> 24);
            for (int shift : new int[]{0, 8, 16}) {
                int value = (color >>> shift) & 255;
                int min = Math.min((WaterlightGradient.TOP >>> shift) & 255, (WaterlightGradient.BOTTOM >>> shift) & 255);
                int max = Math.max((WaterlightGradient.TOP >>> shift) & 255, (WaterlightGradient.BOTTOM >>> shift) & 255);
                assertTrue(value >= min && value <= max);
                assertTrue(Math.abs(value - ((previous >>> shift) & 255)) <= 1);
            }
            previous = color;
        }
    }
    @Test void diagonalGradientMatchesTheDeployedPaletteAndKeepsOpaqueContinuousSteps() {
        assertEquals(WaterlightGradient.TOP, WaterlightGradient.at(0, 0));
        assertEquals(WaterlightGradient.MIDDLE, WaterlightGradient.at(.5f, .5f));
        assertEquals(WaterlightGradient.BOTTOM, WaterlightGradient.at(1, 1));
        int previous = WaterlightGradient.at(0, 0);
        for (int index = 1; index <= 100; index++) {
            int color = WaterlightGradient.at(index / 100f, index / 100f);
            assertEquals(255, color >>> 24);
            for (int shift : new int[]{0, 8, 16})
                assertTrue(Math.abs(((color >>> shift) & 255) - ((previous >>> shift) & 255)) <= 2);
            previous = color;
        }
    }
}
