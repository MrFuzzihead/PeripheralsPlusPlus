package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;

/**
 * Covers the barrel upgrade's bookkeeping.
 *
 * <p>
 * {@code getOreDictEntries} built an {@code ItemStack} from a null item for an empty barrel, the table was 0-based
 * while every other peripheral is 1-based, the item comparison ignored damage, and {@code update()} rewrote the
 * turtle's upgrade NBT every tick (CODE_REVIEW.md section 5.44).
 * </p>
 */
public class BarrelTest {

    // net.minecraft.init.Items is populated by FML's item registration, which does not run headless (and registering
    // items needs FML's class loader), so the test uses two plain items. Registry-name lookups are covered in game.
    private static final Item IRON = new Item().setMaxStackSize(64);
    private static final Item GOLD = new Item().setMaxStackSize(64);

    private static ITurtleAccess turtle() {
        ITurtleAccess turtle = Mockito.mock(ITurtleAccess.class);
        Mockito.when(turtle.getUpgradeNBTData(Mockito.any(TurtleSide.class)))
            .thenReturn(new NBTTagCompound());
        return turtle;
    }

    private static Object[] call(PeripheralBarrel barrel, int method, Object... arguments)
        throws LuaException, InterruptedException {
        return barrel.callMethod(null, null, method, arguments);
    }

    @Test
    public void anEmptyBarrelReportsZeroForEveryQuery() throws Exception {
        PeripheralBarrel barrel = new PeripheralBarrel(turtle(), TurtleSide.Left);
        Assert.assertEquals(0, call(barrel, 5)[0]);
        // The old code did new ItemStack(ITEM_TYPE_STORED) here and NPE'd on a null item.
        Object[] entries = call(barrel, 6);
        Assert.assertEquals(1, entries.length);
        Assert.assertTrue(((Map<?, ?>) entries[0]).isEmpty());
        // The name queries return an empty array rather than a null entry.
        Assert.assertEquals(0, call(barrel, 2).length);
        Assert.assertEquals(0, call(barrel, 3).length);
        Assert.assertEquals(0, call(barrel, 4).length);
    }

    @Test
    public void puttingItemsTeachesTheBarrelItsType() throws Exception {
        ITurtleAccess turtle = turtle();
        ItemStack stored = new ItemStack(IRON, 32);
        net.minecraft.inventory.IInventory inventory = inventoryWith(stored);
        Mockito.when(turtle.getSelectedSlot())
            .thenReturn(0);
        Mockito.when(turtle.getInventory())
            .thenReturn(inventory);
        PeripheralBarrel barrel = new PeripheralBarrel(turtle, TurtleSide.Left);

        Assert.assertEquals(32, call(barrel, 1)[0]);
        Assert.assertEquals(32, call(barrel, 5)[0]);
        // The registry-name / display-name / numeric-id queries need FML's item registry; they are covered in game.
        Assert.assertEquals(1, call(barrel, 2).length);
        Assert.assertEquals(1, call(barrel, 4).length);
    }

    @Test
    public void aDifferentItemIsRefused() throws Exception {
        ITurtleAccess turtle = turtle();
        net.minecraft.inventory.IInventory iron = inventoryWith(new ItemStack(IRON, 8));
        net.minecraft.inventory.IInventory gold = inventoryWith(new ItemStack(GOLD, 8));
        Mockito.when(turtle.getSelectedSlot())
            .thenReturn(0);
        Mockito.when(turtle.getInventory())
            .thenReturn(iron);
        PeripheralBarrel barrel = new PeripheralBarrel(turtle, TurtleSide.Left);
        call(barrel, 1);

        // Swap the slot to another item and try to store it.
        Mockito.when(turtle.getInventory())
            .thenReturn(gold);
        Assert.assertEquals(0, call(barrel, 1)[0]);
    }

    @Test
    public void aDamagedVariantIsRefused() throws Exception {
        ITurtleAccess turtle = turtle();
        net.minecraft.inventory.IInventory plain = inventoryWith(new ItemStack(IRON, 4, 0));
        net.minecraft.inventory.IInventory damaged = inventoryWith(new ItemStack(IRON, 4, 1));
        Mockito.when(turtle.getSelectedSlot())
            .thenReturn(0);
        Mockito.when(turtle.getInventory())
            .thenReturn(plain);
        PeripheralBarrel barrel = new PeripheralBarrel(turtle, TurtleSide.Left);
        call(barrel, 1);

        // The old comparison used a meta-0 template, so meta 1 looked identical.
        Mockito.when(turtle.getInventory())
            .thenReturn(damaged);
        Assert.assertEquals(0, call(barrel, 1)[0]);
    }

    @Test
    public void updateIsANoOpUntilSomethingChanged() {
        ITurtleAccess turtle = turtle();
        PeripheralBarrel barrel = new PeripheralBarrel(turtle, TurtleSide.Left);
        barrel.changed = false;
        barrel.update();
        Mockito.verify(turtle, Mockito.never())
            .updateUpgradeNBTData(Mockito.any(TurtleSide.class));
    }

    @Test
    public void updatePersistsTheState() {
        ITurtleAccess turtle = turtle();
        NBTTagCompound tag = new NBTTagCompound();
        Mockito.when(turtle.getUpgradeNBTData(Mockito.any(TurtleSide.class)))
            .thenReturn(tag);
        PeripheralBarrel barrel = new PeripheralBarrel(turtle, TurtleSide.Left);
        barrel.changed = true;
        barrel.update();
        Mockito.verify(turtle)
            .updateUpgradeNBTData(Mockito.any(TurtleSide.class));
        Assert.assertTrue(tag.hasKey("maxSize"));
        Assert.assertTrue(tag.hasKey("currentUsage"));
    }

    @Test
    public void aDisabledBarrelRejectsEveryCall() {
        Config.enableBarrelTurtle = false;
        try {
            call(new PeripheralBarrel(turtle(), TurtleSide.Left), 5);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("disabled"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        } finally {
            Config.enableBarrelTurtle = true;
        }
    }

    @Test
    public void theMethodListIsStable() {
        Assert.assertArrayEquals(
            new String[] { "get", "put", "getUnlocalizedName", "getLocalizedName", "getItemID", "getAmount",
                "getOreDictEntries" },
            new PeripheralBarrel(turtle(), TurtleSide.Left).getMethodNames());
        Assert.assertEquals("barrel", new PeripheralBarrel(turtle(), TurtleSide.Left).getType());
    }

    private static net.minecraft.inventory.IInventory inventoryWith(ItemStack stack) {
        net.minecraft.inventory.IInventory inventory = Mockito.mock(net.minecraft.inventory.IInventory.class);
        Mockito.when(inventory.getStackInSlot(0))
            .thenReturn(stack);
        Mockito.when(inventory.getSizeInventory())
            .thenReturn(1);
        Mockito.when(inventory.getInventoryStackLimit())
            .thenReturn(64);
        Mockito.when(inventory.isItemValidForSlot(Mockito.anyInt(), Mockito.any(ItemStack.class)))
            .thenReturn(true);
        return inventory;
    }
}
