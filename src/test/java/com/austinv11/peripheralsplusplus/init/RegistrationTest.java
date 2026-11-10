package com.austinv11.peripheralsplusplus.init;

import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.proxy.CommonProxy;
import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAIChatBox;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna;
import com.austinv11.peripheralsplusplus.tiles.TileEntityChatBox;
import com.austinv11.peripheralsplusplus.tiles.TileEntityEnvironmentScanner;
import com.austinv11.peripheralsplusplus.tiles.TileEntityInteractiveSorter;
import com.austinv11.peripheralsplusplus.tiles.TileEntityMEBridge;
import com.austinv11.peripheralsplusplus.tiles.TileEntityNoteBlock;
import com.austinv11.peripheralsplusplus.tiles.TileEntityOreDictionary;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPeripheralContainer;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerInterface;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerSensor;
import com.austinv11.peripheralsplusplus.tiles.TileEntityRFCharger;
import com.austinv11.peripheralsplusplus.tiles.TileEntityResupplyStation;
import com.austinv11.peripheralsplusplus.tiles.TileEntitySpeaker;
import com.austinv11.peripheralsplusplus.tiles.TileEntityTeleporter;
import com.austinv11.peripheralsplusplus.tiles.TileEntityTimeSensor;
import com.austinv11.peripheralsplusplus.tiles.TileEntityTurtle;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.turtle.ITurtleUpgrade;

/**
 * Covers the registration wiring: which tile entities are registered for which optional mods, and the turtle upgrade
 * table.
 *
 * <p>
 * Both are pure "call the registry" code, so a static mock of {@link GameRegistry} and {@link Loader} is enough. This
 * is where the thirty-duplicate-listener bug lived (CODE_REVIEW.md section 4.16) and where the RF charger's
 * block/entity mismatch lived (section 5.37).
 * </p>
 */
public class RegistrationTest {

    @After
    public void tearDown() {
        CreativeTabPPP.upgrades.clear();
    }

    // ---------------------------------------------------------------------------------------------------------
    // CommonProxy#registerTileEntities
    // ---------------------------------------------------------------------------------------------------------

    /** Runs {@code registerTileEntities} with only the named mods reported as loaded. */
    private static void registerWith(String... loadedMods) {
        Set<String> loaded = new java.util.HashSet<String>(java.util.Arrays.asList(loadedMods));
        try (MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded(Mockito.anyString()))
                .thenAnswer(invocation -> loaded.contains(invocation.getArgument(0)));
            new CommonProxy().registerTileEntities();
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends net.minecraft.tileentity.TileEntity> tile(Class<?> type) {
        return (Class<? extends net.minecraft.tileentity.TileEntity>) type;
    }

    @Test
    public void theUnconditionalTileEntitiesAreRegistered() {
        Class<?>[] expected = { TileEntityChatBox.class, TileEntityAIChatBox.class, TileEntityPlayerSensor.class,
            TileEntityOreDictionary.class, TileEntityTeleporter.class, TileEntityEnvironmentScanner.class,
            TileEntitySpeaker.class, TileEntityAntenna.class, TileEntityPeripheralContainer.class,
            TileEntityNoteBlock.class, TileEntityTurtle.class, TileEntityTimeSensor.class,
            TileEntityInteractiveSorter.class, TileEntityPlayerInterface.class, TileEntityResupplyStation.class };
        try (MockedStatic<GameRegistry> registry = Mockito.mockStatic(GameRegistry.class)) {
            registerWith();
            for (Class<?> type : expected) {
                registry.verify(() -> GameRegistry.registerTileEntity(Mockito.eq(tile(type)), Mockito.anyString()));
            }
            // The teleporter mk II is registered under publicName + "T2".
            registry.verify(
                () -> GameRegistry.registerTileEntity(
                    Mockito.eq(tile(com.austinv11.peripheralsplusplus.tiles.TileEntityTeleporterT2.class)),
                    Mockito.anyString()));
        }
    }

    @Test
    public void theOptionalTileEntitiesAreSkippedWhenTheModIsAbsent() {
        try (MockedStatic<GameRegistry> registry = Mockito.mockStatic(GameRegistry.class)) {
            registerWith();
            registry.verify(
                () -> GameRegistry.registerTileEntity(Mockito.eq(tile(TileEntityRFCharger.class)), Mockito.anyString()),
                Mockito.never());
            registry.verify(
                () -> GameRegistry.registerTileEntity(Mockito.eq(tile(TileEntityMEBridge.class)), Mockito.anyString()),
                Mockito.never());
        }
    }

    @Test
    public void forestryAddsTheThreeAnalyzers() {
        try (MockedStatic<GameRegistry> registry = Mockito.mockStatic(GameRegistry.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("Forestry"))
                .thenReturn(true);
            loader.when(() -> Loader.isModLoaded(Mockito.argThat(mod -> !"Forestry".equals(mod))))
                .thenReturn(false);
            new CommonProxy().registerTileEntities();
            Class<?>[] analyzers = { com.austinv11.peripheralsplusplus.tiles.TileEntityAnalyzerBee.class,
                com.austinv11.peripheralsplusplus.tiles.TileEntityAnalyzerButterfly.class,
                com.austinv11.peripheralsplusplus.tiles.TileEntityAnalyzerTree.class };
            for (Class<?> analyzer : analyzers) {
                registry.verify(() -> GameRegistry.registerTileEntity(Mockito.eq(tile(analyzer)), Mockito.anyString()));
            }
        }
    }

    @Test
    public void thermalExpansionAndBuildCraftBothRegisterTheRfCharger() {
        // The block was only created for ThermalExpansion while the tile entity accepted BuildCraft too, so a
        // BuildCraft-only pack registered a tile entity with no block (CODE_REVIEW.md section 5.37).
        for (String mod : new String[] { "ThermalExpansion", "BuildCraft|Core" }) {
            try (MockedStatic<GameRegistry> registry = Mockito.mockStatic(GameRegistry.class);
                MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
                loader.when(() -> Loader.isModLoaded(mod))
                    .thenReturn(true);
                loader.when(() -> Loader.isModLoaded(Mockito.argThat(other -> !mod.equals(other))))
                    .thenReturn(false);
                new CommonProxy().registerTileEntities();
                registry.verify(
                    () -> GameRegistry
                        .registerTileEntity(Mockito.eq(tile(TileEntityRFCharger.class)), Mockito.anyString()));
            }
        }
    }

    @Test
    public void appliedEnergisticsRegistersTheMeBridge() {
        try (MockedStatic<GameRegistry> registry = Mockito.mockStatic(GameRegistry.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("appliedenergistics2"))
                .thenReturn(true);
            loader.when(() -> Loader.isModLoaded(Mockito.argThat(mod -> !"appliedenergistics2".equals(mod))))
                .thenReturn(false);
            new CommonProxy().registerTileEntities();
            registry.verify(
                () -> GameRegistry.registerTileEntity(Mockito.eq(tile(TileEntityMEBridge.class)), Mockito.anyString()));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // PeripheralsPlusPlus#registerUpgrade
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void registerUpgradeAddsToTheCreativeTabAndForwardsToComputerCraft() {
        ITurtleUpgrade upgrade = Mockito.mock(ITurtleUpgrade.class);
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class)) {
            PeripheralsPlusPlus.registerUpgrade(upgrade);
            cc.verify(() -> ComputerCraftAPI.registerTurtleUpgrade(upgrade));
        }
        Assert.assertTrue(CreativeTabPPP.upgrades.contains(upgrade));
    }

    @Test
    public void registeringTwoUpgradesKeepsBoth() {
        ITurtleUpgrade first = Mockito.mock(ITurtleUpgrade.class);
        ITurtleUpgrade second = Mockito.mock(ITurtleUpgrade.class);
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class)) {
            PeripheralsPlusPlus.registerUpgrade(first);
            PeripheralsPlusPlus.registerUpgrade(second);
        }
        Assert.assertEquals(2, CreativeTabPPP.upgrades.size());
        Assert.assertTrue(CreativeTabPPP.upgrades.contains(first));
        Assert.assertTrue(CreativeTabPPP.upgrades.contains(second));
    }

    // ---------------------------------------------------------------------------------------------------------
    // The RedPower-like families
    // ---------------------------------------------------------------------------------------------------------

    private static void callRedPowerRegistration(boolean modsLoaded) throws Exception {
        // registerRedPowerLikeUpgrades is private and only called from init(), which needs the full FML lifecycle.
        java.lang.reflect.Method register = PeripheralsPlusPlus.class.getDeclaredMethod("registerRedPowerLikeUpgrades");
        register.setAccessible(true);
        // The first TurtleDropCollector registration adds a listener to MinecraftForge's real event bus, which needs
        // FML; pre-setting the "already registered" flag skips only that step.
        java.lang.reflect.Field registered = PeripheralsPlusPlus.class.getDeclaredField("dropCollectorRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded(Mockito.anyString()))
                .thenReturn(modsLoaded);
            register.invoke(new PeripheralsPlusPlus());
        }
    }

    @Test
    public void theRedPowerFamiliesRegisterEveryCombination() throws Exception {
        callRedPowerRegistration(true);
        // 3 materials x 5 tools for each of the two families.
        Assert.assertEquals(30, CreativeTabPPP.upgrades.size());
        Set<Integer> ids = new java.util.HashSet<Integer>();
        for (ITurtleUpgrade upgrade : CreativeTabPPP.upgrades) {
            Assert.assertTrue("duplicate upgrade id " + upgrade.getUpgradeID(), ids.add(upgrade.getUpgradeID()));
            Assert.assertTrue("an upgrade id must stay clear of ComputerCraft's range", upgrade.getUpgradeID() > 100);
        }
    }

    @Test
    public void theFamiliesAreSkippedWhenNeitherModIsPresent() throws Exception {
        callRedPowerRegistration(false);
        Assert.assertTrue(CreativeTabPPP.upgrades.isEmpty());
    }

    @Test
    public void theModMetadataIsConsistent() {
        Assert.assertEquals("PeripheralsPlusPlus", Reference.MOD_ID);
        Assert.assertNotNull(Reference.VERSION);
    }
}
