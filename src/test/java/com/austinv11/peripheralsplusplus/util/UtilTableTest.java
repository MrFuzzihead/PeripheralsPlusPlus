package com.austinv11.peripheralsplusplus.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import net.minecraftforge.common.EnumPlantType;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.utils.Util;

/**
 * Covers the Lua table helpers in {@link Util}. These are the boundary between Java collections and Lua tables, so an
 * off-by-one here is directly visible to every script that indexes the result.
 */
public class UtilTableTest {

    @Test
    public void arrayToMapIsOneBased() {
        HashMap<Integer, Integer> map = Util.arrayToMap(new int[] { 10, 20, 30 });
        Assert.assertEquals(3, map.size());
        Assert.assertEquals(Integer.valueOf(10), map.get(1));
        Assert.assertEquals(Integer.valueOf(20), map.get(2));
        Assert.assertEquals(Integer.valueOf(30), map.get(3));
        Assert.assertFalse(map.containsKey(0));
    }

    @Test
    public void byteArraysAreConvertedAsBytes() {
        HashMap<Integer, Byte> map = Util.arrayToMap(new byte[] { 1, -1 });
        Assert.assertEquals(Byte.valueOf((byte) 1), map.get(1));
        Assert.assertEquals(Byte.valueOf((byte) -1), map.get(2));
    }

    @Test
    public void objectArraysKeepOrderAndNulls() {
        HashMap<Integer, Object> map = Util.arrayToMap(new Object[] { "a", null, Boolean.TRUE });
        Assert.assertEquals(3, map.size());
        Assert.assertEquals("a", map.get(1));
        Assert.assertNull(map.get(2));
        Assert.assertEquals(Boolean.TRUE, map.get(3));
    }

    @Test
    public void enumSetToMapIsOneBased() {
        EnumSet<EnumPlantType> types = EnumSet.of(EnumPlantType.Crop, EnumPlantType.Desert);
        HashMap<Integer, Object> map = Util.EnumSetToMap(types);
        Assert.assertEquals(types.size(), map.size());
        // EnumSet iterates in declaration order, so assert membership rather than a fixed position.
        Assert.assertTrue(
            map.values()
                .containsAll(types));
        for (int key : map.keySet()) Assert.assertTrue("keys start at 1", key >= 1 && key <= types.size());
    }

    @Test
    public void collectionToMapIsOneBased() {
        List<String> values = new ArrayList<String>();
        values.add("x");
        values.add("y");
        HashMap<Integer, Object> map = Util.collectionToMap(values);
        Assert.assertEquals("x", map.get(1));
        Assert.assertEquals("y", map.get(2));
        Assert.assertEquals(2, map.size());
    }

    @Test
    public void stringToArrayTrimsElements() {
        String[] parsed = Util.stringToArray("[alpha, beta , gamma]");
        Assert.assertArrayEquals(new String[] { "alpha", "beta", "gamma" }, parsed);
    }

    @Test
    public void stringToArrayHandlesASingleElement() {
        Assert.assertArrayEquals(new String[] { "only" }, Util.stringToArray("[only]"));
    }

    @Test
    public void stringToArrayKeepsEmptyElements() {
        // A message containing a comma is round-tripped through Stack#toString, which produces the same shape; the
        // helper cannot lose items even if it cannot preserve the exact text (CODE_REVIEW.md section 5.6).
        Assert.assertArrayEquals(new String[] { "a", "", "b" }, Util.stringToArray("[a, , b]"));
    }
}
