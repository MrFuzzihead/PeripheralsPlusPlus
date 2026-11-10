package com.austinv11.peripheralsplusplus.turtles;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.reference.Reference;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleVerb;

/**
 * Covers the turtle upgrades' metadata and their disabled states.
 *
 * <p>
 * These classes are mostly four-line accessors, but they are also the mod's public upgrade surface: ComputerCraft
 * swallows a duplicate or invalid id, so nothing else would notice a regression here. The upgrade <em>behaviour</em>
 * (mining, shearing, riding) needs a real turtle and is covered by {@code gameTestServer}.
 * </p>
 */
public class TurtleUpgradesTest {

    @After
    public void tearDown() {
        Config.enableRedPowerLikeTurtles = true;
        Config.enableRidableTurtle = true;
        Config.enableTankTurtle = true;
        Config.enableChunkyTurtle = true;
    }

    private static ITurtleUpgrade[] upgrades() {
        return new ITurtleUpgrade[] { new TurtleChatBox(), new TurtlePlayerSensor(), new TurtleCompass(),
            new TurtleXP(), new TurtleOreDictionary(), new TurtleEnvironmentScanner(), new TurtleFeeder(),
            new TurtleShear(), new TurtleSignReader(), new TurtleGarden(), new TurtleSpeaker(), new TurtleTank(),
            new TurtleNoteBlock(), new TurtleRidable(), new TurtleDispenser(), new TurtleResupply(),
            new TurtleChunkLoader(), new TurtleBarrel() };
    }

    @Test
    public void everyUpgradeHasAnIdAdjectiveAndType() {
        Set<Integer> ids = new HashSet<Integer>();
        for (ITurtleUpgrade upgrade : upgrades()) {
            String name = upgrade.getClass()
                .getSimpleName();
            Assert.assertTrue(name + " has a duplicate id " + upgrade.getUpgradeID(), ids.add(upgrade.getUpgradeID()));
            Assert.assertNotNull(name + " has no adjective", upgrade.getUnlocalisedAdjective());
            Assert.assertFalse(
                name + " has an empty adjective",
                upgrade.getUnlocalisedAdjective()
                    .isEmpty());
            Assert.assertNotNull(name + " has no type", upgrade.getType());
        }
    }

    @Test
    public void everyUpgradeIdMatchesTheReferenceTable() {
        Assert.assertEquals(Reference.CHAT_BOX_UPGRADE, new TurtleChatBox().getUpgradeID());
        Assert.assertEquals(Reference.PLAYER_SENSOR_UPGRADE, new TurtlePlayerSensor().getUpgradeID());
        Assert.assertEquals(Reference.COMPASS_UPGRADE, new TurtleCompass().getUpgradeID());
        Assert.assertEquals(Reference.XP_UPGRADE, new TurtleXP().getUpgradeID());
        Assert.assertEquals(Reference.ORE_DICTIONARY_UPGRADE, new TurtleOreDictionary().getUpgradeID());
        Assert.assertEquals(Reference.ENVIRONMENT_UPGRADE, new TurtleEnvironmentScanner().getUpgradeID());
        Assert.assertEquals(Reference.FEEDER_UPGRADE, new TurtleFeeder().getUpgradeID());
        Assert.assertEquals(Reference.SHEAR_UPGRADE, new TurtleShear().getUpgradeID());
        Assert.assertEquals(Reference.SIGN_READER_UPGRADE, new TurtleSignReader().getUpgradeID());
        Assert.assertEquals(Reference.GARDEN_UPGRADE, new TurtleGarden().getUpgradeID());
        Assert.assertEquals(Reference.SPEAKER_UPGRADE, new TurtleSpeaker().getUpgradeID());
        Assert.assertEquals(Reference.TANK_UPGRADE, new TurtleTank().getUpgradeID());
        Assert.assertEquals(Reference.NOTE_BLOCK_UPGRADE, new TurtleNoteBlock().getUpgradeID());
        Assert.assertEquals(Reference.RIDABLE_UPGRADE, new TurtleRidable().getUpgradeID());
        Assert.assertEquals(Reference.DISPENSER_UPGRADE, new TurtleDispenser().getUpgradeID());
        Assert.assertEquals(Reference.RESUPPLY_UPGRADE, new TurtleResupply().getUpgradeID());
        Assert.assertEquals(Reference.CHUNK_LOADER_UPGRADE, new TurtleChunkLoader().getUpgradeID());
        Assert.assertEquals(Reference.BARREL_UPGRADE, new TurtleBarrel().getUpgradeID());
    }

    @Test
    public void aDisabledUpgradeHasNoCraftingItem() {
        Config.enableRedPowerLikeTurtles = false;
        Assert.assertNull("disabled upgrades must not be craftable", new TurtleProjRedFixture().getCraftingItem());

        Config.enableRidableTurtle = false;
        Assert.assertNull(new TurtleRidable().getCraftingItem());
    }

    @Test
    public void aDisabledUpgradeRefusesTools() {
        Config.enableRedPowerLikeTurtles = false;
        ITurtleAccess turtle = turtle();
        TurtleCommandResult result = new TurtleProjRedFixture().useTool(turtle, TurtleSide.Left, TurtleVerb.Attack, 2);
        Assert.assertNotNull(result);
        Assert.assertFalse("a disabled upgrade must report failure", result.isSuccess());
    }

    @Test
    public void theProjectRedFixtureReportsAnUnknownTool() {
        // ProjectRed is not installed in the test environment, so the tool item cannot be resolved. The upgrade must
        // say so instead of returning a stack with a null item (CODE_REVIEW.md section 5.42).
        Config.enableRedPowerLikeTurtles = true;
        TurtleProjRedFixture upgrade = new TurtleProjRedFixture();
        ItemStack tool = upgrade.getItem();
        if (tool == null) {
            Assert.assertNull(upgrade.getCraftingItem());
        } else {
            Assert.assertNotNull("a crafting item must never have a null item", tool.getItem());
        }
    }

    @Test
    public void theRidableUpgradeOnlyTicksOnTheServer() {
        ITurtleAccess turtle = turtle();
        TurtleRidable upgrade = new TurtleRidable();
        upgrade.update(turtle, TurtleSide.Left);
        Mockito.verify(turtle, Mockito.never())
            .consumeFuel(Mockito.anyInt());
    }

    @Test
    public void theChunkLoaderUpgradeCreatesItsPeripheral() throws Exception {
        Object peripheral = new TurtleChunkLoader().createPeripheral(turtle(), TurtleSide.Left);
        Assert.assertNotNull(peripheral);
        Assert.assertEquals("chunkLoader", ((dan200.computercraft.api.peripheral.IPeripheral) peripheral).getType());
        // It reserves no chunk ticket until a computer attaches it.
        Assert.assertEquals(
            0,
            ((dan200.computercraft.api.peripheral.IPeripheral) peripheral)
                .callMethod(null, null, 0, new Object[0]).length);
    }

    private static ITurtleAccess turtle() {
        ITurtleAccess turtle = Mockito.mock(ITurtleAccess.class);
        // useTool builds a FakeTurtlePlayer, which casts the world to WorldServer.
        World world = Mockito.mock(net.minecraft.world.WorldServer.class);
        world.isRemote = true;
        Mockito.when(turtle.getWorld())
            .thenReturn(world);
        Mockito.when(turtle.getPosition())
            .thenReturn(new net.minecraft.util.ChunkCoordinates(0, 64, 0));
        return turtle;
    }

    @Test
    public void theProjectRedFamilyCoversEveryToolAndMaterial() {
        for (TurtleProjRed.ToolType type : TurtleProjRed.ToolType.values()) {
            for (TurtleProjRed.ToolMaterial material : TurtleProjRed.ToolMaterial.values()) {
                if (type == TurtleProjRed.ToolType.UNKNOWN || material == TurtleProjRed.ToolMaterial.UNKNOWN) continue;
                TurtleProjRed upgrade = projRed(type, material);
                Assert.assertEquals(Reference.BASE_PROJ_RED_UPGRADE, upgrade.getUpgradeID());
                Assert.assertEquals(type.getAdj(), upgrade.getUnlocalisedAdjective());
                Assert.assertEquals(dan200.computercraft.api.turtle.TurtleUpgradeType.Tool, upgrade.getType());
                Assert.assertNull("a tool upgrade attaches no peripheral", upgrade.createPeripheral(null, null));
            }
        }
    }

    @Test
    public void theBlueprintFamilyCoversEveryToolAndMaterial() {
        for (TurtleBluePower.ToolType type : TurtleBluePower.ToolType.values()) {
            for (TurtleBluePower.ToolMaterial material : TurtleBluePower.ToolMaterial.values()) {
                if (type == TurtleBluePower.ToolType.UNKNOWN || material == TurtleBluePower.ToolMaterial.UNKNOWN)
                    continue;
                TurtleBluePower upgrade = bluePower(type, material);
                Assert.assertEquals(Reference.BASE_BLUEPOWER_UPGRADE, upgrade.getUpgradeID());
                Assert.assertNotNull(upgrade.getUnlocalisedAdjective());
                Assert.assertNull("a tool upgrade attaches no peripheral", upgrade.createPeripheral(null, null));
            }
        }
    }

    @Test
    public void aToolUpgradeWithNoInstalledItemReportsFailure() {
        // ProjectRed/BluePower are not installed in the test environment, so the tool item cannot be resolved.
        Config.enableRedPowerLikeTurtles = true;
        ITurtleAccess turtle = turtle();
        Assert.assertFalse(
            projRed(TurtleProjRed.ToolType.PICKAXE, TurtleProjRed.ToolMaterial.RUBY)
                .useTool(turtle, TurtleSide.Left, TurtleVerb.Dig, 2)
                .isSuccess());
        Assert.assertFalse(
            bluePower(TurtleBluePower.ToolType.PICKAXE, TurtleBluePower.ToolMaterial.RUBY)
                .useTool(turtle, TurtleSide.Left, TurtleVerb.Dig, 2)
                .isSuccess());
        Assert.assertFalse(
            projRed(TurtleProjRed.ToolType.SWORD, TurtleProjRed.ToolMaterial.RUBY)
                .useTool(turtle, TurtleSide.Left, TurtleVerb.Attack, 2)
                .isSuccess());
    }

    private static TurtleProjRed projRed(TurtleProjRed.ToolType type, TurtleProjRed.ToolMaterial material) {
        return new TurtleProjRed() {

            @Override
            public int getID() {
                return 0;
            }

            @Override
            public ToolType getToolType() {
                return type;
            }

            @Override
            public ToolMaterial getToolMaterial() {
                return material;
            }
        };
    }

    private static TurtleBluePower bluePower(TurtleBluePower.ToolType type, TurtleBluePower.ToolMaterial material) {
        return new TurtleBluePower() {

            @Override
            public int getID() {
                return 0;
            }

            @Override
            public ToolType getToolType() {
                return type;
            }

            @Override
            public ToolMaterial getToolMaterial() {
                return material;
            }
        };
    }

    /** A concrete ProjectRed upgrade, since the real ones are anonymous subclasses. */
    private static final class TurtleProjRedFixture extends TurtleProjRed {

        @Override
        public int getID() {
            return 0;
        }

        @Override
        public ToolType getToolType() {
            return ToolType.PICKAXE;
        }

        @Override
        public ToolMaterial getToolMaterial() {
            return ToolMaterial.RUBY;
        }
    }
}
