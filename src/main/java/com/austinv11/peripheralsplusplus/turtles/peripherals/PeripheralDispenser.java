package com.austinv11.peripheralsplusplus.turtles.peripherals;

import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class PeripheralDispenser extends MountedPeripheral {

    private ITurtleAccess turtle;

    public PeripheralDispenser(ITurtleAccess turtle) {
        this.turtle = turtle;
    }

    @Override
    public String getType() {
        return "flinging";
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "dispense", "dispenseUp", "dispenseDown" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableFlingingTurtle) throw new LuaException("Flinging turtles have been disabled");
        if (arguments.length > 0 && !(arguments[0] instanceof Double))
            throw new LuaException("Bad argument #1 (expected number)");
        // Slot 1 in Lua is the first turtle slot; no argument means the selected slot. The old code started at 1
        // and *added* the argument, so dispense(0) used slot 1 and dispense(1) used slot 2 (CODE_REVIEW.md 4.10).
        int slot = arguments.length > 0 ? (int) (double) (Double) arguments[0] - 1 : turtle.getSelectedSlot();
        int direction;
        if (method == 1) direction = ForgeDirection.DOWN.flag; // Why these are reversed, idk. Dispensers be dum
        else if (method == 2) direction = ForgeDirection.UP.flag;
        else direction = turtle.getDirection();

        synchronized (this) {
            ItemStack stack = turtle.getInventory()
                .getStackInSlot(slot);
            if (stack != null) {
                IBehaviorDispenseItem behavior = (IBehaviorDispenseItem) BlockDispenser.dispenseBehaviorRegistry
                    .getObject(stack.getItem());
                if (behavior != IBehaviorDispenseItem.itemDispenseBehaviorProvider) {
                    BlockSourceTurtle blockSource = new BlockSourceTurtle(direction);
                    ItemStack newStack = behavior.dispense(blockSource, stack);
                    turtle.getInventory()
                        .setInventorySlotContents(slot, newStack == null || newStack.stackSize == 0 ? null : newStack);
                    turtle.getInventory()
                        .markDirty();
                }
            }
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    public class BlockSourceTurtle implements IBlockSource {

        public int direction;

        public BlockSourceTurtle(int direction) {
            this.direction = direction;
        }

        @Override
        public double getX() {
            return turtle.getPosition().posX;
        }

        @Override
        public double getY() {
            return turtle.getPosition().posY;
        }

        @Override
        public double getZ() {
            return turtle.getPosition().posZ;
        }

        @Override
        public int getXInt() {
            return turtle.getPosition().posX;
        }

        @Override
        public int getYInt() {
            return turtle.getPosition().posY;
        }

        @Override
        public int getZInt() {
            return turtle.getPosition().posZ;
        }

        @Override
        public int getBlockMetadata() {
            return direction;
        }

        @Override
        public TileEntity getBlockTileEntity() {
            return getWorld().getTileEntity(getXInt(), getYInt(), getZInt());
        }

        @Override
        public World getWorld() {
            return turtle.getWorld();
        }
    }
}
