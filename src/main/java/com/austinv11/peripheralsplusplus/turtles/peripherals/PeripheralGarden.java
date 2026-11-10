package com.austinv11.peripheralsplusplus.turtles.peripherals;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.IGrowable;
import net.minecraft.init.Items;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class PeripheralGarden extends MountedPeripheral {

    private ITurtleAccess turtle;

    public PeripheralGarden(ITurtleAccess turtle) {
        this.turtle = turtle;
    }

    @Override
    public String getType() {
        return "gardener";
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getGrowth", "getGrowthUp", "getGrowthDown", "fertilize",
        "fertilizeUp", "fertilizeDown" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableGardeningTurtle) throw new LuaException("Gardening Turtles have been disabled!");
        switch (method) {
            case 0:
            case 1:
            case 2:
                return new Object[] { growthPercent(targetOf(method)) };
            case 3:
            case 4:
            case 5:
                return new Object[] { fertilize(targetOf(method)) };
            default:
                return new Object[0];
        }
    }

    /**
     * @param method 0-2 for the growth queries, 3-5 for the fertilize calls
     * @return the block position the method addresses
     */
    private int[] targetOf(int method) {
        ChunkCoordinates position = turtle.getPosition();
        int offset = method % 3;
        if (offset == 1) return new int[] { position.posX, position.posY + 1, position.posZ };
        if (offset == 2) return new int[] { position.posX, position.posY - 1, position.posZ };
        int direction = turtle.getDirection();
        return new int[] { position.posX + Facing.offsetsXForSide[direction],
            position.posY + Facing.offsetsYForSide[direction], position.posZ + Facing.offsetsZForSide[direction] };
    }

    /**
     * @param target the block position
     * @return the growth percentage (0-100), or {@code null} when the block cannot grow
     */
    private Double growthPercent(int[] target) {
        World world = turtle.getWorld();
        Block block = world.getBlock(target[0], target[1], target[2]);
        if (!(block instanceof IGrowable)) return null;
        float divisor = block instanceof BlockCocoa ? 8.0F : 7.0F;
        return (double) Math.floor((world.getBlockMetadata(target[0], target[1], target[2]) / divisor) * 100.0F);
    }

    /**
     * Applies bone meal from the selected slot.
     *
     * @param target the block position
     * @return true when the block grew
     */
    private boolean fertilize(int[] target) {
        ItemStack bonemeal = turtle.getInventory()
            .getStackInSlot(turtle.getSelectedSlot());
        if (bonemeal == null) return false;
        ItemStack boneMeal = new ItemStack(Items.dye, 1, 15);
        if (!bonemeal.isItemEqual(boneMeal)) return false;
        World world = turtle.getWorld();
        if (!(world.getBlock(target[0], target[1], target[2]) instanceof IGrowable)) return false;
        boolean grew = ItemDye.applyBonemeal(bonemeal, world, target[0], target[1], target[2], null);
        if (grew && !world.isRemote) world.playAuxSFX(2005, target[0], target[1], target[2], 0);
        return grew;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
