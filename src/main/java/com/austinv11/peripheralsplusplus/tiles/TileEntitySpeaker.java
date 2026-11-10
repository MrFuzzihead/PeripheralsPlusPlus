package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;

import net.minecraft.nbt.NBTTagCompound;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.network.AudioPacket;
import com.austinv11.peripheralsplusplus.network.SynthPacket;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.thirdparty.gtranslate.Language;
import com.austinv11.peripheralsplusplus.utils.TranslateUtils;

import cpw.mods.fml.common.network.NetworkRegistry;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;

public class TileEntitySpeaker extends MountedTileEntity {

    public static String publicName = "speaker";
    private ITurtleAccess turtle;
    private TurtleSide side = null;
    private int id;
    /** Computer id -> the text that computer is waiting for a completion event about. */
    private final java.util.Map<Integer, String> pendingSpeech = new java.util.concurrent.ConcurrentHashMap<Integer, String>();
    private HashMap<IComputerAccess, Boolean> computers = new HashMap<IComputerAccess, Boolean>();
    private String lastMessage;
    /** Audio to send on the next tick: {lang, text, range}. A queue, not a single slot. */
    private final java.util.Queue<Object[]> pendingAudio = new java.util.ArrayDeque<Object[]>();

    public TileEntitySpeaker() {
        super();
    }

    public TileEntitySpeaker(ITurtleAccess turtle, TurtleSide side) {
        this();
        this.turtle = turtle;
        this.side = side;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
    }

    @Override
    public void updateEntity() {
        if (turtle != null) {
            this.setWorldObj(turtle.getWorld());
            this.xCoord = turtle.getPosition().posX;
            this.yCoord = turtle.getPosition().posY;
            this.zCoord = turtle.getPosition().posZ;
        }
        if (worldObj != null) id = worldObj.provider.dimensionId;
        Object[] audio;
        while ((audio = pendingAudio.poll()) != null) {
            PeripheralsPlusPlus.NETWORK.sendToAllAround(
                new AudioPacket((String) audio[0], (String) audio[1], xCoord, yCoord, zCoord, side),
                new NetworkRegistry.TargetPoint(id, xCoord, yCoord, zCoord, (Double) audio[2]));
        }
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "speak",
        "synthesize" /* text, [range, [voice, [pitch, [pitchRange, [pitchShift, [rate, [volume, [wait]]]]]]]] */ };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableSpeaker) throw new LuaException("Speakers have been disabled");
        if (method == 0) {
            if (!(arguments.length > 0) || !(arguments[0] instanceof String))
                throw new LuaException("Bad argument #1 (expected string)");
            if (arguments.length > 1 && !(arguments[1] instanceof Double))
                throw new LuaException("Bad argument #2 (expected number)");
            if (arguments.length > 2 && !(arguments[2] instanceof String))
                throw new LuaException("Bad argument #3 (expected string)");
            if (arguments.length > 3 && !(arguments[3] instanceof Boolean))
                throw new LuaException("Bad argument #4 (expected boolean");

            String lang = null;
            if (arguments.length > 2) if (TranslateUtils.isPrefix((String) arguments[2])) lang = (String) arguments[2];
            else {
                lang = TranslateUtils.getLangFromWord((String) arguments[2]);
                if (lang == null || lang.length() < 2)
                    throw new LuaException("Language " + (String) arguments[2] + " is unknown");
            }
            else lang = Language.ENGLISH;// TranslateUtils.detectLangPrefix((String) arguments[0]);
            // Clamp a per-call range to the configured one, like the chat box does; the old code let a script
            // broadcast to the whole dimension (CODE_REVIEW.md section 5.20).
            double range = clampRange(arguments.length > 1 ? (Double) arguments[1] : Config.speechRange);
            pendingAudio.add(new Object[] { lang, arguments[0], range });
            lastMessage = (String) arguments[0];
            if (computer != null) pendingSpeech.put(computer.getID(), (String) arguments[0]);
            if (arguments.length > 3 && (Boolean) arguments[3]) {
                // ComputerCraft 3.x: suspend until the speech finishes instead of blocking the computer thread
                final String spoken = lastMessage;
                final String language = lang;
                return new Object[] { MethodResult.pullEvent(event -> {
                    if (event.length > 0 && "speechComplete".equals(event[0])) return MethodResult.of(spoken, language);
                    // Not ours, keep waiting
                    return null;
                }) };
            }
            return new Object[] { lastMessage, lang };
        } else if (method == 1) {
            if (!(arguments.length > 0) || !(arguments[0] instanceof String))
                throw new LuaException("Bad argument #1 (expected string)");
            if (arguments.length > 1 && !(arguments[1] instanceof Double))
                throw new LuaException("Bad argument #2 (expected number)");
            if (arguments.length > 2 && !(arguments[2] instanceof String))
                throw new LuaException("Bad argument #3 (expected string)");
            if (arguments.length > 3 && !(arguments[3] instanceof Double))
                throw new LuaException("Bad argument #4 (expected number)");
            if (arguments.length > 4 && !(arguments[4] instanceof Double))
                throw new LuaException("Bad argument #5 (expected number)");
            if (arguments.length > 5 && !(arguments[5] instanceof Double))
                throw new LuaException("Bad argument #6 (expected number)");
            if (arguments.length > 6 && !(arguments[6] instanceof Double))
                throw new LuaException("Bad argument #7 (expected number)");
            if (arguments.length > 7 && !(arguments[7] instanceof Double))
                throw new LuaException("Bad argument #8 (expected number)");
            if (arguments.length > 8 && !(arguments[8] instanceof Boolean))
                throw new LuaException("Bad argument #9 (expected boolean");

            String text = (String) arguments[0];
            double range = clampRange(arguments.length > 1 ? (Double) arguments[1] : Config.speechRange);
            String voice = arguments.length > 2 ? (String) arguments[2] : "kevin16";
            Float pitch = arguments.length > 3 ? ((Double) arguments[3]).floatValue() : null;
            Float pitchRange = arguments.length > 4 ? ((Double) arguments[4]).floatValue() : null;
            Float pitchShift = arguments.length > 5 ? ((Double) arguments[5]).floatValue() : null;
            Float rate = arguments.length > 6 ? ((Double) arguments[6]).floatValue() : null;
            Float volume = arguments.length > 7 ? ((Double) arguments[7]).floatValue() : null;

            if (computer != null) pendingSpeech.put(computer.getID(), text);
            PeripheralsPlusPlus.NETWORK.sendToAllAround(
                new SynthPacket(text, voice, pitch, pitchRange, pitchShift, rate, volume, xCoord, yCoord, zCoord, side),
                new NetworkRegistry.TargetPoint(id, xCoord, yCoord, zCoord, range));

            if (arguments.length > 8 && (Boolean) arguments[8]) {
                // ComputerCraft 3.x: suspend until the synthesis finishes instead of blocking the computer thread
                return new Object[] { MethodResult.pullEvent(event -> {
                    if (event.length > 0 && "synthComplete".equals(event[0])) return MethodResult.of(text);
                    // Not ours, keep waiting
                    return null;
                }) };
            }
            return new Object[] { text };
        }
        return new Object[0];
    }

    /**
     * @param requested the range the script asked for
     * @return the range to use: never more than {@link Config#speechRange}, with a negative value meaning unlimited
     */
    private static double clampRange(double requested) {
        if (Config.speechRange < 0) return requested < 0 ? Double.MAX_VALUE : requested;
        if (requested < 0) return Config.speechRange;
        return Math.min(requested, Config.speechRange);
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        pendingSpeech.remove(computer.getID());
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    /**
     * Queues the completion event on the computers that asked for this text.
     *
     * <p>
     * This used to broadcast to every attached computer under a rate limit, so with two computers on one speaker a
     * single completion resumed both coroutines, and a second completion inside the same 20 ticks was dropped
     * entirely (CODE_REVIEW.md section 5.21). The waiting computer is tracked per message instead.
     * </p>
     *
     * @param text the text that finished
     * @param lang the language, or {@code null} for a synthesised (not translated) message
     */
    public void onSpeechCompletion(String text, String lang) {
        for (IComputerAccess computer : computers.keySet()) {
            String expected = pendingSpeech.remove(computer.getID());
            // A computer that never spoke, or spoke something else, is not the owner of this completion.
            if (expected != null && !expected.equals(text)) {
                pendingSpeech.put(computer.getID(), expected);
                continue;
            }
            if (lang != null) computer.queueEvent("speechComplete", new Object[] { text, lang });
            else computer.queueEvent("synthComplete", new Object[] { text });
        }
    }
}
