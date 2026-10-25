package com.expression.state;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ShannonExpressionTest {
    private static final String[] EXPRESSIONS = {"joyful", "excited", "amused", "laughing", "bashful", "embarrassed",
            "relieved", "surprised", "curious", "thinking", "confused", "determined", "tender", "calm", "jealous",
            "wry", "exasperated", "annoyed", "concerned", "sad", "sleepy", "neutral"};

    @Test void everyDeployedFaceAndLegacyIdRoundTripsWithoutFallingBack() {
        assertEquals(22, java.util.Arrays.stream(EXPRESSIONS).map(EmoteType::fromId).distinct().count());
        for (var type : EmoteType.values()) {
            assertEquals(type, EmoteType.fromId(type.getId()));
            assertEquals(type, EmoteType.fromIndex(type.getTextureIndex()));
        }
        assertEquals(EmoteType.SMILE, EmoteType.fromId("joyful"));
        assertEquals(EmoteType.ANGRY, EmoteType.fromId("annoyed"));
        assertEquals(EmoteType.NEUTRAL, EmoteType.fromId(null));
        assertEquals(EmoteType.NEUTRAL, EmoteType.fromId("unknown"));
    }

    @Test void widthsAsymmetryAndInterruptedShapesRemainContinuous() {
        var time = new AtomicLong(1000);
        var state = new EmoteState(time::get);
        state.setEmote(EmoteType.EXCITED, 30_000);
        time.addAndGet(200);
        var visible = state.sampleFace();
        assertTrue(visible.leftWidth() > 1 && visible.leftWidth() < FacePose.of(EmoteType.EXCITED).leftWidth());
        state.setEmote(EmoteType.AMUSED, 30_000);
        assertEquals(visible, state.sampleFace());
        time.addAndGet(EmoteState.TRANSITION_DURATION_MS);
        assertTrue(state.sampleFace().leftOpen() < state.sampleFace().rightOpen());
        assertNotEquals(state.sampleFace().leftTilt(), state.sampleFace().rightTilt());
        for (var type : EmoteType.values()) {
            var pose = FacePose.of(type);
            for (int side = 0; side < 2; side++) for (int t = 0; t <= 12; t++) {
                var step = FacePose.NEUTRAL.interpolate(pose, t / 12f);
                var eye = EyeGeometry.rectangle(1, 3, 2, 3, side == 0 ? step.leftOpen() : step.rightOpen(),
                        side == 0 ? step.leftWidth() : step.rightWidth(), side == 0 ? step.leftTilt() : step.rightTilt(),
                        0, step.gazeX(), step.gazeY());
                for (int u = 0; u <= 1; u++) for (int v = 0; v <= 1; v++) {
                    var point = eye.at(u, v);
                    assertTrue(point.x() >= .9999f && point.x() <= 3.0001f);
                    assertTrue(point.y() >= 1.9999f && point.y() <= 6.0001f);
                }
            }
        }
    }
}
