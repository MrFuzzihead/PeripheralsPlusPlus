package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers {@code getNearbyPlayers}, which used to ignore its range argument entirely and compute {@code |dx + dz|}
 * with no vertical component (CODE_REVIEW.md section 4.13).
 */
public class PlayerSensorTest {

    private TileEntityPlayerSensor sensor;
    private World world;
    private final List<EntityPlayer> players = new ArrayList<EntityPlayer>();

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        // playerEntities is a public field on World, not a getter.
        world.playerEntities = players;
        sensor = new TileEntityPlayerSensor();
        sensor.setWorldObj(world);
        sensor.xCoord = 0;
        sensor.yCoord = 64;
        sensor.zCoord = 0;
        sensor.validate();
    }

    @After
    public void tearDown() {
        com.austinv11.peripheralsplusplus.reference.Config.enablePlayerSensor = true;
        com.austinv11.peripheralsplusplus.reference.Config.additionalMethods = true;
    }

    private EntityPlayer player(String name, double x, double y, double z) {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Mockito.when(player.getDisplayName())
            .thenReturn(name);
        player.posX = x;
        player.posY = y;
        player.posZ = z;
        players.add(player);
        return player;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Map<String, Object>> nearby(Object... arguments) throws LuaException, InterruptedException {
        Object[] result = sensor.callMethod(null, null, 0, arguments);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        return (Map<Integer, Map<String, Object>>) result[0];
    }

    @Test
    public void theConfiguredRangeIsUsed() throws Exception {
        player("close", 1, 64, 0);
        player("far", 50, 64, 0);

        // Default sensorRange is 64, so both are inside; with an explicit range of 10 only the close one is.
        Assert.assertEquals(2, nearby().size());
        Map<Integer, Map<String, Object>> inside = nearby(10.0d);
        Assert.assertEquals(1, inside.size());
        Assert.assertEquals(
            "close",
            inside.get(1)
                .get("player"));
    }

    @Test
    public void adHocRangeAcceptsANegativeValueAsUnlimited() throws Exception {
        player("far", 5000, 64, 0);
        Assert.assertEquals(1, nearby(-1.0d).size());
    }

    @Test
    public void distanceIsEuclidean() throws Exception {
        // 3-4-5 in the horizontal plane, so the old |dx + dz| would have said 7 instead of 5.
        player("corner", 3, 64, 4);
        Map<String, Object> entry = nearby().get(1);
        Assert.assertEquals(5.0d, (Double) entry.get("distance"), 0.0001d);
    }

    @Test
    public void distanceIncludesTheVerticalComponent() throws Exception {
        player("above", 0, 74, 0);
        Map<String, Object> entry = nearby().get(1);
        Assert.assertEquals(10.0d, (Double) entry.get("distance"), 0.0001d);
    }

    @Test
    public void playersAreKeyedFromOne() throws Exception {
        player("a", 1, 64, 0);
        player("b", 2, 64, 0);
        Map<Integer, Map<String, Object>> table = nearby();
        Assert.assertTrue(table.containsKey(1));
        Assert.assertTrue(table.containsKey(2));
        Assert.assertFalse(table.containsKey(0));
    }

    @Test
    public void resultsAreOrderedByDistance() throws Exception {
        player("near", 1, 64, 0);
        player("far", 20, 64, 0);
        Map<Integer, Map<String, Object>> table = nearby();
        Assert.assertEquals(
            "near",
            table.get(1)
                .get("player"));
        Assert.assertEquals(
            "far",
            table.get(2)
                .get("player"));
    }

    @Test
    public void emptyWorldYieldsAnEmptyTable() throws Exception {
        Assert.assertTrue(nearby().isEmpty());
    }

    @Test
    public void aDisabledSensorRejectsEverything() {
        com.austinv11.peripheralsplusplus.reference.Config.enablePlayerSensor = false;
        try {
            sensor.callMethod(null, null, 0, new Object[0]);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("disabled"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    @Test
    public void methodNamesAreGatedByTheAdditionalMethodsFlag() {
        Assert.assertTrue(
            Arrays.asList(sensor.getMethodNames())
                .contains("getNearbyPlayers"));
        com.austinv11.peripheralsplusplus.reference.Config.additionalMethods = false;
        Assert.assertEquals(0, sensor.getMethodNames().length);
    }

    @Test
    public void aSensorWithoutAWorldReportsAnErrorInsteadOfNpe() {
        TileEntityPlayerSensor orphan = new TileEntityPlayerSensor();
        try {
            orphan.callMethod(null, null, 0, new Object[] { 4.0d });
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("not in a world"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    @Test
    public void nonNumericRangeIsRejected() {
        try {
            sensor.callMethod(null, null, 0, new Object[] { "10" });
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("number"));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    /** Kept so a future refactor of the table type fails loudly rather than silently changing the API. */
    @Test
    public void tableShapeIsAMap() throws Exception {
        Assert.assertTrue(nearby() instanceof HashMap);
    }
}
