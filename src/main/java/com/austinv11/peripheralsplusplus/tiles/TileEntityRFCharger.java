package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.collectiveframework.minecraft.tiles.NetworkedTileEntity;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.TurtleUtil;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityRFCharger extends NetworkedTileEntity implements IEnergyReceiver {

    private EnergyStorage storage = new EnergyStorage(80000);// Leadstone Capacitor

    public static String publicName = "rfCharger";

    public TileEntityRFCharger() {
        super();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        storage.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        storage.writeToNBT(nbttagcompound);
    }

    @Override
    public void updateEntity() {
        if (!getWorldObj().isRemote) {
            List<ITurtleAccess> turtles = new ArrayList<ITurtleAccess>(6);
            ForgeDirection[] dirs = { ForgeDirection.DOWN, ForgeDirection.EAST, ForgeDirection.NORTH,
                ForgeDirection.SOUTH, ForgeDirection.UP, ForgeDirection.WEST };
            for (int i = 0; i < 6; i++) {
                int x = this.xCoord + dirs[i].offsetX;
                int y = this.yCoord + dirs[i].offsetY;
                int z = this.zCoord + dirs[i].offsetZ;
                if (!getWorldObj().blockExists(x, y, z)) continue;
                TileEntity te = getWorldObj().getTileEntity(x, y, z);
                if (te != null) {
                    ITurtleAccess turtle = TurtleUtil.getTurtle(te);
                    if (turtle != null) {
                        turtles.add(turtle);
                    }
                }
            }
            if (turtles.isEmpty()) return;
            // Spread this tick's fuel budget over the adjacent turtles instead of dividing 6 by a possibly empty
            // list (which yielded Integer.MAX_VALUE, CODE_REVIEW.md section 4.11).
            int rate = Math.max(1, 6 / turtles.size());
            for (ITurtleAccess turtle : turtles) {
                int accepted = addFuel(turtle, rate);
                if (accepted > 0) storage.extractEnergy(accepted * Config.fuelRF, false);
            }
        }
    }

    /**
     * @param turtle the turtle to refuel
     * @param rate   the fuel requested for this tick
     * @return the fuel actually added, never more than the turtle's remaining capacity
     */
    private int addFuel(ITurtleAccess turtle, int rate) {
        int space = turtle.getFuelLimit() - turtle.getFuelLevel();
        if (space <= 0) return 0;
        int accepted = Math.min(rate, space);
        // setFuelLevel rather than addFuel: addFuel round-trips through the turtle's own NBT and used to crash here.
        // This is the original workaround, now with the capacity clamp it was missing.
        turtle.setFuelLevel(turtle.getFuelLevel() + accepted);
        return accepted;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        return storage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return storage.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }
}
