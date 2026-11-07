package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.item.ItemStack;

import com.austinv11.collectiveframework.minecraft.tiles.TileEntityInventory;
import com.austinv11.peripheralsplusplus.mount.DynamicMount;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;

import dan200.computercraft.api.peripheral.IComputerAccess;

public abstract class MountedTileEntityInventory extends TileEntityInventory implements IPlusPlusPeripheral {

    public MountedTileEntityInventory() {
        super();
    }

    /**
     * CollectiveFramework 1.0.0 dropped this from {@link TileEntityInventory} (it keeps only the
     * {@code removeStackFromSlotStatic} helper), so every inventory here has to supply it again to satisfy
     * {@link net.minecraft.inventory.IInventory}.
     */
    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = TileEntityInventory.removeStackFromSlotStatic(items, slot);
        if (stack != null) markDirty();
        return stack;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computer.mount(DynamicMount.DIRECTORY, new DynamicMount(this));
    }

    @Override
    public void detach(IComputerAccess computer) {
        computer.mount(DynamicMount.DIRECTORY, new DynamicMount(this));
        computer.unmount(DynamicMount.DIRECTORY);
    }
}
