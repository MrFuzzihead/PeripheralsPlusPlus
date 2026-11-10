package com.austinv11.peripheralsplusplus.util;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.utils.RateLimiter;

/**
 * Covers the shared one-second message budget used by the chat box.
 *
 * <p>
 * The old limiter only reset after a message was sent, so the effective rate depended on how long ago the previous
 * one went out, and a configured limit of zero blocked the peripheral permanently (CODE_REVIEW.md section 5.10).
 * </p>
 */
public class RateLimiterTest {

    @Test
    public void aPositiveLimitAllowsExactlyThatManyMessages() {
        RateLimiter limiter = new RateLimiter();
        Assert.assertTrue(limiter.tryAcquire(2));
        Assert.assertTrue(limiter.tryAcquire(2));
        Assert.assertFalse(limiter.tryAcquire(2));
        Assert.assertEquals(2, limiter.used());
    }

    @Test
    public void aZeroLimitIsUnlimited() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 100; i++) Assert.assertTrue(limiter.tryAcquire(0));
        Assert.assertEquals(0, limiter.used());
    }

    @Test
    public void aNegativeLimitIsUnlimited() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 100; i++) Assert.assertTrue(limiter.tryAcquire(-1));
    }

    @Test
    public void theWindowRollsOverAfterExactlyTwentyTicks() {
        RateLimiter limiter = new RateLimiter();
        Assert.assertTrue(limiter.tryAcquire(1));
        for (int i = 0; i < RateLimiter.WINDOW_TICKS - 1; i++) limiter.tick();
        Assert.assertFalse("the window must not roll over early", limiter.tryAcquire(1));
        limiter.tick();
        Assert.assertTrue("the window should have rolled over", limiter.tryAcquire(1));
    }

    @Test
    public void ticksWithoutMessagesDoNothing() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 100; i++) limiter.tick();
        Assert.assertEquals(0, limiter.used());
        Assert.assertEquals(0, limiter.ticksLeft());
        Assert.assertTrue(limiter.tryAcquire(1));
    }

    @Test
    public void aNewMessageRestartsTheWindow() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire(5);
        for (int i = 0; i < 10; i++) limiter.tick();
        limiter.tryAcquire(5);
        Assert.assertEquals(RateLimiter.WINDOW_TICKS, limiter.ticksLeft());
    }

    @Test
    public void resetForgetsTheWindow() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire(1);
        Assert.assertFalse(limiter.tryAcquire(1));
        limiter.reset();
        Assert.assertTrue(limiter.tryAcquire(1));
    }

    @Test
    public void aRejectedMessageDoesNotExtendTheWindow() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire(1);
        int ticksLeft = limiter.ticksLeft();
        limiter.tryAcquire(1);
        Assert.assertEquals(ticksLeft, limiter.ticksLeft());
    }
}
