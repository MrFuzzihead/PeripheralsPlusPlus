package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;

/**
 * Covers the small turtle peripherals through one mocked {@link ITurtleAccess}.
 *
 * <p>
 * Each of these is a handful of {@code callMethod} branches over the turtle's inventory and world, which is the
 * cheapest place in the mod to buy coverage: they are ~700 lines that no test touched, and a mocked turtle plus a
 * mocked world is enough to drive every branch.
 * </p>
 */
public class TurtlePeripheralTest {

    private static final Item WHEAT = new Item().setMaxStackSize(64);
    private static final Item BONEMEAL = new Item().setMaxStackSize(64);

    @After
    public void tearDown() {
        Config.enableFeederTurtle = true;
        Config.enableGardeningTurtle = true;
        Config.enableReaderTurtle = true;
        Config.enableFlingingTurtle = true;
        Config.enableNavigationTurtle = true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Shared scaffolding
    // ---------------------------------------------------------------------------------------------------------

    /** A turtle at the origin facing north, holding {@code held} in slot 0. */
    private static Turtle turtle(ItemStack held) {
        return new Turtle(held);
    }

    /** Everything a turtle peripheral touches, in one place. */
    private static final class Turtle {

        private final World world = Mockito.mock(World.class);
        private final ITurtleAccess access = Mockito.mock(ITurtleAccess.class);
        private final IInventory inventory = Mockito.mock(IInventory.class);
        private final ItemStack[] slots = new ItemStack[1];
        private final List<Entity> entities = new ArrayList<Entity>();

        private Turtle(ItemStack held) {
            slots[0] = held;
            Mockito.when(access.getWorld())
                .thenReturn(world);
            Mockito.when(access.getInventory())
                .thenReturn(inventory);
            Mockito.when(access.getPosition())
                .thenReturn(new ChunkCoordinates(0, 64, 0));
            Mockito.when(access.getSelectedSlot())
                .thenReturn(0);
            Mockito.when(access.getDirection())
                .thenReturn(2); // north
            Mockito.when(access.getUpgradeNBTData(Mockito.any(TurtleSide.class)))
                .thenReturn(new NBTTagCompound());
            Mockito.when(inventory.getSizeInventory())
                .thenReturn(1);
            Mockito.when(inventory.getStackInSlot(0))
                .thenAnswer(invocation -> slots[0]);
            Mockito.when(inventory.getInventoryStackLimit())
                .thenReturn(64);
            Mockito.when(world.getEntitiesWithinAABB(Mockito.eq(Entity.class), Mockito.any(AxisAlignedBB.class)))
                .thenReturn(entities);
            Mockito.when(world.getEntitiesWithinAABB(Mockito.any(Class.class), Mockito.any(AxisAlignedBB.class)))
                .thenAnswer(invocation -> entities);
        }

        private ItemStack held() {
            return slots[0];
        }

        private void setHeld(ItemStack stack) {
            slots[0] = stack;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Feeder
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theFeederNeedsSomethingToFeedWith() throws Exception {
        PeripheralFeeder feeder = new PeripheralFeeder(turtle(null).access);
        Assert.assertArrayEquals(new Object[] { false }, feeder.callMethod(null, null, 0, new Object[0]));
    }

    @Test
    public void theFeederFeedsOneAnimal() throws Exception {
        Turtle turtle = turtle(new ItemStack(WHEAT, 2));
        EntityAnimal animal = Mockito.mock(EntityAnimal.class);
        Mockito.when(animal.getGrowingAge())
            .thenReturn(0);
        Mockito.when(animal.isInLove())
            .thenReturn(false);
        Mockito.when(animal.isBreedingItem(Mockito.any(ItemStack.class)))
            .thenReturn(true);
        turtle.entities.add(animal);

        PeripheralFeeder feeder = new PeripheralFeeder(turtle.access);
        Assert.assertArrayEquals(new Object[] { true }, feeder.callMethod(null, null, 0, new Object[0]));
    }

    @Test
    public void theFeederIgnoresAnimalsItCannotFeed() throws Exception {
        Turtle turtle = turtle(new ItemStack(WHEAT, 2));
        EntityAnimal baby = Mockito.mock(EntityAnimal.class);
        Mockito.when(baby.getGrowingAge())
            .thenReturn(-1);
        turtle.entities.add(baby);

        PeripheralFeeder feeder = new PeripheralFeeder(turtle.access);
        Assert.assertArrayEquals(new Object[] { false }, feeder.callMethod(null, null, 0, new Object[0]));
    }

    @Test
    public void aDisabledFeederRejectsEverything() {
        PeripheralFeeder feeder = new PeripheralFeeder(turtle(null).access);
        Config.enableFeederTurtle = false;
        assertLuaError(() -> feeder.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Garden
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theGardenerReportsNilForANonGrowableBlock() throws Exception {
        Turtle turtle = turtle(null);
        PeripheralGarden garden = new PeripheralGarden(turtle.access);
        for (int method = 0; method < 3; method++) {
            Object[] result = garden.callMethod(null, null, method, new Object[0]);
            // One nil value, which Lua sees as no result.
            Assert.assertEquals(1, result.length);
            Assert.assertNull(result[0]);
        }
    }

    @Test
    public void theGardenerCannotFertilizeWithoutBoneMeal() throws Exception {
        Turtle turtle = turtle(new ItemStack(WHEAT, 1));
        PeripheralGarden garden = new PeripheralGarden(turtle.access);
        for (int method = 3; method < 6; method++)
            Assert.assertArrayEquals(new Object[] { false }, garden.callMethod(null, null, method, new Object[0]));
    }

    @Test
    public void aDisabledGardenerRejectsEverything() {
        PeripheralGarden garden = new PeripheralGarden(turtle(null).access);
        Config.enableGardeningTurtle = false;
        assertLuaError(() -> garden.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void theGardenerExposesSixMethods() {
        Assert.assertEquals(6, new PeripheralGarden(turtle(null).access).getMethodNames().length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Sign reader
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theSignReaderReportsNothingWithoutASign() throws Exception {
        Turtle turtle = turtle(null);
        PeripheralSignReader reader = new PeripheralSignReader(turtle.access);
        for (int method = 0; method < 3; method++)
            Assert.assertEquals(0, reader.callMethod(null, null, method, new Object[0]).length);
    }

    @Test
    public void aDisabledSignReaderRejectsEverything() {
        PeripheralSignReader reader = new PeripheralSignReader(turtle(null).access);
        Config.enableReaderTurtle = false;
        assertLuaError(() -> reader.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void theSignReaderExposesThreeMethods() {
        Assert.assertEquals(3, new PeripheralSignReader(turtle(null).access).getMethodNames().length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Dispenser
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theDispenserDoesNothingWithAnEmptySlot() throws Exception {
        Turtle turtle = turtle(null);
        PeripheralDispenser dispenser = new PeripheralDispenser(turtle.access);
        Assert.assertEquals(0, dispenser.callMethod(null, null, 0, new Object[0]).length);
        // The 1-based slot argument must not throw for a valid side.
        Assert.assertEquals(0, dispenser.callMethod(null, null, 0, new Object[] { 1.0d }).length);
    }

    @Test
    public void aDisabledDispenserRejectsEverything() {
        PeripheralDispenser dispenser = new PeripheralDispenser(turtle(null).access);
        Config.enableFlingingTurtle = false;
        assertLuaError(() -> dispenser.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void theDispenserRejectsANonNumericSlot() {
        PeripheralDispenser dispenser = new PeripheralDispenser(turtle(null).access);
        assertLuaError(() -> dispenser.callMethod(null, null, 0, new Object[] { "first" }), "number");
    }

    @Test
    public void theDispenserExposesThreeMethods() {
        Assert.assertEquals(3, new PeripheralDispenser(turtle(null).access).getMethodNames().length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Compass
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theCompassReportsTheTurtleDirection() throws Exception {
        Turtle turtle = turtle(null);
        PeripheralCompass compass = new PeripheralCompass(turtle.access);
        Assert.assertEquals("compass", compass.getType());
        Object[] result = compass.callMethod(null, null, 0, new Object[0]);
        Assert.assertEquals(1, result.length);
        Assert.assertNotNull(result[0]);
    }

    @Test
    public void aDisabledCompassRejectsEverything() {
        PeripheralCompass compass = new PeripheralCompass(turtle(null).access);
        Config.enableNavigationTurtle = false;
        assertLuaError(() -> compass.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Common peripheral behaviour
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyTurtlePeripheralHasANonNullType() {
        Turtle turtle = turtle(null);
        IPeripheral[] peripherals = { new PeripheralFeeder(turtle.access), new PeripheralGarden(turtle.access),
            new PeripheralSignReader(turtle.access), new PeripheralDispenser(turtle.access),
            new PeripheralCompass(turtle.access), new PeripheralResupply(turtle.access),
            new PeripheralRidable(turtle.access) };
        for (IPeripheral peripheral : peripherals) {
            Assert.assertNotNull(peripheral.getType());
            Assert.assertFalse(
                peripheral.getType()
                    .isEmpty());
            Assert.assertNotNull(peripheral.getMethodNames());
            Assert.assertNotEquals("equals must be identity", peripheral, new Object());
        }
    }

    @Test
    public void anUnknownMethodReturnsNothing() throws Exception {
        Turtle turtle = turtle(null);
        Assert
            .assertEquals(0, new PeripheralSignReader(turtle.access).callMethod(null, null, 99, new Object[0]).length);
        Assert.assertEquals(0, new PeripheralGarden(turtle.access).callMethod(null, null, 99, new Object[0]).length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

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

    /** Keeps the unused imports honest. */
    private static void unused(TileEntity tile, EntityPlayer player, Item item) {
        Assert.assertNull(tile);
        Assert.assertNull(player);
        Assert.assertNull(item);
    }
}
