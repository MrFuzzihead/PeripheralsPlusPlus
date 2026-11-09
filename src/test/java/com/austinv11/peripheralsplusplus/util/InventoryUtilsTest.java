package com.austinv11.peripheralsplusplus.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.utils.InventoryUtils;

/**
 * Covers the shared inventory move helper, which is the single implementation the sorter, the resupply station and
 * the player interface delegate to (CODE_REVIEW.md section 9, phase 1).
 *
 * <p>
 * These are real {@link ItemStack}s against a small in-memory {@link IInventory}: 1.7.10's {@code Item} and
 * {@code ItemStack} need no game bootstrap, so the test exercises the same code paths the game does.
 * </p>
 */
public class InventoryUtilsTest {

    private static final Item IRON = new Item().setMaxStackSize(64);
    private static final Item GOLD = new Item().setMaxStackSize(64);
    private static final Item TOOL = new Item().setMaxStackSize(1);

    private static ItemStack stack(Item item, int size) {
        return new ItemStack(item, size);
    }

    private static ItemStack stack(Item item, int size, int damage) {
        return new ItemStack(item, size, damage);
    }

    private static ItemStack withNbt(Item item, String value) {
        ItemStack stack = new ItemStack(item, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("key", value);
        stack.setTagCompound(tag);
        return stack;
    }

    // ---------------------------------------------------------------------------------------------------------
    // matches
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void matchesRequiresTheSameItem() {
        Assert.assertTrue(InventoryUtils.matches(stack(IRON, 1), stack(IRON, 64)));
        Assert.assertFalse(InventoryUtils.matches(stack(IRON, 1), stack(GOLD, 1)));
    }

    @Test
    public void matchesRequiresTheSameDamage() {
        Assert.assertFalse(InventoryUtils.matches(stack(IRON, 1, 0), stack(IRON, 1, 3)));
        Assert.assertTrue(InventoryUtils.matches(stack(IRON, 1, 3), stack(IRON, 1, 3)));
    }

    @Test
    public void matchesRequiresTheSameNbt() {
        // isItemEqual returns true for both of these; they are not interchangeable (CODE_REVIEW.md section 4.1).
        Assert.assertFalse(InventoryUtils.matches(withNbt(IRON, "Sharpness"), withNbt(IRON, "Silk Touch")));
        Assert.assertTrue(InventoryUtils.matches(withNbt(IRON, "Sharpness"), withNbt(IRON, "Sharpness")));
        Assert.assertFalse(InventoryUtils.matches(withNbt(IRON, "Sharpness"), stack(IRON, 1)));
    }

    @Test
    public void matchesRejectsNulls() {
        Assert.assertFalse(InventoryUtils.matches(null, stack(IRON, 1)));
        Assert.assertFalse(InventoryUtils.matches(stack(IRON, 1), null));
        Assert.assertFalse(InventoryUtils.matches(null, null));
    }

    // ---------------------------------------------------------------------------------------------------------
    // room
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void roomOfAnEmptySlotIsTheLimit() {
        Assert.assertEquals(64, InventoryUtils.room(null, stack(IRON, 1), 64));
        Assert.assertEquals(16, InventoryUtils.room(null, stack(IRON, 1), 16));
    }

    @Test
    public void roomRespectsTheItemMaxStackSize() {
        Assert.assertEquals(1, InventoryUtils.room(null, stack(TOOL, 1), 64));
    }

    @Test
    public void roomOfAFilledSlotIsZeroNeverNegative() {
        Assert.assertEquals(0, InventoryUtils.room(stack(IRON, 64), stack(IRON, 1), 64));
        // A slot that somehow holds more than its limit must not yield a negative room.
        Assert.assertEquals(0, InventoryUtils.room(stack(IRON, 70), stack(IRON, 1), 64));
    }

    @Test
    public void roomOfAPartialSlot() {
        Assert.assertEquals(14, InventoryUtils.room(stack(IRON, 50), stack(IRON, 1), 64));
    }

    @Test
    public void roomWithoutAMovingStackIsZero() {
        Assert.assertEquals(0, InventoryUtils.room(null, null, 64));
    }

    // ---------------------------------------------------------------------------------------------------------
    // slotsFor
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void slotsForAPlainInventoryIsEverything() {
        IInventory inventory = new FakeInventory(3);
        Assert.assertArrayEquals(new int[] { 0, 1, 2 }, InventoryUtils.slotsFor(inventory, ForgeDirection.NORTH));
    }

    @Test
    public void slotsForNullIsEmpty() {
        Assert.assertEquals(0, InventoryUtils.slotsFor(null, ForgeDirection.UP).length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // move
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void moveTakesExactlyTheRequestedAmount() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, stack(IRON, 10));

        Assert.assertEquals(4, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 4));

        Assert.assertEquals(6, from.get(0).stackSize);
        Assert.assertEquals(4, to.get(0).stackSize);
        Assert.assertNull(to.get(1));
    }

    @Test
    public void moveToAnEmptySlotMovesOnlyTheRequest() {
        // Regression: the sorter used to move the whole stack when the target slot was empty.
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, stack(IRON, 32));

        Assert.assertEquals(1, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 1));

        Assert.assertEquals(31, from.get(0).stackSize);
        Assert.assertEquals(1, to.get(0).stackSize);
    }

    @Test
    public void moveNeverExceedsTheTargetStackLimit() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 64));
        to.set(0, stack(IRON, 60));

        Assert.assertEquals(4, InventoryUtils.move(from, 0, to, new int[] { 0 }, 64));
        Assert.assertEquals(64, to.get(0).stackSize);
        Assert.assertEquals(60, from.get(0).stackSize);
    }

    @Test
    public void moveSpillsIntoTheNextSlot() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, stack(IRON, 64));
        to.set(0, stack(IRON, 62));

        Assert.assertEquals(64, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 64));
        Assert.assertEquals(64, to.get(0).stackSize);
        Assert.assertEquals(62, to.get(1).stackSize);
        Assert.assertNull(from.get(0));
    }

    @Test
    public void moveSkipsSlotsHoldingADifferentItem() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, stack(IRON, 10));
        to.set(0, stack(GOLD, 1));

        Assert.assertEquals(10, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 10));
        Assert.assertSame(
            GOLD,
            to.get(0)
                .getItem());
        Assert.assertEquals(1, to.get(0).stackSize);
        Assert.assertEquals(10, to.get(1).stackSize);
    }

    @Test
    public void moveDoesNotMergeDifferentNbt() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, withNbt(IRON, "Sharpness"));
        to.set(0, withNbt(IRON, "Silk Touch"));

        Assert.assertEquals(1, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 1));

        Assert.assertEquals(1, to.get(0).stackSize);
        Assert.assertEquals(
            "Silk Touch",
            to.get(0)
                .getTagCompound()
                .getString("key"));
        Assert.assertEquals(
            "Sharpness",
            to.get(1)
                .getTagCompound()
                .getString("key"));
    }

    @Test
    public void moveStopsAtTheInventoryStackLimit() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        to.stackLimit = 16;
        from.set(0, stack(IRON, 64));

        Assert.assertEquals(32, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 64));
        Assert.assertEquals(16, to.get(0).stackSize);
        Assert.assertEquals(16, to.get(1).stackSize);
        Assert.assertEquals(32, from.get(0).stackSize);
    }

    @Test
    public void moveDoesNotTouchARejectingSlot() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        to.rejectSlot = 0;
        from.set(0, stack(IRON, 10));

        Assert.assertEquals(10, InventoryUtils.move(from, 0, to, new int[] { 0, 1 }, 10));
        Assert.assertNull(to.get(0));
        Assert.assertEquals(10, to.get(1).stackSize);
    }

    @Test
    public void moveOfNothingMovesNothing() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        Assert.assertEquals(0, InventoryUtils.move(from, 0, to, new int[] { 0 }, 10));
        Assert.assertEquals(0, InventoryUtils.move(from, 5, to, new int[] { 0 }, 10));
        Assert.assertEquals(0, InventoryUtils.move(from, 0, to, new int[0], 10));
    }

    @Test
    public void moveWithNoRoomMovesNothing() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 10));
        to.set(0, stack(IRON, 64));

        Assert.assertEquals(0, InventoryUtils.move(from, 0, to, new int[] { 0 }, 10));
        Assert.assertEquals(10, from.get(0).stackSize);
    }

    @Test
    public void moveWithZeroRequestIsANoOp() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 10));
        Assert.assertEquals(0, InventoryUtils.move(from, 0, to, new int[] { 0 }, 0));
        Assert.assertEquals(10, from.get(0).stackSize);
    }

    @Test
    public void movePrefersThePreferredSlot() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(3);
        from.set(0, stack(IRON, 10));
        to.set(2, stack(IRON, 60));

        // The preferred slot fills first (4 of its 14 free... it only had 4 free), the remainder spills to slot 0.
        Assert.assertEquals(10, InventoryUtils.move(from, 0, to, 2, 10));
        Assert.assertEquals(64, to.get(2).stackSize);
        Assert.assertEquals(6, to.get(0).stackSize);
        Assert.assertNull(to.get(1));
    }

    @Test
    public void moveIgnoresAnOutOfRangePreferredSlot() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(2);
        from.set(0, stack(IRON, 10));

        Assert.assertEquals(10, InventoryUtils.move(from, 0, to, 99, 10));
        Assert.assertEquals(10, to.get(0).stackSize);
    }

    @Test
    public void moveMarksBothInventoriesDirty() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 4));

        InventoryUtils.move(from, 0, to, new int[] { 0 }, 2);

        Assert.assertEquals(1, from.dirtyCalls);
        Assert.assertEquals(1, to.dirtyCalls);
    }

    @Test
    public void moveDoesNotMarkDirtyWhenNothingMoved() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 4));
        to.set(0, stack(GOLD, 1));

        InventoryUtils.move(from, 0, to, new int[] { 0 }, 2);

        Assert.assertEquals(0, from.dirtyCalls);
        Assert.assertEquals(0, to.dirtyCalls);
    }

    @Test
    public void moveNeverSharesAStackInstanceWithTheTarget() {
        FakeInventory from = new FakeInventory(1);
        FakeInventory to = new FakeInventory(1);
        from.set(0, stack(IRON, 10));
        to.set(0, stack(IRON, 1));

        InventoryUtils.move(from, 0, to, new int[] { 0 }, 1);

        Assert.assertNotSame(to.get(0), from.get(0));
        // Mutating the merged stack must not reach back into the source.
        to.get(0).stackSize = 64;
        Assert.assertEquals(9, from.get(0).stackSize);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Test double
    // ---------------------------------------------------------------------------------------------------------

    private static final class FakeInventory implements IInventory {

        private final List<ItemStack> slots;
        private int stackLimit = 64;
        private int rejectSlot = -1;
        private int dirtyCalls;

        private FakeInventory(int size) {
            slots = new ArrayList<ItemStack>(size);
            for (int i = 0; i < size; i++) slots.add(null);
        }

        private void set(int slot, ItemStack stack) {
            slots.set(slot, stack);
        }

        private ItemStack get(int slot) {
            return slots.get(slot);
        }

        @Override
        public int getSizeInventory() {
            return slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack current = get(slot);
            if (current == null) return null;
            int take = Math.min(amount, current.stackSize);
            if (take <= 0) return null;
            ItemStack taken = current.copy();
            taken.stackSize = take;
            current.stackSize -= take;
            if (current.stackSize <= 0) set(slot, null);
            return taken;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            ItemStack stack = get(slot);
            set(slot, null);
            return stack;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            slots.set(slot, stack);
        }

        @Override
        public String getInventoryName() {
            return "fake";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return stackLimit;
        }

        @Override
        public void markDirty() {
            dirtyCalls++;
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return slot != rejectSlot;
        }
    }
}
