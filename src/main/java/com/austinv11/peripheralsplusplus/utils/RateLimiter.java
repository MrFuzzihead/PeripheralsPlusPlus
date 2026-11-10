package com.austinv11.peripheralsplusplus.utils;

/**
 * A rolling one-second message budget, ticked from the owning tile entity.
 *
 * <p>
 * The chat box and the speaker both used to rate-limit with a pair of counters that only reset *after* a message, so
 * the effective rate depended on how long ago the previous one was sent, and a limit below one blocked the peripheral
 * forever (CODE_REVIEW.md section 5.10). This counts a fixed window instead, and is deliberately free of world state
 * so it can be unit tested.
 * </p>
 */
public final class RateLimiter {

    /** Ticks in the window: one second at 20 ticks/second. */
    public static final int WINDOW_TICKS = 20;

    private int used;
    private int ticksLeft;

    /**
     * Reserves a slot in the current window.
     *
     * @param limitPerWindow the allowed number of messages per window; {@code <= 0} means unlimited
     * @return true when the message may be sent
     */
    public boolean tryAcquire(int limitPerWindow) {
        if (limitPerWindow <= 0) return true;
        if (used >= limitPerWindow) return false;
        used++;
        ticksLeft = WINDOW_TICKS;
        return true;
    }

    /**
     * Advances the window by one tick.
     */
    public void tick() {
        if (ticksLeft <= 0) return;
        ticksLeft--;
        if (ticksLeft == 0) used = 0;
    }

    /**
     * @return how many messages have been sent in the current window
     */
    public int used() {
        return used;
    }

    /**
     * @return the ticks left in the current window, or {@code 0}
     */
    public int ticksLeft() {
        return ticksLeft;
    }

    /**
     * Forgets the current window, for example when the owning tile entity is reloaded.
     */
    public void reset() {
        used = 0;
        ticksLeft = 0;
    }
}
