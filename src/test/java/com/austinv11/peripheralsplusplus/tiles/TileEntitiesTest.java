package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.ITurtleAccess;

/**
 * Covers the tile entities whose logic is reachable without a running game.
 *
 * <p>
 * Each of these needs nothing beyond a mocked {@link World} and a mocked inventory, which is why they belong in the
 * headless suite rather than the in-game one: the game test is reserved for the parts that genuinely need registries
 * or a tick loop.
 * </p>
 */
public class TileEntitiesTest {

    @org.junit.BeforeClass
    public static void registerTileEntities() {
        // TileEntity#writeToNBT writes the registry id, which needs a mapping; the game supplies it in
        // CommonProxy#registerTileEntities.
        net.minecraft.tileentity.TileEntity.addMapping(TileEntityRFCharger.class, "pppRfChargerTest");
    }

    @After
    public void tearDown() {
        Config.enableEnvironmentScanner = true;
        Config.enableNoteBlock = true;
        Config.enablePeripheralContainer = true;
        Config.enableSpeaker = true;
        Config.enableSmartHelmet = true;
        Config.enableNanoBots = true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Environment scanner
    // ---------------------------------------------------------------------------------------------------------

    private static TileEntityEnvironmentScanner scanner() {
        World world = Mockito.mock(World.class);
        BiomeGenBase biome = Mockito.mock(BiomeGenBase.class);
        biome.biomeName = "Plains";
        Mockito.when(biome.getTempCategory())
            .thenReturn(BiomeGenBase.TempCategory.MEDIUM);
        Mockito.when(biome.getEnableSnow())
            .thenReturn(true);
        Mockito.when(world.getBiomeGenForCoords(Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(biome);
        Mockito.when(world.isRaining())
            .thenReturn(true);
        TileEntityEnvironmentScanner scanner = new TileEntityEnvironmentScanner();
        scanner.setWorldObj(world);
        return scanner;
    }

    @Test
    public void theScannerReadsTheWorld() throws Exception {
        TileEntityEnvironmentScanner scanner = scanner();
        scanner.updateEntity();
        Assert.assertArrayEquals(new Object[] { true }, scanner.callMethod(null, null, 0, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "Plains" }, scanner.callMethod(null, null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "MEDIUM" }, scanner.callMethod(null, null, 2, new Object[0]));
        // getTemperature is an alias for getTemp, kept for the bundled temperature.lua.
        Assert.assertArrayEquals(new Object[] { "MEDIUM" }, scanner.callMethod(null, null, 3, new Object[0]));
        Assert.assertArrayEquals(new Object[] { true }, scanner.callMethod(null, null, 4, new Object[0]));
    }

    @Test
    public void theScannerSurvivesAWorldWithoutABiome() throws Exception {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getBiomeGenForCoords(Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(null);
        TileEntityEnvironmentScanner scanner = new TileEntityEnvironmentScanner();
        scanner.setWorldObj(world);
        scanner.updateEntity();
        Assert.assertArrayEquals(new Object[] { null }, scanner.callMethod(null, null, 1, new Object[0]));
    }

    @Test
    public void theScannerDoesNothingWithoutAWorld() throws Exception {
        TileEntityEnvironmentScanner scanner = new TileEntityEnvironmentScanner();
        scanner.updateEntity();
        Assert.assertArrayEquals(new Object[] { false }, scanner.callMethod(null, null, 0, new Object[0]));
    }

    @Test
    public void aDisabledScannerRejectsEverything() {
        TileEntityEnvironmentScanner scanner = scanner();
        Config.enableEnvironmentScanner = false;
        assertLuaError(() -> scanner.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void theScannerExposesFiveMethods() {
        Assert.assertEquals(5, scanner().getMethodNames().length);
        Assert.assertEquals("environmentScanner", scanner().getType());
    }

    // ---------------------------------------------------------------------------------------------------------
    // RF charger
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theChargerStoresEnergy() {
        TileEntityRFCharger charger = new TileEntityRFCharger();
        Assert.assertEquals(0, charger.getEnergyStored(ForgeDirection.UP));
        Assert.assertEquals(4000, charger.receiveEnergy(ForgeDirection.UP, 4000, false));
        Assert.assertEquals(4000, charger.getEnergyStored(ForgeDirection.UP));
        // Nothing is simulated into the store.
        Assert.assertEquals(4000, charger.receiveEnergy(ForgeDirection.UP, 4000, true));
        Assert.assertEquals(4000, charger.getEnergyStored(ForgeDirection.UP));
        Assert.assertTrue(charger.canConnectEnergy(ForgeDirection.NORTH));
        Assert.assertTrue(charger.getMaxEnergyStored(ForgeDirection.NORTH) > 0);
    }

    @Test
    public void theChargerSurvivesAnNbtRoundTrip() {
        TileEntityRFCharger charger = new TileEntityRFCharger();
        charger.receiveEnergy(ForgeDirection.UP, 1234, false);

        NBTTagCompound tag = new NBTTagCompound();
        charger.writeToNBT(tag);

        TileEntityRFCharger loaded = new TileEntityRFCharger();
        loaded.readFromNBT(tag);
        Assert.assertEquals(1234, loaded.getEnergyStored(ForgeDirection.UP));
    }

    @Test
    public void theChargerDoesNothingWithoutNeighbours() {
        // The old arithmetic divided 6 by an empty turtle list (CODE_REVIEW.md section 4.11); this is the guard.
        World world = Mockito.mock(World.class);
        world.isRemote = false;
        TileEntityRFCharger charger = new TileEntityRFCharger();
        charger.setWorldObj(world);
        charger.updateEntity();
        Assert.assertEquals(0, charger.getEnergyStored(ForgeDirection.UP));
    }

    @Test
    public void theChargerDoesNotRunOnTheClient() {
        World world = Mockito.mock(World.class);
        world.isRemote = true;
        TileEntityRFCharger charger = new TileEntityRFCharger();
        charger.setWorldObj(world);
        charger.updateEntity();
        Assert.assertEquals(0, charger.getEnergyStored(ForgeDirection.UP));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Resupply station
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theStationRefusesAnEmptyStock() {
        // The success path needs a registered item id (Item.itemRegistry needs FML), so it is covered by the in-game
        // suite; this asserts the guard that returns before any registry lookup.
        TileEntityResupplyStation station = new TileEntityResupplyStation();
        ITurtleAccess turtle = turtleWith(new ItemStack[1]);
        Assert.assertFalse(station.resupply(turtle, 0, "some:id", 0));
    }

    @Test
    public void theStationExposesFiftySixSlots() {
        Assert.assertEquals(56, new TileEntityResupplyStation().getSize());
        Assert.assertEquals(56, new TileEntityResupplyStation().getSizeInventory());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Peripheral container
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void anEmptyContainerListsNothing() throws Exception {
        TileEntityPeripheralContainer container = new TileEntityPeripheralContainer();
        Object[] result = container.callMethod(null, null, 0, new Object[0]);
        Assert.assertEquals(1, result.length);
        Assert.assertTrue(((java.util.Map<?, ?>) result[0]).isEmpty());
        Assert.assertTrue(
            container.getBlocksContained()
                .isEmpty());
    }

    @Test
    public void aContainerAcceptsAPeripheralBlock() {
        TileEntityPeripheralContainer container = new TileEntityPeripheralContainer();
        Assert.assertTrue(container.addPeripheral(com.austinv11.peripheralsplusplus.init.ModBlocks.chatBox));
        Assert.assertEquals(
            1,
            container.getBlocksContained()
                .size());
        // Adding the same block again is allowed up to the capacity, but a null block is not.
        Assert.assertFalse(container.addPeripheral(null));
    }

    @Test
    public void aContainerRefusesANonPeripheralBlock() {
        TileEntityPeripheralContainer container = new TileEntityPeripheralContainer();
        Assert.assertFalse(container.addPeripheral(net.minecraft.init.Blocks.stone));
    }

    @Test
    public void aContainerReportsAnUnknownName() {
        TileEntityPeripheralContainer container = new TileEntityPeripheralContainer();
        assertLuaError(
            () -> container.callMethod(null, null, 1, new Object[] { "notContained" }),
            "No contained peripheral");
    }

    @Test
    public void aDisabledContainerRejectsEverything() {
        TileEntityPeripheralContainer container = new TileEntityPeripheralContainer();
        Config.enablePeripheralContainer = false;
        assertLuaError(() -> container.callMethod(null, null, 0, new Object[0]), "disabled");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Note block
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theNoteBlockValidatesItsArguments() {
        TileEntityNoteBlock note = new TileEntityNoteBlock();
        assertLuaError(() -> note.callMethod(null, null, 0, new Object[] { 0.0d }), "too few arguments");
        assertLuaError(() -> note.callMethod(null, null, 0, new Object[] { "harp", 0.0d }), "number");
        assertLuaError(() -> note.callMethod(null, null, 0, new Object[] { 99.0d, 0.0d }), "instrument");
        assertLuaError(() -> note.callMethod(null, null, 0, new Object[] { 0.0d, 999.0d }), "note");
    }

    @Test
    public void theNoteBlockNeedsAWorldToPlay() throws Exception {
        // No world: the call is validated and then does nothing.
        Assert.assertEquals(0, new TileEntityNoteBlock().callMethod(null, null, 0, new Object[] { 0.0d, 5.0d }).length);
    }

    @Test
    public void theNoteBlockExposesTwoMethods() {
        Assert.assertEquals(2, new TileEntityNoteBlock().getMethodNames().length);
        Assert.assertEquals("noteBlock", new TileEntityNoteBlock().getType());
    }

    @Test
    public void aDisabledNoteBlockRejectsEverything() {
        Config.enableNoteBlock = false;
        assertLuaError(() -> new TileEntityNoteBlock().callMethod(null, null, 0, new Object[0]), "disabled");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Speaker
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theSpeakerClampsAndQueuesAudio() throws Exception {
        com.austinv11.peripheralsplusplus.PeripheralsPlusPlus.NETWORK = Mockito
            .mock(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper.class);
        try {
            TileEntitySpeaker speaker = new TileEntitySpeaker();
            Config.speechRange = 16;
            Object[] result = speaker.callMethod(null, null, 0, new Object[] { "hello", 5000.0d });
            Assert.assertEquals(2, result.length);
            Assert.assertEquals("hello", result[0]);
        } finally {
            com.austinv11.peripheralsplusplus.PeripheralsPlusPlus.NETWORK = null;
            Config.speechRange = 64;
        }
    }

    @Test
    public void theSpeakerRejectsAnUnknownLanguage() {
        TileEntitySpeaker speaker = new TileEntitySpeaker();
        assertLuaError(() -> speaker.callMethod(null, null, 0, new Object[] { "hi", 8.0d, "klingon" }), "is unknown");
    }

    @Test
    public void aDisabledSpeakerRejectsEverything() {
        Config.enableSpeaker = false;
        assertLuaError(() -> new TileEntitySpeaker().callMethod(null, null, 0, new Object[0]), "disabled");
    }

    @Test
    public void theSpeakerExposesTwoMethods() {
        Assert.assertEquals(2, new TileEntitySpeaker().getMethodNames().length);
        Assert.assertEquals("speaker", new TileEntitySpeaker().getType());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Antenna
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void theAntennaStoresItsLabel() throws Exception {
        TileEntityAntenna antenna = new TileEntityAntenna();
        Object[] set = antenna.callMethod(null, null, 2, new Object[] { "north wing" });
        Assert.assertArrayEquals(new Object[] { "north wing" }, set);
        Assert.assertArrayEquals(new Object[] { "north wing" }, antenna.callMethod(null, null, 3, new Object[0]));
        Assert.assertEquals("north wing", antenna.getLabel());
    }

    @Test
    public void theAntennaListsInfectedEntities() throws Exception {
        TileEntityAntenna antenna = new TileEntityAntenna();
        net.minecraft.entity.Entity entity = Mockito.mock(net.minecraft.entity.Entity.class);
        Mockito.when(entity.getEntityId())
            .thenReturn(42);
        antenna.associatedEntities.add(entity);

        Object[] result = antenna.callMethod(null, null, 4, new Object[0]);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals(java.util.Collections.singletonMap(1, 42), result[0]);
    }

    @Test
    public void theAntennaDropsDeadEntities() throws Exception {
        TileEntityAntenna antenna = new TileEntityAntenna();
        net.minecraft.entity.Entity dead = Mockito.mock(net.minecraft.entity.Entity.class);
        dead.isDead = true;
        antenna.associatedEntities.add(dead);

        Assert.assertTrue(((java.util.Map<?, ?>) antenna.callMethod(null, null, 4, new Object[0])[0]).isEmpty());
        Assert.assertTrue(antenna.associatedEntities.isEmpty());
    }

    @Test
    public void theAntennaReportsAnUnknownEntityId() {
        TileEntityAntenna antenna = new TileEntityAntenna();
        assertLuaError(() -> antenna.callMethod(null, null, 5, new Object[] { 77.0d }), "not found");
    }

    @Test
    public void theAntennaGatesTheHelmetMethods() {
        // enableSmartHelmet = false must block the HUD methods, and enableNanoBots = false must not.
        Config.enableSmartHelmet = false;
        assertLuaError(() -> new TileEntityAntenna().callMethod(null, null, 3, new Object[0]), "Smart Helmets");

        Config.enableSmartHelmet = true;
        Config.enableNanoBots = false;
        try {
            new TileEntityAntenna().callMethod(null, null, 3, new Object[0]);
        } catch (LuaException e) {
            Assert.fail("the label method must not be gated on nano bots: " + e.getMessage());
        } catch (Exception e) {
            Assert.fail("unexpected " + e);
        }
    }

    @Test
    public void theAntennaExposesSixMethods() {
        Assert.assertEquals(6, new TileEntityAntenna().getMethodNames().length);
        Assert.assertEquals("antenna", new TileEntityAntenna().getType());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static ITurtleAccess turtleWith(ItemStack[] slots) {
        List<ItemStack> backing = new ArrayList<ItemStack>();
        for (ItemStack slot : slots) backing.add(slot);
        IInventory inventory = new IInventory() {

            @Override
            public int getSizeInventory() {
                return backing.size();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return backing.get(slot);
            }

            @Override
            public ItemStack decrStackSize(int slot, int amount) {
                ItemStack current = backing.get(slot);
                if (current == null) return null;
                int take = Math.min(amount, current.stackSize);
                if (take <= 0) return null;
                ItemStack taken = current.copy();
                taken.stackSize = take;
                current.stackSize -= take;
                if (current.stackSize <= 0) backing.set(slot, null);
                return taken;
            }

            @Override
            public ItemStack getStackInSlotOnClosing(int slot) {
                ItemStack stack = backing.get(slot);
                backing.set(slot, null);
                return stack;
            }

            @Override
            public void setInventorySlotContents(int slot, ItemStack stack) {
                backing.set(slot, stack);
            }

            @Override
            public String getInventoryName() {
                return "turtle";
            }

            @Override
            public boolean hasCustomInventoryName() {
                return false;
            }

            @Override
            public int getInventoryStackLimit() {
                return 64;
            }

            @Override
            public void markDirty() {}

            @Override
            public boolean isUseableByPlayer(EntityPlayer player) {
                return true;
            }

            @Override
            public void openInventory() {}

            @Override
            public void closeInventory() {}

            @Override
            public boolean isItemValidForSlot(int slot, ItemStack stack) {
                return true;
            }
        };
        ITurtleAccess turtle = Mockito.mock(ITurtleAccess.class);
        Mockito.when(turtle.getInventory())
            .thenReturn(inventory);
        return turtle;
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
