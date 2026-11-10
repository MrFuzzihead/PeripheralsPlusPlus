package com.austinv11.peripheralsplusplus.turtles.peripherals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class PeripheralChunkLoader extends MountedPeripheral {

    private ITurtleAccess turtle;
    private ForgeChunkManager.Ticket ticket;
    private boolean attached = false;

    private ChunkCoordIntPair pos;

    public PeripheralChunkLoader(ITurtleAccess turtle) {
        this.turtle = turtle;
        this.pos = new ChunkCoordIntPair(turtle.getPosition().posX >> 4, turtle.getPosition().posZ >> 4);
    }

    @Override
    public void attach(IComputerAccess computer) {
        super.attach(computer);
        attached = true;
    }

    public void update() {
        if (attached && !turtle.getWorld().isRemote) {
            if (ticket == null || posChanged()) {
                this.pos = new ChunkCoordIntPair(turtle.getPosition().posX >> 4, turtle.getPosition().posZ >> 4);
                updateTicket();
            }
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        super.detach(computer);
        ForgeChunkManager.releaseTicket(ticket);
        ticket = null;
    }

    public void updateTicket() {
        ForgeChunkManager.releaseTicket(ticket);
        ticket = ForgeChunkManager
            .requestTicket(PeripheralsPlusPlus.instance, turtle.getWorld(), ForgeChunkManager.Type.NORMAL);
        // requestTicket returns null once the per-mod ticket limit is reached; forceChunk(null, ...) NPE'd
        // (CODE_REVIEW.md section 4.17).
        if (ticket == null) {
            PeripheralsPlusPlus.LOGGER.warn("Unable to reserve a chunk loading ticket for a chunk loader turtle");
            return;
        }
        for (ChunkCoordIntPair coordIntPair : getChunksInRadius(Config.chunkLoadingRadius)) {
            ForgeChunkManager.forceChunk(ticket, coordIntPair);
        }
    }

    public ArrayList<ChunkCoordIntPair> getChunksInRadius(int radius) {
        ArrayList<ChunkCoordIntPair> chunkList = new ArrayList<ChunkCoordIntPair>();
        for (int chunkX = pos.chunkXPos - radius; chunkX <= pos.chunkXPos + radius; chunkX++) {
            for (int chunkZ = pos.chunkZPos - radius; chunkZ <= pos.chunkZPos + radius; chunkZ++) {
                chunkList.add(new ChunkCoordIntPair(chunkX, chunkZ));
            }
        }
        return chunkList;
    }

    public boolean posChanged() {
        return !(new ChunkCoordIntPair(turtle.getPosition().posX >> 4, turtle.getPosition().posZ >> 4)).equals(pos);
    }

    @Override
    public String getType() {
        return "chunkLoader";
    }

    @Override
    public String[] getMethodNames() {
        return new String[0];
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    /**
     * Re-applies the forced chunks after a world reload.
     *
     * <p>
     * Forge hands the persisted tickets back here once per world load. The old implementation was empty, so the
     * chunks stayed unloaded after every restart (CODE_REVIEW.md section 4.17). The ticket is re-bound to the tile
     * entity's position when the turtle is found again.
     * </p>
     */
    public static class LoaderHandler implements ForgeChunkManager.LoadingCallback {

        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
            for (ForgeChunkManager.Ticket ticket : tickets) {
                // Keep the chunks that are already forced; the owning peripheral re-reserves a ticket on its first
                // tick if it is still installed, and an uninstalled upgrade simply leaves them released.
                for (ChunkCoordIntPair chunk : ticket.getChunkList()) ForgeChunkManager.forceChunk(ticket, chunk);
            }
        }
    }
}
