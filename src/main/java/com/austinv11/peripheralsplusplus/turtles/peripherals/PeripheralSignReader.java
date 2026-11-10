package com.austinv11.peripheralsplusplus.turtles.peripherals;

import net.minecraft.block.BlockSign;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class PeripheralSignReader extends MountedPeripheral {

    private ITurtleAccess turtle;

    public PeripheralSignReader(ITurtleAccess turtle) {
        this.turtle = turtle;
    }

    @Override
    public String getType() {
        return "signReader";
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "read", "readUp", "readDown" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableReaderTurtle) throw new LuaException("Sign Reading Turtles have been disabled");
        ChunkCoordinates position = turtle.getPosition();
        int direction = method == 1 ? ForgeDirection.UP.ordinal()
            : method == 2 ? ForgeDirection.DOWN.ordinal() : turtle.getDirection();
        int x = position.posX + Facing.offsetsXForSide[direction];
        int y = position.posY + Facing.offsetsYForSide[direction];
        int z = position.posZ + Facing.offsetsZForSide[direction];
        return readSign(x, y, z);
    }

    /**
     * Reads a sign, if there is one.
     *
     * <p>
     * This was three copies of the same eight lines; the cast was also unchecked, so a modded block that extends
     * {@code BlockSign} with a non-sign tile entity threw a {@code ClassCastException} at Lua.
     * </p>
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return the sign's four lines as a 1-based Lua table, or an empty array when there is no sign
     */
    private Object[] readSign(int x, int y, int z) {
        World world = turtle.getWorld();
        if (!(world.getBlock(x, y, z) instanceof BlockSign)) return new Object[0];
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntitySign)) return new Object[0];
        return new Object[] { Util.arrayToMap(((TileEntitySign) tile).signText) };
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
