package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;

/**
 * Covers the remaining turtle peripherals: XP, resupply and ridable.
 *
 * <p>
 * These are the last {@code callMethod}s that need nothing but a mocked turtle; the tank, barrel and XP enchanting need
 * fluid/item registries and belong to the in-game suite.
 * </p>
 */
public class TurtlePeripheralsExtraTest {

    @After
    public void tearDown() {
        Config.enableXPTurtle = true;
        Config.enableResupplyStation = true;
        Config.enableRidableTurtle = true;
    }

    private static World world() {
        World world = Mockito.mock(World.class);
        world.isRemote = false;
        // World.rand is a field, and PeripheralXP seeds its Random from it.
        world.rand = new java.util.Random(1L);
        return world;
    }

    private static ITurtleAccess turtle(World world) {
        ITurtleAccess access = Mockito.mock(ITurtleAccess.class);
        Mockito.when(access.getWorld())
            .thenReturn(world);
        Mockito.when(access.getPosition())
            .thenReturn(new ChunkCoordinates(0, 64, 0));
        Mockito.when(access.getSelectedSlot())
            .thenReturn(0);
        Mockito.when(access.getUpgradeNBTData(Mockito.any(TurtleSide.class)))
            .thenReturn(new NBTTagCompound());
        return access;
    }

    // ---------------------------------------------------------------------------------------------------------
    // XP
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theXpTurtleReportsItsBalance() throws Exception {
        PeripheralXP xp = new PeripheralXP(turtle(world()), TurtleSide.Left);
        Assert.assertArrayEquals(new Object[] { 0 }, xp.callMethod(null, null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { 0 }, xp.callMethod(null, null, 2, new Object[0]));
    }

    @Test
    public void theXpTurtleTogglesAutoCollect() throws Exception {
        PeripheralXP xp = new PeripheralXP(turtle(world()), TurtleSide.Left);
        Assert.assertArrayEquals(new Object[] { true }, xp.callMethod(null, null, 4, new Object[0]));
        Assert.assertArrayEquals(new Object[] { false }, xp.callMethod(null, null, 4, new Object[] { false }));
        Assert.assertArrayEquals(new Object[] { true }, xp.callMethod(null, null, 4, new Object[] { true }));
        assertLuaError(() -> xp.callMethod(null, null, 4, new Object[] { "yes" }), "boolean");
    }

    @Test
    public void theXpTurtleCollectsNothingFromAnEmptyWorld() throws Exception {
        World world = world();
        Mockito.when(world.getEntitiesWithinAABB(Mockito.any(Class.class), Mockito.any(AxisAlignedBB.class)))
            .thenReturn(new ArrayList<Object>());
        PeripheralXP xp = new PeripheralXP(turtle(world), TurtleSide.Left);
        Assert.assertArrayEquals(new Object[] { 0 }, xp.callMethod(null, null, 3, new Object[0]));
    }

    @Test
    public void theXpTurtleAddsNothingWithoutABottleInHand() throws Exception {
        ITurtleAccess access = turtle(world());
        Mockito.when(access.getInventory())
            .thenReturn(inventory(null));
        PeripheralXP xp = new PeripheralXP(access, TurtleSide.Left);
        Assert.assertArrayEquals(new Object[] { 0 }, xp.callMethod(null, null, 0, new Object[0]));
        assertLuaError(() -> xp.callMethod(null, null, 0, new Object[] { "lots" }), "number");
    }

    @Test
    public void theXpTurtleValidatesTheEnchantCost() {
        ITurtleAccess access = turtle(world());
        Mockito.when(access.getInventory())
            .thenReturn(inventory(null));
        PeripheralXP xp = new PeripheralXP(access, TurtleSide.Left);
        assertLuaError(() -> xp.callMethod(null, null, 5, new Object[0]), "Bad argument #1");
        assertLuaError(() -> xp.callMethod(null, null, 5, new Object[] { 0.0d }), "invalid level");
        assertLuaError(() -> xp.callMethod(null, null, 5, new Object[] { 99.0d }), "invalid level");
    }

    @Test
    public void theXpTurtlePersistsItsState() {
        ITurtleAccess access = turtle(world());
        NBTTagCompound tag = new NBTTagCompound();
        Mockito.when(access.getUpgradeNBTData(Mockito.any(TurtleSide.class)))
            .thenReturn(tag);
        PeripheralXP xp = new PeripheralXP(access, TurtleSide.Left);
        xp.changed = true;
        xp.update();
        Mockito.verify(access)
            .updateUpgradeNBTData(Mockito.any(TurtleSide.class));
        Assert.assertTrue(tag.hasKey("experience"));
        Assert.assertTrue(tag.hasKey("rndSeed"));
    }

    @Test
    public void aDisabledXpTurtleRejectsEverything() {
        PeripheralXP xp = new PeripheralXP(turtle(world()), TurtleSide.Left);
        Config.enableXPTurtle = false;
        assertLuaError(() -> xp.callMethod(null, null, 1, new Object[0]), "disabled");
    }

    @Test
    public void theXpTurtleExposesSixMethods() {
        Assert.assertEquals(6, new PeripheralXP(turtle(world()), TurtleSide.Left).getMethodNames().length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Resupply
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theResupplyTurtleRefusesANonStation() throws Exception {
        World world = world();
        Mockito.when(world.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(net.minecraft.init.Blocks.stone);
        PeripheralResupply resupply = new PeripheralResupply(turtle(world));
        Assert.assertArrayEquals(new Object[] { false }, resupply.callMethod(null, null, 0, new Object[] { "north" }));
    }

    @Test
    public void theResupplyTurtleRefusesAnAirBlock() throws Exception {
        World world = world();
        Mockito.when(world.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        Mockito.when(world.isAirBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        PeripheralResupply resupply = new PeripheralResupply(turtle(world));
        Assert.assertArrayEquals(new Object[] { false }, resupply.callMethod(null, null, 0, new Object[] { "north" }));
    }

    @Test
    public void theResupplyTurtleRejectsAWrongArgumentCount() {
        PeripheralResupply resupply = new PeripheralResupply(turtle(world()));
        assertLuaError(() -> resupply.callMethod(null, null, 0, new Object[0]), "link() expects");
        assertLuaError(() -> resupply.callMethod(null, null, 0, new Object[] { "north", 1.0d }), "link() expects");
    }

    @Test
    public void theResupplyTurtleNeedsALink() {
        PeripheralResupply resupply = new PeripheralResupply(turtle(world()));
        assertLuaError(() -> resupply.callMethod(null, null, 1, new Object[0]), "not been linked");
    }

    @Test
    public void aDisabledResupplyTurtleRejectsEverything() {
        PeripheralResupply resupply = new PeripheralResupply(turtle(world()));
        Config.enableResupplyStation = false;
        assertLuaError(() -> resupply.callMethod(null, null, 0, new Object[] { "north" }), "disabled");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Ridable
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theRidableTurtleReportsNoEntity() throws Exception {
        World world = world();
        Mockito.when(world.getEntitiesWithinAABB(Mockito.any(Class.class), Mockito.any(AxisAlignedBB.class)))
            .thenReturn(new ArrayList<Object>());
        PeripheralRidable ridable = new PeripheralRidable(turtle(world));
        // getEntity returns nothing when there is none; the other three report false.
        Assert.assertEquals(0, ridable.callMethod(null, null, 0, new Object[0]).length);
        Assert.assertArrayEquals(new Object[] { false }, ridable.callMethod(null, null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { false }, ridable.callMethod(null, null, 2, new Object[0]));
        Assert.assertArrayEquals(new Object[] { false }, ridable.callMethod(null, null, 3, new Object[0]));
    }

    @Test
    public void theRidableTurtleListsNoRidingEntity() throws Exception {
        World world = world();
        Mockito.when(world.getEntitiesWithinAABB(Mockito.any(Class.class), Mockito.any(AxisAlignedBB.class)))
            .thenReturn(new ArrayList<Object>());
        PeripheralRidable ridable = new PeripheralRidable(turtle(world));
        Assert.assertEquals(0, ridable.callMethod(null, null, 0, new Object[0]).length);
    }

    @Test
    public void theRidableTurtleExposesFourMethods() {
        Assert.assertEquals(4, new PeripheralRidable(turtle(world())).getMethodNames().length);
        Assert.assertEquals("ridable", new PeripheralRidable(turtle(world())).getType());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static IInventory inventory(ItemStack slot) {
        List<ItemStack> slots = new ArrayList<ItemStack>();
        slots.add(slot);
        return new IInventory() {

            @Override
            public int getSizeInventory() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int index) {
                return slots.get(0);
            }

            @Override
            public ItemStack decrStackSize(int index, int amount) {
                ItemStack current = slots.get(0);
                if (current == null) return null;
                int take = Math.min(amount, current.stackSize);
                if (take <= 0) return null;
                ItemStack taken = current.copy();
                taken.stackSize = take;
                current.stackSize -= take;
                if (current.stackSize <= 0) slots.set(0, null);
                return taken;
            }

            @Override
            public ItemStack getStackInSlotOnClosing(int index) {
                ItemStack value = slots.get(0);
                slots.set(0, null);
                return value;
            }

            @Override
            public void setInventorySlotContents(int index, ItemStack stack) {
                slots.set(0, stack);
            }

            @Override
            public String getInventoryName() {
                return "turtle";
            }

            @Override
            public boolean hasCustomInventoryName() {
                return false;
            }

            @Override
            public int getInventoryStackLimit() {
                return 64;
            }

            @Override
            public void markDirty() {}

            @Override
            public boolean isUseableByPlayer(EntityPlayer player) {
                return true;
            }

            @Override
            public void openInventory() {}

            @Override
            public void closeInventory() {}

            @Override
            public boolean isItemValidForSlot(int index, ItemStack stack) {
                return true;
            }
        };
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
