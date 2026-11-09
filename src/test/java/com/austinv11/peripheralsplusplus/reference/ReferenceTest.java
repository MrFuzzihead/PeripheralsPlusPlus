package com.austinv11.peripheralsplusplus.reference;

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import dan200.computercraft.api.pocket.IPocketUpgrade;

/**
 * The upgrade id tables are a wire format: they are stored in saved turtle and pocket computer NBT. A collision with
 * another mod, or with our own RedPower-like families, is silent — {@code ComputerCraftAPI.registerTurtleUpgrade}
 * swallows the exception — so it is asserted here instead of discovered in a world.
 */
public class ReferenceTest {

    private static final int[] FIXED_TURTLE_IDS = { Reference.CHAT_BOX_UPGRADE, Reference.PLAYER_SENSOR_UPGRADE,
        Reference.COMPASS_UPGRADE, Reference.XP_UPGRADE, Reference.BARREL_UPGRADE, Reference.ORE_DICTIONARY_UPGRADE,
        Reference.SHEAR_UPGRADE, Reference.ENVIRONMENT_UPGRADE, Reference.FEEDER_UPGRADE, Reference.SPEAKER_UPGRADE,
        Reference.TANK_UPGRADE, Reference.NOTE_BLOCK_UPGRADE, Reference.SIGN_READER_UPGRADE, Reference.GARDEN_UPGRADE,
        Reference.RIDABLE_UPGRADE, Reference.DISPENSER_UPGRADE, Reference.CHUNK_LOADER_UPGRADE,
        Reference.RESUPPLY_UPGRADE };

    /** Matches the 3 materials x 5 tools each family registers in PeripheralsPlusPlus#registerRedPowerLikeUpgrades. */
    private static final int FAMILY_SIZE = 15;

    @Test
    public void fixedTurtleIdsAreUnique() {
        Set<Integer> seen = new HashSet<Integer>();
        for (int id : FIXED_TURTLE_IDS) Assert.assertTrue("duplicate turtle upgrade id " + id, seen.add(id));
    }

    @Test
    public void familyIdsDoNotCollideWithFixedIds() {
        Set<Integer> seen = new HashSet<Integer>();
        for (int id : FIXED_TURTLE_IDS) seen.add(id);
        for (int i = 0; i < FAMILY_SIZE; i++) {
            Assert.assertTrue("projectred id collides", seen.add(Reference.BASE_PROJ_RED_UPGRADE + i));
            Assert.assertTrue("bluepower id collides", seen.add(Reference.BASE_BLUEPOWER_UPGRADE + i));
        }
    }

    @Test
    public void turtleIdsAvoidComputerCraftsRange() {
        // ComputerCraft keeps a small, low id range for its own upgrades and its saved items assume it.
        for (int id : FIXED_TURTLE_IDS) Assert.assertTrue("turtle upgrade id " + id + " is in CC's range", id > 100);
        Assert.assertTrue(Reference.BASE_PROJ_RED_UPGRADE > 100);
        Assert.assertTrue(Reference.BASE_BLUEPOWER_UPGRADE > 100);
    }

    @Test
    public void familyIdsFitBetweenTheFixedIds() {
        // registerRedPowerLikeUpgrades allocates base + 0..14; the families must not run into SPEAKER_UPGRADE (125)
        // or CHUNK_LOADER_UPGRADE (147).
        Assert.assertTrue(Reference.BASE_PROJ_RED_UPGRADE + FAMILY_SIZE - 1 < Reference.SPEAKER_UPGRADE);
        Assert.assertTrue(Reference.BASE_BLUEPOWER_UPGRADE + FAMILY_SIZE - 1 < Reference.CHUNK_LOADER_UPGRADE);
    }

    @Test
    public void pocketIdsAreAboveComputerCraftsRange() {
        Assert.assertEquals(7, Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID, IPocketUpgrade.FIRST_MOD_UPGRADE_ID);
        Assert.assertTrue(Reference.MOTION_DETECTOR >= Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID);
        Assert.assertTrue(Reference.PERIPHERAL_CONTAINER >= Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID);
        Assert.assertNotEquals(Reference.MOTION_DETECTOR, Reference.PERIPHERAL_CONTAINER);
    }

    @Test
    public void modMetadataIsConsistent() {
        Assert.assertEquals("PeripheralsPlusPlus", Reference.MOD_ID);
        Assert.assertNotNull(Reference.VERSION);
        Assert.assertFalse(Reference.VERSION.isEmpty());
        // Reference.GUI_FACTORY_CLASS must not point at a class that does not exist.
        try {
            Class.forName(Reference.CLIENT_PROXY_CLASS);
            Class.forName(Reference.SERVER_PROXY_CLASS);
        } catch (ClassNotFoundException e) {
            Assert.fail("proxy class is missing: " + e.getMessage());
        }
    }
}
