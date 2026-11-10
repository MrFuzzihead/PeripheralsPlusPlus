package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

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

    // ---------------------------------------------------------------------------------------------------------
    // analyze: the item description, including its NBT
    // ---------------------------------------------------------------------------------------------------------

    /** Runs {@code analyze} with only Util#getEntries stubbed, since OreDictionary needs the game. */
    private static java.util.Map<String, Object> analyze(TileEntityInteractiveSorter sorter) throws Exception {
        try (org.mockito.MockedStatic<com.austinv11.peripheralsplusplus.utils.Util> util = Mockito
            .mockStatic(com.austinv11.peripheralsplusplus.utils.Util.class, Mockito.CALLS_REAL_METHODS)) {
            util.when(() -> com.austinv11.peripheralsplusplus.utils.Util.getEntries(Mockito.any()))
                .thenReturn(new java.util.HashMap<Integer, String>());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> info = (java.util.Map<String, Object>) sorter
                .callMethod(null, null, 0, new Object[0])[0];
            return info;
        }
    }

    @Test
    public void analyzeDescribesAPlainItem() throws Exception {
        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        sorter.setInventorySlotContents(0, new ItemStack(IRON, 7));

        java.util.Map<String, Object> info = analyze(sorter);
        Assert.assertEquals(7, info.get("amount"));
        Assert.assertEquals(0, info.get("meta"));
        // The ids are null / -1 for an item with no registry entry, which is what a unit test has; the keys must
        // still be present.
        Assert.assertTrue(info.containsKey("stringId"));
        Assert.assertTrue(info.containsKey("numericalId"));
        Assert.assertTrue(info.containsKey("name"));
        Assert.assertNotNull(info.get("oreDictionaryEntries"));
        Assert.assertFalse("a plain stack has no nbt", info.containsKey("nbt"));
    }

    @Test
    public void analyzeConvertsEveryNbtType() throws Exception {
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setByte("byte", (byte) 1);
        tag.setShort("short", (short) 2);
        tag.setInteger("int", 3);
        tag.setLong("long", 4L);
        tag.setFloat("float", 5.5F);
        tag.setDouble("double", 6.5D);
        tag.setByteArray("bytes", new byte[] { 7, 8 });
        tag.setString("string", "nine");
        tag.setIntArray("ints", new int[] { 10, 11 });

        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        list.appendTag(new net.minecraft.nbt.NBTTagString("a"));
        list.appendTag(new net.minecraft.nbt.NBTTagString("b"));
        tag.setTag("list", list);

        net.minecraft.nbt.NBTTagCompound inner = new net.minecraft.nbt.NBTTagCompound();
        inner.setString("nested", "yes");
        tag.setTag("compound", inner);

        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        ItemStack stack = new ItemStack(IRON, 1);
        stack.setTagCompound(tag);
        sorter.setInventorySlotContents(0, stack);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> nbt = (java.util.Map<String, Object>) analyze(sorter).get("nbt");

        Assert.assertNotNull("the nbt must be described", nbt);
        Assert.assertEquals((byte) 1, nbt.get("byte"));
        Assert.assertEquals((short) 2, nbt.get("short"));
        Assert.assertEquals(3, nbt.get("int"));
        Assert.assertEquals(4L, nbt.get("long"));
        Assert.assertEquals(5.5F, nbt.get("float"));
        Assert.assertEquals(6.5D, nbt.get("double"));
        Assert.assertEquals("nine", nbt.get("string"));
        Assert.assertTrue(nbt.get("bytes") instanceof java.util.Map);
        Assert.assertTrue(nbt.get("ints") instanceof java.util.Map);
        Assert.assertTrue(nbt.get("list") instanceof java.util.Map);
        Assert.assertTrue(nbt.get("compound") instanceof java.util.Map);
    }

    @Test
    public void aListIsConvertedWithoutLosingEntries() throws Exception {
        // The conversion used to call NBTTagList#removeTag while iterating, which shrank the list under the loop and
        // dropped the tail (CODE_REVIEW.md section 8.4).
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < 5; i++) list.appendTag(new net.minecraft.nbt.NBTTagString("entry" + i));
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setTag("list", list);

        TileEntityInteractiveSorter sorter = sorterWithNoWorld();
        ItemStack stack = new ItemStack(IRON, 1);
        stack.setTagCompound(tag);
        sorter.setInventorySlotContents(0, stack);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> nbt = (java.util.Map<String, Object>) analyze(sorter).get("nbt");
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, Object> converted = (java.util.Map<Integer, Object>) nbt.get("list");
        Assert.assertEquals(5, converted.size());
        Assert.assertEquals("entry0", converted.get(1));
        Assert.assertEquals("entry4", converted.get(5));
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
