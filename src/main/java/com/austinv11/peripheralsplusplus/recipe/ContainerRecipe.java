package com.austinv11.peripheralsplusplus.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.blocks.BlockPeripheralContainer;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.peripheral.IPeripheral;

public class ContainerRecipe implements IRecipe {

    /**
     * Blocks that must never end up inside a container, even if their tile entity is a peripheral.
     */
    private static final Block[] EXCLUDED = new Block[] { ModBlocks.antenna };

    /**
     * Builds the tile entity a block would contribute, so it can be tested for {@link IPeripheral}.
     *
     * <p>
     * This used to test {@code block instanceof IPeripheralProvider}. No {@link Block} implements that interface
     * (providers are the objects registered with {@code ComputerCraftAPI.registerPeripheralProvider}), so the test
     * was always false and the recipe could never match. See CODE_REVIEW.md section 3.2.
     * </p>
     *
     * @param block The block to probe
     * @return The peripheral the block contributes, or {@code null} if it is not one
     */
    private static IPeripheral peripheralOf(Block block) {
        if (block == null) return null;
        for (Block excluded : EXCLUDED) if (block == excluded) return null;
        TileEntity tileEntity = block.createTileEntity(null, 0);
        return tileEntity instanceof IPeripheral ? (IPeripheral) tileEntity : null;
    }

    @Override
    public boolean matches(InventoryCrafting craftingInventory, World world) {
        boolean hasContainer = false;
        boolean hasPeripheral = false;
        int numOfPeripherals = 0;
        ItemStack container = null;
        for (int i = 0; i < craftingInventory.getSizeInventory(); i++) {
            ItemStack stack = craftingInventory.getStackInSlot(i);
            if (stack == null) continue;
            if (!(stack.getItem() instanceof ItemBlock)) return false;
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block instanceof BlockPeripheralContainer) {
                if (hasContainer) return false;
                hasContainer = true;
                container = stack;
            } else if (peripheralOf(block) == null) {
                return false;
            } else {
                hasPeripheral = true;
                numOfPeripherals++;
            }
        }
        if (container == null) return false;
        int numOfContained = container.stackTagCompound == null || container.stackTagCompound.hasNoTags() ? 0
            : NBTHelper.getIntArray(container, "ids").length;
        return hasContainer && hasPeripheral && (numOfContained + numOfPeripherals <= Config.maxNumberOfPeripherals);
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting craftingInventory) {
        HashMap<Integer, IPeripheral> map = new HashMap<Integer, IPeripheral>();
        ItemStack base = new ItemStack(ModBlocks.peripheralContainer);
        for (int i = 0; i < craftingInventory.getSizeInventory(); i++) {
            ItemStack stack = craftingInventory.getStackInSlot(i);
            if (stack == null) continue;
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block instanceof BlockPeripheralContainer) {
                base.stackTagCompound = stack.stackTagCompound == null ? null
                    : (NBTTagCompound) stack.stackTagCompound.copy();
                continue;
            }
            IPeripheral peripheral = peripheralOf(block);
            if (peripheral != null) map.put(Block.getIdFromBlock(block), peripheral);
        }
        List<String> text = new ArrayList<String>();
        if (base.stackTagCompound == null || base.stackTagCompound.hasNoTags()
            || !base.stackTagCompound.hasKey("ids")) {
            int[] ids = setToArray(map.keySet());
            NBTHelper.setIntArray(base, "ids", ids);
            text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
            for (IPeripheral p : map.values()) text.add(Colors.RESET + p.getType());
            NBTHelper.addInfo(base, text);
        } else {
            text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
            int[] existing = NBTHelper.getIntArray(base, "ids");
            int[] added = setToArray(map.keySet());
            int[] newIds = new int[existing.length + added.length];
            System.arraycopy(existing, 0, newIds, 0, existing.length);
            System.arraycopy(added, 0, newIds, existing.length, added.length);
            NBTHelper.setIntArray(base, "ids", newIds);
            for (int id : newIds) {
                IPeripheral peripheral = peripheralOf(Block.getBlockById(id));
                if (peripheral != null) text.add(Colors.RESET + peripheral.getType());
            }
            NBTHelper.setInfo(base, text);
        }
        return base;
    }

    private int[] setToArray(Set<Integer> c) {
        int[] array = new int[c.size()];
        int i = 0;
        for (int j : c) {
            array[i] = j;
            i++;
        }
        return array;
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(ModBlocks.peripheralContainer);
    }
}
