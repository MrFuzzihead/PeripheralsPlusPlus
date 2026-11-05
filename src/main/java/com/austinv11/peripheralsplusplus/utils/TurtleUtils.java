package com.austinv11.peripheralsplusplus.utils;

import net.minecraft.tileentity.TileEntity;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.shared.turtle.blocks.ITurtleTile;

public final class TurtleUtils {

    private TurtleUtils() {}

    /**
     * Gets the turtle at a tile entity, if there is one.
     *
     * <p>
     * This used to guess the tile entity class name and call {@code getAccess} reflectively, because ComputerCraft's
     * turtle tiles only expose it through {@link ITurtleTile}. The API interface is public, so no reflection is
     * needed.
     */
    public static ITurtleAccess getTurtle(TileEntity te) {
        if (te instanceof ITurtleAccess) return (ITurtleAccess) te;
        if (te instanceof ITurtleTile) return ((ITurtleTile) te).getAccess();
        return null;
    }
}
