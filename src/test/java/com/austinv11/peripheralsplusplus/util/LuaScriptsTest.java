package com.austinv11.peripheralsplusplus.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.mount.DynamicMount;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAnalyzerBee;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna;
import com.austinv11.peripheralsplusplus.tiles.TileEntityChatBox;
import com.austinv11.peripheralsplusplus.tiles.TileEntityEnvironmentScanner;
import com.austinv11.peripheralsplusplus.tiles.TileEntityNoteBlock;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPeripheralContainer;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerSensor;
import com.austinv11.peripheralsplusplus.tiles.TileEntitySpeaker;
import com.austinv11.peripheralsplusplus.turtles.peripherals.PeripheralBarrel;
import com.austinv11.peripheralsplusplus.turtles.peripherals.PeripheralCompass;
import com.austinv11.peripheralsplusplus.turtles.peripherals.PeripheralFeeder;
import com.austinv11.peripheralsplusplus.turtles.peripherals.PeripheralXP;
import com.google.gson.Gson;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaState;
import dan200.computercraft.shadow.org.squiddev.cobalt.compiler.LoadState;

/**
 * Tests the bundled Lua programs: they compile, they are all listed in the manifests, and every peripheral method
 * they call actually exists.
 *
 * <p>
 * Until this class existed the scripts were only checked for "exists and is listed", so a syntax error or a call to a
 * method that had been renamed would have shipped and only shown up when a player ran the program.
 * </p>
 *
 * <p>
 * Compilation uses ComputerCraft's own Lua 5.1 compiler (Cobalt), so a script that compiles here compiles in game. The
 * programs are not <em>run</em>: they all call {@code peripheral}/{@code os}/{@code term}, which needs ComputerCraft's
 * environment.
 * </p>
 */
public class LuaScriptsTest {

    /** Directory name to the scripts it ships, straight from the bundled manifests. */
    private static List<String> scriptPaths() throws IOException {
        Gson gson = new Gson();
        List<String> paths = new ArrayList<String>();
        String[] dirs = gson.fromJson(read("index.json"), Dirs.class).dirs;
        for (String dir : dirs) {
            String[] files = gson.fromJson(read(dir + "/index.json"), Files.class).files;
            for (String file : files) {
                if (file == null || file.endsWith(".json")) continue;
                paths.add(dir + "/" + file);
            }
        }
        return paths;
    }

    private static String read(String resource) throws IOException {
        InputStream stream = LuaScriptsTest.class.getResourceAsStream(DynamicMount.RESOURCE_ROOT + resource);
        Assert.assertNotNull("missing bundled resource " + resource, stream);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            stream.close();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Manifests
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyListedScriptExists() throws Exception {
        List<String> paths = scriptPaths();
        Assert.assertFalse("the manifests list no scripts at all", paths.isEmpty());
        for (String path : paths) {
            InputStream stream = LuaScriptsTest.class.getResourceAsStream(DynamicMount.RESOURCE_ROOT + path);
            Assert.assertNotNull("manifest lists a missing script: " + path, stream);
            stream.close();
        }
    }

    @Test
    public void everyManifestListsOnlyRealScripts() throws Exception {
        // The reverse direction: a script dropped into a directory but not into index.json is invisible to the mount,
        // so it would ship without ever being reachable.
        Gson gson = new Gson();
        for (String dir : gson.fromJson(read("index.json"), Dirs.class).dirs) {
            String[] listed = gson.fromJson(read(dir + "/index.json"), Files.class).files;
            Assert.assertTrue(dir + " lists no scripts", listed != null && listed.length > 0);
            for (String file : listed) {
                if (file.endsWith(".json")) continue;
                InputStream stream = LuaScriptsTest.class
                    .getResourceAsStream(DynamicMount.RESOURCE_ROOT + dir + "/" + file);
                Assert.assertNotNull(dir + "/index.json lists a missing script " + file, stream);
                stream.close();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Compilation
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyScriptCompiles() throws Exception {
        List<String> paths = scriptPaths();
        Assert.assertTrue("expected the bundled docs to include several scripts", paths.size() >= 10);
        LuaState state = new LuaState();
        for (String path : paths) {
            byte[] source = read(path).getBytes("UTF-8");
            try {
                Object closure = LoadState
                    .load(state, new java.io.ByteArrayInputStream(source), "@" + path, state.globals());
                Assert.assertNotNull(path + " compiled to nothing", closure);
            } catch (Exception e) {
                Assert.fail(path + " does not compile: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lua surface: the methods the scripts call must exist
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyScriptCallNamesARealPeripheralMethod() throws Exception {
        for (String path : scriptPaths()) {
            String dir = path.substring(0, path.indexOf('/'));
            Set<String> methods = exposedMethods(dir);
            Assert.assertNotNull("no method table for the '" + dir + "' scripts", methods);

            String source = read(path);
            int index = source.indexOf("p.");
            while (index != -1) {
                int start = index + 2;
                int end = start;
                while (end < source.length()
                    && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) end++;
                if (end > start && end < source.length() && source.charAt(end) == '(') {
                    String name = source.substring(start, end);
                    Assert.assertTrue(
                        path + " calls p."
                            + name
                            + "(), which "
                            + dir
                            + " does not expose (it exposes "
                            + methods
                            + ")",
                        methods.contains(name));
                }
                index = source.indexOf("p.", Math.max(end, start));
            }
        }
    }

    /** The methods the peripheral behind each docs directory exposes. */
    private static Set<String> exposedMethods(String dir) {
        String[] names;
        switch (dir) {
            case "analyzers":
                names = new TileEntityAnalyzerBee().getMethodNames();
                break;
            case "antenna":
                names = new TileEntityAntenna().getMethodNames();
                break;
            case "barrel":
                names = new PeripheralBarrel(turtle(), null).getMethodNames();
                break;
            case "chatBox":
                names = new TileEntityChatBox().getMethodNames();
                break;
            case "compass":
                names = new PeripheralCompass(turtle()).getMethodNames();
                break;
            case "environmentScanner":
                names = new TileEntityEnvironmentScanner().getMethodNames();
                break;
            case "feeder":
                names = new PeripheralFeeder(turtle()).getMethodNames();
                break;
            case "noteBlock":
                names = new TileEntityNoteBlock().getMethodNames();
                break;
            case "oreDictionary":
                // The turtle upgrade; the block variant exposes no methods.
                names = new com.austinv11.peripheralsplusplus.tiles.TileEntityOreDictionary(turtle()).getMethodNames();
                break;
            case "peripheralContainer":
                names = new TileEntityPeripheralContainer().getMethodNames();
                break;
            case "playerSensor":
                names = new TileEntityPlayerSensor().getMethodNames();
                break;
            case "speaker":
                names = new TileEntitySpeaker().getMethodNames();
                break;
            case "xp":
                names = new PeripheralXP(turtle(), null).getMethodNames();
                break;
            default:
                return null;
        }
        return new HashSet<String>(Arrays.asList(names));
    }

    private static ITurtleAccess turtle() {
        ITurtleAccess turtle = Mockito.mock(ITurtleAccess.class);
        Mockito.when(turtle.getUpgradeNBTData(Mockito.any()))
            .thenReturn(new NBTTagCompound());
        // World.rand is a field, and PeripheralXP seeds its Random from it.
        World world = Mockito.mock(World.class);
        world.rand = new java.util.Random(1L);
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        return turtle;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Hygiene
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theScriptsUseUnixLineEndings() throws Exception {
        for (String path : scriptPaths()) Assert.assertFalse(path + " contains CRLF", read(path).contains("\r"));
    }

    @Test
    public void theScriptsHaveNoNotesToSelf() throws Exception {
        // These are example programs, so printing is expected; only flag notes-to-self.
        for (String path : scriptPaths()) {
            String source = read(path);
            for (String marker : new String[] { "TODO", "FIXME", "XXX" }) {
                Assert.assertFalse(path + " still contains '" + marker + "'", source.contains(marker));
            }
        }
    }

    @Test
    public void theScriptsDoNotDependOnAccidentalGlobals() throws Exception {
        // A script that assigns a name without `local` leaks it into the shared _ENV and can break the player's shell
        // (these programs are copied to disk and run from the shell). The names each file declares with `local` are
        // collected first, so reassigning a local is allowed and only genuine globals are flagged.
        for (String path : scriptPaths()) {
            String source = read(path);
            Set<String> declared = declaredLocals(source);
            for (String line : source.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("local ")) continue;
                String name = assignedName(trimmed);
                if (name == null) continue;
                Assert.assertTrue(
                    path + " assigns '" + name + "' without `local`, which leaks it into the shell's globals",
                    declared.contains(name));
            }
        }
    }

    /**
     * @param source Lua source
     * @return every name the file declares with {@code local}
     */
    private static Set<String> declaredLocals(String source) {
        Set<String> declared = new HashSet<String>();
        int index = source.indexOf("local ");
        while (index != -1) {
            int start = index + "local ".length();
            int end = start;
            while (end < source.length() && isIdentifierPart(source.charAt(end))) end++;
            if (end > start) {
                declared.add(source.substring(start, end));
                // Handle a comma-separated declaration list: `local a, b = 1, 2`.
                int cursor = end;
                while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
                if (cursor < source.length() && source.charAt(cursor) == ',') {
                    int next = cursor + 1;
                    while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
                    int nextEnd = next;
                    while (nextEnd < source.length() && isIdentifierPart(source.charAt(nextEnd))) nextEnd++;
                    if (nextEnd > next) declared.add(source.substring(next, nextEnd));
                }
            }
            index = source.indexOf("local ", index + 1);
        }
        return declared;
    }

    /**
     * @param trimmed a trimmed source line
     * @return the plain name being assigned on that line, or {@code null} when it is not a plain assignment
     */
    private static String assignedName(String trimmed) {
        int equals = trimmed.indexOf('=');
        if (equals <= 0 || equals == trimmed.length() - 1) return null;
        char before = trimmed.charAt(equals - 1);
        char after = trimmed.charAt(equals + 1);
        // Skip comparisons, concatenation and compound operators.
        if (after == '=' || before == '=' || before == '~' || before == '<' || before == '>' || before == '.')
            return null;
        String name = trimmed.substring(0, equals)
            .trim();
        if (name.isEmpty() || !isIdentifierPart(name.charAt(0))) return null;
        for (int i = 0; i < name.length(); i++) if (!isIdentifierPart(name.charAt(i))) return null;
        return name;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** The root manifest. */
    private static final class Dirs {

        private String[] dirs;
    }

    /** A per-directory manifest. */
    private static final class Files {

        private String[] files;
    }
}
