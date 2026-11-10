package com.austinv11.peripheralsplusplus.mount;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Guards the ComputerCraft mount contract that the old code violated (CODE_REVIEW.md section 3.3).
 *
 * <p>
 * {@code IComputerAccess#mount} returns {@code null} when the requested path is already in use, and
 * {@code IComputerAccess#unmount} throws when the path was never mounted by that access object. Both behaviours are
 * modelled here so a regression fails a unit test instead of a player's computer.
 * </p>
 */
public class PeripheralMountsTest {

    private static IComputerAccess computer(int id, String... rejectedPaths) {
        IComputerAccess access = Mockito.mock(IComputerAccess.class);
        Mockito.when(access.getID())
            .thenReturn(id);
        // Default: every mount succeeds and echoes the requested path.
        Mockito.when(access.mount(Mockito.anyString(), Mockito.any(IMount.class)))
            .thenAnswer(invocation -> {
                String desired = invocation.getArgument(0);
                for (String rejected : rejectedPaths) if (rejected.equals(desired)) return null;
                return desired;
            });
        return access;
    }

    private static IPeripheral peripheral(String type) {
        IPeripheral p = Mockito.mock(IPeripheral.class);
        Mockito.when(p.getType())
            .thenReturn(type);
        return p;
    }

    @Test
    public void mountsUnderItsOwnTypeNotTheSharedRoot() {
        IComputerAccess access = computer(1);
        Map<Integer, String> paths = new HashMap<Integer, String>();

        String mounted = PeripheralMounts.mount(access, peripheral("chatBox"), paths);

        Assert.assertEquals("/ppp/chatBox", mounted);
        Assert.assertEquals("/ppp/chatBox", paths.get(1));

        ArgumentCaptor<String> requested = ArgumentCaptor.forClass(String.class);
        Mockito.verify(access)
            .mount(requested.capture(), Mockito.any(IMount.class));
        Assert.assertEquals("/ppp/chatBox", requested.getValue());
    }

    @Test
    public void twoPeripheralsOnOneComputerBothGetADirectory() {
        // The rejected path models ComputerCraft refusing to mount over an existing location.
        IComputerAccess access = computer(1, "/ppp/chatBox");

        String first = PeripheralMounts.mount(access, peripheral("chatBox"), new HashMap<Integer, String>());
        Map<Integer, String> paths = new HashMap<Integer, String>();
        String second = PeripheralMounts.mount(access, peripheral("speaker"), paths);

        // The chat box falls back to a suffixed path rather than silently getting no docs at all.
        Assert.assertEquals("/ppp/chatBox_2", first);
        Assert.assertEquals("/ppp/speaker", second);
        Assert.assertEquals("/ppp/speaker", paths.get(1));
        Assert.assertNotEquals(first, second);
    }

    @Test
    public void aSecondPeripheralOfTheSameTypeGetsASuffixedDirectory() {
        IComputerAccess access = computer(1, "/ppp/chatBox");

        String mounted = PeripheralMounts.mount(access, peripheral("chatBox"), new HashMap<Integer, String>());

        Assert.assertEquals("/ppp/chatBox_2", mounted);
    }

    @Test
    public void givesUpAfterTooManyOfTheSameType() {
        String[] all = new String[8];
        for (int i = 0; i < all.length; i++) all[i] = i == 0 ? "/ppp/chatBox" : "/ppp/chatBox_" + (i + 1);
        IComputerAccess access = computer(1, all);

        Assert.assertNull(PeripheralMounts.mount(access, peripheral("chatBox"), new HashMap<Integer, String>()));
    }

    @Test
    public void unmountUsesThePathThatWasActuallyMounted() {
        IComputerAccess access = computer(1, "/ppp/chatBox");
        Map<Integer, String> paths = new HashMap<Integer, String>();
        String mounted = PeripheralMounts.mount(access, peripheral("chatBox"), paths);
        Assert.assertNotNull(mounted);

        PeripheralMounts.unmount(access, paths);

        Mockito.verify(access)
            .unmount("/ppp/chatBox_2");
        Assert.assertTrue("the bookkeeping entry must be consumed", paths.isEmpty());
    }

    @Test
    public void unmountDoesNothingWhenNothingWasMounted() {
        // This is the case that used to throw RuntimeException("You didn't mount this location") for every
        // peripheral that lost the mount race. Model a computer where every candidate path is taken.
        String[] all = new String[8];
        for (int i = 0; i < all.length; i++) all[i] = i == 0 ? "/ppp/chatBox" : "/ppp/chatBox_" + (i + 1);
        IComputerAccess access = computer(1, all);
        Map<Integer, String> paths = new HashMap<Integer, String>();
        Assert.assertNull(PeripheralMounts.mount(access, peripheral("chatBox"), paths));

        PeripheralMounts.unmount(access, paths);

        Mockito.verify(access, Mockito.never())
            .unmount(Mockito.anyString());
    }

    @Test
    public void unmountIsScopedToTheComputerThatMounted() {
        IComputerAccess first = computer(1);
        IComputerAccess second = computer(2);
        Map<Integer, String> paths = new HashMap<Integer, String>();
        PeripheralMounts.mount(first, peripheral("chatBox"), paths);
        PeripheralMounts.mount(second, peripheral("speaker"), paths);

        PeripheralMounts.unmount(first, paths);

        Mockito.verify(first)
            .unmount("/ppp/chatBox");
        Mockito.verify(second, Mockito.never())
            .unmount(Mockito.anyString());
        Assert.assertEquals("/ppp/speaker", paths.get(2));
    }

    @Test
    public void nullArgumentsAreIgnored() {
        Assert.assertNull(PeripheralMounts.mount(null, peripheral("chatBox"), new HashMap<Integer, String>()));
        Assert.assertNull(PeripheralMounts.mount(computer(1), null, new HashMap<Integer, String>()));
        PeripheralMounts.unmount(null, new HashMap<Integer, String>());
    }

    @Test
    public void aRemountReplacesTheRememberedPath() {
        IComputerAccess access = computer(1);
        Map<Integer, String> paths = new HashMap<Integer, String>();
        PeripheralMounts.mount(access, peripheral("chatBox"), paths);
        PeripheralMounts.mount(access, peripheral("chatBox"), paths);

        PeripheralMounts.unmount(access, paths);

        // The most recent mount is the one that must be released.
        Mockito.verify(access)
            .unmount("/ppp/chatBox");
        Assert.assertTrue(paths.isEmpty());
    }
}
