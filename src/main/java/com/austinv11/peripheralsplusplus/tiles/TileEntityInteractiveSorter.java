package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.collectiveframework.minecraft.utils.Location;
import com.austinv11.collectiveframework.minecraft.utils.WorldUtils;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.InventoryUtils;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityInteractiveSorter extends MountedTileEntityInventory {

    public static String publicName = "interactiveSorter";
    private List<IComputerAccess> computers = new ArrayList<IComputerAccess>();

    public TileEntityInteractiveSorter() {
        super();
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "analyze", "push", "pull", "isInventoryPresent" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableInteractiveSorter) throw new LuaException("Interactive Sorters have been disabled");
        if (method == 0) {
            return new Object[] { getItemInfo(getStackInSlot(0)) };

        } else if (method == 1) {
            if (getStackInSlot(0) == null) return new Object[] { false };
            ForgeDirection dir;
            if (arguments.length < 1) throw new LuaException("Too few arguments");
            if (!(arguments[0] instanceof String) && !(arguments[0] instanceof Double))
                throw new LuaException("Bad argument #1 (expected string or number)");
            if (arguments.length > 1 && !(arguments[1] instanceof Double))
                throw new LuaException("Bad argument #2 (expected number)");
            if (arguments[0] instanceof String) dir = ForgeDirection.valueOf(((String) arguments[0]).toUpperCase());
            else dir = ForgeDirection.getOrientation((int) (double) (Double) arguments[0]);
            int amount = arguments.length > 1
                ? MathHelper.clamp_int((int) (double) (Double) arguments[1], 0, getStackInSlot(0).stackSize)
                : getStackInSlot(0).stackSize;
            IInventory inventory = getInventoryForSide(dir);
            if (inventory == null) {
                ItemStack dropped = decrStackSize(0, amount);
                if (dropped == null) return new Object[] { false };
                WorldUtils.spawnItemInWorld(
                    new Location(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, worldObj),
                    dropped);
                return new Object[] { true };
            }
            int moved = InventoryUtils
                .move(this, 0, inventory, InventoryUtils.slotsFor(inventory, dir.getOpposite()), amount);
            markDirty();
            return new Object[] { moved > 0 };

        } else if (method == 2) {
            ForgeDirection dir;
            if (arguments.length < 1) throw new LuaException("Too few arguments");
            if (!(arguments[0] instanceof String) && !(arguments[0] instanceof Double))
                throw new LuaException("Bad argument #1 (expected string or number)");
            if (arguments.length > 1 && !(arguments[1] instanceof Double))
                throw new LuaException("Bad argument #2 (expected number)");
            if (arguments.length > 2 && !(arguments[2] instanceof Double))
                throw new LuaException("Bad argument #3 (expected number)");
            if (arguments[0] instanceof String) dir = ForgeDirection.valueOf(((String) arguments[0]).toUpperCase());
            else dir = ForgeDirection.getOrientation((int) (double) (Double) arguments[0]);
            if (!isInventoryOnSide(dir)) throw new LuaException("Block is not a valid inventory");
            IInventory inventory = getInventoryForSide(dir);
            int[] slots = InventoryUtils.slotsFor(inventory, dir.getOpposite());
            int slot = -1;
            if (arguments.length > 2) {
                if (slots.length == 0) return new Object[] { false };
                slot = getNearestSlot((int) (double) (Double) arguments[2], slots);
                if (inventory.getStackInSlot(slot) == null) return new Object[] { false };
            } else {
                for (int candidate : slots) {
                    ItemStack inSlot = inventory.getStackInSlot(candidate);
                    if (inSlot == null) continue;
                    // An empty sorter slot accepts anything; otherwise the items have to be interchangeable.
                    if (getStackInSlot(0) == null || InventoryUtils.matches(inSlot, getStackInSlot(0))) {
                        slot = candidate;
                        break;
                    }
                }
            }
            if (slot == -1) return new Object[] { false };
            ItemStack source = inventory.getStackInSlot(slot);
            if (source == null) return new Object[] { false };
            int amount = arguments.length > 1
                ? MathHelper.clamp_int((int) (double) (Double) arguments[1], 0, source.stackSize)
                : source.stackSize;
            int moved = InventoryUtils.move(inventory, slot, this, 0, amount);
            markDirty();
            return new Object[] { moved > 0 };

        } else if (method == 3) {
            ForgeDirection dir;
            if (arguments.length < 1) throw new LuaException("Too few arguments");
            if (!(arguments[0] instanceof String) && !(arguments[0] instanceof Double))
                throw new LuaException("Bad argument #1 (expected string or number)");
            if (arguments[0] instanceof String) dir = ForgeDirection.valueOf(((String) arguments[0]).toUpperCase());
            else dir = ForgeDirection.getOrientation((int) (double) (Double) arguments[0]);
            return new Object[] { isInventoryOnSide(dir) };
        }
        return new Object[0];
    }

    private int getNearestSlot(int requested, int[] slots) {
        if (slots.length == 0) return -1;
        int difference = Integer.MAX_VALUE;
        int currentSlot = slots[0];
        for (int slot : slots) {
            if (slot == requested) return slot;
            if (Math.abs(requested - slot) < difference) {
                difference = Math.abs(requested - slot);
                currentSlot = slot;
            }
        }
        return currentSlot;
    }

    /**
     * Whether the block on one side exposes an inventory.
     *
     * @param dir the side, relative to this tile entity
     * @return true when items can be pushed or pulled on that side
     */
    private boolean isInventoryOnSide(ForgeDirection dir) {
        return getInventoryForSide(dir) != null;
    }

    /**
     * @param dir the side, relative to this tile entity
     * @return the inventory on that side, or {@code null}
     */
    private IInventory getInventoryForSide(ForgeDirection dir) {
        if (worldObj == null) return null;
        return InventoryUtils.at(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    @Override
    public void attach(IComputerAccess computer) {
        super.attach(computer);
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        super.detach(computer);
        computers.remove(computer);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        super.setInventorySlotContents(slot, stack);
        if (stack != null && stack.stackSize > 0 && slot == 0)
            for (IComputerAccess computer : computers) computer.queueEvent("itemReady", null);
    }

    private HashMap<String, Object> getItemInfo(ItemStack stack) {
        if (stack == null) return null;
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("amount", stack.stackSize);
        String id;
        int numId;
        if (stack.getItem() instanceof ItemBlock) {
            Block block = Block.getBlockFromItem(stack.getItem());
            id = Block.blockRegistry.getNameForObject(block);
            numId = Block.blockRegistry.getIDForObject(block);
        } else {
            id = Item.itemRegistry.getNameForObject(stack.getItem());
            numId = Item.itemRegistry.getIDForObject(stack.getItem());
        }
        map.put("stringId", id);
        map.put("numericalId", numId);
        map.put("oreDictionaryEntries", Util.getEntries(stack));
        map.put("meta", stack.getItemDamage());
        map.put("name", stack.getDisplayName());
        if (stack.hasTagCompound()) map.put("nbt", convertNBTToMap(stack.getTagCompound()));

        return map;
    }

    private HashMap<String, Object> convertNBTToMap(NBTTagCompound tag) {
        HashMap<String, Object> nbtMap = new HashMap<String, Object>();
        Iterator iterator = tag.func_150296_c()
            .iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            NBTBase nbtBase = tag.getTag(key);
            nbtMap.put(key, getObjectForNBT(nbtBase.copy()));
        }
        return nbtMap;
    }

    private Object getObjectForNBT(NBTBase nbtBase) {
        if (nbtBase == null) return null;
        switch (nbtBase.getId()) {
            case 0: // NBTTagEnd
                return null;

            case 1:
                NBTTagByte tagByte = (NBTTagByte) nbtBase;
                return tagByte.func_150290_f();

            case 2:
                NBTTagShort tagShort = (NBTTagShort) nbtBase;
                return tagShort.func_150289_e();

            case 3:
                NBTTagInt tagInt = (NBTTagInt) nbtBase;
                return tagInt.func_150287_d();

            case 4:
                NBTTagLong tagLong = (NBTTagLong) nbtBase;
                return tagLong.func_150291_c();

            case 5:
                NBTTagFloat tagFloat = (NBTTagFloat) nbtBase;
                return tagFloat.func_150288_h();

            case 6:
                NBTTagDouble tagDouble = (NBTTagDouble) nbtBase;
                return tagDouble.func_150286_g();

            case 7:
                NBTTagByteArray tagByteArray = (NBTTagByteArray) nbtBase;
                return Util.arrayToMap(tagByteArray.func_150292_c());

            case 8:
                NBTTagString tagString = (NBTTagString) nbtBase;
                return tagString.func_150285_a_();

            case 9:
                NBTTagList tagList = (NBTTagList) nbtBase;
                Object[] tags = new Object[tagList.tagCount()];
                for (int i = 0; i < tagList.tagCount(); i++) tags[i] = getObjectForNBT(tagList.removeTag(i));
                return Util.arrayToMap(tags);

            case 10:
                NBTTagCompound tagCompound = (NBTTagCompound) nbtBase;
                return convertNBTToMap(tagCompound);

            case 11:
                NBTTagIntArray tagIntArray = (NBTTagIntArray) nbtBase;
                return Util.arrayToMap(tagIntArray.func_150302_c());

            default:
                return null;
        }
    }
}
