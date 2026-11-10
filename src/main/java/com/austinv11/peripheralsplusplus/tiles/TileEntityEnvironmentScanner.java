package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.BiomeGenBase;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityEnvironmentScanner extends MountedTileEntity {

    public static String publicName = "environmentScanner";
    private boolean isRaining = false;
    private String biome;
    private String temp;
    private boolean isSnow;
    private ITurtleAccess turtle;

    public TileEntityEnvironmentScanner() {
        super();
    }

    public TileEntityEnvironmentScanner(ITurtleAccess turtle) {
        this.turtle = turtle;
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
    public void updateEntity() {
        if (worldObj != null) {
            isRaining = worldObj.isRaining();
            // One lookup per tick, not three (CODE_REVIEW.md section 5.23).
            BiomeGenBase biomeGen = worldObj.getBiomeGenForCoords(xCoord, zCoord);
            if (biomeGen != null) {
                biome = biomeGen.biomeName;
                temp = biomeGen.getTempCategory()
                    .name();
                isSnow = biomeGen.getEnableSnow();
            }
        }
        if (turtle != null) {
            this.setWorldObj(turtle.getWorld());
            this.xCoord = turtle.getPosition().posX;
            this.yCoord = turtle.getPosition().posY;
            this.zCoord = turtle.getPosition().posZ;
        }
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "isRaining", "getBiome", "getTemp", "isSnow" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableEnvironmentScanner) throw new LuaException("Environment Scanners have been disabled");
        switch (method) {
            case 0:
                return new Object[] { isRaining };
            case 1:
                return new Object[] { biome };
            case 2:
                return new Object[] { temp };
            case 3:
                return new Object[] { isSnow };
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
