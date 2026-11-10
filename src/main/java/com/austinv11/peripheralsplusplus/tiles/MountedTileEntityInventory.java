package com.austinv11.peripheralsplusplus.tiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;

import com.austinv11.collectiveframework.minecraft.tiles.TileEntityInventory;
import com.austinv11.peripheralsplusplus.mount.PeripheralMounts;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;

import dan200.computercraft.api.peripheral.IComputerAccess;

public abstract class MountedTileEntityInventory extends TileEntityInventory implements IPlusPlusPeripheral {

    /** Computer id -> the {@code /ppp} path this peripheral mounted, so detach can unmount exactly that. */
    private final Map<Integer, String> mountedPaths = new ConcurrentHashMap<Integer, String>();

    public MountedTileEntityInventory() {
        super();
    }

    /**
     * {@inheritDoc}
     * <p>
     * CollectiveFramework 1.0.1 restored this method on {@link TileEntityInventory}; the mod's own copy is kept
     * only so the {@code markDirty()} side effect stays, and should be deleted once the container logic is moved to
     * the shared inventory helper (CODE_REVIEW.md section 7).
     * </p>
     */
    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = TileEntityInventory.removeStackFromSlotStatic(items, slot);
        if (stack != null) markDirty();
        return stack;
    }

    @Override
    public void attach(IComputerAccess computer) {
        PeripheralMounts.mount(computer, this, mountedPaths);
    }

    @Override
    public void detach(IComputerAccess computer) {
        PeripheralMounts.unmount(computer, mountedPaths);
    }
}
