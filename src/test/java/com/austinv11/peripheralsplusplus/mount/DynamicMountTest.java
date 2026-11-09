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
 * These tests run against the real resources on the test classpath, which is the whole point: the mount silently
 * returns an empty directory when a manifest is missing, and that failure mode is otherwise only visible in game.
 * </p>
 */
public class DynamicMountTest {

    private static String readAll(InputStream stream) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) builder.append(buffer, 0, read);
        return builder.toString();
    }

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
    public void directoryIsExposed() {
        Assert.assertEquals("chatBox", new DynamicMount("chatBox").getDirectory());
    }

    @Test
    public void rootAndOwnDirectoryExist() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        Assert.assertTrue(mount.exists(""));
        Assert.assertTrue(mount.exists("/"));
        Assert.assertTrue(mount.exists("chatBox"));
        Assert.assertTrue(mount.exists("/chatBox/"));
        Assert.assertTrue(mount.isDirectory(""));
        Assert.assertTrue(mount.isDirectory("chatBox"));
    }

    @Test
    public void foreignDirectoriesAreInvisible() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        Assert.assertFalse(mount.exists("speaker"));
        Assert.assertFalse(mount.exists("chatBox/../../speaker"));
        Assert.assertFalse(mount.exists("notARealPeripheral"));
    }

    @Test
    public void listReturnsOnlyOwnFilesAndHidesTheManifest() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        List<String> root = new ArrayList<String>();
        mount.list("", root);
        Assert.assertEquals(1, root.size());
        Assert.assertEquals("chatBox", root.get(0));

        List<String> files = new ArrayList<String>();
        mount.list("chatBox", files);
        Assert.assertFalse("the manifest is internal", files.contains("index.json"));
        Assert.assertFalse(files.isEmpty());
    }

    @Test
    public void listedFilesAreReadable() throws IOException {
        DynamicMount mount = new DynamicMount("chatBox");
        List<String> files = new ArrayList<String>();
        mount.list("chatBox", files);
        Assert.assertFalse(files.isEmpty());
        for (String file : files) {
            String path = "chatBox/" + file;
            Assert.assertTrue(path, mount.exists(path));
            Assert.assertFalse(path, mount.isDirectory(path));
            Assert.assertTrue(path, mount.getSize(path) > 0);
            InputStream stream = mount.openForRead(path);
            Assert.assertNotNull(stream);
            try {
                Assert.assertFalse(path, readAll(stream).isEmpty());
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
        assertUnreadable(mount, "chatBox/index.json");
        assertUnreadable(mount, "speaker/anything.lua");
        assertUnreadable(mount, "../chatBox/anything.lua");
    }

    @Test
    public void unknownDirectoryIsEmptyNotBroken() throws IOException {
        DynamicMount mount = new DynamicMount("doesNotExist");
        Assert.assertFalse(mount.exists(""));
        Assert.assertFalse(mount.isDirectory("doesNotExist"));
        List<String> root = new ArrayList<String>();
        mount.list("", root);
        Assert.assertTrue(root.isEmpty());
    }

    @Test
    public void everyManifestDirectoryResolves() throws IOException {
        // index.json lists the doc directories that ship with the mod; every one must be mountable, or players get
        // a /ppp folder that lists a directory it cannot open.
        DynamicMount any = new DynamicMount("chatBox");
        Assert.assertTrue(any.exists("chatBox"));
        String[] directories = { "chatBox", "environmentScanner", "analyzers", "oreDictionary", "barrel", "compass",
            "feeder", "xp", "speaker", "noteBlock", "peripheralContainer", "antenna", "playerSensor" };
        for (String directory : directories) {
            DynamicMount mount = new DynamicMount(directory);
            List<String> files = new ArrayList<String>();
            mount.list(directory, files);
            Assert.assertFalse("no scripts found for " + directory, files.isEmpty());
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
