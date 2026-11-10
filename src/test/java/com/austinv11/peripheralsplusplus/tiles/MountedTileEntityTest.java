package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.mount.PeripheralMounts;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Covers the four peripheral bases that mount the {@code /ppp} docs directory.
 *
 * <p>
 * The mount bookkeeping lives in {@link PeripheralMounts}; what these assert is that each base remembers the path it
 * was given and releases exactly that one. The old {@code detach} mounted a fresh directory and then unmounted
 * {@code /ppp}, which threw for every peripheral that had lost the mount race (CODE_REVIEW.md section 3.3).
 * </p>
 */
public class MountedTileEntityTest {

    private static final class Plain extends MountedTileEntity {

        @Override
        public String getType() {
            return "chatBox";
        }

        @Override
        public String[] getMethodNames() {
            return new String[0];
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    private static final class Networked extends MountedNetworkedTileEntity {

        @Override
        public String getType() {
            return "chatBox";
        }

        @Override
        public String[] getMethodNames() {
            return new String[0];
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    private static final class InventoryBase extends MountedTileEntityInventory {

        @Override
        public int getSize() {
            return 1;
        }

        @Override
        public String getInventoryName() {
            return "test";
        }

        @Override
        public String getType() {
            return "chatBox";
        }

        @Override
        public String[] getMethodNames() {
            return new String[0];
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    private static final class TurtleBase
        extends com.austinv11.peripheralsplusplus.turtles.peripherals.MountedPeripheral {

        @Override
        public String getType() {
            return "chatBox";
        }

        @Override
        public String[] getMethodNames() {
            return new String[0];
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    /** A computer access whose mount always succeeds and echoes the requested path. */
    private static IComputerAccess testComputer(int id) {
        IComputerAccess access = Mockito.mock(IComputerAccess.class);
        Mockito.when(access.getID())
            .thenReturn(id);
        Mockito.when(access.mount(Mockito.anyString(), Mockito.any(IMount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return access;
    }

    private static void assertMountRoundTrip(IPeripheral peripheral) {
        IComputerAccess access = testComputer(1);
        peripheral.attach(access);
        Mockito.verify(access)
            .mount(Mockito.eq("/ppp/chatBox"), Mockito.any(IMount.class));
        peripheral.detach(access);
        Mockito.verify(access)
            .unmount("/ppp/chatBox");
    }

    @Test
    public void thePlainBaseMountsAndUnmountsTheSamePath() {
        assertMountRoundTrip(new Plain());
    }

    @Test
    public void theNetworkedBaseMountsAndUnmountsTheSamePath() {
        assertMountRoundTrip(new Networked());
    }

    @Test
    public void theInventoryBaseMountsAndUnmountsTheSamePath() {
        assertMountRoundTrip(new InventoryBase());
    }

    @Test
    public void theTurtleBaseMountsAndUnmountsTheSamePath() {
        assertMountRoundTrip(new TurtleBase());
    }

    @Test
    public void detachWithoutAnAttachIsANoOp() {
        // This is the case that used to throw RuntimeException("You didn't mount this location").
        IComputerAccess access = testComputer(1);
        new Plain().detach(access);
        Mockito.verify(access, Mockito.never())
            .unmount(Mockito.anyString());
    }

    @Test
    public void twoComputersGetTheirOwnBookkeeping() {
        IComputerAccess first = testComputer(1);
        IComputerAccess second = testComputer(2);
        Map<Integer, String> mounted = new HashMap<Integer, String>();

        Assert.assertEquals("/ppp/chatBox", PeripheralMounts.mount(first, new Plain(), mounted));
        Assert.assertEquals("/ppp/chatBox", PeripheralMounts.mount(second, new Networked(), mounted));
        Assert.assertEquals(2, mounted.size());

        PeripheralMounts.unmount(first, mounted);
        Mockito.verify(first)
            .unmount("/ppp/chatBox");
        Mockito.verify(second, Mockito.never())
            .unmount(Mockito.anyString());
    }

    @Test
    public void theInventoryBaseExposesItsSlots() {
        InventoryBase tile = new InventoryBase();
        Assert.assertEquals(1, tile.getSizeInventory());
        Assert.assertEquals(1, tile.getSize());
        Assert.assertEquals("test", tile.getInventoryName());
        Assert.assertFalse(tile.hasCustomInventoryName());
        tile.setInventorySlotContents(0, null);
        Assert.assertNull(tile.getStackInSlotOnClosing(0));
    }
}
