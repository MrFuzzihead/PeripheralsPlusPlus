package com.austinv11.peripheralsplusplus.utils;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.IShearable;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;

import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.shared.turtle.blocks.ITurtleTile;

public class TurtleUtil {

    /**
     * Gets the turtle at a tile entity, if there is one.
     *
     * <p>
     * This used to live in a second utility class, {@code TurtleUtils}, whose only method was this one. It also used
     * to guess the tile entity class name and call {@code getAccess} reflectively; the API interface is public.
     * </p>
     *
     * @param te the tile entity to inspect
     * @return the turtle access, or {@code null} when the tile entity is not a turtle
     */
    public static ITurtleAccess getTurtle(TileEntity te) {
        if (te instanceof ITurtleAccess) return (ITurtleAccess) te;
        if (te instanceof ITurtleTile) return ((ITurtleTile) te).getAccess();
        return null;
    }

    public static List<ItemStack> harvestBlock(ITurtleAccess turtle, FakeTurtlePlayer player, int dir,
        ItemStack itemToUse) {
        int x = turtle.getPosition().posX + Facing.offsetsXForSide[dir];
        int y = turtle.getPosition().posY + Facing.offsetsYForSide[dir];
        int z = turtle.getPosition().posZ + Facing.offsetsZForSide[dir];
        if (!turtle.getWorld()
            .isAirBlock(x, y, z)) {
            Block block = turtle.getWorld()
                .getBlock(x, y, z);
            player.setCurrentItemOrArmor(0, itemToUse);
            if (block.getBlockHardness(turtle.getWorld(), x, y, z) >= 0
                && block.canHarvestBlock(player, block.getDamageValue(turtle.getWorld(), x, y, z))) {
                List<ItemStack> items = block
                    .getDrops(turtle.getWorld(), x, y, z, block.getDamageValue(turtle.getWorld(), x, y, z), 0);
                turtle.getWorld()
                    .setBlockToAir(x, y, z);
                return items;
            }
        }
        return null;
    }

    public static List<Entity> getEntitiesNearTurtle(ITurtleAccess turtle, FakeTurtlePlayer player, int dir) {
        int x = turtle.getPosition().posX + Facing.offsetsXForSide[dir];
        int y = turtle.getPosition().posY + Facing.offsetsYForSide[dir];
        int z = turtle.getPosition().posZ + Facing.offsetsZForSide[dir];
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        return turtle.getWorld()
            .getEntitiesWithinAABBExcludingEntity(player, box);
    }

    /**
     * The entity of the given type closest to {@code ent}.
     *
     * @param list the candidate entities
     * @param ent  the reference entity
     * @return the closest candidate, or {@code null} when the list holds none
     */
    public static Entity getClosestEntity(List<Entity> list, Entity ent) {
        return getClosest(list, ent, null);
    }

    /**
     * The shearable entity closest to {@code ent}.
     *
     * @param list the candidate entities
     * @param ent  the reference entity
     * @return the closest shearable candidate, or {@code null}
     */
    public static Entity getClosestShearableEntity(List<Entity> list, Entity ent) {
        return getClosest(list, ent, IShearable.class);
    }

    /**
     * @param list     the candidate entities
     * @param ent      the reference entity
     * @param required a type every candidate must implement, or {@code null}
     * @return the closest matching entity, or {@code null}
     */
    private static Entity getClosest(List<Entity> list, Entity ent, Class<?> required) {
        if (list == null || ent == null) return null;
        Vec3 from = Vec3.createVectorHelper(ent.posX, ent.posY, ent.posZ);
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity candidate : list) {
            if (required != null && !required.isInstance(candidate)) continue;
            Vec3 to = Vec3.createVectorHelper(candidate.posX, candidate.posY, candidate.posZ);
            double distance = to.distanceTo(from);
            // closestDistance was never assigned, so this used to return whichever entity came last in the list.
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    public static void addItemListToInv(List<ItemStack> items, ITurtleAccess turtle) {
        for (ItemStack item : items) {
            addToInv(turtle, item);
        }
    }

    public static ArrayList<ItemStack> entityItemsToItemStack(ArrayList<EntityItem> entities) {
        ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
        for (EntityItem e : entities) {
            stacks.add(e.getEntityItem());
        }
        return stacks;
    }

    public static void addToInv(ITurtleAccess turtle, ItemStack stack) {
        boolean drop = true;
        IInventory inv = turtle.getInventory();
        ChunkCoordinates coords = turtle.getPosition();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack currentStack = inv.getStackInSlot(i);
            if (currentStack == null) {
                inv.setInventorySlotContents(i, stack);
                drop = false;
                break;
            }
            if (currentStack.isStackable() && currentStack.isItemEqual(stack)) {
                int space = currentStack.getMaxStackSize() - currentStack.stackSize;
                if (stack.stackSize > space) {
                    currentStack.stackSize = currentStack.getMaxStackSize();
                    stack.stackSize -= space;
                    drop = true;
                } else {
                    currentStack.stackSize += stack.stackSize;
                    stack.stackSize = 0;
                    drop = false;
                    break;
                }
            }
        }
        if (drop) {
            int dir = turtle.getDirection();
            turtle.getWorld()
                .spawnEntityInWorld(
                    new EntityItem(
                        turtle.getWorld(),
                        coords.posX + Facing.offsetsXForSide[dir],
                        coords.posY + Facing.offsetsYForSide[dir] + 1,
                        coords.posZ + Facing.offsetsZForSide[dir],
                        stack.copy()));
        }
    }

    public static ItemStack getTurtle(boolean isAdvanced, ITurtleUpgrade upgradeLeft, ITurtleUpgrade upgradeRight) {
        if (upgradeLeft == null && upgradeRight == null) {
            return new ItemStack(
                GameRegistry.findBlock("ComputerCraft", isAdvanced ? "CC-TurtleAdvanced" : "CC-Turtle"));
        } else {
            ItemStack turtle = new ItemStack(
                GameRegistry.findBlock("ComputerCraft", isAdvanced ? "CC-TurtleAdvanced" : "CC-TurtleExpanded"));
            if (upgradeLeft != null) NBTHelper.setShort(turtle, "leftUpgrade", (short) upgradeLeft.getUpgradeID());
            if (upgradeRight != null) NBTHelper.setShort(turtle, "rightUpgrade", (short) upgradeRight.getUpgradeID());
            return turtle;
        }
    }

    public static ItemStack getTurtle(boolean isAdvanced, ITurtleUpgrade upgrade) {
        return getTurtle(isAdvanced, upgrade, null);
    }

    public static ItemStack getTurtle(boolean isAdvanced) {
        return getTurtle(isAdvanced, null);
    }

}
