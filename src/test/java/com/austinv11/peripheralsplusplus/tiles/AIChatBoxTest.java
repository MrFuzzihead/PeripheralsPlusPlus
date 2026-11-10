package com.austinv11.peripheralsplusplus.tiles;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.tiles.TileEntityAIChatBox.BotSessionLuaObject;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;

/**
 * Covers the AI chat box's session bookkeeping and its Lua argument helpers.
 *
 * <p>
 * Creating a session talks to Cleverbot over HTTP, so {@code newSession} is out of scope; everything around it — the
 * session handle, the argument parsing, the event fan-out and the disabled guard — needs no network.
 * </p>
 */
public class AIChatBoxTest {

    @After
    public void tearDown() {
        com.austinv11.peripheralsplusplus.reference.Config.enableAIChatBox = true;
    }

    private static TileEntityAIChatBox box() {
        return new TileEntityAIChatBox();
    }

    private static BotSessionLuaObject session(TileEntityAIChatBox box, IComputerAccess computer) {
        return new BotSessionLuaObject(
            java.util.UUID.randomUUID(),
            Mockito.mock(com.google.code.chatterbotapi.ChatterBot.class),
            Mockito.mock(com.google.code.chatterbotapi.ChatterBotSession.class),
            computer,
            box);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Peripheral surface
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theBoxExposesThreeMethods() {
        Assert.assertEquals(3, box().getMethodNames().length);
        Assert.assertEquals("aiChatBox", box().getType());
    }

    @Test
    public void aDisabledBoxRejectsEveryMethod() {
        com.austinv11.peripheralsplusplus.reference.Config.enableAIChatBox = false;
        assertLuaError(() -> box().callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void getAllSessionsIsEmptyOnAFreshBox() throws Exception {
        Object[] result = box().callMethod(null, null, 2, new Object[0]);
        Assert.assertEquals(1, result.length);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> table = (java.util.Map<String, Object>) result[0];
        Assert.assertTrue(table.isEmpty());
    }

    @Test
    public void getSessionReportsNothingForAnUnknownId() throws Exception {
        Assert.assertEquals(
            0,
            box().callMethod(
                null,
                null,
                1,
                new Object[] { java.util.UUID.randomUUID()
                    .toString() }).length);
    }

    @Test
    public void getSessionRejectsAMalformedId() {
        assertLuaError(() -> box().callMethod(null, null, 1, new Object[] { "not-a-uuid" }), "Bad uuid");
        assertLuaError(() -> box().callMethod(null, null, 1, new Object[0]), "#1");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Session handles
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void aSessionHandleReportsItsId() throws Exception {
        TileEntityAIChatBox box = box();
        BotSessionLuaObject handle = session(box, Mockito.mock(IComputerAccess.class));
        Assert.assertArrayEquals(
            new Object[] { handle.getUUID()
                .toString() },
            handle.callMethod(null, 2, new Object[0]));
    }

    @Test
    public void aSessionHandleCanBeRemoved() throws Exception {
        TileEntityAIChatBox box = box();
        BotSessionLuaObject handle = session(box, Mockito.mock(IComputerAccess.class));
        box.getSessions()
            .add(handle);

        Assert.assertArrayEquals(new Object[] { true }, handle.callMethod(null, 3, new Object[0]));
        Assert.assertTrue(
            box.getSessions()
                .isEmpty());
        // A removed handle refuses everything afterwards.
        assertLuaError(() -> handle.callMethod(null, 2, new Object[0]), "removed session");
    }

    @Test
    public void aSessionHandleExposesFourMethods() {
        BotSessionLuaObject handle = session(box(), Mockito.mock(IComputerAccess.class));
        Assert.assertArrayEquals(new String[] { "think", "thinkAsync", "getUUID", "remove" }, handle.getMethodNames());
    }

    @Test
    public void aSessionHandleOfAnInvalidBoxRefusesEverything() throws Exception {
        TileEntityAIChatBox box = box();
        BotSessionLuaObject handle = session(box, Mockito.mock(IComputerAccess.class));
        box.invalidate();
        assertLuaError(() -> handle.callMethod(null, 2, new Object[0]), "invalid tile entity");
    }

    @Test
    public void containsUuidUsesIdentity() {
        TileEntityAIChatBox box = box();
        BotSessionLuaObject handle = session(box, Mockito.mock(IComputerAccess.class));
        List<BotSessionLuaObject> sessions = new java.util.ArrayList<BotSessionLuaObject>();
        sessions.add(handle);
        Assert.assertTrue(BotSessionLuaObject.containsUUID(sessions, handle.getUUID()));
        Assert.assertFalse(BotSessionLuaObject.containsUUID(sessions, java.util.UUID.randomUUID()));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Event fan-out
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void anEventReachesEveryAttachedComputer() {
        TileEntityAIChatBox box = box();
        IComputerAccess first = Mockito.mock(IComputerAccess.class);
        IComputerAccess second = Mockito.mock(IComputerAccess.class);
        Mockito.when(first.getAttachmentName())
            .thenReturn("left");
        Mockito.when(second.getAttachmentName())
            .thenReturn("right");
        box.attach(first);
        box.attach(second);

        box.sendEvent(new Object[] { true, "hi", "some-uuid" });

        Mockito.verify(first)
            .queueEvent(
                Mockito.eq(com.austinv11.peripheralsplusplus.cleverbot.AIChatRequest.EVENT),
                Mockito.any(Object[].class));
        Mockito.verify(second)
            .queueEvent(
                Mockito.eq(com.austinv11.peripheralsplusplus.cleverbot.AIChatRequest.EVENT),
                Mockito.any(Object[].class));
    }

    @Test
    public void aDetachedComputerStopsReceivingEvents() {
        TileEntityAIChatBox box = box();
        IComputerAccess computer = Mockito.mock(IComputerAccess.class);
        Mockito.when(computer.getAttachmentName())
            .thenReturn("left");
        box.attach(computer);
        box.detach(computer);
        box.sendEvent(new Object[] { true, "hi", "some-uuid" });
        Mockito.verify(computer, Mockito.never())
            .queueEvent(Mockito.anyString(), Mockito.any(Object[].class));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Argument helper
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theArgumentHelperReadsStrings() throws Exception {
        Assert.assertEquals("hello", TileEntityAIChatBox.ArgumentHelper.getString(new Object[] { "hello" }, 0));
        Assert.assertEquals("fallback", TileEntityAIChatBox.ArgumentHelper.getString(new Object[0], 0, "fallback"));
        Assert.assertEquals(
            "fallback",
            TileEntityAIChatBox.ArgumentHelper.getString(new Object[] { 42.0d }, 0, "fallback"));
        assertLuaError(() -> TileEntityAIChatBox.ArgumentHelper.getString(new Object[0], 0), "Bad argument #1");
        assertLuaError(() -> TileEntityAIChatBox.ArgumentHelper.getString(new Object[] { 1.0d }, 0), "Bad argument #1");
    }

    @Test
    public void theArgumentHelperReadsUuids() throws Exception {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        Assert.assertEquals(uuid, TileEntityAIChatBox.ArgumentHelper.getUUID(new Object[] { uuid.toString() }, 0));
        Assert.assertEquals(uuid, TileEntityAIChatBox.ArgumentHelper.getUUID(new Object[0], 0, uuid));
        assertLuaError(() -> TileEntityAIChatBox.ArgumentHelper.getUUID(new Object[] { "nope" }, 0), "Bad uuid");
        // A missing argument with no default is an argument error, not a bad uuid.
        assertLuaError(() -> TileEntityAIChatBox.ArgumentHelper.getUUID(new Object[0], 0), "Bad argument #1");
    }

    @Test
    public void aRandomUuidAvoidsTheExistingOnes() {
        TileEntityAIChatBox box = box();
        List<BotSessionLuaObject> sessions = new java.util.ArrayList<BotSessionLuaObject>();
        java.util.UUID first = TileEntityAIChatBox.ArgumentHelper.randomUUID(sessions);
        Assert.assertNotNull(first);
        BotSessionLuaObject handle = new BotSessionLuaObject(
            first,
            Mockito.mock(com.google.code.chatterbotapi.ChatterBot.class),
            Mockito.mock(com.google.code.chatterbotapi.ChatterBotSession.class),
            Mockito.mock(IComputerAccess.class),
            box);
        sessions.add(handle);
        Assert.assertNotEquals(first, TileEntityAIChatBox.ArgumentHelper.randomUUID(sessions));
    }

    @Test
    public void theBoxExposesItsFactoryAndSessions() {
        TileEntityAIChatBox box = box();
        Assert.assertNotNull(box.getFactory());
        Assert.assertNotNull(box.getSessions());
        Assert.assertTrue(
            box.getSessions()
                .isEmpty());
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
