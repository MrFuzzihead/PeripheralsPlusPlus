package com.austinv11.peripheralsplusplus.utils;

import java.util.Objects;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Inventory moves that preserve item identity.
 *
 * <p>
 * Every peripheral that moves items used to roll its own loop, and each one had a variation of the same three bugs
 * (CODE_REVIEW.md section 7.2):
 * </p>
 *
 * <ul>
 * <li>clamping a <em>total</em> instead of a delta, which produced negative stack sizes;</li>
 * <li>writing into an {@link ItemStack} obtained from an inventory instead of copying it;</li>
 * <li>comparing with {@code isItemEqual}, which ignores NBT, so a silktouch or enchanted variant merged into the
 * plain one.</li>
 * </ul>
 *
 * <p>
 * This class is deliberately free of world state so it can be unit tested with mocked inventories.
 * </p>
 */
public final class InventoryUtils {

    private InventoryUtils() {}

    /**
     * Whether two stacks are interchangeable: same item, same damage and same NBT.
     *
     * @param a the first stack
     * @param b the second stack
     * @return true when they can be merged into one slot
     */
    public static boolean matches(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getItemDamage() != b.getItemDamage()) return false;
        // 1.7 introduced NBT as part of item identity; NBTTagCompound#equals is a deep comparison.
        return Objects.equals(a.getTagCompound(), b.getTagCompound());
    }

    /**
     * How many of {@code moving} would fit into a slot that currently holds {@code target}.
     *
     * @param target         the stack in the slot, or {@code null} for an empty slot
     * @param moving         the stack being inserted
     * @param inventoryLimit the inventory's own per-slot limit
     * @return the free space, never negative
     */
    public static int room(ItemStack target, ItemStack moving, int inventoryLimit) {
        if (moving == null) return 0;
        int limit = Math.min(inventoryLimit, moving.getMaxStackSize());
        if (limit <= 0) return 0;
        if (target == null) return limit;
        int maximum = Math.min(inventoryLimit, target.getMaxStackSize());
        return Math.max(0, maximum - target.stackSize);
    }

    /**
     * The slots of an inventory that are reachable from a side, or every slot when it is not a sided inventory.
     *
     * @param inventory the inventory
     * @param side      the side being accessed (the opposite of the side the peripheral sits on)
     * @return the slot indices, never {@code null}
     */
    public static int[] slotsFor(IInventory inventory, ForgeDirection side) {
        if (inventory == null) return new int[0];
        if (inventory instanceof ISidedInventory && side != null)
            return ((ISidedInventory) inventory).getAccessibleSlotsFromSide(side.flag);
        int[] all = new int[inventory.getSizeInventory()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    /**
     * Moves up to {@code requested} items out of one inventory slot and into the first slots of another inventory
     * that accept them.
     *
     * <p>
     * The moved items are taken with {@link IInventory#decrStackSize} so the source keeps ownership of its own
     * stacks, and merges write a copy back, so no inventory ever shares an {@link ItemStack} instance with another.
     * </p>
     *
     * @param from      the source inventory
     * @param fromSlot  the source slot
     * @param to        the destination inventory
     * @param toSlots   the destination slots to try, in order
     * @param requested the maximum number of items to move
     * @return the number of items actually moved
     */
    public static int move(IInventory from, int fromSlot, IInventory to, int[] toSlots, int requested) {
        if (from == null || to == null || toSlots == null || requested <= 0) return 0;
        ItemStack source = from.getStackInSlot(fromSlot);
        if (source == null || source.stackSize <= 0) return 0;

        int remaining = Math.min(requested, source.stackSize);
        int moved = 0;
        for (int slot : toSlots) {
            if (remaining <= 0) break;
            ItemStack target = to.getStackInSlot(slot);
            if (target != null && !matches(target, source)) continue;
            if (!to.isItemValidForSlot(slot, source)) continue;
            int room = room(target, source, to.getInventoryStackLimit());
            if (room <= 0) continue;
            int take = Math.min(remaining, room);
            ItemStack taken = from.decrStackSize(fromSlot, take);
            if (taken == null || taken.stackSize <= 0) break;
            if (target == null) {
                to.setInventorySlotContents(slot, taken);
            } else {
                ItemStack merged = target.copy();
                merged.stackSize = target.stackSize + taken.stackSize;
                to.setInventorySlotContents(slot, merged);
            }
            remaining -= taken.stackSize;
            moved += taken.stackSize;
        }
        if (moved > 0) {
            from.markDirty();
            to.markDirty();
        }
        return moved;
    }

    /**
     * Moves up to {@code requested} items out of one inventory slot and into another inventory, choosing the slots
     * automatically (an explicitly requested slot takes priority, exactly like the Lua API promises).
     *
     * @param from      the source inventory
     * @param fromSlot  the source slot
     * @param to        the destination inventory
     * @param preferred a slot to try first, or a negative value for none
     * @param requested the maximum number of items to move
     * @return the number of items actually moved
     */
    public static int move(IInventory from, int fromSlot, IInventory to, int preferred, int requested) {
        if (to == null) return 0;
        int size = to.getSizeInventory();
        int[] slots = new int[size];
        int cursor = 0;
        if (preferred >= 0 && preferred < size) slots[cursor++] = preferred;
        for (int slot = 0; slot < size; slot++) if (slot != preferred) slots[cursor++] = slot;
        return move(from, fromSlot, to, slots, requested);
    }

    /**
     * The inventory at a position, if there is one.
     *
     * <p>
     * The old per-peripheral copies tested {@code block instanceof IInventory} (a {@link net.minecraft.block.Block}
     * is never an {@code IInventory}) and otherwise assumed that any {@code BlockContainer} has an inventory tile,
     * which cast-fails for computers, AE interfaces and most modded containers.
     * </p>
     *
     * @param world the world
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     * @return the inventory behind the block, or {@code null}
     */
    public static IInventory at(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return null;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
    }

    /**
     * Whether an inventory slot currently holds nothing.
     *
     * @param inventory the inventory
     * @param slot      the slot
     * @return true when the slot is empty
     */
    public static boolean isEmpty(IInventory inventory, int slot) {
        return inventory == null || inventory.getStackInSlot(slot) == null;
    }
}
