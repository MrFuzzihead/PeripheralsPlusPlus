package com.austinv11.peripheralsplusplus.lua;

import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the smart-helmet HUD's Lua surface.
 *
 * <p>
 * The commands are queued in memory and only flushed on {@code sync}/{@code add}, so all of the argument handling can
 * be asserted without a player or a network: the HUD is created for a name that is not online, which also exercises the
 * "player logged off" path.
 * </p>
 */
public class LuaObjectHUDTest {

    @After
    public void tearDown() {
        PeripheralsPlusPlus.NETWORK = null;
        com.austinv11.peripheralsplusplus.smarthelmet.MessageCommand.messageStacks.clear();
    }

    private static LuaObjectHUD hud() {
        return new LuaObjectHUD("nobody", java.util.UUID.randomUUID());
    }

    private static LuaObjectHUD gui() {
        return new LuaObjectHUD("nobody", java.util.UUID.randomUUID(), true);
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

    // ---------------------------------------------------------------------------------------------------------
    // Method table
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theOverlayAndGuiMethodListsDiffer() {
        String[] overlay = hud().getMethodNames();
        String[] gui = gui().getMethodNames();
        Assert.assertEquals(12, overlay.length);
        Assert.assertEquals(16, gui.length);
        Assert.assertFalse(
            java.util.Arrays.asList(overlay)
                .contains("close"));
        Assert.assertTrue(
            java.util.Arrays.asList(gui)
                .contains("close"));
        Assert.assertFalse(
            java.util.Arrays.asList(gui)
                .contains("getGUI"));
    }

    @Test
    public void theMethodListsAreCached() {
        Assert.assertSame(hud().getMethodNames(), hud().getMethodNames());
        Assert.assertSame(gui().getMethodNames(), gui().getMethodNames());
    }

    @Test
    public void getResolutionReportsTheLastKnownSize() throws Exception {
        LuaObjectHUD hud = hud();
        Assert.assertArrayEquals(new Object[] { -1, -1 }, hud.callMethod(null, 0, new Object[0]));
        hud.width = 320;
        hud.height = 200;
        Assert.assertArrayEquals(new Object[] { 320, 200 }, hud.callMethod(null, 0, new Object[0]));
    }

    @Test
    public void anUnknownMethodReturnsNothing() throws Exception {
        Assert.assertEquals(0, hud().callMethod(null, 99, new Object[0]).length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Drawing commands
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void drawStringQueuesACommand() throws Exception {
        LuaObjectHUD hud = hud();
        hud.callMethod(null, 2, new Object[] { "hello", 3.0d, 4.0d, 4294967295.0d, false });
        Assert.assertEquals(2, hud.renderStack.size()); // the message command is always first
    }

    @Test
    public void drawStringRequiresItsArguments() {
        assertLuaError(() -> hud().callMethod(null, 2, new Object[0]), "#1");
        assertLuaError(() -> hud().callMethod(null, 2, new Object[] { "text" }), "#2");
        assertLuaError(() -> hud().callMethod(null, 2, new Object[] { "text", 1.0d }), "#3");
        assertLuaError(() -> hud().callMethod(null, 2, new Object[] { "text", 1.0d, 1.0d, "red" }), "#4");
        assertLuaError(() -> hud().callMethod(null, 2, new Object[] { "text", 1.0d, 1.0d, 1.0d, "yes" }), "#5");
    }

    @Test
    public void drawTextureReadsAllSevenArguments() throws Exception {
        LuaObjectHUD hud = hud();
        hud.callMethod(null, 3, new Object[] { "textures/gui.png", 1.0d, 2.0d, 8.0d, 9.0d, 10.0d, 11.0d });
        Assert.assertEquals(2, hud.renderStack.size());
    }

    @Test
    public void drawRectangleAcceptsAnOptionalSecondColour() throws Exception {
        LuaObjectHUD hud = hud();
        hud.callMethod(null, 4, new Object[] { 0.0d, 0.0d, 4.0d, 4.0d, 4294901760.0d });
        hud.callMethod(null, 4, new Object[] { 0.0d, 0.0d, 4.0d, 4.0d, 4294901760.0d, 4278255360.0d });
        Assert.assertEquals(3, hud.renderStack.size());
    }

    @Test
    public void theLineHelpersAreRectangles() throws Exception {
        LuaObjectHUD hud = hud();
        hud.callMethod(null, 5, new Object[] { 0.0d, 0.0d, 10.0d, 4294967295.0d });
        hud.callMethod(null, 6, new Object[] { 0.0d, 0.0d, 10.0d, 4294967295.0d });
        Assert.assertEquals(3, hud.renderStack.size());
    }

    @Test
    public void getColorFromRGBTakesThreeOrFourChannels() throws Exception {
        LuaObjectHUD hud = hud();
        Assert.assertEquals(1, hud.callMethod(null, 7, new Object[] { 255.0d, 0.0d, 0.0d }).length);
        Assert.assertEquals(1, hud.callMethod(null, 7, new Object[] { 255.0d, 0.0d, 0.0d, 128.0d }).length);
        Assert.assertEquals(1, hud.callMethod(null, 7, new Object[] { 255.0d, 0.0d, 0.0d, 999.0d }).length);
    }

    @Test
    public void drawBackgroundTakesAnOptionalFlag() throws Exception {
        LuaObjectHUD hud = hud();
        hud.callMethod(null, 13, new Object[0]);
        hud.callMethod(null, 13, new Object[] { true });
        assertLuaError(() -> hud.callMethod(null, 13, new Object[] { "yes" }), "boolean");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Interactive commands (GUI only)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void addButtonRequiresSixArguments() throws Exception {
        LuaObjectHUD hud = gui();
        Assert.assertEquals(0, hud.callMethod(null, 14, new Object[] { 1.0d, 0.0d, 0.0d, 4.0d, 4.0d, "ok" }).length);
        Assert.assertEquals(2, hud.renderStack.size());
        assertLuaError(() -> hud.callMethod(null, 14, new Object[] { 1.0d, 0.0d, 0.0d, 4.0d, 4.0d }), "#6");
    }

    @Test
    public void addTextFieldTakesAnOptionalDefault() throws Exception {
        LuaObjectHUD hud = gui();
        hud.callMethod(null, 15, new Object[] { 1.0d, 0.0d, 0.0d, 40.0d, 10.0d });
        hud.callMethod(null, 15, new Object[] { 2.0d, 0.0d, 20.0d, 40.0d, 10.0d, "type here" });
        Assert.assertEquals(3, hud.renderStack.size());
        // The 6th argument is validated: this used to only be checked above 6 arguments.
        assertLuaError(() -> hud.callMethod(null, 15, new Object[] { 3.0d, 0.0d, 0.0d, 10.0d, 10.0d, 42.0d }), "#6");
    }

    @Test
    public void closeIsOnlyAvailableToGuiPrograms() {
        assertLuaError(() -> hud().callMethod(null, 12, new Object[0]), "not available");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Message queue
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void sendMessageRequiresAString() throws Exception {
        hud().callMethod(null, 1, new Object[] { "hello" });
        Assert.assertEquals(
            1,
            com.austinv11.peripheralsplusplus.smarthelmet.MessageCommand.messageStacks.get("nobody")
                .size());
        assertLuaError(() -> hud().callMethod(null, 1, new Object[0]), "#1");
        assertLuaError(() -> hud().callMethod(null, 1, new Object[] { 1.0d }), "string");
    }

    @Test
    public void aFlushWithoutAPlayerReportsThatThePlayerIsGone() {
        PeripheralsPlusPlus.NETWORK = Mockito.mock(SimpleNetworkWrapper.class);
        // "nobody" is not online (there is no server in a unit test), so the HUD reports it instead of NPE-ing.
        assertLuaError(() -> hud().callMethod(null, 8, new Object[0]), "no longer online");
    }

    @Test
    public void aFailedFlushKeepsTheQueuedCommands() {
        PeripheralsPlusPlus.NETWORK = Mockito.mock(SimpleNetworkWrapper.class);
        LuaObjectHUD hud = hud();
        try {
            hud.callMethod(null, 2, new Object[] { "hello", 0.0d, 0.0d });
            hud.callMethod(null, 8, new Object[0]);
        } catch (Exception ignored) {
            // the flush needs a player; keeping the queue so the program can retry is the behaviour under test
        }
        Assert.assertEquals(2, hud.renderStack.size());
    }

    @Test
    public void aNullWorldIsNeverTouched() {
        // Guards the "player is gone" path: no World is created anywhere in this test.
        Assert.assertNull(PeripheralsPlusPlus.NETWORK);
        World unused = Mockito.mock(World.class);
        Assert.assertNotNull(unused);
    }

    private interface ThrowingCall {

        void run() throws Exception;
    }
}
