package com.austinv11.peripheralsplusplus.tiles;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.ChatUtil;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the chat box's Lua surface: argument validation, the range clamp and the rate limiter.
 *
 * <p>
 * The tile entity deliberately has no world here, so {@code ChatUtil.sendMessage} returns before touching the network
 * or a {@code WorldProvider}; the parts under test are the ones that used to be wrong — {@code tell} ignoring
 * {@code Config.sayRange} and the limiter depending on message spacing (CODE_REVIEW.md sections 5.8, 5.10).
 * Broadcasting itself is covered by the in-game {@code gameTest} mod.
 * </p>
 */
public class ChatBoxTest {

    @After
    public void tearDown() {
        Config.enableChatBox = true;
        Config.sayRange = 64;
        Config.sayRate = 1;
        Config.logCoords = true;
        Config.allowUnlimitedVertical = true;
    }

    private static TileEntityChatBox chatBox() {
        return new TileEntityChatBox();
    }

    private static Object[] say(TileEntityChatBox box, Object... arguments) throws LuaException, InterruptedException {
        return box.callMethod(null, null, 0, arguments);
    }

    private static Object[] tell(TileEntityChatBox box, Object... arguments) throws LuaException, InterruptedException {
        return box.callMethod(null, null, 1, arguments);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rate limiting
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theRateLimitBlocksASecondMessageInTheSameSecond() throws Exception {
        Config.sayRate = 1;
        TileEntityChatBox box = chatBox();
        say(box, "first");
        assertLuaError(() -> say(box, "second"), "too often");
    }

    @Test
    public void theRateLimitAllowsTheConfiguredNumberOfMessages() throws Exception {
        Config.sayRate = 2;
        TileEntityChatBox box = chatBox();
        say(box, "first");
        say(box, "second");
        assertLuaError(() -> say(box, "third"), "too often");
    }

    @Test
    public void aZeroRateDisablesTheLimiter() throws Exception {
        Config.sayRate = 0;
        TileEntityChatBox box = chatBox();
        for (int i = 0; i < 5; i++) Assert.assertArrayEquals(new Object[] { true }, say(box, "message " + i));
    }

    @Test
    public void theWindowRollsOverAfterOneSecond() throws Exception {
        Config.sayRate = 1;
        TileEntityChatBox box = chatBox();
        say(box, "first");
        for (int i = 0; i < 20; i++) box.updateEntity();
        Assert.assertArrayEquals(new Object[] { true }, say(box, "second"));
    }

    @Test
    public void theWindowDoesNotRollOverEarly() throws Exception {
        Config.sayRate = 1;
        TileEntityChatBox box = chatBox();
        say(box, "first");
        for (int i = 0; i < 19; i++) box.updateEntity();
        assertLuaError(() -> say(box, "second"), "too often");
    }

    @Test
    public void sayAndTellShareTheBudget() throws Exception {
        Config.sayRate = 1;
        TileEntityChatBox box = chatBox();
        say(box, "first");
        assertLuaError(() -> tell(box, "nobody", "second"), "too often");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Argument validation
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void sayRejectsMissingAndWrongTypedArguments() {
        assertLuaError(() -> say(chatBox()), "Too few arguments");
        assertLuaError(() -> say(chatBox(), 42.0d), "number");
        assertLuaError(() -> say(chatBox(), "text", "far"), "number");
        assertLuaError(() -> say(chatBox(), "text", 1.0d, "yes"), "boolean");
        assertLuaError(() -> say(chatBox(), "text", 1.0d, true, 7.0d), "string");
    }

    @Test
    public void tellRejectsMissingAndWrongTypedArguments() {
        assertLuaError(() -> tell(chatBox(), "player"), "Too few arguments");
        assertLuaError(() -> tell(chatBox(), "player", 1.0d), "string");
        assertLuaError(() -> tell(chatBox(), "player", "text", "far"), "number");
        assertLuaError(() -> tell(chatBox(), "player", "text", 1.0d, "yes"), "boolean");
    }

    @Test
    public void tellRefusesALabelWhenCoordinatesAreLogged() {
        Config.logCoords = true;
        assertLuaError(() -> tell(chatBox(), "player", "text", 10.0d, false, "label"), "Coordinate logging");
    }

    @Test
    public void aDisabledChatBoxRejectsEverything() {
        Config.enableChatBox = false;
        assertLuaError(() -> say(chatBox(), "hello"), "disabled");
        assertLuaError(() -> tell(chatBox(), "player", "hello"), "disabled");
    }

    @Test
    public void anUnknownMethodReturnsNothing() throws Exception {
        Assert.assertEquals(0, chatBox().callMethod(null, null, 99, new Object[0]).length);
    }

    @Test
    public void theMethodListIsStable() {
        Assert.assertArrayEquals(new String[] { "say", "tell" }, chatBox().getMethodNames());
    }

    @Test
    public void tellToAnOfflinePlayerReportsFalse() throws Exception {
        // No world is attached, so nothing can be delivered.
        Assert.assertArrayEquals(new Object[] { false }, tell(chatBox(), "nobody", "hello"));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Formatting and clamping (pure helpers)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void coordinateLoggingWins() {
        Assert.assertEquals("[#1,2,3] hi", ChatUtil.formatMessage("hi", "bot", true, "[#1,2,3] "));
        Assert.assertEquals("hi", ChatUtil.formatMessage("hi", "bot", true, null));
    }

    @Test
    public void theLabelIsUsedWhenLoggingIsOff() {
        Assert.assertEquals("[bot] hi", ChatUtil.formatMessage("hi", "bot", false, null));
    }

    @Test
    public void theDefaultTagIsUsedWithoutALabel() {
        Assert.assertEquals("[@] hi", ChatUtil.formatMessage("hi", null, false, null));
    }

    @Test
    public void rangesAreClampedToTheConfiguredMaximum() {
        Assert.assertEquals(64.0d, ChatUtil.clampRange(1000.0d, 64.0d), 0d);
        Assert.assertEquals(8.0d, ChatUtil.clampRange(8.0d, 64.0d), 0d);
        Assert.assertEquals(64.0d, ChatUtil.clampRange(-5.0d, 64.0d), 0d);
    }

    @Test
    public void aNegativeConfiguredRangeIsUnlimited() {
        Assert.assertEquals(Double.MAX_VALUE, ChatUtil.clampRange(-1.0d, -1.0d), 0d);
        Assert.assertEquals(32.0d, ChatUtil.clampRange(32.0d, -1.0d), 0d);
    }

    private static void assertLuaError(ThrowingCall call, String expectedFragment) {
        try {
            call.run();
            Assert.fail("expected a LuaException mentioning " + expectedFragment);
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .toLowerCase()
                    .contains(expectedFragment.toLowerCase()));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    private interface ThrowingCall {

        void run() throws Exception;
    }
}
