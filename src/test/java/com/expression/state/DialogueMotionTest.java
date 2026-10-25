package com.expression.state;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DialogueMotionTest {
    @Test void staleActivityAndTargetExpireWithoutTicks() {
        var time = new AtomicLong(0); var state = new DialogueState(time::get);
        state.update(DialogueActivity.THINKING, Long.MAX_VALUE, true, 90, -90);
        assertEquals(5000, state.remainingMs()); assertEquals(30, state.yaw()); assertEquals(-30, state.pitch());
        time.set(5000); assertEquals(DialogueActivity.IDLE, state.activity()); assertFalse(state.hasTarget());
        assertEquals(0, state.remainingMs());
    }
    @Test void renewedActivityAndUnknownValuesAreBounded() {
        var time = new AtomicLong(0); var state = new DialogueState(time::get);
        state.update(DialogueActivity.LISTENING, 2000, true, Float.NaN, 0);
        assertFalse(state.hasTarget()); time.set(1900);
        state.update(DialogueActivity.LISTENING, 2000, true, 15, 4); time.set(2100);
        assertEquals(DialogueActivity.LISTENING, state.activity()); assertEquals(1800, state.remainingMs());
        state.update(DialogueActivity.fromId("untrusted"), 5000, true, 0, 0);
        assertEquals(DialogueActivity.IDLE, state.activity()); assertFalse(state.hasTarget());
    }
    @Test void eyesLeadTheHeadAndSettleAtTheTarget() {
        var motion = new AliveMotion();
        motion.sample(FacePose.NEUTRAL, EmoteType.NEUTRAL, DialogueActivity.LISTENING, 0, 0, 0, 0, false);
        var early = motion.sample(FacePose.NEUTRAL, EmoteType.NEUTRAL, DialogueActivity.LISTENING, .3f, 0, .05, 0, false);
        assertTrue(early.gazeX() > .1); assertTrue(early.pose().headYaw() / 5 < early.gazeX() * .3);
        AliveMotion.Sample settled = early;
        for (int i = 2; i <= 100; i++) settled = motion.sample(FacePose.NEUTRAL, EmoteType.NEUTRAL,
                DialogueActivity.LISTENING, .3f, 0, i * .05, 0, false);
        assertEquals(.3, settled.gazeX(), .001); assertEquals(1.5, settled.pose().headYaw(), .01);
    }
    @Test void activityInterruptionIsContinuousAndDoesNotReplaceEmotion() {
        var motion = new AliveMotion(); var base = FacePose.of(EmoteType.BASHFUL);
        AliveMotion.Sample a = null;
        for (int i = 0; i <= 20; i++) a = motion.sample(base, EmoteType.BASHFUL, DialogueActivity.THINKING, .2f, 0, i * .05, 3, true);
        var b = motion.sample(base, EmoteType.BASHFUL, DialogueActivity.SPEAKING, .2f, 0, 1, 3, true);
        assertEquals(a.pose(), b.pose()); assertEquals(a.gazeX(), b.gazeX()); assertEquals(a.gazeY(), b.gazeY());
        assertEquals(base.leftTilt(), b.pose().leftTilt()); assertEquals(base.blush(), b.pose().blush());
        for (int i = 0; i < 200; i++) {
            var sample = motion.sample(base, EmoteType.BASHFUL, DialogueActivity.SPEAKING, .2f, 0, 1+i*.016, 3, true);
            assertTrue(Math.abs(sample.pose().leftWidth() / base.leftWidth()-1) < .05);
            assertTrue(Math.abs(sample.pose().leftOpen() / base.leftOpen()-1) < .05);
        }
    }
    @Test void frameRateAndPlayerSeedDoNotShareMotionState() {
        var a = new AliveMotion(); var b = new AliveMotion(); AliveMotion.Sample x=null,y=null;
        for (int i=0;i<=120;i++) x=a.sample(FacePose.NEUTRAL,EmoteType.NEUTRAL,DialogueActivity.IDLE,.3f,0,i/60.0,1,false);
        for (int i=0;i<=60;i++) y=b.sample(FacePose.NEUTRAL,EmoteType.NEUTRAL,DialogueActivity.IDLE,.3f,0,i/30.0,2,false);
        assertEquals(x.gazeX(),y.gazeX(),.001); assertEquals(x.pose().headYaw(),y.pose().headYaw(),.01);
        var other = new AliveMotion().sample(FacePose.NEUTRAL,EmoteType.NEUTRAL,DialogueActivity.IDLE,-.3f,0,2,2,false);
        assertEquals(0,other.gazeX());
    }
    @Test void sleepyBlinkOpensSlowlyWithoutChangingAnActiveBlink() {
        var time = new AtomicLong(1000); var sleepy = new BlinkState(time::get, () -> .5);
        sleepy.openingScale(1.9f); sleepy.triggerBlink(); time.addAndGet(185);
        assertTrue(sleepy.getBlinkProgress() > .2); var closed = sleepy.getBlinkProgress();
        sleepy.openingScale(1); assertEquals(closed,sleepy.getBlinkProgress()); time.addAndGet(110);
        assertEquals(0,sleepy.getBlinkProgress());
    }
}
