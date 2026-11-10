package com.austinv11.peripheralsplusplus.gametest;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.austinv11.peripheralsplusplus.mount.DynamicMount;
import com.austinv11.peripheralsplusplus.mount.PeripheralMounts;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

/**
 * Assertions that need a real, running ComputerCraft computer.
 *
 * <p>
 * These cannot run on the server thread: a CC computer only attaches its peripherals and services its
 * {@code SynchronousExecutor} tasks from the tick loop, so blocking the thread that fires
 * {@code FMLServerStartedEvent} would deadlock the test. {@link #prepare} places the blocks on the server thread and
 * captures the computer's {@link IComputerAccess} through {@link ProbePeripheral}; {@link #run} then waits (on a
 * worker thread) for the attach and asserts ComputerCraft's mount contract, which is what
 * {@code CODE_REVIEW.md} section 3.3 is built on.
 * </p>
 */
final class ComputerGameTests {

    /** The block that becomes the probe: a diamond block next to a computer. */
    static final Block PROBE_BLOCK = Blocks.diamond_block;

    /** The ComputerCraft computer block, resolved once. */
    private static Block computerBlock;

    /** How many times {@link #keepAlive} had to put the computer block back. */
    private static int restores;

    /** Where {@link #prepare} puts the computer. */
    private static int computerX;
    private static int computerY;
    private static int computerZ;
    private static WorldServer world;

    /** Set from the computer's thread when the probe is attached. */
    private static volatile IComputerAccess access;

    /**
     * Keeps the test chunk loaded. A dedicated server with no players unloads every chunk, and an unloaded chunk's
     * tile entities are removed from {@code loadedTileEntityList}, so the computer would never tick.
     */
    private static net.minecraftforge.common.ForgeChunkManager.Ticket ticket;

    private ComputerGameTests() {}

    /**
     * Registers the probe provider. Called once from the mod's {@code preInit}.
     */
    static void registerProbe() {
        dan200.computercraft.api.ComputerCraftAPI.registerPeripheralProvider(new IPeripheralProvider() {

            @Override
            public IPeripheral getPeripheral(net.minecraft.world.World world, int x, int y, int z, int side) {
                Block block = world.getBlock(x, y, z);
                if (block != PROBE_BLOCK) return null;
                probeLookups++;
                return new ProbePeripheral();
            }
        });
    }

    /** How many times ComputerCraft asked the probe provider about the probe block. */
    private static volatile int probeLookups;

    /**
     * Places the computer and the probe. Must run on the server thread.
     *
     * @param serverWorld the overworld
     */
    static void prepare(WorldServer serverWorld) {
        world = serverWorld;
        computerX = 16;
        computerY = 120;
        computerZ = 0;
        access = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++)
                serverWorld.setBlock(computerX + dx, computerY - 1, computerZ + dz, Blocks.stone);
        }
        computerBlock = Block.getBlockFromName("ComputerCraft:CC-Computer");
        serverWorld.setBlock(computerX, computerY, computerZ, computerBlock);
        // The probe sits directly next to the computer, so CC resolves it on one of the six sides.
        serverWorld.setBlock(computerX + 1, computerY, computerZ, PROBE_BLOCK);

        // Keep the chunk loaded so the computer's tile entity is in loadedTileEntityList and ticks. A FakePlayer
        // would also work but crashes the dedicated server's EntityTracker (no network handler).
        ticket = net.minecraftforge.common.ForgeChunkManager.requestTicket(
            com.austinv11.peripheralsplusplus.PeripheralsPlusPlus.instance,
            serverWorld,
            net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
        if (ticket != null) {
            net.minecraftforge.common.ForgeChunkManager
                .forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(computerX >> 4, computerZ >> 4));
        }
    }

    /**
     * Keeps the test computer alive. Called on the server thread at the start of every tick by {@link GameTestMod}.
     *
     * <p>
     * A dedicated server that has no players queues every chunk for unload, and the queued chunk's tile entities stay
     * in {@code loadedTileEntityList} for one more tick. A stale {@code TileComputer} whose block has been discarded
     * makes ComputerCraft's own {@code updateEntity} throw — {@code TileComputerBase#getBlock()} returns {@code null}
     * and {@code TileComputer#createComputer} dereferences it — which kills the server before the test can report. That
     * is what happened on CI (and locally on a fresh world), so the test now re-loads its chunk and, if the block
     * really is gone, puts it back before the tick loop looks at the tile entity.
     * </p>
     */
    static void keepAlive() {
        if (world == null || computerBlock == null) return;
        int chunkX = computerX >> 4;
        int chunkZ = computerZ >> 4;
        if (!world.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) {
            world.getChunkProvider()
                .loadChunk(chunkX, chunkZ);
        }
        if (world.getBlock(computerX, computerY, computerZ) != computerBlock) {
            restores++;
            world.setBlock(computerX, computerY, computerZ, computerBlock);
            world.setBlock(computerX + 1, computerY, computerZ, PROBE_BLOCK);
        }
    }

    /**
     * Waits for the computer to attach the probe, then runs the assertions. Must run on a worker thread.
     *
     * @param timeoutMillis how long to wait for the computer
     * @return an array of {failures, checks}; the caller adds them to its totals
     */
    static int[] run(long timeoutMillis) {
        int failures = 0;
        int checks = 0;

        long deadline = System.currentTimeMillis() + timeoutMillis;
        long nextTurnOnAttempt = 0L;
        while (access == null && System.currentTimeMillis() < deadline) {
            // ComputerCraft only calls IPeripheral#attach from PeripheralAPI#startup, i.e. once the computer has
            // booted. A computer placed with setBlock never starts on its own, so switch it on.
            if (System.currentTimeMillis() >= nextTurnOnAttempt) {
                turnOn();
                nextTurnOnAttempt = System.currentTimeMillis() + 500L;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return new int[] { failures + 1, checks + 1 };
            }
        }
        if (access == null) {
            System.out.println("[PPP-GAMETEST] FAIL the computer never attached the probe peripheral");
            diagnose();
            return new int[] { failures + 1, checks + 1 };
        }

        int[] result = assertMountContract(access);
        failures += result[0];
        checks += result[1];
        System.out.println(
            "[PPP-GAMETEST] info mounted against a real computer access: " + (result[1] - result[0]) + "/" + result[1]);
        if (restores > 0) System.out.println(
            "[PPP-GAMETEST] info the server discarded the computer's chunk " + restores
                + " time(s); the block was restored each time");

        cleanup();
        return new int[] { failures, checks };
    }

    /**
     * Pins the ComputerCraft behaviours the mod's mount code depends on (CODE_REVIEW.md section 3.3).
     *
     * @param computer the real computer access
     * @return an array of {failures, checks}
     */
    private static int[] assertMountContract(IComputerAccess computer) {
        int failures = 0;
        int checks = 0;

        // 1. A fresh path mounts and the API echoes it back.
        IMount chatBoxDocs = new DynamicMount("chatBox");
        String first = computer.mount("/ppp/chatBox", chatBoxDocs);
        checks++;
        if (!"/ppp/chatBox".equals(first)) {
            failures++;
            System.out.println("[PPP-GAMETEST] FAIL mounting a fresh path returned " + first);
        }

        // 2. Mounting over an existing path returns null instead of replacing it. This is exactly why the old code,
        // which mounted every peripheral at /ppp, left all but the first without docs.
        String second = computer.mount("/ppp/chatBox", chatBoxDocs);
        checks++;
        if (second != null) {
            failures++;
            System.out.println("[PPP-GAMETEST] FAIL mounting over an existing path returned " + second);
        }

        // 3. Unmounting a path this access never mounted throws. That is what made the old detach() crash for the
        // peripherals that lost the race.
        boolean threw = false;
        try {
            computer.unmount("/ppp/neverMounted");
        } catch (RuntimeException expected) {
            threw = true;
        }
        checks++;
        if (!threw) {
            failures++;
            System.out.println("[PPP-GAMETEST] FAIL unmounting an unowned path did not throw");
        }

        // 4. PeripheralMounts picks a free path per peripheral, so two different types both get docs.
        Map<Integer, String> paths = new java.util.HashMap<Integer, String>();
        String viaHelper = PeripheralMounts.mount(computer, new StubPeripheral("speaker"), paths);
        checks++;
        if (!"/ppp/speaker".equals(viaHelper)) {
            failures++;
            System.out.println("[PPP-GAMETEST] FAIL PeripheralMounts.mount returned " + viaHelper);
        }

        // 5. The helper only unmounts what it actually mounted, and does not throw when it mounted nothing.
        PeripheralMounts.unmount(computer, paths);
        Map<Integer, String> empty = new java.util.HashMap<Integer, String>();
        try {
            PeripheralMounts.unmount(computer, empty);
            checks++;
        } catch (RuntimeException e) {
            failures++;
            checks++;
            System.out.println("[PPP-GAMETEST] FAIL PeripheralMounts.unmount threw for an unmounted peripheral: " + e);
        }

        // 6. Clean up the first mount so a later run starts fresh.
        computer.unmount("/ppp/chatBox");

        return new int[] { failures, checks };
    }

    /** Switches the test computer on, so it boots Lua and attaches its peripherals. */
    private static void turnOn() {
        if (world == null) return;
        TileEntity tile = world.getTileEntity(computerX, computerY, computerZ);
        if (tile == null) return;
        try {
            Object serverComputer = tile.getClass()
                .getMethod("getComputer")
                .invoke(tile);
            if (serverComputer == null) return;
            serverComputer.getClass()
                .getMethod("turnOn")
                .invoke(serverComputer);
        } catch (Exception e) {
            // Reported by diagnose() if the probe never attaches.
        }
    }

    /** Logs why the computer did not come up, so a CI failure is actionable. */
    private static void diagnose() {
        if (world == null) {
            System.out.println("[PPP-GAMETEST] info no world");
            return;
        }
        Block block = world.getBlock(computerX, computerY, computerZ);
        TileEntity tile = world.getTileEntity(computerX, computerY, computerZ);
        System.out.println("[PPP-GAMETEST] info block at computer position: " + block);
        System.out.println(
            "[PPP-GAMETEST] info tile entity: " + (tile == null ? "none"
                : tile.getClass()
                    .getName()));
        System.out.println(
            "[PPP-GAMETEST] info chunk loaded: " + world.getChunkProvider()
                .chunkExists(computerX >> 4, computerZ >> 4));
        System.out.println("[PPP-GAMETEST] info chunk ticket: " + ticket);
        System.out.println("[PPP-GAMETEST] info tile entity loaded: " + world.loadedTileEntityList.contains(tile));
        System.out.println("[PPP-GAMETEST] info probe provider hits: " + probeLookups);
        if (tile != null) {
            try {
                Object computer = tile.getClass()
                    .getMethod("getComputer")
                    .invoke(tile);
                if (computer != null) {
                    System.out.println(
                        "[PPP-GAMETEST] info computer isOn: " + computer.getClass()
                            .getMethod("isOn")
                            .invoke(computer));
                    for (int side = 0; side < 6; side++) {
                        System.out.println(
                            "[PPP-GAMETEST] info computer peripheral on side " + side
                                + ": "
                                + computer.getClass()
                                    .getMethod("getPeripheral", int.class)
                                    .invoke(computer, side));
                    }
                }
            } catch (Exception e) {
                System.out.println("[PPP-GAMETEST] info computer probe failed: " + e);
            }
        }
        if (tile != null) {
            try {
                Object computer = tile.getClass()
                    .getMethod("getComputer")
                    .invoke(tile);
                System.out.println("[PPP-GAMETEST] info getComputer(): " + computer);
            } catch (Exception e) {
                System.out.println("[PPP-GAMETEST] info getComputer() unavailable: " + e);
            }
        }
    }

    /**
     * Releases the chunk ticket. The blocks are deliberately left in place: removing the computer from a worker
     * thread races its own tile entity tick (CC's {@code TileComputerBase#getBlock()} returns null once the block is
     * gone and the next tick NPEs), and {@link #prepare} overwrites the same coordinates on the next run, so leaving
     * them is idempotent.
     */
    private static void cleanup() {
        if (ticket != null) {
            net.minecraftforge.common.ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
    }

    /**
     * Stands in for a Peripherals++ peripheral: its only job is to hand the computer's {@link IComputerAccess} to the
     * test.
     */
    private static final class ProbePeripheral implements IPeripheral {

        private static final String[] METHODS = new String[0];

        @Override
        public String getType() {
            return "pppGameTestProbe";
        }

        @Override
        public String[] getMethodNames() {
            return METHODS;
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, dan200.computercraft.api.lua.ILuaContext context,
            int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public void attach(IComputerAccess computer) {
            access = computer;
        }

        @Override
        public void detach(IComputerAccess computer) {
            access = null;
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    /** A peripheral with no behaviour, used to exercise the mount helper. */
    private static final class StubPeripheral implements IPeripheral {

        private final String type;

        private StubPeripheral(String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String[] getMethodNames() {
            return new String[0];
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, dan200.computercraft.api.lua.ILuaContext context,
            int method, Object[] arguments) {
            return new Object[0];
        }

        @Override
        public void attach(IComputerAccess computer) {}

        @Override
        public void detach(IComputerAccess computer) {}

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }
}
