package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the time sensor. {@code getDate} used to allocate a {@code SimpleDateFormat} and a {@code String[]} per call
 * and to depend on the default locale, and {@code startTimer} returned nothing at all
 * (CODE_REVIEW.md section 5.22).
 */
public class TimeSensorTest {

    @After
    public void tearDown() {
        Config.enableTimeSensor = true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> date(TileEntityTimeSensor sensor) throws LuaException, InterruptedException {
        Object[] result = sensor.callMethod(null, null, 0, new Object[0]);
        Assert.assertEquals(1, result.length);
        return (Map<String, Integer>) result[0];
    }

    @Test
    public void getDateReturnsEveryCalendarField() throws Exception {
        Map<String, Integer> date = date(new TileEntityTimeSensor());
        for (String key : new String[] { "year", "month", "day", "hour", "minute", "second" })
            Assert.assertTrue("missing " + key, date.containsKey(key));
        Assert.assertTrue(date.get("year") >= 1970);
        // Calendar months are 0-based; Lua callers get the human month.
        Assert.assertTrue("month " + date.get("month"), date.get("month") >= 1 && date.get("month") <= 12);
        Assert.assertTrue(date.get("day") >= 1 && date.get("day") <= 31);
        Assert.assertTrue(date.get("hour") >= 0 && date.get("hour") <= 23);
        Assert.assertTrue(date.get("minute") >= 0 && date.get("minute") <= 59);
        Assert.assertTrue(date.get("second") >= 0 && date.get("second") <= 60);
    }

    @Test
    public void getTimeIsEpochMilliseconds() throws Exception {
        Object[] result = new TileEntityTimeSensor().callMethod(null, null, 1, new Object[0]);
        Assert.assertEquals(1, result.length);
        long now = (Long) result[0];
        Assert.assertTrue("epoch ms should be recent", Math.abs(System.currentTimeMillis() - now) < 60_000L);
    }

    @Test
    public void aStoppedTimerReportsZero() throws Exception {
        Object[] result = new TileEntityTimeSensor().callMethod(null, null, 3, new Object[0]);
        Assert.assertEquals(0L, result[0]);
    }

    @Test
    public void startThenStopReportsAnElapsedTime() throws Exception {
        TileEntityTimeSensor sensor = new TileEntityTimeSensor();
        Object[] started = sensor.callMethod(null, null, 2, new Object[0]);
        Assert.assertEquals(1, started.length);
        Thread.sleep(5L);
        Object[] stopped = sensor.callMethod(null, null, 3, new Object[0]);
        Assert.assertTrue("elapsed should be non-negative", (Long) stopped[0] >= 0L);
    }

    @Test
    public void theTimerOnlyFiresOnce() throws Exception {
        TileEntityTimeSensor sensor = new TileEntityTimeSensor();
        sensor.callMethod(null, null, 2, new Object[0]);
        sensor.callMethod(null, null, 3, new Object[0]);
        // The profiler is cleared, so a second stop must not reuse the previous reading.
        Assert.assertEquals(0L, sensor.callMethod(null, null, 3, new Object[0])[0]);
    }

    @Test
    public void anUnknownMethodReturnsNothing() throws Exception {
        Assert.assertEquals(0, new TileEntityTimeSensor().callMethod(null, null, 99, new Object[0]).length);
    }

    @Test
    public void theSensorCanBeDisabled() {
        Config.enableTimeSensor = false;
        try {
            new TileEntityTimeSensor().callMethod(null, null, 0, new Object[0]);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("disabled"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    @Test
    public void theTableStaysAHashMap() throws Exception {
        Assert.assertTrue(date(new TileEntityTimeSensor()) instanceof HashMap);
    }
}
