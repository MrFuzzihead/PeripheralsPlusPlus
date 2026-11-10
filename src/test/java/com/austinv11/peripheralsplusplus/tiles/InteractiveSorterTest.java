package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the interactive sorter against a mocked world.
 *
 * <p>
 * The item arithmetic itself is covered by {@code InventoryUtilsTest}; this class is about the peripheral wiring:
 * side resolution, the 1-based slot argument, the "no inventory on that side" path and the event the block raises when
 * something lands in its slot.
 * </p>
 */
public class InteractiveSorterTest {

    private static final Item IRON = new Item().setMaxStackSize(64);
    private static final Item GOLD = new Item().setMaxStackSize(64);

    @Test
    public void anEmptySorterCannotPush() throws Exception {
        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        Assert.assertArrayEquals(new Object[] { false }, sorter.callMethod(null, null, 1, new Object[] { "north" }));
    }

    @Test
    public void analyzeOfAnEmptySlotReturnsNothing() throws Exception {
        Object[] result = sorterWithNoWorld().callMethod(null, null, 0, new Object[0]);
        Assert.assertEquals(1, result.length);
        Assert.assertNull(result[0]);
    }

    @Test
    public void anUnknownSideIsRejected() throws Exception {
        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        sorter.setInventorySlotContents(0, new ItemStack(IRON, 4));
        assertLuaError(() -> sorter.callMethod(null, null, 1, new Object[] { "sideways" }), "unknown side");
        assertLuaError(() -> sorter.callMethod(null, null, 1, new Object[] { 42.0d }), "unknown side");
    }

    @Test
    public void isInventoryPresentReportsFalseWithoutAnInventory() throws Exception {
        Assert.assertArrayEquals(
            new Object[] { false },
            sorterWithNoWorld().callMethod(null, null, 3, new Object[] { "north" }));
    }

    @Test
    public void aDisabledSorterRejectsEverything() {
        Config.enableInteractiveSorter = false;
        try {
            sorterWithNoWorld().callMethod(null, null, 0, new Object[0]);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("disabled"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        } finally {
            Config.enableInteractiveSorter = true;
        }
    }

    @Test
    public void theMethodListAndTypeAreStable() {
        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        Assert.assertArrayEquals(
            new String[] { "analyze", "push", "pull", "isInventoryPresent" },
            sorter.getMethodNames());
        Assert.assertEquals("interactiveSorter", sorter.getType());
        Assert.assertEquals(1, sorter.getSize());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static TileEntityInteractiveSorter sorterWithNoWorld() {
        TileEntityInteractiveSorter sorter = new TileEntityInteractiveSorter();
        sorter.xCoord = 0;
        sorter.yCoord = 64;
        sorter.zCoord = 0;
        return sorter;
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
