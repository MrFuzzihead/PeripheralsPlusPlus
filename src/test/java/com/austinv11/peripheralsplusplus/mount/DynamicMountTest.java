package com.austinv11.peripheralsplusplus.mount;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Covers the bundled Lua docs mount.
 *
 * <p>
 * These tests run against the real resources on the test classpath, which is the point: the mount silently returns an
 * empty directory when a manifest is missing, and that failure mode is otherwise only visible in game.
 * </p>
 *
 * <p>
 * The <em>layout</em> assertions matter as much as the content ones. The mount point carries the peripheral type
 * ({@code /ppp/chatBox}) and the scripts sit directly at its root, so the documented paths stay
 * {@code /ppp/<type>/<script>.lua}. An earlier revision of this fix mounted at {@code /ppp/<type>} but also exposed
 * the type as a directory inside the mount, which silently moved every bundled program to
 * {@code /ppp/chatBox/chatBox/talk.lua}.
 * </p>
 */
public class DynamicMountTest {

    private static final String[] DIRECTORIES = { "chatBox", "environmentScanner", "analyzers", "oreDictionary",
        "barrel", "compass", "feeder", "xp", "speaker", "noteBlock", "peripheralContainer", "antenna", "playerSensor" };

    private static String readAll(InputStream stream) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) builder.append(buffer, 0, read);
        return builder.toString();
    }

    private static List<String> list(DynamicMount mount) throws IOException {
        List<String> contents = new ArrayList<String>();
        mount.list("", contents);
        return contents;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Mount point and layout
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void mountPathIsPerPeripheralType() {
        // A shared "/ppp" mount is what made only the first peripheral's scripts visible (CODE_REVIEW.md 3.3).
        DynamicMount chatBox = new DynamicMount("chatBox");
        DynamicMount speaker = new DynamicMount("speaker");
        Assert.assertEquals("/ppp/chatBox", chatBox.mountPath(""));
        Assert.assertEquals("/ppp/speaker", speaker.mountPath(""));
        Assert.assertNotEquals(chatBox.mountPath(""), speaker.mountPath(""));
        // A second peripheral of the same type must not collide with the first.
        Assert.assertEquals("/ppp/chatBox_2", chatBox.mountPath("_2"));
    }

    @Test
    public void theMountedLayoutIsUnchangedForPlayers() {
        // Mount point + file == the documented path, which is what it was when every peripheral shared /ppp.
        DynamicMount mount = new DynamicMount("chatBox");
        Assert.assertEquals("/ppp/chatBox", mount.mountPath(""));
        Assert.assertEquals("/ppp/chatBox/talk.lua", mount.mountPath("") + "/talk.lua");
    }

    @Test
    public void directoryIsExposed() {
        Assert.assertEquals("chatBox", new DynamicMount("chatBox").getDirectory());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Structure
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theMountRootIsItsOnlyDirectory() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        Assert.assertTrue(mount.exists(""));
        Assert.assertTrue(mount.exists("/"));
        Assert.assertTrue(mount.isDirectory(""));
        // The mount point already carries the type, so it must not be repeated inside the mount.
        Assert.assertFalse("the type must not be repeated inside the mount", mount.exists("chatBox"));
        Assert.assertFalse(mount.isDirectory("chatBox"));
    }

    @Test
    public void theScriptsAreDirectlyAtTheMountRoot() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        List<String> files = list(mount);
        Assert.assertTrue(files.contains("talk.lua"));
        Assert.assertTrue(mount.exists("talk.lua"));
        Assert.assertFalse(mount.isDirectory("talk.lua"));
        Assert.assertTrue(mount.getSize("talk.lua") > 0);
    }

    @Test
    public void foreignDirectoriesAreInvisible() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        Assert.assertFalse(mount.exists("speaker"));
        Assert.assertFalse(mount.exists("../speaker/anything.lua"));
        Assert.assertFalse(mount.exists("notARealPeripheral"));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Listings
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void listReturnsOnlyOwnFilesAndHidesTheManifest() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        List<String> files = list(mount);
        Assert.assertFalse("the manifest is internal", files.contains("index.json"));
        Assert.assertFalse(files.isEmpty());
        // This mount has no subdirectories.
        List<String> nested = new ArrayList<String>();
        mount.list("chatBox", nested);
        Assert.assertTrue(nested.isEmpty());
    }

    @Test
    public void listedFilesAreReadable() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        List<String> files = list(mount);
        Assert.assertFalse(files.isEmpty());
        for (String file : files) {
            Assert.assertTrue(file, mount.exists(file));
            Assert.assertFalse(file, mount.isDirectory(file));
            Assert.assertTrue(file, mount.getSize(file) > 0);
            InputStream stream = mount.openForRead(file);
            Assert.assertNotNull(stream);
            try {
                Assert.assertFalse(file, readAll(stream).isEmpty());
            } finally {
                stream.close();
            }
        }
    }

    @Test
    public void unreadablePathsThrow() {
        DynamicMount mount = new DynamicMount("chatBox");
        assertUnreadable(mount, "");
        assertUnreadable(mount, "chatBox");
        assertUnreadable(mount, "index.json");
        assertUnreadable(mount, "chatBox/talk.lua");
        assertUnreadable(mount, "speaker/anything.lua");
        assertUnreadable(mount, "../chatBox/anything.lua");
    }

    @Test
    public void unknownDirectoryIsEmptyNotBroken() throws IOException {
        DynamicMount mount = new DynamicMount("doesNotExist");
        Assert.assertFalse(mount.exists(""));
        Assert.assertFalse(mount.isDirectory(""));
        Assert.assertTrue(list(mount).isEmpty());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Every bundled directory
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyManifestDirectoryResolves() throws IOException {
        // index.json lists the doc directories that ship with the mod; every one must be mountable and must expose
        // its scripts at the mount root.
        for (String directory : DIRECTORIES) {
            DynamicMount mount = new DynamicMount(directory);
            List<String> files = list(mount);
            Assert.assertFalse("no scripts found for " + directory, files.isEmpty());
            for (String file : files) Assert.assertTrue(directory + "/" + file, mount.exists(file));
        }
    }

    @Test
    public void everyManifestDirectoryHasADistinctMountPoint() {
        java.util.Set<String> mountPoints = new java.util.HashSet<String>();
        for (String directory : DIRECTORIES) {
            Assert.assertTrue(
                "two peripherals would collide on " + directory,
                mountPoints.add(new DynamicMount(directory).mountPath("")));
        }
    }

    private static void assertUnreadable(DynamicMount mount, String path) {
        try {
            mount.openForRead(path);
            Assert.fail("expected an IOException for " + path);
        } catch (IOException expected) {
            // expected
        }
    }
}
