package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.lua.LuaObjectPeripheralWrap;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityPeripheralContainer extends MountedNetworkedTileEntity {

    private List<Block> blocksContained = new ArrayList<Block>();
    private List<IPeripheral> peripheralsContained = new ArrayList<IPeripheral>();
    public static String publicName = "peripheralContainer";
    private boolean needsUpdate = false;

    public TileEntityPeripheralContainer() {
        super();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        // Reloading must not append to the previous contents (CODE_REVIEW.md section 4.9).
        blocksContained.clear();
        peripheralsContained.clear();
        int[] ids = nbttagcompound.getIntArray("ids");
        for (int id : ids) addPeripheral(Block.getBlockById(id));
        for (IPeripheral p : peripheralsContained) {
            NBTTagCompound tag = nbttagcompound.getCompoundTag(p.getType());
            ((TileEntity) p).readFromNBT(tag);
        }
        needsUpdate = worldObj != null && !peripheralsContained.isEmpty();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        int[] ids = new int[blocksContained.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = Block.getIdFromBlock(blocksContained.get(i));
        nbttagcompound.setIntArray("ids", ids);
        for (IPeripheral p : peripheralsContained) {
            NBTTagCompound tag = new NBTTagCompound();
            ((TileEntity) p).writeToNBT(tag);
            nbttagcompound.setTag(p.getType(), tag);
        }
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getContainedPeripherals", "wrapPeripheral" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enablePeripheralContainer) throw new LuaException("Peripheral Containers have been disabled");
        if (method == 0) {
            HashMap<Integer, String> returnVals = new HashMap<Integer, String>();
            for (int i = 0; i < peripheralsContained.size(); i++) returnVals.put(
                i + 1,
                peripheralsContained.get(i)
                    .getType());
            return new Object[] { returnVals };
        } else if (method == 1) {
            if (arguments.length < 1) throw new LuaException("Too few arguments");
            if (!(arguments[0] instanceof String)) throw new LuaException("Bad argument #1 (expected string)");
            IPeripheral wrapped = getPeripheralByName((String) arguments[0]);
            if (wrapped == null) throw new LuaException("No contained peripheral named " + arguments[0]);
            return new Object[] { new LuaObjectPeripheralWrap(wrapped, computer) };
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    @Override
    public void updateEntity() {
        if (needsUpdate && worldObj != null) {
            // `needsUpdate` was never cleared, so this ran markBlockForUpdate every tick forever (section 4.9).
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            for (IPeripheral peripheral : peripheralsContained) {
                TileEntity tile = (TileEntity) peripheral;
                tile.setWorldObj(worldObj);
                tile.xCoord = xCoord;
                tile.yCoord = yCoord;
                tile.zCoord = zCoord;
            }
            needsUpdate = false;
        }
        for (IPeripheral peripheral : peripheralsContained) ((TileEntity) peripheral).updateEntity();
    }

    /**
     * Adds a peripheral block to this container.
     *
     * @param peripheral the block, or {@code null} when the id in the item's NBT no longer exists
     * @return true when a peripheral was added
     */
    public boolean addPeripheral(Block peripheral) {
        if (peripheral == null) return false;
        if (blocksContained.size() >= Config.maxNumberOfPeripherals) return false;
        TileEntity tile = peripheral.createTileEntity(worldObj, 0);
        if (!(tile instanceof IPeripheral)) return false;
        blocksContained.add(peripheral);
        peripheralsContained.add((IPeripheral) tile);
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            tile.setWorldObj(worldObj);
            tile.xCoord = xCoord;
            tile.yCoord = yCoord;
            tile.zCoord = zCoord;
        } else needsUpdate = true;
        return true;
    }

    public List<Block> getBlocksContained() {
        return blocksContained;
    }

    private IPeripheral getPeripheralByName(String peripheral) {
        for (IPeripheral p_ : peripheralsContained) if (p_.getType()
            .equals(peripheral)) return p_;
        return null;
    }

    @Override
    public void attach(IComputerAccess computer) {
        for (IPeripheral p : peripheralsContained) p.attach(computer);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        for (IPeripheral p : peripheralsContained) p.detach(computer);
        super.detach(computer);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!worldObj.isRemote) {
            ItemStack drop = new ItemStack(ModBlocks.peripheralContainer);
            if (peripheralsContained.size() > 0) {
                NBTTagCompound tag = new NBTTagCompound();
                this.writeToNBT(tag);
                drop.stackTagCompound = tag;
                List<String> text = new ArrayList<String>();
                text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
                for (int id : NBTHelper.getIntArray(drop, "ids")) {
                    Block peripheral = Block.getBlockById(id);
                    IPeripheral iPeripheral = (IPeripheral) peripheral.createTileEntity(null, 0);
                    text.add(Colors.RESET + iPeripheral.getType());
                }
                NBTHelper.setInfo(drop, text);
            }
            worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord, yCoord + 1, zCoord, drop.copy()));
        }
    }
}
