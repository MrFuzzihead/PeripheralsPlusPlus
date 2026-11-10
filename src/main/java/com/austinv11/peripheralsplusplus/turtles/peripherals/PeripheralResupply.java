package com.austinv11.peripheralsplusplus.turtles.peripherals;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.peripheralsplusplus.blocks.BlockResupplyStation;
import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.tiles.TileEntityResupplyStation;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class PeripheralResupply extends MountedPeripheral {

    private ITurtleAccess turtle;
    private ChunkCoordinates linkedStation;
    private int linkedDimension = Integer.MIN_VALUE;

    public PeripheralResupply(ITurtleAccess turtle) {
        this.turtle = turtle;
    }

    @Override
    public String getType() {
        return "resupply";
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "link", "resupply" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        if (!Config.enableResupplyStation) throw new LuaException("The resupply station has been disabled");
        LuaArgs arguments = LuaArgs.of(rawArguments);
        switch (method) {
            case 0:
                // link() takes one argument (a side) or three (x, y, z). Two was rejected as "too few" before.
                if (arguments.size() != 1 && arguments.size() != 3)
                    throw new LuaException("link() expects a side, or x, y and z");
                if (arguments.size() == 3) {
                    linkedStation = new ChunkCoordinates(
                        arguments.integer(0),
                        arguments.integer(1),
                        arguments.integer(2));
                    linkedDimension = turtle.getWorld().provider.dimensionId;
                    return new Object[] { true };
                }
                ForgeDirection dir = arguments.direction(0);
                ChunkCoordinates newLink = new ChunkCoordinates(
                    turtle.getPosition().posX + dir.offsetX,
                    turtle.getPosition().posY + dir.offsetY,
                    turtle.getPosition().posZ + dir.offsetZ);
                World turtleWorld = turtle.getWorld();
                if (!turtleWorld.blockExists(newLink.posX, newLink.posY, newLink.posZ)
                    || turtleWorld.isAirBlock(newLink.posX, newLink.posY, newLink.posZ)
                    || !(turtleWorld
                        .getBlock(newLink.posX, newLink.posY, newLink.posZ) instanceof BlockResupplyStation))
                    return new Object[] { false };
                linkedStation = newLink;
                linkedDimension = turtleWorld.provider.dimensionId;
                return new Object[] { true };

            case 1:
                if (linkedStation == null) throw new LuaException("A station has not been linked!");
                World world = turtle.getWorld();
                if (linkedDimension != world.provider.dimensionId)
                    throw new LuaException("The linked station is in another dimension");
                if (!world.blockExists(linkedStation.posX, linkedStation.posY, linkedStation.posZ)
                    || world.isAirBlock(linkedStation.posX, linkedStation.posY, linkedStation.posZ)
                    || !(world.getBlock(
                        linkedStation.posX,
                        linkedStation.posY,
                        linkedStation.posZ) instanceof BlockResupplyStation))
                    throw new LuaException("The linked station is nonexistant!");
                int slot = arguments.integer(0, turtle.getSelectedSlot() + 1) - 1;
                String id = null;
                if (arguments.has(1) && arguments.numberOrString(1) instanceof String) id = arguments.string(1);
                else {
                    if (turtle.getInventory()
                        .getStackInSlot(slot) == null) return new Object[] { false };
                    if (turtle.getInventory()
                        .getStackInSlot(slot)
                        .getItem() instanceof ItemBlock) {
                        Block itemBlock = Block.getBlockFromItem(
                            turtle.getInventory()
                                .getStackInSlot(slot)
                                .getItem());
                        id = Block.blockRegistry.getNameForObject(itemBlock);
                    } else {
                        Item item = turtle.getInventory()
                            .getStackInSlot(slot)
                            .getItem();
                        id = Item.itemRegistry.getNameForObject(item);
                    }
                }
                ItemStack selected = turtle.getInventory()
                    .getStackInSlot(slot);
                int meta = arguments.integer(2, selected == null ? 0 : selected.getItemDamage());
                synchronized (this) {
                    TileEntityResupplyStation station = (TileEntityResupplyStation) world
                        .getTileEntity(linkedStation.posX, linkedStation.posY, linkedStation.posZ);
                    return new Object[] { station.resupply(turtle, slot, id, meta) };
                }
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
