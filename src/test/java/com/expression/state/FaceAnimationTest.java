package com.expression.state;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FaceAnimationTest {
    @Test void smileKeepsFourStraightEdgesAndClosesCompletely() {
        var pose = FacePose.of(EmoteType.SMILE);
        var eye = EyeGeometry.rectangle(1, 3, 2, 3, pose.leftOpen(), pose.leftWidth(), pose.leftTilt(), 0, 0, 0);
        assertTrue(eye.width() > 1);
        var neutral = EyeGeometry.rectangle(1, 3, 2, 3, 1, 1, 0, 0, 0, 0);
        assertTrue(eye.height() < neutral.height());
        var a = eye.at(0, 0); var b = eye.at(1, 0); var midpoint = eye.at(.5f, 0);
        assertEquals((a.x() + b.x()) / 2, midpoint.x(), .0001f);
        assertEquals((a.y() + b.y()) / 2, midpoint.y(), .0001f);
        var closed = EyeGeometry.rectangle(1, 3, 2, 3, pose.leftOpen(), pose.leftWidth(), pose.leftTilt(), 1, 0, 0);
        assertEquals(0, closed.area());
        assertEquals(closed.at(0, 0).y(), closed.at(1, 1).y());
    }

    @Test void transitionsBetweenFramesWithoutWaitingForATick() {
        var time = new AtomicLong(1000);
        var state = new EmoteState(time::get);
        state.setEmote(EmoteType.SMILE, 3000);
        assertEquals(FacePose.NEUTRAL, state.sampleFace());
        time.addAndGet(16);
        float first = state.sampleFace().leftOpen();
        time.addAndGet(16);
        assertTrue(state.sampleFace().leftOpen() < first);
        time.set(1000 + EmoteState.TRANSITION_DURATION_MS);
        assertEquals(FacePose.of(EmoteType.SMILE), state.sampleFace());
    }

    @Test void interruptingAndRenewingAnExpressionDoesNotJumpBack() {
        var time = new AtomicLong(1000);
        var state = new EmoteState(time::get);
        state.setEmote(EmoteType.SAD, 3000);
        time.addAndGet(150);
        FacePose intermediate = state.sampleFace();
        state.setEmote(EmoteType.SURPRISED, 3000);
        assertEquals(intermediate, state.sampleFace());
        time.addAndGet(75);
        intermediate = state.sampleFace();
        state.setEmote(EmoteType.SURPRISED, 25_000);
        assertEquals(intermediate, state.sampleFace());
        assertEquals(25_000, state.getRemainingTimeMs());
    }

    @Test void remoteAndOffscreenExpressionsExpireWithoutLocalPlayerTicks() {
        var time = new AtomicLong(1000);
        var remote = new EmoteState(time::get);
        remote.setEmote(EmoteType.ANGRY, 500);
        time.set(1650);
        assertEquals(EmoteType.NEUTRAL, remote.getCurrentEmote());
        assertTrue(remote.sampleFace().leftOpen() > .65f);
        time.set(1500 + EmoteState.TRANSITION_DURATION_MS);
        assertEquals(FacePose.NEUTRAL, remote.sampleFace());
        assertEquals(0, remote.getRemainingTimeMs());
    }

    @Test void durationsAreBoundedAndNeutralClearsTheLease() {
        assertEquals(3000, EmoteState.boundedDuration(0));
        assertEquals(3000, EmoteState.boundedDuration(-1));
        assertEquals(30_000, EmoteState.boundedDuration(Long.MAX_VALUE));
        var state = new EmoteState(() -> 1000);
        state.setEmote(EmoteType.SHY, Long.MAX_VALUE);
        state.setEmote(EmoteType.NEUTRAL, 1000);
        assertEquals(0, state.getRemainingTimeMs());
    }

    @Test void playersUsingTheSameSkinHaveIndependentExpressions() {
        var time = new AtomicLong(1000);
        var one = new EmoteState(time::get);
        var other = new EmoteState(time::get);
        one.setEmote(EmoteType.SMILE, 3000);
        other.setEmote(EmoteType.SAD, 3000);
        time.addAndGet(EmoteState.TRANSITION_DURATION_MS);
        assertEquals(FacePose.of(EmoteType.SMILE), one.sampleFace());
        assertEquals(FacePose.of(EmoteType.SAD), other.sampleFace());
    }

    @Test void allEmotesHaveDifferentContinuousPoses() {
        assertEquals(EmoteType.values().length, java.util.Arrays.stream(EmoteType.values()).map(FacePose::of).distinct().count());
    }

    @Test void blinkClosesHoldsAndOpensContinuously() {
        var time = new AtomicLong(1000);
        var blink = new BlinkState(time::get, () -> .5);
        blink.triggerBlink();
        assertEquals(0, blink.getBlinkProgress());
        time.addAndGet(27);
        assertTrue(blink.getBlinkProgress() > .4f && blink.getBlinkProgress() < .6f);
        time.set(1055);
        assertEquals(1, blink.getBlinkProgress());
        time.set(1070);
        assertEquals(1, blink.getBlinkProgress());
        time.set(1130);
        assertEquals(.5f, blink.getBlinkProgress(), .0001f);
        time.set(1185);
        assertEquals(0, blink.getBlinkProgress());
        assertFalse(blink.isBlinking());
    }

    @Test void periodicBlinkAndConfigurationRemainBounded() {
        var time = new AtomicLong(1000);
        var blink = new BlinkState(time::get, () -> 0);
        time.set(4000);
        assertTrue(blink.isBlinking());
        time.set(4185);
        assertFalse(blink.isBlinking());
        blink.configure(-100, Long.MAX_VALUE);
        time.set(4435);
        assertTrue(blink.isBlinking());
    }

    @Test void fullClosureHidesEveryEmotesEyeEvenWithTilt() {
        for (var emote : EmoteType.values()) {
            var pose = FacePose.of(emote);
            for (int side = 0; side < 2; side++) {
                var closed = EyeGeometry.rectangle(1, 3, 2, 3,
                        side == 0 ? pose.leftOpen() : pose.rightOpen(), side == 0 ? pose.leftWidth() : pose.rightWidth(),
                        side == 0 ? pose.leftTilt() : pose.rightTilt(), 1, 0, 0);
                assertEquals(0, closed.area());
                assertEquals(closed.at(0, 0).y(), closed.at(1, 1).y());
            }
        }
    }

    @Test void rectangleShrinksSmoothlyAndFitsWithinTheAuthoredEyeRegion() {
        float lastArea = Float.MAX_VALUE;
        for (int frame = 0; frame <= 100; frame++) {
            var eye = EyeGeometry.rectangle(1, 3, 2, 3, 1.4f, 1.8f, 28, frame / 100f, .6f, .4f);
            assertTrue(eye.area() <= lastArea + .0001f);
            for (int u = 0; u <= 1; u++) for (int v = 0; v <= 1; v++) {
                var point = eye.at(u, v);
                assertTrue(point.x() >= .9999f && point.x() <= 3.0001f);
                assertTrue(point.y() >= 1.9999f && point.y() <= 6.0001f);
            }
            lastArea = eye.area();
        }
    }
}
