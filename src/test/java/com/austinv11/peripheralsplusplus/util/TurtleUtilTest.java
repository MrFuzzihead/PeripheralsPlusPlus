package com.austinv11.peripheralsplusplus.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.IShearable;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.utils.TurtleUtil;

/**
 * Covers the distance helpers used by the shear and RedPower-like tool upgrades.
 *
 * <p>
 * {@code getClosestEntity} never updated its {@code lastDistance}, so it returned whichever entity happened to come
 * last in the AABB result rather than the nearest one (CODE_REVIEW.md section 5.2).
 * </p>
 */
public class TurtleUtilTest {

    // ---------------------------------------------------------------------------------------------------------
    // Harvesting and inventory insertion
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void harvestBlockDigsOnlyWhatTheToolCanHarvest() throws Exception {
        net.minecraft.block.Block block = Mockito.mock(net.minecraft.block.Block.class);
        Mockito.when(block.getBlockHardness(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(1.0F);
        Mockito
            .when(block.canHarvestBlock(Mockito.any(net.minecraft.entity.player.EntityPlayer.class), Mockito.anyInt()))
            .thenReturn(true);
        // Block#getDrops returns an ArrayList in 1.7.10.
        java.util.ArrayList<ItemStack> blockDrops = new java.util.ArrayList<ItemStack>();
        blockDrops.add(new ItemStack(new Item(), 1));
        Mockito
            .when(
                block.getDrops(
                    Mockito.any(),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                    Mockito.anyInt()))
            .thenReturn(blockDrops);

        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        world.isRemote = false;
        Mockito.when(world.isAirBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(false);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(block);
        dan200.computercraft.api.turtle.ITurtleAccess turtle = Mockito
            .mock(dan200.computercraft.api.turtle.ITurtleAccess.class);
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        Mockito.when(turtle.getPosition())
            .thenReturn(new net.minecraft.util.ChunkCoordinates(0, 64, 0));
        com.austinv11.peripheralsplusplus.utils.FakeTurtlePlayer player = Mockito
            .mock(com.austinv11.peripheralsplusplus.utils.FakeTurtlePlayer.class);

        java.util.List<ItemStack> drops = com.austinv11.peripheralsplusplus.utils.TurtleUtil
            .harvestBlock(turtle, player, 2, new ItemStack(new Item(), 1));
        Assert.assertNotNull(drops);
        Assert.assertEquals(1, drops.size());
        Mockito.verify(world)
            .setBlockToAir(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void harvestBlockRefusesAnUnharvestableBlock() throws Exception {
        net.minecraft.block.Block block = Mockito.mock(net.minecraft.block.Block.class);
        Mockito.when(block.getBlockHardness(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(-1.0F);
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        world.isRemote = false;
        Mockito.when(world.isAirBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(false);
        Mockito.when(world.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(block);
        dan200.computercraft.api.turtle.ITurtleAccess turtle = Mockito
            .mock(dan200.computercraft.api.turtle.ITurtleAccess.class);
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        Mockito.when(turtle.getPosition())
            .thenReturn(new net.minecraft.util.ChunkCoordinates(0, 64, 0));

        Assert.assertNull(
            com.austinv11.peripheralsplusplus.utils.TurtleUtil.harvestBlock(
                turtle,
                Mockito.mock(com.austinv11.peripheralsplusplus.utils.FakeTurtlePlayer.class),
                2,
                new ItemStack(new Item(), 1)));
    }

    @Test
    public void harvestBlockIgnoresAir() throws Exception {
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(world.isAirBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        dan200.computercraft.api.turtle.ITurtleAccess turtle = Mockito
            .mock(dan200.computercraft.api.turtle.ITurtleAccess.class);
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        Mockito.when(turtle.getPosition())
            .thenReturn(new net.minecraft.util.ChunkCoordinates(0, 64, 0));

        Assert.assertNull(
            com.austinv11.peripheralsplusplus.utils.TurtleUtil.harvestBlock(
                turtle,
                Mockito.mock(com.austinv11.peripheralsplusplus.utils.FakeTurtlePlayer.class),
                2,
                null));
    }

    @Test
    public void addToInvFillsAnEmptySlot() {
        dan200.computercraft.api.turtle.ITurtleAccess turtle = inventory(1, null);
        com.austinv11.peripheralsplusplus.utils.TurtleUtil.addToInv(turtle, new ItemStack(new Item(), 3));

        Mockito.verify(turtle.getInventory())
            .setInventorySlotContents(Mockito.eq(0), Mockito.any(ItemStack.class));
    }

    @Test
    public void addToInvMergesIntoAPartialStack() {
        // The overflow path constructs an EntityItem, which needs a real World (Entity's constructor reads
        // world.provider.dimensionId); the in-game suite covers that path.
        Item item = new Item().setMaxStackSize(64);
        ItemStack existing = new ItemStack(item, 63);
        dan200.computercraft.api.turtle.ITurtleAccess turtle = inventory(1, existing);

        com.austinv11.peripheralsplusplus.utils.TurtleUtil.addToInv(turtle, new ItemStack(item, 1));

        Assert.assertEquals("the item was merged into the existing stack", 64, existing.stackSize);
        Mockito.verify(turtle.getWorld(), Mockito.never())
            .spawnEntityInWorld(Mockito.any(net.minecraft.entity.Entity.class));
    }

    private static dan200.computercraft.api.turtle.ITurtleAccess inventory(int size, ItemStack first) {
        net.minecraft.inventory.IInventory inv = Mockito.mock(net.minecraft.inventory.IInventory.class);
        Mockito.when(inv.getSizeInventory())
            .thenReturn(size);
        Mockito.when(inv.getStackInSlot(0))
            .thenReturn(first);
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        world.isRemote = false;
        dan200.computercraft.api.turtle.ITurtleAccess turtle = Mockito
            .mock(dan200.computercraft.api.turtle.ITurtleAccess.class);
        Mockito.when(turtle.getInventory())
            .thenReturn(inv);
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        Mockito.when(turtle.getPosition())
            .thenReturn(new net.minecraft.util.ChunkCoordinates(0, 64, 0));
        Mockito.when(turtle.getDirection())
            .thenReturn(2);
        return turtle;
    }

    private static Entity at(double x, double y, double z) {
        Entity entity = Mockito.mock(Entity.class);
        entity.posX = x;
        entity.posY = y;
        entity.posZ = z;
        return entity;
    }

    private static Entity shearableAt(double x, double y, double z) {
        Entity entity = Mockito.mock(
            EntityAnimal.class,
            Mockito.withSettings()
                .extraInterfaces(IShearable.class));
        entity.posX = x;
        entity.posY = y;
        entity.posZ = z;
        return entity;
    }

    @Test
    public void closestEntityIsTheNearestNotTheLast() {
        Entity origin = at(0, 0, 0);
        Entity far = at(20, 0, 0);
        Entity near = at(2, 0, 0);
        Entity middle = at(9, 0, 0);
        // Deliberately out of order, with the nearest first so "returns the last" would pick `middle`.
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(near);
        candidates.add(middle);
        candidates.add(far);

        Assert.assertSame(near, TurtleUtil.getClosestEntity(candidates, origin));
    }

    @Test
    public void closestEntityIgnoresOrdering() {
        Entity origin = at(0, 0, 0);
        Entity far = at(50, 0, 0);
        Entity near = at(1, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(far);
        candidates.add(near);

        Assert.assertSame(near, TurtleUtil.getClosestEntity(candidates, origin));
    }

    @Test
    public void closestShearableOnlyConsidersShearables() {
        Entity origin = at(0, 0, 0);
        Entity notShearable = at(1, 0, 0);
        Entity sheep = shearableAt(10, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(notShearable);
        candidates.add(sheep);

        Assert.assertSame(sheep, TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void closestShearablePicksTheNearestShearable() {
        Entity origin = at(0, 0, 0);
        Entity close = shearableAt(3, 0, 0);
        Entity further = shearableAt(7, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(further);
        candidates.add(close);

        Assert.assertSame(close, TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void closestShearableIsNullWhenNothingCanBeSheared() {
        Entity origin = at(0, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(at(1, 0, 0));

        Assert.assertNull(TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void emptyAndNullListsAreSafe() {
        Entity origin = at(0, 0, 0);
        Assert.assertNull(TurtleUtil.getClosestEntity(new ArrayList<Entity>(), origin));
        Assert.assertNull(TurtleUtil.getClosestEntity(null, origin));
        Assert.assertNull(TurtleUtil.getClosestEntity(new ArrayList<Entity>(), null));
    }

    @Test
    public void distanceUsesAllThreeAxes() {
        Entity origin = at(0, 0, 0);
        Entity diagonal = at(3, 4, 12); // 13 blocks away
        Entity closerOnXAlone = at(5, 0, 0); // 5 blocks away
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(diagonal);
        candidates.add(closerOnXAlone);

        Assert.assertSame(closerOnXAlone, TurtleUtil.getClosestEntity(candidates, origin));
    }
}
