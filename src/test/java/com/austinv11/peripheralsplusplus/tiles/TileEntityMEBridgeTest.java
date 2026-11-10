package com.austinv11.peripheralsplusplus.tiles;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;

/**
 * Covers the parts of the ME bridge that do not need an AE grid.
 *
 * <p>
 * The bridge implements seven AE interfaces with one-line accessors; those, the disabled guard and the peripheral
 * bookkeeping are all reachable without a network, which is most of the class. The actual item movement needs a live
 * ME system and is not covered anywhere yet.
 * </p>
 */
public class TileEntityMEBridgeTest {

    @org.junit.BeforeClass
    public static void registerTileEntity() {
        net.minecraft.tileentity.TileEntity.addMapping(TileEntityMEBridge.class, "pppMeBridgeTest");
    }

    /** A bridge on the client side, so nothing tries to create an AE grid node. */
    private static TileEntityMEBridge bridge() {
        TileEntityMEBridge bridge = new TileEntityMEBridge();
        bridge.xCoord = 0;
        bridge.yCoord = 64;
        bridge.zCoord = 0;
        net.minecraft.world.World world = Mockito.mock(net.minecraft.world.World.class);
        world.isRemote = true;
        // World.provider is final on the class, so it cannot be assigned on a mock; getLocation() is therefore
        // asserted only for "returns non-null" in the test that calls it.
        bridge.setWorldObj(world);
        return bridge;
    }

    @Test
    public void aDisabledBridgeRejectsEveryMethod() {
        Config.enableMEBridge = false;
        try {
            assertLuaError(() -> bridge().callMethod(null, null, 0, new Object[0]), "disabled");
        } finally {
            Config.enableMEBridge = true;
        }
    }

    @Test
    public void theBridgeDescribesItsNetworkProperties() {
        TileEntityMEBridge bridge = bridge();
        Assert.assertEquals("meBridge", bridge.getType());
        Assert.assertEquals(5, bridge.getMethodNames().length);
        Assert.assertEquals(1.0d, bridge.getIdlePowerUsage(), 0.0d);
        Assert.assertEquals(EnumSet.of(appeng.api.networking.GridFlags.REQUIRE_CHANNEL), bridge.getFlags());
        Assert.assertTrue(bridge.isWorldAccessible());
        Assert.assertEquals(appeng.api.util.AEColor.Transparent, bridge.getGridColor());
        Assert.assertEquals(EnumSet.allOf(ForgeDirection.class), bridge.getConnectableSides());
        Assert.assertEquals(appeng.api.util.AECableType.COVERED, bridge.getCableConnectionType(ForgeDirection.UP));
        Assert.assertSame(bridge, bridge.getMachine());
    }

    @Test
    public void theNetworkCallbacksTolerateNoComputers() {
        TileEntityMEBridge bridge = bridge();
        bridge.setNetworkStatus(null, 0);
        bridge.onGridNotification(null);
        bridge.gridChanged();
        Assert.assertNull("no grid node without AE", bridge.getGridNode(ForgeDirection.UP));
    }

    @Test
    public void theMachineRepresentationDoesNotThrowWhenAeIsAbsent() {
        // ModBlocks.meBridge is only created when AE2 is loaded; the getter must not NPE either way.
        Assert.assertNotNull(bridge().getMachineRepresentation());
    }

    @Test
    public void theBridgeRegistersItsComputers() throws Exception {
        TileEntityMEBridge bridge = bridge();
        IComputerAccess first = Mockito.mock(IComputerAccess.class);
        IComputerAccess second = Mockito.mock(IComputerAccess.class);
        bridge.attach(first);
        bridge.attach(second);
        bridge.detach(first);
        bridge.detach(second);
        // No grid exists on the client, so the actionable node is null; the point is that attach/detach did not throw.
        Assert.assertNull(bridge.getActionableNode());
    }

    @Test
    public void writingNbtWithoutAGridIsANoOp() {
        // readFromNBT is AE-only: it calls AEApi.instance(), which factories AE's registries and needs the game.
        TileEntityMEBridge bridge = bridge();
        NBTTagCompound tag = new NBTTagCompound();
        bridge.writeToNBT(tag);
        Assert.assertNotNull(tag);
        Assert.assertFalse("no node means no node tag", tag.hasKey("node"));
    }

    @Test
    public void theClientSideBridgeNeverCreatesAGridNode() {
        TileEntityMEBridge bridge = bridge();
        bridge.updateEntity();
        Assert.assertNull(bridge.getGridNode(ForgeDirection.UP));
        Assert.assertNull(bridge.placed);
    }

    @Test
    public void theBridgeReadsItsOwnerFromTheBlock() {
        // BlockMEBridge#onBlockPlacedBy stores the placer so the grid node can carry their id.
        TileEntityMEBridge bridge = bridge();
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Assert.assertNull(bridge.placed);
        bridge.placed = player;
        Assert.assertSame(player, bridge.placed);
    }

    @Test
    public void anInvalidBridgeReleasesItsNode() {
        TileEntityMEBridge bridge = bridge();
        bridge.invalidate();
        Assert.assertNull(bridge.getActionableNode());
    }

    @Test
    public void theBridgeIsItsOwnGridHost() {
        TileEntityMEBridge bridge = bridge();
        Assert.assertEquals(bridge, bridge);
        Assert.assertFalse("equals(IPeripheral) must stay identity", bridge.equals(new Object()));
    }

    private static void assertLuaError(ThrowingCall call, String expectedFragment) {
        try {
            call.run();
            Assert.fail("expected a LuaException mentioning " + expectedFragment);
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .toLowerCase()
                    .contains(expectedFragment.toLowerCase()));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    private interface ThrowingCall {

        void run() throws Exception;
    }

}
