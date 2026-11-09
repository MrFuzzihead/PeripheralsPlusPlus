package com.austinv11.peripheralsplusplus.mount;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Mounts the bundled Lua examples under {@code /ppp} for one peripheral.
 *
 * <p>
 * This exists because of two ComputerCraft behaviours (verified against the 3.3.1 sources,
 * {@code core/lua/lib/LuaEnvironment.java} and {@code core/apis/PeripheralAPI.java}):
 * </p>
 *
 * <ul>
 * <li>{@code IComputerAccess#mount} refuses to mount over an existing path — {@code findFreeLocation} returns
 * {@code null} and nothing is mounted. Mounting every peripheral at {@code /ppp} therefore means only the first
 * peripheral attached to a computer gets its scripts.</li>
 * <li>{@code IComputerAccess#unmount} throws {@code RuntimeException("You didn't mount this location")} when the
 * path is not in <em>that</em> access object's mount list. The old {@code detach()} re-mounted and then unmounted
 * {@code /ppp}, which threw for every peripheral that had lost the mount race.</li>
 * </ul>
 *
 * <p>
 * The fix is one mount point per peripheral ({@code /ppp/<type>}, with a numeric suffix for a second peripheral of
 * the same type) and remembering the path that {@code mount} actually returned, so {@code unmount} can be given the
 * exact location it accepted. {@code fs.list("/ppp")} still shows one directory per attached peripheral, because
 * {@code FileSystem#list} appends the names of mounts whose parent directory matches.
 * </p>
 */
public final class PeripheralMounts {

    private static final Logger LOG = LogManager.getLogger("Peripherals++");

    /** How many peripherals of the same type can share a computer before the docs are skipped. */
    private static final int MAX_SAME_TYPE = 8;

    private PeripheralMounts() {}

    /**
     * Mounts this peripheral's docs for a computer.
     *
     * @param computer     the access the peripheral was attached with
     * @param peripheral   the peripheral whose scripts should be exposed
     * @param mountedPaths per-peripheral map of computer id to the mount path that was accepted
     * @return the mount path, or {@code null} if nothing could be mounted
     */
    public static String mount(IComputerAccess computer, IPeripheral peripheral, Map<Integer, String> mountedPaths) {
        if (computer == null || peripheral == null) return null;
        DynamicMount mount = new DynamicMount(peripheral);
        for (int attempt = 1; attempt <= MAX_SAME_TYPE; attempt++) {
            // attempt 1 is "/ppp/<type>", attempt 2 "/ppp/<type>_2", and so on.
            String desired = mount.mountPath(attempt == 1 ? "" : "_" + attempt);
            String mounted = computer.mount(desired, mount);
            if (mounted != null) {
                mountedPaths.put(computer.getID(), mounted);
                return mounted;
            }
        }
        LOG.error("Unable to expose the /ppp docs for " + peripheral.getType() + " to computer " + computer.getID());
        return null;
    }

    /**
     * Unmounts the docs this peripheral mounted for a computer, if it managed to mount any.
     *
     * @param computer     the access the peripheral is being detached from
     * @param mountedPaths per-peripheral map of computer id to the mount path that was accepted
     */
    public static void unmount(IComputerAccess computer, Map<Integer, String> mountedPaths) {
        if (computer == null) return;
        String mounted = mountedPaths.remove(computer.getID());
        if (mounted != null) computer.unmount(mounted);
    }
}
