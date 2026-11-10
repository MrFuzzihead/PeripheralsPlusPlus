package com.austinv11.peripheralsplusplus.mount;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAnalyzer;
import com.austinv11.peripheralsplusplus.utils.JSONFileList;
import com.austinv11.peripheralsplusplus.utils.JSONIndex;
import com.google.gson.Gson;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Read-only mount exposing the example Lua programs for a peripheral under {@code /ppp}.
 *
 * <p>
 * The programs ship inside this mod's jar at {@value #RESOURCE_ROOT}, so they are always present, always match the
 * installed version and never need a network connection. Only the directory belonging to the mounted peripheral is
 * visible; the listing comes from the {@code index.json} manifests stored next to the scripts.
 * </p>
 */
public class DynamicMount implements IMount {

    public static final String DIRECTORY = "/ppp";
    /** Classpath root the programs are packaged under. */
    public static final String RESOURCE_ROOT = "/assets/peripheralsplusplus/lua/";
    private static final String MANIFEST = "index.json";

    /** Directory name -> the scripts it offers, built once from the bundled manifests. */
    private static Map<String, List<String>> directories;
    /** The directory this mount exposes, i.e. the peripheral's type. */
    private final String directory;

    public DynamicMount(IPeripheral peripheral) {
        this(getSafeType(peripheral));
    }

    /**
     * @param directory the docs directory to expose, i.e. one of the entries in the Lua {@code index.json}
     */
    public DynamicMount(String directory) {
        this.directory = directory;
    }

    /**
     * The mount location this instance should be mounted at.
     *
     * <p>
     * Each peripheral gets its own path below {@link #DIRECTORY}. Mounting everything at {@code /ppp} means
     * ComputerCraft's {@code findFreeLocation} returns {@code null} for the second peripheral (it refuses to mount
     * over an existing path), so only the first peripheral's scripts would ever be visible. See CODE_REVIEW.md
     * section 3.3.
     * </p>
     *
     * @param suffix disambiguator for a second peripheral of the same type, or empty for the first
     * @return an absolute mount path such as {@code /ppp/chatBox}
     */
    public String mountPath(String suffix) {
        return DIRECTORY + "/" + directory + suffix;
    }

    /**
     * Reads the {@code index.json} manifests bundled with the scripts to learn which files each directory offers.
     * A manifest that is missing or unparsable simply contributes no directories, so a damaged jar can never make
     * mounting throw.
     */
    private static synchronized Map<String, List<String>> getDirectories() {
        if (directories != null) return directories;
        Map<String, List<String>> found = new HashMap<String, List<String>>();
        Gson gson = new Gson();
        try {
            JSONIndex index = gson.fromJson(readResource(MANIFEST), JSONIndex.class);
            if (index != null && index.dirs != null) {
                for (String dir : index.dirs) {
                    if (dir == null) continue;
                    JSONFileList manifest = gson.fromJson(readResource(dir + "/" + MANIFEST), JSONFileList.class);
                    if (manifest == null || manifest.files == null) continue;
                    List<String> files = new ArrayList<String>();
                    for (String file : manifest.files) {
                        // The manifest lists itself, which is only useful when comparing versions on disk; players
                        // should not see it in the mount.
                        if (file != null && !MANIFEST.equals(file)) files.add(file);
                    }
                    found.put(dir, Collections.unmodifiableList(files));
                }
            }
        } catch (IOException e) {
            PeripheralsPlusPlus.LOGGER
                .error("Unable to read the bundled mount manifests, /ppp will be empty: " + e.getMessage());
        }
        directories = Collections.unmodifiableMap(found);
        return directories;
    }

    private static byte[] readBytes(String resource) throws IOException {
        InputStream stream = DynamicMount.class.getResourceAsStream(RESOURCE_ROOT + resource);
        if (stream == null) throw new IOException("Missing bundled resource " + RESOURCE_ROOT + resource);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static String readResource(String resource) throws IOException {
        return new String(readBytes(resource), StandardCharsets.UTF_8);
    }

    /** Strips the separators mounts are addressed with and normalises them to {@code /}. */
    private static String normalize(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    @Override
    public boolean exists(String path) throws IOException {
        String normalized = normalize(path);
        List<String> files = getDirectories().get(directory);
        if (files == null) return false;
        return normalized.isEmpty() || files.contains(normalized);
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        // The mount root is the only directory: it holds the peripheral's scripts directly.
        return normalize(path).isEmpty() && getDirectories().containsKey(directory);
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {
        List<String> files = getDirectories().get(directory);
        if (files == null || !normalize(path).isEmpty()) return;
        contents.addAll(files);
    }

    @Override
    public long getSize(String path) throws IOException {
        return readBytesFor(path).length;
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        return new ByteArrayInputStream(readBytesFor(path));
    }

    private byte[] readBytesFor(String path) throws IOException {
        String normalized = normalize(path);
        List<String> files = getDirectories().get(directory);
        if (normalized.isEmpty() || files == null || !files.contains(normalized))
            throw new IOException("No such file " + path);
        return readBytes(directory + "/" + normalized);
    }

    private static String getSafeType(IPeripheral peripheral) {
        // The analyzers share one family of scripts but are registered per species.
        return peripheral instanceof TileEntityAnalyzer ? "analyzers" : peripheral.getType();
    }

    /**
     * @return the docs directory this mount exposes
     */
    public String getDirectory() {
        return directory;
    }
}
