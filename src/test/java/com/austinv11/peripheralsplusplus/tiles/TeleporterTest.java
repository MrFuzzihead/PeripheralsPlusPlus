package com.austinv11.peripheralsplusplus.tiles;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers link bookkeeping. The base teleporter holds one link, and the NBT round-trip used to append to the existing
 * list on every chunk reload while {@code addLink} threw the new link away whenever the list was already full
 * (CODE_REVIEW.md section 4.12).
 */
public class TeleporterTest {

    @BeforeClass
    public static void registerTileEntity() {
        // TileEntity#writeToNBT writes the registry id, which needs a mapping. The game normally gets this from
        // CommonProxy#registerTileEntities.
        TileEntity.addMapping(TileEntityTeleporter.class, "pppTeleporterTest");
        TileEntity.addMapping(TileEntityTeleporterT2.class, "pppTeleporterT2Test");
    }

    private static TileEntityTeleporter teleporter() {
        return new TileEntityTeleporter();
    }

    @Test
    public void addLinkKeepsTheNewestLink() {
        TileEntityTeleporter te = teleporter();
        Assert.assertEquals(1, te.addLink(0, new ChunkCoordinates(1, 2, 3)));
        // The old `while (size > max) pop()` popped the link that had just been added, so this was a silent no-op.
        Assert.assertEquals(1, te.addLink(0, new ChunkCoordinates(4, 5, 6)));
        Assert.assertEquals(1, te.links.size());
        Assert.assertEquals(4, te.links.get(0).link.posX);
        Assert.assertEquals(6, te.links.get(0).link.posZ);
    }

    @Test
    public void linkDataSurvivesAnNbtRoundTrip() {
        TileEntityTeleporter te = teleporter();
        te.addLink(-1, new ChunkCoordinates(10, 20, 30));

        NBTTagCompound tag = new NBTTagCompound();
        te.writeToNBT(tag);

        TileEntityTeleporter loaded = teleporter();
        loaded.readFromNBT(tag);

        Assert.assertEquals(1, loaded.links.size());
        Assert.assertEquals(-1, loaded.links.get(0).linkDim);
        Assert.assertEquals(10, loaded.links.get(0).link.posX);
        Assert.assertEquals(20, loaded.links.get(0).link.posY);
        Assert.assertEquals(30, loaded.links.get(0).link.posZ);
    }

    @Test
    public void reloadingDoesNotDuplicateLinks() {
        TileEntityTeleporter te = teleporter();
        te.addLink(0, new ChunkCoordinates(1, 2, 3));
        NBTTagCompound tag = new NBTTagCompound();
        te.writeToNBT(tag);

        // Chunk reloads call readFromNBT on the same instance.
        te.readFromNBT(tag);
        te.readFromNBT(tag);
        te.readFromNBT(tag);

        Assert.assertEquals(1, te.links.size());
    }

    @Test
    public void getLinksIsOneBased() throws LuaException, InterruptedException {
        TileEntityTeleporter te = teleporter();
        te.addLink(7, new ChunkCoordinates(1, 2, 3));

        Object[] result = te.callMethod(null, null, 2, new Object[0]);
        Assert.assertEquals(1, result.length);
        @SuppressWarnings("unchecked")
        Map<Integer, Map<String, Object>> table = (Map<Integer, Map<String, Object>>) result[0];
        Assert.assertTrue("links start at 1 like every other Lua table here", table.containsKey(1));
        Assert.assertFalse(table.containsKey(0));
        Assert.assertEquals(
            7,
            table.get(1)
                .get("dim"));
        Assert.assertEquals(
            1,
            table.get(1)
                .get("x"));
    }

    @Test
    public void anEmptyTeleporterListsNothing() throws LuaException, InterruptedException {
        @SuppressWarnings("unchecked")
        Map<Integer, Map<String, Object>> table = (Map<Integer, Map<String, Object>>) teleporter()
            .callMethod(null, null, 2, new Object[0])[0];
        Assert.assertTrue(table.isEmpty());
    }

    @Test
    public void aTagSurvivesAnNbtRoundTrip() {
        TileEntityTeleporter te = teleporter();
        te.tag = "hub";

        NBTTagCompound tag = new NBTTagCompound();
        te.writeToNBT(tag);

        TileEntityTeleporter loaded = teleporter();
        loaded.readFromNBT(tag);
        Assert.assertEquals("hub", loaded.tag);
    }

    @Test
    public void theCoordinateListIsBounded() {
        TileEntityTeleporter te = teleporter();
        for (int i = 0; i < 10; i++) te.addLink(0, new ChunkCoordinates(i, 0, 0));
        Assert.assertTrue(te.links.size() <= te.getMaxLinks());
        Assert.assertEquals(9, te.links.get(0).link.posX);
    }
}
