package com.austinv11.peripheralsplusplus.gametest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

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

    private boolean ran;

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (ran) return;
        ran = true;

        int failures;
        try {
            failures = ServerGameTests.runAll();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println(FAIL_MARKER + ": " + t);
            exit(1);
            return;
        }

        if (failures > 0) {
            System.out.println(FAIL_MARKER + ": " + failures + " assertion(s) failed, see the lines above");
            exit(1);
        } else {
            System.out.println(OK_MARKER);
            exit(0);
        }
    }

    private static void exit(int status) {
        FMLCommonHandler.instance()
            .exitJava(status, false);
    }
}
