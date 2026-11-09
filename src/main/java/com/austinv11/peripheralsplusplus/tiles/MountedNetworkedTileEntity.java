package com.austinv11.peripheralsplusplus.tiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.austinv11.collectiveframework.minecraft.tiles.NetworkedTileEntity;
import com.austinv11.peripheralsplusplus.mount.PeripheralMounts;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;

import dan200.computercraft.api.peripheral.IComputerAccess;

public abstract class MountedNetworkedTileEntity extends NetworkedTileEntity implements IPlusPlusPeripheral {

    /** Computer id -> the {@code /ppp} path this peripheral mounted, so detach can unmount exactly that. */
    private final Map<Integer, String> mountedPaths = new ConcurrentHashMap<Integer, String>();

    public MountedNetworkedTileEntity() {
        super();
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
