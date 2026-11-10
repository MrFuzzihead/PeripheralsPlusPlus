package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.mixins.early.ItemBucketAccessor;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;

public class PeripheralTank extends MountedPeripheral {

    private ITurtleAccess turtle;
    private TurtleSide side;
    private FluidTank fluidTank = new FluidTank(Config.maxNumberOfMillibuckets);
    private boolean doUpdate = false;

    public PeripheralTank(ITurtleAccess turtle, TurtleSide side) {
        this.turtle = turtle;
        this.side = side;

        if (!turtle.getWorld().isRemote) {
            NBTTagCompound turtleTag = turtle.getUpgradeNBTData(side);
            NBTTagCompound tankData = turtleTag.getCompoundTag("TankData");
            // getCompoundTag never returns null, so the old check always ran readFromNBT even for a brand new turtle.
            if (tankData.hasKey("FluidName") || tankData.hasKey("Amount")) fluidTank.readFromNBT(tankData);
        }

    }

    @Override
    public String getType() {
        return "tank";
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getFluid", "fill", "drain", "empty", "emptyUp", "emptyDown",
        "suck", "suckUp", "suckDown" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        LuaArgs arguments = LuaArgs.of(rawArguments);
        ForgeDirection dir;
        int posX, posY, posZ;
        try {
            switch (method) {
                // "getFluid": Return information about the fluid within the tank
                case 0:
                    if (fluidTank.getFluid() != null) {
                        HashMap<String, Object> map = new HashMap<String, Object>();
                        map.put("amount", fluidTank.getFluidAmount()); //
                        map.put(
                            "name",
                            fluidTank.getFluid()
                                .getLocalizedName()); // Add appropriate information to return map
                        map.put(
                            "id",
                            fluidTank.getFluid()
                                .getFluidID()); //
                        return new Object[] { map };
                    }
                    break;
                // "fill": Move fluid from internal tank to container in specified slot / currently selected slot
                // Returns amount of fluid moved
                case 1: {
                    // A slot argument is 1-based, like every other Lua slot in this mod.
                    int fillSlot = arguments.integer(0, turtle.getSelectedSlot() + 1) - 1;

                    // Get the item in that slot and check that it is a valid container
                    ItemStack fillStack = turtle.getInventory()
                        .getStackInSlot(fillSlot);
                    // An empty slot used to NPE into the catch-all below, which returned an empty array instead of 0.
                    if (fillStack == null) return new Object[] { 0 };
                    if (fillStack.getItem() instanceof ItemBucket) {
                        // Check that the container is empty
                        if (((ItemBucketAccessor) fillStack.getItem()).ppp$getFullBlock() != Blocks.air) {
                            return new Object[] { 0 };
                        }

                        // Check that there is fluid to fill with
                        if (fluidTank.getFluid() == null) {
                            return new Object[] { 0 };
                        }

                        // Only the two fluids that have a vanilla bucket are supported; the old if/else-if had no
                        // else, so any other bucket type had its fluid drained and got nothing back.
                        ItemStack filledBucket = null;
                        if (fluidTank.getFluid()
                            .getFluid()
                            .getBlock() == Blocks.water) filledBucket = new ItemStack(Items.water_bucket);
                        else if (fluidTank.getFluid()
                            .getFluid()
                            .getBlock() == Blocks.lava) filledBucket = new ItemStack(Items.lava_bucket);
                        if (filledBucket == null) return new Object[] { 0 };

                        // Find a slot to put the full bucket in.
                        if (fillStack.stackSize > 1) {
                            int emptySlot = getFirstEmptySlotIndex(turtle.getInventory());
                            if (emptySlot == -1) {
                                return new Object[] { 0 };
                            }
                            turtle.getInventory()
                                .decrStackSize(fillSlot, 1);
                            fillSlot = emptySlot;
                        }

                        turtle.getInventory()
                            .setInventorySlotContents(fillSlot, filledBucket);
                        fluidTank.drain(1000, true);

                        doUpdate = true;
                        return new Object[] { 1000 };
                    } else if (fillStack.getItem() instanceof IFluidContainerItem) {
                        try {
                            IFluidContainerItem containerItem = (IFluidContainerItem) fillStack.getItem();
                            // Check that there is fluid to fill with
                            if (fluidTank.getFluid() == null) {
                                return new Object[] { 0 };
                            }

                            // Check that the fluids are equal
                            FluidStack containerFluid = containerItem.getFluid(fillStack);
                            if (containerFluid != null && !fluidTank.getFluid()
                                .isFluidEqual(containerFluid)) {
                                return new Object[] { 0 };
                            }

                            // Check that there is room in the container
                            if (containerFluid != null
                                && containerFluid.amount + 1000 > containerItem.getCapacity(fillStack)) {
                                return new Object[] { 0 };
                            }

                            // Fill only as much as the item accepts, and only drain that much from the tank.
                            int filled = containerItem
                                .fill(fillStack, new FluidStack(fluidTank.getFluid(), 1000), true);
                            if (filled <= 0) return new Object[] { 0 };
                            fluidTank.drain(filled, true);

                            doUpdate = true;
                            return new Object[] { filled };
                        } catch (RuntimeException e) {
                            PeripheralsPlusPlus.LOGGER.error("Unable to fill a fluid container: " + e.getMessage());
                        }
                    }
                    return new Object[] { 0 };
                }
                // "drain": Moves fluid from container in specified slot to internal tank
                // Returns amount of fluid moved
                case 2: {
                    try {
                        int drainSlot = arguments.integer(0, turtle.getSelectedSlot() + 1) - 1;

                        // Get the item in that slot and check that it is a valid container
                        ItemStack drainStack = turtle.getInventory()
                            .getStackInSlot(drainSlot);
                        if (drainStack == null) return new Object[] { 0 };
                        if (!(drainStack.getItem() instanceof ItemBucket)
                            && !(drainStack.getItem() instanceof IFluidContainerItem)) {
                            return new Object[] { 0 };
                        }

                        // Check that the fluid in the container matches the fluid in the internal tank
                        if (fluidTank.getFluid() != null) {
                            // Determine if the fluids match. Retrieve the container fluid based on type because people
                            // dumb and don't register their containers
                            boolean matches = fluidTank.getFluid()
                                .isFluidEqual(
                                    drainStack.getItem() instanceof ItemBucket
                                        ? FluidContainerRegistry.getFluidForFilledItem(drainStack)
                                        : ((IFluidContainerItem) drainStack.getItem()).getFluid(drainStack));
                            if (!matches) {
                                return new Object[] { 0 };
                            }
                        }

                        // Check that there is room for the fluid
                        if (fluidTank.getFluidAmount() + 1000 > fluidTank.getCapacity()) {
                            return new Object[] { 0 };
                        }

                        // Move the fluid
                        // Retrieve the fluid based on type because people dumb and don't register their containers
                        FluidStack fluidStack;
                        if (drainStack.getItem() instanceof ItemBucket) {
                            if (FluidContainerRegistry.getFluidForFilledItem(drainStack) == null) {
                                return new Object[] { 0 };
                            }
                            fluidStack = new FluidStack(FluidContainerRegistry.getFluidForFilledItem(drainStack), 1000);
                        } else {
                            FluidStack containerFluid = ((IFluidContainerItem) drainStack.getItem())
                                .getFluid(drainStack);
                            if (containerFluid == null || containerFluid.amount < 1000) return new Object[] { 0 };
                            fluidStack = new FluidStack(containerFluid, 1000);
                        }
                        int accepted = fluidTank.fill(fluidStack, true);
                        if (accepted <= 0) return new Object[] { 0 };

                        if (drainStack.getItem() instanceof ItemBucket) {
                            turtle.getInventory()
                                .setInventorySlotContents(drainSlot, new ItemStack(Items.bucket));
                        } else {
                            // Only drain what the tank actually accepted, and honour the drain result.
                            IFluidContainerItem containerItem = (IFluidContainerItem) drainStack.getItem();
                            FluidStack drained = containerItem.drain(drainStack, accepted, true);
                            if (drained == null || drained.amount < accepted) {
                                fluidTank.drain(accepted - (drained == null ? 0 : drained.amount), true);
                            }
                        }

                        doUpdate = true;
                        return new Object[] { accepted };
                    } catch (RuntimeException e) {
                        PeripheralsPlusPlus.LOGGER.error("Unable to drain a fluid container: " + e.getMessage());
                    }
                    return new Object[] { 0 };
                }
                case 3:
                case 4:
                case 5:
                    // "empty[dir]" Places a block of the fluid in the internal tank in the world.
                    // Returns the amount of fluid emptied (always either 0 or 1000 for now)
                    // Get the correct direction relative to the turtle depending on the method executed
                    dir = method == 3 ? ForgeDirection.getOrientation(turtle.getDirection())
                        : method == 4 ? ForgeDirection.UP : ForgeDirection.DOWN;
                    posX = turtle.getPosition().posX + dir.offsetX; //
                    posY = turtle.getPosition().posY + dir.offsetY; // Get the position of the block from the direction
                    posZ = turtle.getPosition().posZ + dir.offsetZ; //
                    // Check that the block where the turtle is trying to place the fluid is air
                    if (turtle.getWorld()
                        .isAirBlock(posX, posY, posZ)) {
                        if (fluidTank.getFluid() != null) {
                            if (fluidTank.getFluid()
                                .getFluid()
                                .canBePlacedInWorld()) {
                                Block fluidBlock = fluidTank.getFluid()
                                    .getFluid()
                                    .getBlock();
                                turtle.getWorld()
                                    .setBlock(posX, posY, posZ, getFlowingBlock(fluidBlock)); // Place the flowing
                                                                                              // version of the fluid
                                fluidTank.drain(1000, true);

                                doUpdate = true;
                                return new Object[] { 1000 };
                            }
                        }
                    } else if (turtle.getWorld()
                        .getTileEntity(posX, posY, posZ) instanceof IFluidHandler) {
                            IFluidHandler fluidHandler = (IFluidHandler) turtle.getWorld()
                                .getTileEntity(posX, posY, posZ);

                            // Check that the fluids are the same
                            if (fluidHandler.getTankInfo(dir.getOpposite())[0].fluid != null) {
                                if (!fluidTank.getFluid()
                                    .isFluidEqual(fluidHandler.getTankInfo(dir.getOpposite())[0].fluid)) {
                                    return new Object[] { 0 };
                                }

                                if (fluidHandler.getTankInfo(dir.getOpposite())[0].fluid.amount + 1000
                                    > fluidHandler.getTankInfo(dir.getOpposite())[0].capacity) {
                                    return new Object[] { 0 };
                                }
                            }

                            if (fluidTank.getFluidAmount() - 1000 < 0) {
                                return new Object[] { 0 };
                            }

                            fluidHandler.fill(
                                dir.getOpposite(),
                                new FluidStack(
                                    fluidTank.getFluid()
                                        .getFluid(),
                                    1000),
                                true);
                            fluidTank.drain(1000, true);

                            doUpdate = true;
                            return new Object[] { 1000 };
                        }
                    return new Object[] { 0 };
                case 6:
                case 7:
                case 8:
                    // "suck[dir]" Takes a block of fluid from the world into the internal tank
                    // Returns the amount of fluid moved (always either 0 or 1000 for now)
                    // Get the correct direction relative to the turtle depending on the method executed
                    dir = method == 6 ? ForgeDirection.getOrientation(turtle.getDirection())
                        : method == 7 ? ForgeDirection.UP : ForgeDirection.DOWN;
                    posX = turtle.getPosition().posX + dir.offsetX; //
                    posY = turtle.getPosition().posY + dir.offsetY; // Get the position of the block from the direction
                    posZ = turtle.getPosition().posZ + dir.offsetZ; //

                    Block block = turtle.getWorld()
                        .getBlock(posX, posY, posZ);
                    // Check that there is fluid in the specified space
                    if (turtle.getWorld()
                        .isAnyLiquid(AxisAlignedBB.getBoundingBox(posX, posY, posZ, posX, posY, posZ))) {
                        // We can only pick up the source versions of the block.
                        if (turtle.getWorld()
                            .getBlockMetadata(posX, posY, posZ) != 0) {
                            return new Object[] { 0 };
                        }
                        // Check that the fluid being taken is equal to the fluid in the internal tank (if there is any)
                        if (fluidTank.getFluid() != null) {
                            if (!fluidTank.getFluid()
                                .isFluidEqual(new FluidStack(FluidRegistry.lookupFluidForBlock(block), 0))) {
                                return new Object[] { 0 };
                            }
                        }

                        // Check that there is room for the fluid
                        if (fluidTank.getFluidAmount() + 1000 <= fluidTank.getCapacity()) {
                            // Move the fluid
                            fluidTank.fill(new FluidStack(FluidRegistry.lookupFluidForBlock(block), 1000), true);
                            turtle.getWorld()
                                .setBlock(posX, posY, posZ, Blocks.air);

                            doUpdate = true;
                            return new Object[] { 1000 };
                        }
                    } else if (turtle.getWorld()
                        .getTileEntity(posX, posY, posZ) instanceof IFluidHandler) {
                            IFluidHandler fluidHandler = (IFluidHandler) turtle.getWorld()
                                .getTileEntity(posX, posY, posZ);

                            if (fluidHandler.getTankInfo(dir.getOpposite())[0].fluid == null) {
                                return new Object[] { 0 };
                            }

                            // Check that the fluids are the same
                            if (fluidTank.getFluid() != null) {
                                if (!fluidHandler.getTankInfo(dir.getOpposite())[0].fluid
                                    .isFluidEqual(fluidTank.getFluid())) {
                                    return new Object[] { 0 };
                                }
                            }

                            if (fluidHandler.getTankInfo(dir.getOpposite())[0].fluid.amount - 1000 < 0
                                && fluidTank.getFluidAmount() + 1000 <= fluidTank.getCapacity()) {
                                return new Object[] { 0 };
                            }

                            fluidTank.fill(
                                new FluidStack(fluidHandler.getTankInfo(dir.getOpposite())[0].fluid.getFluid(), 1000),
                                true);
                            fluidHandler.drain(dir.getOpposite(), 1000, true);

                            doUpdate = true;
                            return new Object[] { 1000 };
                        }
                    return new Object[] { 0 };
            }
        } catch (LuaException e) {
            // A LuaException is the program's error and must reach Lua.
            throw e;
        } catch (RuntimeException e) {
            PeripheralsPlusPlus.LOGGER.error("Tank operation failed: " + e.getMessage());
        }
        return new Object[0];
    }

    public void update() {
        if (doUpdate) {
            NBTTagCompound newTag = new NBTTagCompound();
            NBTTagCompound turtleTag = turtle.getUpgradeNBTData(side);
            fluidTank.writeToNBT(newTag);
            turtleTag.setTag("TankData", newTag);
            turtle.updateUpgradeNBTData(side);
            doUpdate = false;
        }
    }

    /**
     * Gets the flowing version of a fluid block. Only vanilla liquids are two separate blocks.
     * 
     * @param staticBlock The static version of the fluid (water or lava)
     * @return The flowing version (flowing_water or flowing_lava). If the staticBlock is neither, then it is a forge
     *         fluid which is already the flowing version.
     */
    private Block getFlowingBlock(Block staticBlock) {
        if (staticBlock == Blocks.water) {
            return Blocks.flowing_water;
        } else if (staticBlock == Blocks.lava) {
            return Blocks.flowing_lava;
        }
        return staticBlock;
    }

    // Finds the first empty slot in the given inventory.
    private int getFirstEmptySlotIndex(IInventory inv) {
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            if (inv.getStackInSlot(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
