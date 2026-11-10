package com.austinv11.peripheralsplusplus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.austinv11.peripheralsplusplus.reference.Reference;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Development/CI only. When the {@code peripheralsplusplus.smokeTest} system property is set, the game logs a
 * machine-readable marker after FML has finished loading and then exits. This lets a CI job boot the real dedicated
 * server (or the client under Xvfb) without needing an operator to type {@code stop}.
 *
 * <p>
 * The hook is a no-op in normal gameplay: the property is only set by the {@code -PsmokeTest} Gradle property (see
 * {@code addon.gradle.kts}).
 * </p>
 */
public final class SmokeTestHook {

    public static final String ENABLE_PROPERTY = "peripheralsplusplus.smokeTest";
    public static final String DELAY_PROPERTY = "peripheralsplusplus.smokeTest.delay";
    public static final String SUCCESS_MARKER = "PERIPHERALSPLUSPLUS_SMOKE_TEST_OK";

    /**
     * Fired from {@link WorldEvent.Load} once the overworld exists. On a dedicated server this is the earliest
     * point at which the world is real: exiting at load-complete alone races the spawn-chunk generation and the
     * server never prints {@code Done}.
     */
    private static final CountDownLatch WORLD_LOADED = new CountDownLatch(1);

    private static volatile boolean scheduled;

    private SmokeTestHook() {}

    /**
     * Called from {@link PeripheralsPlusPlus#loadComplete}. Schedules the exit on a daemon thread.
     */
    public static void scheduleExitIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        synchronized (SmokeTestHook.class) {
            if (scheduled) return;
            scheduled = true;
        }

        if (isServerSide()) MinecraftForge.EVENT_BUS.register(new WorldListener());

        final long delay = Long.getLong(DELAY_PROPERTY, 2000L);
        final boolean serverSide = isServerSide();
        final Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    if (serverSide) {
                        // A generous window: a missing world should fail the CI job via its own timeout rather
                        // than exiting early and masking the real problem.
                        WORLD_LOADED.await(10, TimeUnit.MINUTES);
                    }
                    Thread.sleep(Math.max(0L, delay));
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }

                System.out.println(SUCCESS_MARKER + ": FML load complete, shutting down");
                FMLCommonHandler.instance()
                    .exitJava(0, false);
            }
        }, Reference.MOD_NAME + " smoke test");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * @return true when one of the CI boot checks is running
     */
    public static boolean isEnabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static boolean isServerSide() {
        try {
            return FMLCommonHandler.instance()
                .getSide() == Side.SERVER;
        } catch (Throwable t) {
            // Outside FML (unit tests, early class loading): treat as a client so no latch wait is introduced.
            return false;
        }
    }

    /** Releases {@link #WORLD_LOADED} the first time a world is loaded. */
    public static final class WorldListener {

        private WorldListener() {}

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) {
            WORLD_LOADED.countDown();
        }
    }
}
