package com.austinv11.peripheralsplusplus.lua;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the shared Lua argument reader. Every peripheral in the mod is meant to route its argument handling through
 * this class (CODE_REVIEW.md section 9, phase 2), so its boundaries are the boundaries of ~150 call sites.
 */
public class LuaArgsTest {

    private static Object[] args(Object... values) {
        return values;
    }

    @Test
    public void emptyArrayIsUsable() {
        LuaArgs reader = LuaArgs.of(null);
        Assert.assertEquals(0, reader.size());
        Assert.assertFalse(reader.has(0));
        Assert.assertEquals(
            0,
            LuaArgs.of(new Object[0])
                .size());
    }

    @Test
    public void hasIsZeroBasedAndBounded() {
        LuaArgs reader = LuaArgs.of(args("a", 1.0d));
        Assert.assertTrue(reader.has(0));
        Assert.assertTrue(reader.has(1));
        Assert.assertFalse(reader.has(2));
        Assert.assertFalse(reader.has(-1));
    }

    @Test
    public void aNullElementCountsAsAbsent() throws LuaException {
        // Lua has no null, only nil, so a nil argument never reaches Java; an explicit null behaves the same way.
        LuaArgs reader = LuaArgs.of(args("a", null));
        Assert.assertTrue(reader.has(0));
        Assert.assertFalse(reader.has(1));
        Assert.assertEquals("fallback", reader.string(1, "fallback"));
        Assert.assertEquals(7, reader.integer(1, 7));
    }

    @Test
    public void atLeastReportsTheExpectedCount() throws LuaException {
        Assert.assertSame(
            LuaArgs.of(args(1.0d))
                .atLeast(1)
                .size(),
            1);
        try {
            LuaArgs.of(new Object[0])
                .atLeast(2);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("Too few arguments"));
        }
    }

    @Test
    public void numberRejectsNonNumbers() throws LuaException {
        LuaArgs reader = LuaArgs.of(args(1.0d, "text", Boolean.TRUE));
        Assert.assertEquals(1.0d, reader.number(0), 0d);
        assertBadArgument(reader, "number", 1);
        assertBadArgument(reader, "number", 2);
    }

    @Test
    public void numberRejectsNilAndReportsTheLuaIndex() {
        try {
            LuaArgs.of(new Object[0])
                .number(1);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            // Java index 1 is Lua argument #2.
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("#2"));
        }
    }

    @Test
    public void numberDefaultsOnlyApplyWhenTheArgumentIsAbsent() throws LuaException {
        Assert.assertEquals(
            7.5d,
            LuaArgs.of(new Object[0])
                .number(0, 7.5d),
            0d);
        Assert.assertEquals(
            1.0d,
            LuaArgs.of(args(1.0d))
                .number(0, 7.5d),
            0d);
        try {
            LuaArgs.of(args("nope"))
                .number(0, 7.5d);
            Assert.fail("a present but wrong-typed argument must still be rejected");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("number expected"));
        }
    }

    @Test
    public void integerFloorsLikeLuaToIntConversions() throws LuaException {
        LuaArgs reader = LuaArgs.of(args(3.99d, -3.99d));
        Assert.assertEquals(3, reader.integer(0));
        Assert.assertEquals(-4, reader.integer(1));
        Assert.assertEquals(
            9,
            LuaArgs.of(new Object[0])
                .integer(0, 9));
    }

    @Test
    public void stringAndBoolRejectOtherTypes() throws LuaException {
        LuaArgs reader = LuaArgs.of(args("hello", Boolean.TRUE));
        Assert.assertEquals("hello", reader.string(0));
        Assert.assertTrue(reader.bool(1));
        assertBadArgument(reader, "string", 1);
        assertBadArgument(reader, "boolean", 0);
    }

    @Test
    public void defaultsForStringAndBool() throws LuaException {
        LuaArgs empty = LuaArgs.of(new Object[0]);
        Assert.assertEquals("kevin16", empty.string(0, "kevin16"));
        Assert.assertTrue(empty.bool(0, true));
        Assert.assertEquals(
            "x",
            LuaArgs.of(args("x"))
                .string(0, "kevin16"));
        Assert.assertFalse(
            LuaArgs.of(args(Boolean.FALSE))
                .bool(0, true));
    }

    @Test
    public void numberOrStringAcceptsBothShapes() throws LuaException {
        LuaArgs reader = LuaArgs.of(args("top", 2.0d, Boolean.TRUE));
        Assert.assertEquals("top", reader.numberOrString(0));
        Assert.assertEquals(2.0d, reader.numberOrString(1));
        assertBadArgument(reader, "string or number", 2);
    }

    @Test
    public void numberOrStringRejectsBooleans() {
        try {
            LuaArgs.of(args(Boolean.TRUE))
                .numberOrString(0);
            Assert.fail("expected a LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("string or number expected"));
        }
    }

    @Test
    public void tableIsOneBasedAndStopsAtNull() {
        HashMap<Integer, Object> table = LuaArgs.table("a", 2.0d, Boolean.TRUE, null, "unreachable");
        Assert.assertEquals(3, table.size());
        Assert.assertEquals("a", table.get(1));
        Assert.assertEquals(2.0d, table.get(2));
        Assert.assertEquals(Boolean.TRUE, table.get(3));
        Assert.assertFalse(table.containsKey(5));
    }

    @Test
    public void tableOfNothingIsEmpty() {
        Assert.assertTrue(
            LuaArgs.table()
                .isEmpty());
        Assert.assertTrue(
            LuaArgs.table((Object) null)
                .isEmpty());
    }

    private static void assertBadArgument(LuaArgs reader, String expectedType, int index) {
        try {
            if ("number".equals(expectedType)) reader.number(index);
            else if ("string".equals(expectedType)) reader.string(index);
            else if ("boolean".equals(expectedType)) reader.bool(index);
            else reader.numberOrString(index);
            Assert.fail("expected a LuaException for argument " + index);
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("#" + (index + 1))
                    && expected.getMessage()
                        .contains(expectedType));
        }
    }
}
