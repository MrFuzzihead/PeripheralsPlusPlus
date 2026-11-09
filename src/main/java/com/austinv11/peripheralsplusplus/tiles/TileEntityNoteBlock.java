package com.austinv11.peripheralsplusplus.tiles;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.minecraft.utils.Location;
import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.network.ParticlePacket;
import com.austinv11.peripheralsplusplus.reference.Config;

import cpw.mods.fml.common.network.NetworkRegistry;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityNoteBlock extends MountedTileEntity {

    private static final int MAX_NOTES = 24;
    private static final int MAX_NOTES_TICK = 5;
    public static final String publicName = "noteBlock";
    private ITurtleAccess turtle;
    private Location location;
    private int tick = 0;
    private final String[] instruments = { "harp", "bd", "snare", "hat", "bassattack" };

    public TileEntityNoteBlock() {
        super();
        location = new Location(xCoord, yCoord, zCoord, getWorldObj());
    }

    public TileEntityNoteBlock(ITurtleAccess turtle) {
        location = new Location(
            turtle.getPosition().posX,
            turtle.getPosition().posY,
            turtle.getPosition().posZ,
            turtle.getWorld());
        this.xCoord = (int) location.getX();
        this.yCoord = (int) location.getY();
        this.zCoord = (int) location.getZ();
        this.setWorldObj(location.getWorld());
        this.turtle = turtle;
    }

    @Override
    public String getType() {
        return publicName;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "playNote", "playSound" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableNoteBlock) {
            throw new LuaException("Iron Note Blocks have been disabled");
        }
        switch (method) {
            case 0:
                checkPlayNote(arguments);
                break;
            case 1:
                checkPlaySound(arguments);
                break;
        }
        return new Object[0];
    }

    private void checkPlaySound(Object[] rawArguments) throws LuaException {
        LuaArgs arguments = LuaArgs.of(rawArguments)
            .atLeast(1);
        String sound = arguments.string(0);
        double volume = arguments.number(1, 1.0d);
        // This used to read argument #2 (the volume) as the pitch.
        double pitch = arguments.number(2, 1.0d);
        if (location.getWorld() == null) return;
        location.getWorld()
            .playSoundEffect(
                location.getX() + 0.5D,
                location.getY() + 0.5D,
                location.getZ() + 0.5D,
                sound,
                (float) volume,
                (float) pitch);
    }

    private void checkPlayNote(Object[] rawArguments) throws LuaException {
        LuaArgs arguments = LuaArgs.of(rawArguments)
            .atLeast(2);

        // Check instrument
        int instrument = arguments.integer(0);
        if (instrument < 0 || instrument >= instruments.length) {
            throw new LuaException("Invalid instrument");
        }

        // Check note
        int note = arguments.integer(1);
        if (note < 0 || note > MAX_NOTES) {
            throw new LuaException("Invalid note");
        }

        // Check notes per tick
        if (++tick > MAX_NOTES_TICK) {
            throw new LuaException("More than " + MAX_NOTES_TICK + " note plays called per tick");
        }

        // Check position
        ChunkCoordinates position = location.toChunkCoordinates();
        World world = location.getWorld();
        if (position == null || world == null) {
            return;
        }
        playNote(world, location.getX(), location.getY(), location.getZ(), instrument, note);
        PeripheralsPlusPlus.NETWORK.sendToAllAround(
            new ParticlePacket(
                "note",
                location.getX() + 0.5,
                location.getY() + 1.2,
                location.getZ() + 0.5,
                note / 24D,
                0,
                0),
            new NetworkRegistry.TargetPoint(
                world.provider.dimensionId,
                location.getX(),
                location.getY(),
                location.getZ(),
                Config.noteBlockRange));
    }

    private void playNote(World world, double x, double y, double z, int instrument, int note) {
        float inflate = (float) Math.pow(2D, (note - 12) / 12D);
        String instrumentName = instruments[instrument];
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "note." + instrumentName, 3F, inflate);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        tick = 0;
    }

    public void updateEntity(boolean turtle) {
        if (turtle) {
            location = new Location(
                this.turtle.getPosition().posX,
                this.turtle.getPosition().posY,
                this.turtle.getPosition().posZ,
                this.turtle.getWorld());
            this.xCoord = (int) location.getX();
            this.yCoord = (int) location.getY();
            this.zCoord = (int) location.getZ();
        }
        updateEntity();
    }

    @Override
    public void attach(IComputerAccess computer) {
        super.attach(computer);
        location = new Location(xCoord, yCoord, zCoord, getWorldObj());
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

}
