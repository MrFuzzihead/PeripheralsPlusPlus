package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
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
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityInteractiveSorter extends MountedTileEntityInventory {

    public static String publicName = "interactiveSorter";
    private String name = "tileEntityInteractiveSorter";
    private List<IComputerAccess> computers = new ArrayList<IComputerAccess>();

    public TileEntityInteractiveSorter() {
        super();
    }

    @Override
    public int getSize() {
        return 1;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return publicName;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "analyze", "push", "pull", "isInventoryPresent" };
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
            if (!isInventoryOnSide(dir)) {
                WorldUtils.spawnItemInWorld(
                    new Location(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, worldObj),
                    getStackInSlot(0).splitStack(amount));
                markDirty();
                return new Object[] { true };
            }
            IInventory inventory = getInventoryForSide(dir);
            int[] slots = inventory instanceof ISidedInventory
                ? ((ISidedInventory) inventory).getAccessibleSlotsFromSide(dir.getOpposite().flag)
                : getDefaultSlots(inventory);
            int remaining = amount;
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                ItemStack source = getStackInSlot(0);
                if (source == null) break;
                int slot = slots[i];
                ItemStack target = inventory.getStackInSlot(slot);
                int room;
                if (target == null) {
                    if (!inventory.isItemValidForSlot(slot, source)) continue;
                    room = Math.min(inventory.getInventoryStackLimit(), source.getMaxStackSize());
                } else {
                    if (!isSameItem(target, source)) continue;
                    if (!inventory.isItemValidForSlot(slot, source)) continue;
                    room = Math.min(inventory.getInventoryStackLimit(), target.getMaxStackSize()) - target.stackSize;
                }
                if (room <= 0) continue;
                // decrStackSize gives us our own copy, so the target inventory never shares a stack with us and the
                // moved amount is exactly `take` (the old code subtracted a clamped *total* from the source, which
                // produced negative stack sizes -- CODE_REVIEW.md section 4.1).
                ItemStack taken = decrStackSize(0, Math.min(remaining, room));
                if (taken == null) break;
                if (target == null) inventory.setInventorySlotContents(slot, taken);
                else {
                    target.stackSize += taken.stackSize;
                    inventory.setInventorySlotContents(slot, target);
                }
                remaining -= taken.stackSize;
                inventory.markDirty();
                markDirty();
            }
            return new Object[] { remaining < amount };

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
            int slots[] = inventory instanceof ISidedInventory
                ? ((ISidedInventory) inventory).getAccessibleSlotsFromSide(dir.getOpposite().flag)
                : getDefaultSlots(inventory);
            int slot = -1;
            if (arguments.length > 2) {
                if (slots.length == 0) return new Object[] { false };
                slot = getNearestSlot((int) (double) (Double) arguments[2], slots);
                if (inventory.getStackInSlot(slot) == null) return new Object[] { false };
            } else {
                for (int slot1 : slots) if (getStackInSlot(0) == null) {
                    if (inventory.getStackInSlot(slot1) != null) {
                        slot = slot1;
                        break;
                    }
                } else {
                    if (inventory.getStackInSlot(slot1) != null) {
                        if (isSameItem(inventory.getStackInSlot(slot1), getStackInSlot(0))) {
                            slot = slot1;
                            break;
                        }
                    }
                }
            }
            if (slot == -1) return new Object[] { false };
            ItemStack source = inventory.getStackInSlot(slot);
            if (source == null) return new Object[] { false };
            int amount = arguments.length > 1
                ? MathHelper.clamp_int((int) (double) (Double) arguments[1], 0, source.stackSize)
                : source.stackSize;
            ItemStack current = getStackInSlot(0);
            int room = current == null ? Math.min(source.getMaxStackSize(), getInventoryStackLimit())
                : Math.min(current.getMaxStackSize(), getInventoryStackLimit()) - current.stackSize;
            int move = Math.min(amount, room);
            if (move <= 0) return new Object[] { false };
            ItemStack taken = inventory.decrStackSize(slot, move);
            if (taken == null) return new Object[] { false };
            if (current == null) setInventorySlotContents(0, taken);
            else {
                current.stackSize += taken.stackSize;
                setInventorySlotContents(0, current);
            }
            if (inventory.getStackInSlot(slot) != null && inventory.getStackInSlot(slot).stackSize < 1)
                inventory.setInventorySlotContents(slot, null);
            inventory.markDirty();
            markDirty();
            return new Object[] { true };

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
     * Whether two stacks of the same item can be merged. NBT is part of item identity since 1.7, so a silktouch
     * variant must not be merged into its plain counterpart.
     *
     * @param a the first stack
     * @param b the second stack
     * @return true when they are interchangeable
     */
    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private int[] getDefaultSlots(IInventory inventory) {
        int[] array = new int[inventory.getSizeInventory()];
        for (int i = 0; i < inventory.getSizeInventory(); i++) array[i] = i;
        return array;
    }

    private boolean isInventoryOnSide(ForgeDirection dir) {
        if (!worldObj.isAirBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ)) {
            Block block = worldObj.getBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (block instanceof BlockContainer || block instanceof IInventory) return true;
            if (block.hasTileEntity(
                worldObj.getBlockMetadata(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ))) {
                return worldObj.getTileEntity(
                    xCoord + dir.offsetX,
                    yCoord + dir.offsetY,
                    zCoord + dir.offsetZ) instanceof IInventory;
            }
        }
        return false;
    }

    private IInventory getInventoryForSide(ForgeDirection dir) {
        if (!worldObj.isAirBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ)) {
            Block block = worldObj.getBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (block instanceof IInventory) {
                return (IInventory) block;
            }
            if (block instanceof BlockContainer && block.hasTileEntity(
                worldObj.getBlockMetadata(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ)))
                return (IInventory) worldObj
                    .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        }
        return null;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other == this;
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
