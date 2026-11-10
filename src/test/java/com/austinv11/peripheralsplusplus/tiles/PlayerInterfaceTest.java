package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the player interface's peripheral surface.
 *
 * <p>
 * The player lookup used to dereference {@code MinecraftServer.getServer()} unconditionally, and its side setters used
 * {@code ForgeDirection.valueOf} on the raw argument, so an unknown side name surfaced as an
 * {@code IllegalArgumentException} rather than a Lua error.
 * </p>
 */
public class PlayerInterfaceTest {

    @After
    public void tearDown() {
        Config.enablePlayerInterface = true;
        Config.enableInterfacePermissions = true;
    }

    private static TileEntityPlayerInterface anInterface() {
        TileEntityPlayerInterface te = new TileEntityPlayerInterface();
        te.setWorldObj(Mockito.mock(World.class));
        return te;
    }

    @Test
    public void theSideSettersAcceptNamesAndOrdinals() throws Exception {
        TileEntityPlayerInterface te = anInterface();
        te.callMethod(null, null, 1, new Object[] { "north" });
        te.callMethod(null, null, 2, new Object[] { 3.0d });
        Assert.assertArrayEquals(new Object[] { "NORTH" }, te.callMethod(null, null, 3, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "SOUTH" }, te.callMethod(null, null, 4, new Object[0]));
    }

    @Test
    public void anUnsetSideReadsAsNil() throws Exception {
        TileEntityPlayerInterface te = anInterface();
        Assert.assertArrayEquals(new Object[] { null }, te.callMethod(null, null, 3, new Object[0]));
        Assert.assertArrayEquals(new Object[] { null }, te.callMethod(null, null, 4, new Object[0]));
    }

    @Test
    public void anUnknownSideIsALuaError() {
        TileEntityPlayerInterface te = anInterface();
        assertLuaError(() -> te.callMethod(null, null, 1, new Object[] { "sideways" }), "unknown side");
        assertLuaError(() -> te.callMethod(null, null, 1, new Object[0]), "Bad argument #1");
        assertLuaError(() -> te.callMethod(null, null, 1, new Object[] { true }), "string or number");
    }

    @Test
    public void aPlayerLookupWithoutAServerIsReported() {
        // No dedicated server in a unit test, so MinecraftServer.getServer() is null.
        try (MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded(Mockito.anyString()))
                .thenReturn(false);
            TileEntityPlayerInterface te = anInterface();
            assertLuaError(() -> te.callMethod(null, null, 0, new Object[] { "alice" }), "No server is running");
        }
    }

    @Test
    public void thePlayerArgumentIsRequired() {
        TileEntityPlayerInterface te = anInterface();
        assertLuaError(() -> te.callMethod(null, null, 0, new Object[0]), "Bad argument #1");
        assertLuaError(() -> te.callMethod(null, null, 0, new Object[] { 1.0d }), "string");
    }

    @Test
    public void aDisabledInterfaceRejectsEverything() {
        Config.enablePlayerInterface = false;
        TileEntityPlayerInterface te = anInterface();
        assertLuaError(() -> te.callMethod(null, null, 0, new Object[] { "alice" }), "disabled");
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

    @Test
    public void theInterfaceExposesEightSlots() {
        Assert.assertEquals(8, anInterface().getSize());
        Assert.assertEquals(8, anInterface().getSizeInventory());
        Assert.assertEquals("playerInterfacePPP", anInterface().getType());
    }
}
