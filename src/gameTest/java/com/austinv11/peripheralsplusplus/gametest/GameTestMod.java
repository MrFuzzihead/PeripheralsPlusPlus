package com.austinv11.peripheralsplusplus.gametest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Test-only mod: a separate, non-shipped {@code @Mod} that asserts Peripherals++ works against the real game.
 *
 * <p>
 * It lives in the {@code gameTest} source set and is only put on the classpath by {@code ./gradlew gameTestServer}
 * (or {@code -PgameTest}), so it never reaches the published jar. Run it with:
 * </p>
 *
 * <pre>
 * mkdir -p run/server &amp;&amp; echo "eula=true" &gt; run/server/eula.txt
 * ./gradlew gameTestServer
 * </pre>
 *
 * <p>
 * The assertions run on the server thread at {@link FMLServerStartedEvent}, where the overworld exists and block
 * mutations are legal without a tick-loop hand-off. Tests that drive a real ComputerCraft computer have to run on a
 * worker thread instead, because the computer waits on CC's {@code SynchronousExecutor} tasks which only run from the
 * tick loop; {@link ServerGameTests} marks those cases.
 * </p>
 */
@Mod(
    modid = GameTestMod.MOD_ID,
    name = "Peripherals++ Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:PeripheralsPlusPlus")
public class GameTestMod {

    public static final String MOD_ID = "peripheralsplusplusgametest";
    static final String OK_MARKER = "PPP_GAMETEST_OK";
    static final String FAIL_MARKER = "PPP_GAMETEST_FAILED";

    /** How long to wait for a real ComputerCraft computer to boot and attach its peripherals. */
    private static final long COMPUTER_TIMEOUT_MILLIS = 60_000L;

    private boolean ran;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ComputerGameTests.registerProbe();
        // TickEvent lives on FML's bus in 1.7.10, not on the Forge bus.
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /**
     * Keeps the computer test's chunk loaded. Must be the START phase: the world unloads queued chunks inside
     * {@code world.tick()}, so restoring the chunk and its block afterwards would be a tick too late.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ComputerGameTests.keepAlive();
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (ran) return;
        ran = true;

        int failures;
        int checks;
        try {
            // Phase 1: everything that only needs a live world. Runs on the server thread, which is safe because
            // none of it waits for a tick.
            failures = ServerGameTests.runAll();
            checks = ServerGameTests.checkCount();
            WorldServer overworld = MinecraftServer.getServer()
                .worldServerForDimension(0);
            if (overworld != null) ComputerGameTests.prepare(overworld);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println(FAIL_MARKER + ": " + t);
            exit(1);
            return;
        }

        final int syncFailures = failures;
        final int syncChecks = checks;
        // Phase 2: the computer tests need the tick loop to run, so they wait on a worker thread and the server
        // thread returns to ticking.
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                int[] result;
                try {
                    result = ComputerGameTests.run(COMPUTER_TIMEOUT_MILLIS);
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.out.println(FAIL_MARKER + ": " + t);
                    exit(1);
                    return;
                }
                int totalFailures = syncFailures + result[0];
                int totalChecks = syncChecks + result[1];
                System.out
                    .println("[PPP-GAMETEST] " + (totalChecks - totalFailures) + "/" + totalChecks + " checks passed");
                if (totalFailures > 0) {
                    System.out.println(FAIL_MARKER + ": " + totalFailures + " assertion(s) failed, see above");
                    exit(1);
                } else {
                    System.out.println(OK_MARKER);
                    exit(0);
                }
            }
        }, "PPP-game-test");
        worker.setDaemon(true);
        worker.start();
    }

    private static void exit(int status) {
        FMLCommonHandler.instance()
            .exitJava(status, false);
    }
}
