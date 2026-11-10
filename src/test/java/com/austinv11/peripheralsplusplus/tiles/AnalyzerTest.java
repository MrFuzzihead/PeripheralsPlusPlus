package com.austinv11.peripheralsplusplus.tiles;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the three Forestry analyzer peripherals.
 *
 * <p>
 * Analysing a real bee or sapling needs Forestry's allele registry, so only the plumbing is asserted here: the method
 * table, the public name, the empty-slot branch (which short-circuits before the registry) and the disabled guard.
 * </p>
 */
public class AnalyzerTest {

    @After
    public void tearDown() {
        Config.enableAnalyzers = true;
    }

    private static TileEntityAnalyzer[] analyzers() {
        return new TileEntityAnalyzer[] { new TileEntityAnalyzerBee(), new TileEntityAnalyzerButterfly(),
            new TileEntityAnalyzerTree() };
    }

    @Test
    public void everyAnalyzerExposesTheSameTwoMethods() {
        for (TileEntityAnalyzer analyzer : analyzers()) {
            Assert.assertArrayEquals(new String[] { "analyze", "isMember" }, analyzer.getMethodNames());
            Assert.assertNotNull(analyzer.getType());
            Assert.assertFalse(
                analyzer.getType()
                    .isEmpty());
            Assert.assertNotNull(analyzer.getPublicName());
        }
    }

    @Test
    public void eachAnalyzerReportsItsOwnType() {
        Assert.assertEquals("beeAnalyzer", new TileEntityAnalyzerBee().getType());
        Assert.assertEquals("butterflyAnalyzer", new TileEntityAnalyzerButterfly().getType());
        Assert.assertEquals("treeAnalyzer", new TileEntityAnalyzerTree().getType());
    }

    @Test
    public void isMemberIsFalseForAnEmptySlot() throws Exception {
        // The null check short-circuits before Forestry's allele registry is consulted.
        for (TileEntityAnalyzer analyzer : analyzers()) {
            Assert.assertArrayEquals(new Object[] { false }, analyzer.callMethod(null, null, 1, new Object[0]));
        }
    }

    @Test
    public void analyzeIsFalseForAnEmptySlot() throws Exception {
        for (TileEntityAnalyzer analyzer : analyzers()) {
            Object[] result = analyzer.callMethod(null, null, 0, new Object[0]);
            Assert.assertEquals(1, result.length);
            Assert.assertEquals(Boolean.FALSE, result[0]);
        }
    }

    @Test
    public void aDisabledAnalyzerRejectsEverything() {
        Config.enableAnalyzers = false;
        for (TileEntityAnalyzer analyzer : analyzers()) {
            try {
                analyzer.callMethod(null, null, 1, new Object[0]);
                Assert.fail("expected a LuaException");
            } catch (LuaException expected) {
                Assert.assertTrue(
                    expected.getMessage()
                        .contains("disabled"));
            } catch (Exception e) {
                Assert.fail("expected a LuaException, got " + e);
            }
        }
    }

    @Test
    public void anAnalyzerIsItself() {
        for (TileEntityAnalyzer analyzer : analyzers()) {
            Assert.assertTrue(analyzer.equals(analyzer));
            Assert.assertFalse(analyzer.equals(Mockito.mock(TileEntityAnalyzer.class)));
        }
    }

    @Test
    public void theAnalyzerHasOneSlot() {
        for (TileEntityAnalyzer analyzer : analyzers()) {
            Assert.assertEquals(1, analyzer.getSize());
        }
    }
}
