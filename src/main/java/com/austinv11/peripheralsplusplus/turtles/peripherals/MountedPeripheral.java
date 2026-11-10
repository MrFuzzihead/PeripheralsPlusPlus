package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.austinv11.peripheralsplusplus.mount.PeripheralMounts;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public abstract class MountedPeripheral implements IPeripheral {

    /** Computer id -> the {@code /ppp} path this peripheral mounted, so detach can unmount exactly that. */
    private final Map<Integer, String> mountedPaths = new ConcurrentHashMap<Integer, String>();

    public MountedPeripheral() {
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
