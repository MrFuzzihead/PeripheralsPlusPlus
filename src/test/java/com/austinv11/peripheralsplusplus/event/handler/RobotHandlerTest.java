package com.austinv11.peripheralsplusplus.event.handler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.reference.Config;

/**
 * Covers the nano-bot input queue's bookkeeping.
 *
 * <p>
 * {@link RobotHandler} is deliberately never constructed: {@code new java.awt.Robot()} drives the real mouse and
 * keyboard on a machine with a display. The operations are pure state machines, and the key-name lookup is the part
 * that used to NPE, so both are asserted directly (CODE_REVIEW.md section 5.28).
 * </p>
 */
public class RobotHandlerTest {

    @After
    public void tearDown() {
        RobotHandler.operationList.clear();
        Config.secondsBeforeReversal = 5.0d;
    }

    @Test
    public void anOperationFinishesAfterTheReversalDelay() {
        Config.secondsBeforeReversal = 0.05d; // 1 tick
        Recorder recorder = new Recorder();

        Assert.assertFalse(recorder.isFinished());
        recorder.tick(null);
        Assert.assertTrue("the operation ran its action", recorder.operated);
        Assert.assertTrue(recorder.isFinished());
        recorder.onFinish(null);
        Assert.assertTrue(recorder.finished);
    }

    @Test
    public void aZeroDelayStillOperatesOnce() {
        // secondsBeforeReversal = 0 used to make isFinished() true immediately, so operate() never ran.
        Config.secondsBeforeReversal = 0.0d;
        Recorder recorder = new Recorder();
        Assert.assertTrue(recorder.isFinished());
        recorder.tick(null);
        Assert.assertTrue(recorder.operated);
    }

    @Test
    public void theTickerCountsDownToZero() {
        Config.secondsBeforeReversal = 0.15d; // 3 ticks
        Recorder recorder = new Recorder();
        int ticks = 0;
        while (!recorder.isFinished() && ticks < 10) {
            recorder.tick(null);
            ticks++;
        }
        Assert.assertEquals(3, ticks);
        Assert.assertTrue(recorder.operated);
    }

    @Test
    public void aKnownKeyNameResolvesToAKeyCode() {
        RobotHandler.KeyBoardOperation operation = new RobotHandler.KeyBoardOperation();
        operation.key = "W";
        Assert.assertEquals(java.awt.event.KeyEvent.VK_W, operation.keyCode());
        operation.key = "space";
        Assert.assertEquals(java.awt.event.KeyEvent.VK_SPACE, operation.keyCode());
    }

    @Test
    public void anUnknownKeyNameIsRejectedRatherThanNpeIn() {
        RobotHandler.KeyBoardOperation operation = new RobotHandler.KeyBoardOperation();
        operation.key = "definitelyNotAKey";
        Assert.assertEquals(-1, operation.keyCode());
        operation.key = null;
        Assert.assertEquals(-1, operation.keyCode());
    }

    @Test
    public void keyNamesAreCaseInsensitive() {
        RobotHandler.KeyBoardOperation lower = new RobotHandler.KeyBoardOperation();
        lower.key = "a";
        RobotHandler.KeyBoardOperation upper = new RobotHandler.KeyBoardOperation();
        upper.key = "A";
        Assert.assertEquals(java.awt.event.KeyEvent.VK_A, lower.keyCode());
        Assert.assertEquals(java.awt.event.KeyEvent.VK_A, upper.keyCode());
    }

    @Test
    public void theOperationsAreDistinctTypes() {
        Assert.assertNotEquals(
            new RobotHandler.KeyBoardOperation().getClass(),
            new RobotHandler.MouseClickOperation().getClass());
        Assert.assertNotEquals(
            new RobotHandler.MouseMoveOperation().getClass(),
            new RobotHandler.MouseClickOperation().getClass());
    }

    /** An operation that records whether {@code operate} and {@code onFinish} ran. */
    private static final class Recorder extends RobotHandler.RobotOperation {

        private boolean operated;
        private boolean finished;

        @Override
        public void onFinish(java.awt.Robot robot) {
            finished = true;
        }

        @Override
        public void operate(java.awt.Robot robot) {
            operated = true;
        }
    }
}
