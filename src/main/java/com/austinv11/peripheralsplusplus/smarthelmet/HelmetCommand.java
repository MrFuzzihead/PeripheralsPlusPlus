package com.austinv11.peripheralsplusplus.smarthelmet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A single drawing command for the smart helmet HUD.
 *
 * <p>
 * Instances travel over the network and are rebuilt on the client from a type name. That name used to be fed straight
 * into {@code Class.forName("…smarthelmet." + name).newInstance()}, so a malicious server could make the client
 * instantiate anything in this package, and every HUD command allocated via reflection
 * (CODE_REVIEW.md section 5.30). The types are a fixed registry of {@link Factory}s now; an unknown name is ignored.
 * </p>
 */
public abstract class HelmetCommand {

    /** Command name to factory, in registration order. */
    private static final Map<String, Factory> FACTORIES = new LinkedHashMap<String, Factory>();

    static {
        register("DrawStringCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new DrawStringCommand();
            }
        });
        register("DrawTextureCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new DrawTextureCommand();
            }
        });
        register("DrawRectangleCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new DrawRectangleCommand();
            }
        });
        register("DrawBackgroundCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new DrawBackgroundCommand();
            }
        });
        register("AddButtonCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new AddButtonCommand();
            }
        });
        register("AddTextFieldCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new AddTextFieldCommand();
            }
        });
        register("MessageCommand", new Factory() {

            @Override
            public HelmetCommand create() {
                return new MessageCommand();
            }
        });
    }

    /**
     * @param name    the wire name of the command
     * @param factory the factory that rebuilds it
     */
    public static void register(String name, Factory factory) {
        FACTORIES.put(name, factory);
    }

    /**
     * @return the registered wire names, for diagnostics
     */
    public static Map<String, Factory> factories() {
        return Collections.unmodifiableMap(FACTORIES);
    }

    /**
     * Rebuilds a command from its wire name.
     *
     * @param name the name written by {@link #getCommandName()}
     * @return a fresh instance, or {@code null} when the name is not one of ours
     */
    public static HelmetCommand getCommandFromName(String name) {
        if (name == null) return null;
        Factory factory = FACTORIES.get(name);
        return factory == null ? null : factory.create();
    }

    @SideOnly(Side.CLIENT)
    public abstract void call(Gui gui);

    public abstract String getCommandName();

    public abstract void readFromNBT(NBTTagCompound tagCompound);

    public abstract void writeToNBT(NBTTagCompound tagCompound);

    /**
     * Stores a string, tolerating an empty one.
     *
     * <p>
     * {@code NBTTagCompound#setString} throws {@code IllegalArgumentException("Empty string not allowed")} for
     * {@code ""}, which made re-serialising a command that had been read from a packet (where a missing key reads back
     * as {@code ""}) crash on the server thread. An absent key still reads back as {@code ""}.
     * </p>
     *
     * @param tag   the compound to write to
     * @param key   the key
     * @param value the value, may be {@code null} or empty
     */
    protected static void writeString(NBTTagCompound tag, String key, String value) {
        if (value != null && !value.isEmpty()) tag.setString(key, value);
    }

    /** Creates one command; keeps the registry free of reflection. */
    public interface Factory {

        HelmetCommand create();
    }
}
