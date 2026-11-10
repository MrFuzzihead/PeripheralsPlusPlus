package com.austinv11.peripheralsplusplus.pocket.peripherals;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.austinv11.peripheralsplusplus.lua.LuaObjectPeripheralWrap;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.turtles.peripherals.MountedPeripheral;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * The peripheral of a pocket computer carrying a peripheral container upgrade.
 *
 * <p>
 * The peripherals of the upgrades in the contained list are owned by the upgrade that built this one and passed in
 * here, rather than looked up from a static map keyed by computer id.
 * </p>
 */
public class PeripheralPeripheralContainer extends MountedPeripheral {

    private final Map<Integer, IPeripheral> contained;

    /**
     * @param contained the peripherals of the upgrades this container carries, keyed by upgrade id
     */
    public PeripheralPeripheralContainer(Map<Integer, IPeripheral> contained) {
        this.contained = contained;
    }

    /**
     * The peripherals this container carries, keyed by upgrade id.
     *
     * @return the live map, keyed by upgrade id
     */
    public Map<Integer, IPeripheral> getContainedPeripherals() {
        return contained;
    }

    @Override
    public String getType() {
        return "peripheralContainer";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "getContainedPeripherals", "wrapPeripheral" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enablePeripheralContainer) throw new LuaException("Peripheral Containers have been disabled");
        if (method == 0) {
            HashMap<Integer, String> returnVals = new HashMap<Integer, String>();
            int i = 1;
            for (IPeripheral peripheral : contained.values()) returnVals.put(i++, peripheral.getType());
            return new Object[] { returnVals };
        } else if (method == 1) {
            if (arguments.length < 1) throw new LuaException("Too few arguments");
            if (!(arguments[0] instanceof String)) throw new LuaException("Bad argument #1 (expected string)");
            IPeripheral wrapped = getPeripheralByName((String) arguments[0]);
            if (wrapped == null) throw new LuaException("No contained peripheral named " + arguments[0]);
            return new Object[] { new LuaObjectPeripheralWrap(wrapped, computer) };
        }
        return new Object[0];
    }

    private IPeripheral getPeripheralByName(String argument) {
        for (IPeripheral peripheral : contained.values()) if (peripheral.getType()
            .equals(argument)) return peripheral;
        return null;
    }

    /**
     * @return the contained peripherals; a live view, so callers do not allocate on every Lua call
     */
    public Collection<IPeripheral> getPeripherals() {
        return contained.values();
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other == this;
    }

    @Override
    public void attach(IComputerAccess computer) {
        super.attach(computer);
        for (IPeripheral peripheral : getPeripherals()) peripheral.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        super.detach(computer);
        for (IPeripheral peripheral : getPeripherals()) peripheral.detach(computer);
    }
}
