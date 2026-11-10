package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import com.austinv11.collectiveframework.minecraft.utils.Location;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityPlayerSensor extends MountedTileEntity {

    public static String publicName = "playerSensor";
    private HashMap<IComputerAccess, Boolean> computers = new HashMap<IComputerAccess, Boolean>();
    private Location location;
    private ITurtleAccess turtle;

    public TileEntityPlayerSensor() {
        super();
    }

    public TileEntityPlayerSensor(ITurtleAccess turtle) {
        location = new Location(
            turtle.getPosition().posX,
            turtle.getPosition().posY,
            turtle.getPosition().posZ,
            turtle.getWorld());
        this.turtle = turtle;
        this.xCoord = turtle.getPosition().posX;
        this.yCoord = turtle.getPosition().posY;
        this.zCoord = turtle.getPosition().posZ;
        this.setWorldObj(turtle.getWorld());
    }

    @Override
    public void validate() {
        super.validate();

        if (worldObj != null) location = new Location(this);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
    }

    @Override
    public String getType() {
        return publicName;
    }

    @Override
    public String[] getMethodNames() {
        if (Config.additionalMethods)
            return new String[] { "getNearbyPlayers"/*
                                                     * params: (optional) range returns:table containing players (in
                                                     * table with 'player' and 'distance' keys)
                                                     */,
                "getAllPlayers"/* params: (optional) limitToCurrentWorld returns:table containing all players */ };
        return new String[0];
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enablePlayerSensor) throw new LuaException("Player sensors have been disabled");
        if (!Config.additionalMethods)
            throw new LuaException("Additional methods for player sensors have been disabled");
        if (method == 0) {
            if (arguments.length > 0 && !(arguments[0] instanceof Double))
                throw new LuaException("Bad argument #1 (expected number)");
            // The range argument used to be parsed and then ignored, and the distance was |dx + dz| with no Y term
            // (CODE_REVIEW.md section 4.13).
            double range = arguments.length > 0 ? (Double) arguments[0] : Config.sensorRange;
            if (range < 0) range = Double.MAX_VALUE;
            Location from = location;
            if (from == null || from.getWorld() == null) throw new LuaException("Sensor is not in a world");
            // Nearest first: the old code emitted a HashMap in hash order, so the Lua table order was arbitrary.
            List<Object[]> found = new ArrayList<Object[]>();
            for (Object entity : from.getWorld().playerEntities) {
                EntityPlayer player = (EntityPlayer) entity;
                double distance = Math.sqrt(
                    Math.pow(from.getX() - player.posX, 2) + Math.pow(from.getY() - player.posY, 2)
                        + Math.pow(from.getZ() - player.posZ, 2));
                if (distance <= range) found.add(new Object[] { player.getDisplayName(), distance });
            }
            Collections.sort(found, new Comparator<Object[]>() {

                @Override
                public int compare(Object[] a, Object[] b) {
                    return Double.compare((Double) a[1], (Double) b[1]);
                }
            });
            HashMap<Integer, HashMap<String, Object>> returnVal = new HashMap<Integer, HashMap<String, Object>>();
            int i = 1;
            for (Object[] entry : found) {
                HashMap<String, Object> table = new HashMap<String, Object>();
                table.put("player", entry[0]);
                table.put("distance", entry[1]);
                returnVal.put(i, table);
                i++;
            }
            return new Object[] { returnVal };
        } else if (method == 1) {
            if (arguments.length > 0 && !(arguments[0] instanceof Boolean))
                throw new LuaException("Bad argument #1 (expected boolean)");
            boolean inWorld = false;
            if (arguments.length > 0) inWorld = (Boolean) arguments[0];
            synchronized (this) {
                HashMap<Integer, String> map = new HashMap<Integer, String>();
                int i = 1;
                for (String p : Util.getPlayers(inWorld ? getWorldObj() : null)) {
                    map.put(i, p);
                    i++;
                }
                return new Object[] { map };
            }
        }
        return new Object[0];
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    public void update() {
        this.xCoord = turtle.getPosition().posX;
        this.yCoord = turtle.getPosition().posY;
        this.zCoord = turtle.getPosition().posZ;
    }

    public void blockActivated(String player) {
        for (IComputerAccess computer : computers.keySet()) {
            computer.queueEvent("player", new Object[] { player });
        }
    }
}
