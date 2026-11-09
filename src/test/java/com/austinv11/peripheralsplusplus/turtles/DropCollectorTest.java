package com.austinv11.peripheralsplusplus.turtles;

import net.minecraft.entity.Entity;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import dan200.computercraft.api.turtle.ITurtleAccess;

/**
 * Covers the drop collector's bookkeeping. The map used to be an unbounded {@code static HashMap} keyed by entity that
 * was only pruned when the entity actually fired a drops event, so a despawn or a {@code doMobLoot=false} kill leaked
 * the whole turtle/world graph (CODE_REVIEW.md section 4.16).
 */
public class DropCollectorTest {

    private static final class Collector extends TurtleDropCollector {
    }

    private final Collector collector = new Collector();

    @After
    public void tearDown() {
        TurtleDropCollector.clear();
    }

    private static Entity entity() {
        return Mockito.mock(Entity.class);
    }

    @Test
    public void tracksAnEntityOnce() {
        Entity hit = entity();
        collector.addEntity(Mockito.mock(ITurtleAccess.class), hit);
        Assert.assertEquals(1, TurtleDropCollector.trackedCount());
        // A second hit on the same entity must not add a second entry.
        collector.addEntity(Mockito.mock(ITurtleAccess.class), hit);
        Assert.assertEquals(1, TurtleDropCollector.trackedCount());
    }

    @Test
    public void ignoresNullEntities() {
        collector.addEntity(Mockito.mock(ITurtleAccess.class), null);
        Assert.assertEquals(0, TurtleDropCollector.trackedCount());
    }

    @Test
    public void isBounded() {
        for (int i = 0; i < 5000; i++) collector.addEntity(Mockito.mock(ITurtleAccess.class), entity());
        Assert.assertTrue(
            "tracked entities must stay bounded, was " + TurtleDropCollector.trackedCount(),
            TurtleDropCollector.trackedCount() <= 256);
    }

    @Test
    public void clearForgetsEverything() {
        collector.addEntity(Mockito.mock(ITurtleAccess.class), entity());
        TurtleDropCollector.clear();
        Assert.assertEquals(0, TurtleDropCollector.trackedCount());
    }

    @Test
    public void handsOutAStableListener() {
        // registerUpgrade registers exactly one of these; asking twice must not create a second handler.
        Assert.assertNotNull(collector.newInstanceOfListener());
        Assert.assertSame(collector.newInstanceOfListener(), collector.newInstanceOfListener());
    }

    @Test
    public void everyCollectorSharesTheSameListenerClass() {
        Assert.assertSame(
            new Collector().newInstanceOfListener()
                .getClass(),
            new Collector().newInstanceOfListener()
                .getClass());
    }
}
