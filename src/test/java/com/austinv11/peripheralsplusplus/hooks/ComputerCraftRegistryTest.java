package com.austinv11.peripheralsplusplus.hooks;

import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;

/**
 * Covers the pocket-computer upgrade registry.
 *
 * <p>
 * The registry refuses ids below ComputerCraft's reserved range and duplicate ids, and it used to keep a second
 * {@code HashMap<ItemStack, Integer>} of crafting items — a hash map keyed by a type that has no {@code hashCode},
 * iterated with {@code isItemEqual} (CODE_REVIEW.md section 5.11).
 * </p>
 */
public class ComputerCraftRegistryTest {

    @After
    public void tearDown() {
        ComputerCraftRegistry.pocketUpgrades.clear();
        ComputerCraftRegistry.pocketUpgradeList.clear();
    }

    private static IPocketComputerUpgrade upgrade(int id, String adjective) {
        // One item instance per upgrade: findUpgradeFor compares items by identity.
        final net.minecraft.item.Item crafting = new net.minecraft.item.Item();
        return new IPocketComputerUpgrade() {

            @Override
            public int getUpgradeID() {
                return id;
            }

            @Override
            public String getUnlocalisedAdjective() {
                return adjective;
            }

            @Override
            public ItemStack getCraftingItem() {
                return new ItemStack(crafting, 1);
            }

            @Override
            public IPeripheral createPeripheral(IPocketAccess access) {
                return null;
            }

            @Override
            public boolean onRightClick(net.minecraft.world.World world,
                net.minecraft.entity.player.EntityPlayer player, ItemStack stack) {
                return false;
            }
        };
    }

    @Test
    public void aValidUpgradeIsRegisteredInBothPlaces() throws Exception {
        IPocketComputerUpgrade upgrade = upgrade(7, "motionDetector");
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("ComputerCraft"))
                .thenReturn(true);
            ComputerCraftRegistry.registerPocketUpgrade(upgrade);
            cc.verify(() -> ComputerCraftAPI.registerPocketUpgrade(upgrade));
        }
        Assert.assertSame(upgrade, ComputerCraftRegistry.pocketUpgrades.get(7));
        Assert.assertTrue(ComputerCraftRegistry.pocketUpgradeList.contains(upgrade));
    }

    @Test
    public void anIdInComputerCraftsRangeIsRefused() {
        try (MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("ComputerCraft"))
                .thenReturn(true);
            try {
                ComputerCraftRegistry.registerPocketUpgrade(upgrade(1, "reserved"));
                Assert.fail("expected InvalidUpgradeIDException");
            } catch (InvalidUpgradeIDException expected) {
                // ComputerCraft owns ids 1-6.
            } catch (Exception e) {
                Assert.fail("expected InvalidUpgradeIDException, got " + e);
            }
        }
        Assert.assertTrue(ComputerCraftRegistry.pocketUpgrades.isEmpty());
    }

    @Test
    public void aDuplicateIdIsRefused() throws Exception {
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("ComputerCraft"))
                .thenReturn(true);
            ComputerCraftRegistry.registerPocketUpgrade(upgrade(7, "first"));
            try {
                ComputerCraftRegistry.registerPocketUpgrade(upgrade(7, "second"));
                Assert.fail("expected InvalidUpgradeIDException");
            } catch (InvalidUpgradeIDException expected) {
                // expected
            }
        }
        Assert.assertEquals(1, ComputerCraftRegistry.pocketUpgrades.size());
        Assert.assertEquals(
            "first",
            ComputerCraftRegistry.pocketUpgrades.get(7)
                .getUnlocalisedAdjective());
    }

    @Test
    public void registeringWithoutComputerCraftIsRefused() {
        try (MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded(Mockito.anyString()))
                .thenReturn(false);
            try {
                ComputerCraftRegistry.registerPocketUpgrade(upgrade(7, "x"));
                Assert.fail("expected ComputerCraftNotFoundException");
            } catch (ComputerCraftNotFoundException expected) {
                // expected
            } catch (Exception e) {
                Assert.fail("expected ComputerCraftNotFoundException, got " + e);
            }
        }
    }

    @Test
    public void findUpgradeForMatchesByItem() throws Exception {
        IPocketComputerUpgrade upgrade = upgrade(7, "motionDetector");
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("ComputerCraft"))
                .thenReturn(true);
            ComputerCraftRegistry.registerPocketUpgrade(upgrade);
        }
        Assert.assertSame(upgrade, ComputerCraftRegistry.findUpgradeFor(upgrade.getCraftingItem()));
        Assert.assertNull(ComputerCraftRegistry.findUpgradeFor(new ItemStack(new net.minecraft.item.Item(), 1)));
        Assert.assertNull(ComputerCraftRegistry.findUpgradeFor(null));
    }

    @Test
    public void theRegistryIsKeyedByUpgradeId() throws Exception {
        try (MockedStatic<ComputerCraftAPI> cc = Mockito.mockStatic(ComputerCraftAPI.class);
            MockedStatic<Loader> loader = Mockito.mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("ComputerCraft"))
                .thenReturn(true);
            ComputerCraftRegistry.registerPocketUpgrade(upgrade(7, "a"));
            ComputerCraftRegistry.registerPocketUpgrade(upgrade(8, "b"));
        }
        Assert.assertEquals(2, ComputerCraftRegistry.pocketUpgrades.size());
        Assert.assertEquals(
            "b",
            ComputerCraftRegistry.pocketUpgrades.get(8)
                .getUnlocalisedAdjective());
        Assert.assertEquals(2, ComputerCraftRegistry.pocketUpgradeList.size());
    }

    @Test
    public void anUnknownUuidIsJustAbsent() {
        Assert.assertNull(ComputerCraftRegistry.pocketUpgrades.get(999));
        Assert.assertNull(ComputerCraftRegistry.pocketUpgrades.get(UUID.randomUUID()));
    }
}
