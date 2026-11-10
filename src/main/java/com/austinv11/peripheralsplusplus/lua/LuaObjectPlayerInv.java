package com.austinv11.peripheralsplusplus.lua;

import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerInterface;
import com.austinv11.peripheralsplusplus.utils.InventoryUtils;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

public class LuaObjectPlayerInv implements ILuaObject {

    private final InventoryPlayer inv;
    private final TileEntityPlayerInterface playerInterface;
    private final ItemStack permCard;

    public LuaObjectPlayerInv(EntityPlayer player, TileEntityPlayerInterface playerInterface, ItemStack permCard) {
        this.inv = player.inventory;
        this.playerInterface = playerInterface;
        this.permCard = permCard;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getStackInSlot", "retrieveFromSlot", "pushToSlot", "push",
        "getSize" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        LuaArgs arguments = LuaArgs.of(rawArguments);
        switch (method) {
            case 0: { // getStackInSlot(slot)
                requirePermission("getStacks");
                int slot = arguments.integer(0);
                ItemStack stack = getPlayerStack(slot);
                return stack == null ? new Object[0] : new Object[] { describe(stack) };
            }
            case 1: { // retrieveFromSlot(slot, amount)
                requirePermission("withdraw");
                int slot = arguments.integer(0);
                int amount = arguments.integer(1);
                IInventory output = requireInventory(playerInterface.outputSide, "Output");
                if (getPlayerStack(slot) == null) return new Object[] { false };
                return new Object[] { InventoryUtils.move(inv, slot, output, -1, amount) > 0 };
            }
            case 2: { // pushToSlot(slot, targetSlot, amount)
                requirePermission("deposit");
                // Argument #1 used to be ignored; it is the input slot only for symmetry with pushToSlot's name,
                // so read it and honour it.
                int sourceSlot = arguments.integer(1);
                int amount = arguments.integer(2);
                IInventory input = requireInventory(playerInterface.inputSide, "Input");
                if (input.getStackInSlot(sourceSlot) == null) return new Object[] { false };
                return new Object[] { InventoryUtils.move(input, sourceSlot, inv, arguments.integer(0), amount) > 0 };
            }
            case 3: { // push(slot, amount)
                requirePermission("deposit");
                int sourceSlot = arguments.integer(0);
                int amount = arguments.integer(1);
                IInventory input = requireInventory(playerInterface.inputSide, "Input");
                if (input.getStackInSlot(sourceSlot) == null) return new Object[] { false };
                return new Object[] { InventoryUtils.move(input, sourceSlot, inv, -1, amount) > 0 };
            }
            case 4: // getSize()
                requirePermission("getStacks");
                return new Object[] { inv.getSizeInventory() };
            default:
                return new Object[0];
        }
    }

    private ItemStack getPlayerStack(int slot) {
        return slot >= 0 && slot < inv.getSizeInventory() ? inv.getStackInSlot(slot) : null;
    }

    private Object describe(ItemStack stack) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("name", Item.itemRegistry.getNameForObject(stack.getItem()));
        map.put("meta", stack.getItemDamage());
        map.put("amount", stack.stackSize);
        map.put("displayName", stack.getDisplayName());
        return map;
    }

    /**
     * @param side  the configured side of the player interface
     * @param label the human-readable name used in the error message
     * @return the inventory on that side
     * @throws LuaException when the side is unset or does not hold an inventory
     */
    private IInventory requireInventory(ForgeDirection side, String label) throws LuaException {
        if (side == null) throw new LuaException(label + " side has not been set.");
        IInventory inventory = InventoryUtils.at(
            playerInterface.getWorldObj(),
            playerInterface.xCoord + side.offsetX,
            playerInterface.yCoord + side.offsetY,
            playerInterface.zCoord + side.offsetZ);
        if (inventory == null) throw new LuaException("Invalid " + label + " inventory.");
        return inventory;
    }

    /**
     * @param key the permission flag on the inserted card
     * @throws LuaException when permissions are enabled and the flag is not set
     */
    private void requirePermission(String key) throws LuaException {
        if (!Config.enableInterfacePermissions) return;
        NBTTagCompound tag = permCard == null ? null : permCard.getTagCompound();
        // A card that was never right-clicked has no tag at all; that denies rather than throwing (it used to NPE).
        if (tag == null || !tag.getBoolean(key)) throw new LuaException("This permission card may not " + key);
    }
}
