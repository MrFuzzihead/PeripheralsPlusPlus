package com.austinv11.peripheralsplusplus.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraftforge.common.IShearable;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.utils.TurtleUtil;

/**
 * Covers the distance helpers used by the shear and RedPower-like tool upgrades.
 *
 * <p>
 * {@code getClosestEntity} never updated its {@code lastDistance}, so it returned whichever entity happened to come
 * last in the AABB result rather than the nearest one (CODE_REVIEW.md section 5.2).
 * </p>
 */
public class TurtleUtilTest {

    private static Entity at(double x, double y, double z) {
        Entity entity = Mockito.mock(Entity.class);
        entity.posX = x;
        entity.posY = y;
        entity.posZ = z;
        return entity;
    }

    private static Entity shearableAt(double x, double y, double z) {
        Entity entity = Mockito.mock(
            EntityAnimal.class,
            Mockito.withSettings()
                .extraInterfaces(IShearable.class));
        entity.posX = x;
        entity.posY = y;
        entity.posZ = z;
        return entity;
    }

    @Test
    public void closestEntityIsTheNearestNotTheLast() {
        Entity origin = at(0, 0, 0);
        Entity far = at(20, 0, 0);
        Entity near = at(2, 0, 0);
        Entity middle = at(9, 0, 0);
        // Deliberately out of order, with the nearest first so "returns the last" would pick `middle`.
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(near);
        candidates.add(middle);
        candidates.add(far);

        Assert.assertSame(near, TurtleUtil.getClosestEntity(candidates, origin));
    }

    @Test
    public void closestEntityIgnoresOrdering() {
        Entity origin = at(0, 0, 0);
        Entity far = at(50, 0, 0);
        Entity near = at(1, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(far);
        candidates.add(near);

        Assert.assertSame(near, TurtleUtil.getClosestEntity(candidates, origin));
    }

    @Test
    public void closestShearableOnlyConsidersShearables() {
        Entity origin = at(0, 0, 0);
        Entity notShearable = at(1, 0, 0);
        Entity sheep = shearableAt(10, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(notShearable);
        candidates.add(sheep);

        Assert.assertSame(sheep, TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void closestShearablePicksTheNearestShearable() {
        Entity origin = at(0, 0, 0);
        Entity close = shearableAt(3, 0, 0);
        Entity further = shearableAt(7, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(further);
        candidates.add(close);

        Assert.assertSame(close, TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void closestShearableIsNullWhenNothingCanBeSheared() {
        Entity origin = at(0, 0, 0);
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(at(1, 0, 0));

        Assert.assertNull(TurtleUtil.getClosestShearableEntity(candidates, origin));
    }

    @Test
    public void emptyAndNullListsAreSafe() {
        Entity origin = at(0, 0, 0);
        Assert.assertNull(TurtleUtil.getClosestEntity(new ArrayList<Entity>(), origin));
        Assert.assertNull(TurtleUtil.getClosestEntity(null, origin));
        Assert.assertNull(TurtleUtil.getClosestEntity(new ArrayList<Entity>(), null));
    }

    @Test
    public void distanceUsesAllThreeAxes() {
        Entity origin = at(0, 0, 0);
        Entity diagonal = at(3, 4, 12); // 13 blocks away
        Entity closerOnXAlone = at(5, 0, 0); // 5 blocks away
        List<Entity> candidates = new ArrayList<Entity>();
        candidates.add(diagonal);
        candidates.add(closerOnXAlone);

        Assert.assertSame(closerOnXAlone, TurtleUtil.getClosestEntity(candidates, origin));
    }
}
