package com.expression.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CircuitPulseTest {
    @Test void denserBandLoopsAndTravelsSmoothlyAcrossAPaleBase() {
        assertEquals(CircuitPulse.color(3, .7), CircuitPulse.color(3, .7 + CircuitPulse.PERIOD_SECONDS));
        assertEquals(CircuitPulse.PALE, CircuitPulse.color(0, CircuitPulse.PERIOD_SECONDS / 2));
        assertTrue(((CircuitPulse.color(0, 0) >>> 16) & 255) < ((CircuitPulse.PALE >>> 16) & 255) - 60);
        assertEquals(CircuitPulse.color(0, CircuitPulse.PERIOD_SECONDS / 4), CircuitPulse.color(3, 0));
        int previous = CircuitPulse.color(0, 0);
        for (int step = 1; step <= 560; step++) {
            int color = CircuitPulse.color(0, step * .01);
            assertEquals(255, color >>> 24);
            for (int shift = 0; shift <= 16; shift += 8) {
                int value = (color >>> shift) & 255;
                assertTrue(value <= ((CircuitPulse.PALE >>> shift) & 255));
                assertTrue(value >= ((CircuitPulse.DENSE >>> shift) & 255));
                assertTrue(Math.abs(value - ((previous >>> shift) & 255)) <= 2);
            }
            previous = color;
        }
    }

    @Test void onlyOpaqueCyanTraceColorsAreSelected() {
        assertTrue(CircuitPulse.isTrace(0xFF96E2FD));
        assertTrue(CircuitPulse.isTrace(0xFF00BDFF));
        assertFalse(CircuitPulse.isTrace(0xFFFEFEFE));
        assertFalse(CircuitPulse.isTrace(0xFF000000));
        assertFalse(CircuitPulse.isTrace(0x0096E2FD));
    }
}
