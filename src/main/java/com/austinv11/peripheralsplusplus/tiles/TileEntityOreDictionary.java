package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.ChatUtil;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityOreDictionary extends MountedTileEntity {

    public static String publicName = "oreDictionary";
    private HashMap<IComputerAccess, Boolean> computers = new HashMap<IComputerAccess, Boolean>();
    private ITurtleAccess turtle = null;

    public TileEntityOreDictionary() {
        super();
    }

    public TileEntityOreDictionary(ITurtleAccess turtle) {
        super();
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
    public String getType() {
        return publicName;
    }

    private boolean isTurtle() {
        return !(turtle == null);
    }

    @Override
    public String[] getMethodNames() {
        if (isTurtle()) return new String[] { "getEntries"/* getEntries() - returns table w/ all oredict entries */,
            "combineStacks"/* combineStacks(slotNum,slotNum2) - returns boolean and places new stack in selected slot */,
            "transmute"/* transmute() - transmutes selected items to next oreDict possibility */,
            "doItemsMatch"/* doItemsMatch(slotNum, slotNum2) - returns whether the two items match */ };
        return new String[0];
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        if (!Config.enableOreDictionary) throw new LuaException("Ore Dictionaries have been disabled");
        LuaArgs arguments = LuaArgs.of(rawArguments);
        if (!isTurtle()) throw new LuaException("Ore Dictionary blocks have no Lua API; use the turtle upgrade");
        try {
            if (method == 0) {
                ItemStack slot = turtle.getInventory()
                    .getStackInSlot(turtle.getSelectedSlot());
                return new Object[] { Util.getEntries(slot) };
            } else if (method == 1) {
                // 1-based, like every other Lua slot in this mod.
                int arg1 = arguments.integer(0) - 1;
                int arg2 = arguments.integer(1) - 1;
                ItemStack slot = turtle.getInventory()
                    .getStackInSlot(turtle.getSelectedSlot());
                ItemStack stack1 = turtle.getInventory()
                    .getStackInSlot(arg1);
                ItemStack stack2 = turtle.getInventory()
                    .getStackInSlot(arg2);
                if (stack1 == null || stack2 == null)
                    throw new LuaException("One or more selected slots have nil items");
                if (!Util.compare(stack1, stack2)) return new Object[] { false };
                if (!Util.compare(stack1, slot) && slot != null)
                    throw new LuaException("The destination slot is incompatible");
                int maxMoveSize = 0;
                if (slot != null) {
                    maxMoveSize = slot.getMaxStackSize() - slot.stackSize;
                } else {
                    maxMoveSize = stack1.getMaxStackSize() - stack1.stackSize;
                }
                int move1 = stack1.stackSize;
                int move2 = stack2.stackSize;
                if (move1 + move2 > maxMoveSize) {
                    if (move1 >= maxMoveSize) move1 = maxMoveSize;
                    move2 = maxMoveSize - move1;
                }
                stack1.stackSize = stack1.stackSize - move1;
                stack2.stackSize = stack2.stackSize - move2;
                if (slot != null) {
                    slot.stackSize = move1 + move2;
                    turtle.getInventory()
                        .setInventorySlotContents(turtle.getSelectedSlot(), slot.copy());
                    if (stack1.stackSize <= 0) {
                        turtle.getInventory()
                            .setInventorySlotContents(arg1, null);
                    } else {
                        turtle.getInventory()
                            .setInventorySlotContents(arg1, stack1.copy());
                    }
                    if (stack2.stackSize <= 0) {
                        turtle.getInventory()
                            .setInventorySlotContents(arg2, null);
                    } else {
                        turtle.getInventory()
                            .setInventorySlotContents(arg2, stack2.copy());
                    }
                } else {
                    ItemStack newStack = new ItemStack(stack1.getItem());
                    newStack.stackSize = move1 + move2;
                    turtle.getInventory()
                        .setInventorySlotContents(turtle.getSelectedSlot(), newStack.copy());
                    if (stack1.stackSize <= 0) {
                        turtle.getInventory()
                            .setInventorySlotContents(arg1, null);
                    } else {
                        turtle.getInventory()
                            .setInventorySlotContents(arg1, stack1.copy());
                    }
                    if (stack2.stackSize <= 0) {
                        turtle.getInventory()
                            .setInventorySlotContents(arg2, null);
                    } else {
                        turtle.getInventory()
                            .setInventorySlotContents(arg2, stack2.copy());
                    }
                }
                return new Object[] { true };
            } else if (method == 2) {
                ItemStack slot = turtle.getInventory()
                    .getStackInSlot(turtle.getSelectedSlot());
                if (slot == null) throw new LuaException("Selected slot's item is nil");
                ItemStack newStack = transmute(slot);
                if (newStack == null || newStack.isItemEqual(slot)) return new Object[] { false };
                newStack.stackSize = slot.stackSize;
                turtle.getInventory()
                    .setInventorySlotContents(turtle.getSelectedSlot(), newStack);
                return new Object[] { true };
            } else if (method == 3) {
                int arg1 = arguments.integer(0) - 1;
                int arg2 = arguments.integer(1) - 1;
                return new Object[] { Util.compare(
                    turtle.getInventory()
                        .getStackInSlot(arg1),
                    turtle.getInventory()
                        .getStackInSlot(arg2)) };
            }
        } catch (LuaException e) {
            // A LuaException is the program's error and must reach Lua, not be converted into nil.
            throw e;
        } catch (RuntimeException e) {
            PeripheralsPlusPlus.LOGGER.error("Ore Dictionary operation failed: " + e);
            throw new LuaException("Ore Dictionary operation failed: " + e.getMessage());
        }
        return new Object[0];
    }

    @Override
    public void attach(IComputerAccess computer) {
        if (!isTurtle()) computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        if (!isTurtle()) computers.remove(computer);
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    private ItemStack transmute(ItemStack item) {
        HashMap<Integer, String> entries = Util.getEntries(item);
        int i = 0;
        boolean test = false;
        for (String v : entries.values()) {
            for (ItemStack stack : OreDictionary.getOres(v)) {
                if (test) return stack.copy();
                if (stack.isItemEqual(item)) {
                    test = true;
                }
                i++;
            }
            if (test) return OreDictionary.getOres(v)
                .get(0)
                .copy();
            i = 0;
        }
        return null;
    }

    public void blockActivated(EntityPlayer player) {
        if (player.getHeldItem() != null) {
            for (IComputerAccess computer : computers.keySet()) {
                computer.queueEvent("oreDict", new Object[] { Util.getEntries(player.getHeldItem()) });
            }
            if (Config.oreDictionaryMessage) ChatUtil.sendMessage(
                player.getDisplayName(),
                this,
                Util.getEntries(player.getHeldItem())
                    .entrySet()
                    .toString(),
                100,
                true);
        }
    }
}
