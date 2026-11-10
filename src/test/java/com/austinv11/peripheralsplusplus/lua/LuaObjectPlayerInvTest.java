package com.austinv11.peripheralsplusplus.lua;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerInterface;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the player interface's inventory handle.
 *
 * <p>
 * The permission checks used to NPE for a card that had never been right-clicked (no tag at all) or for a player
 * holding no card, and every failure fell through to an empty result instead of a Lua error
 * (CODE_REVIEW.md section 4.18). The inventory moves themselves go through {@code InventoryUtils}, which has its own
 * test, and the remote-inventory wiring is covered by the in-game suite.
 * </p>
 */
public class LuaObjectPlayerInvTest {

    private static final Item IRON = new Item().setMaxStackSize(64);

    @After
    public void tearDown() {
        Config.enableInterfacePermissions = true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void aCardWithoutATagDeniesEverything() {
        // A freshly crafted card has no NBT until it is right-clicked; this used to be an NPE.
        LuaObjectPlayerInv handle = handle(IRON, 4, null, new ItemStack(new Item(), 1));
        for (int method = 0; method < 5; method++) {
            final int m = method;
            assertLuaError(() -> handle.callMethod(null, m, argumentsFor(m)), "may not");
        }
    }

    @Test
    public void noCardAtAllDeniesEverything() {
        LuaObjectPlayerInv handle = handle(IRON, 4, null, null);
        assertLuaError(() -> handle.callMethod(null, 0, new Object[] { 0.0d }), "may not");
        assertLuaError(() -> handle.callMethod(null, 4, new Object[0]), "may not");
    }

    @Test
    public void permissionsOffAllowsEverything() throws Exception {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(IRON, 4, null, null);
        Assert.assertEquals(1, handle.callMethod(null, 4, new Object[0]).length);
    }

    @Test
    public void aCardGrantsOnlyItsFlags() {
        ItemStack card = new ItemStack(new Item(), 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("getStacks", true);
        card.setTagCompound(tag);

        LuaObjectPlayerInv handle = handle(IRON, 4, null, card);
        try {
            Assert.assertEquals(1, handle.callMethod(null, 4, new Object[0]).length);
        } catch (Exception e) {
            Assert.fail("getStacks is set, so getSize must be allowed: " + e);
        }
        assertLuaError(() -> handle.callMethod(null, 1, new Object[] { 0.0d, 1.0d }), "withdraw");
        assertLuaError(() -> handle.callMethod(null, 3, new Object[] { 0.0d, 1.0d }), "deposit");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void getStackInSlotDescribesAnItem() throws Exception {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(IRON, 7, null, null);
        Object[] result = handle.callMethod(null, 0, new Object[] { 0.0d });
        Assert.assertEquals(1, result.length);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) result[0];
        Assert.assertEquals(7, map.get("amount"));
        Assert.assertEquals(0, map.get("meta"));
    }

    @Test
    public void getStackInSlotToleratesAnEmptySlotAndABadIndex() throws Exception {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(null, 0, null, null);
        Assert.assertEquals(0, handle.callMethod(null, 0, new Object[] { 0.0d }).length);
        // Out of range must be a nil result, not an IndexOutOfBounds.
        Assert.assertEquals(0, handle.callMethod(null, 0, new Object[] { 99.0d }).length);
    }

    @Test
    public void getSizeReportsTheInventorySize() throws Exception {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(IRON, 1, playerInventory(36), null);
        Assert.assertArrayEquals(new Object[] { 36 }, handle.callMethod(null, 4, new Object[0]));
    }

    @Test
    public void aNonNumericSlotIsRejected() {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(IRON, 1, null, null);
        assertLuaError(() -> handle.callMethod(null, 0, new Object[] { "first" }), "number");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Remote sides
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void anUnsetSideIsReported() {
        Config.enableInterfacePermissions = false;
        LuaObjectPlayerInv handle = handle(IRON, 4, null, null);
        assertLuaError(() -> handle.callMethod(null, 1, new Object[] { 0.0d, 1.0d }), "has not been set");
        assertLuaError(() -> handle.callMethod(null, 3, new Object[] { 0.0d, 1.0d }), "has not been set");
    }

    @Test
    public void theMethodListIsStable() {
        Assert.assertArrayEquals(
            new String[] { "getStackInSlot", "retrieveFromSlot", "pushToSlot", "push", "getSize" },
            handle(null, 0, null, null).getMethodNames());
    }

    @Test
    public void anUnknownMethodReturnsNothing() throws Exception {
        Config.enableInterfacePermissions = false;
        Assert.assertEquals(0, handle(IRON, 1, null, null).callMethod(null, 99, new Object[0]).length);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Scaffolding
    // ---------------------------------------------------------------------------------------------------------

    private static Object[] argumentsFor(int method) {
        switch (method) {
            case 0:
                return new Object[] { 0.0d };
            case 1:
                return new Object[] { 0.0d, 1.0d };
            case 2:
                return new Object[] { 0.0d, 0.0d, 1.0d };
            case 3:
                return new Object[] { 0.0d, 1.0d };
            default:
                return new Object[0];
        }
    }

    private static InventoryPlayer playerInventory(int size) {
        InventoryPlayer inventory = Mockito.mock(InventoryPlayer.class);
        Mockito.when(inventory.getSizeInventory())
            .thenReturn(size);
        Mockito.when(inventory.getInventoryStackLimit())
            .thenReturn(64);
        return inventory;
    }

    /**
     * @param item     the item in slot 0, or {@code null} for an empty player inventory
     * @param amount   the stack size
     * @param remote   the player interface's own inventory (unused here)
     * @param permCard the card held, or {@code null}
     * @return a handle wired to a player interface with no sides configured
     */
    private static LuaObjectPlayerInv handle(Item item, int amount, InventoryPlayer given, ItemStack permCard) {
        InventoryPlayer inventory = given != null ? given : Mockito.mock(InventoryPlayer.class);
        if (given == null) {
            Mockito.when(inventory.getSizeInventory())
                .thenReturn(1);
            Mockito.when(inventory.getInventoryStackLimit())
                .thenReturn(64);
        }
        ItemStack stack = item == null ? null : new ItemStack(item, amount);
        Mockito.when(inventory.getStackInSlot(Mockito.anyInt()))
            .thenReturn(stack);

        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        player.inventory = inventory;

        TileEntityPlayerInterface te = new TileEntityPlayerInterface();
        World world = Mockito.mock(World.class);
        Mockito.when(world.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(false);
        te.setWorldObj(world);
        return new LuaObjectPlayerInv(player, te, permCard);
    }

    private static void assertLuaError(ThrowingCall call, String expectedFragment) {
        try {
            call.run();
            Assert.fail("expected a LuaException mentioning " + expectedFragment);
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .toLowerCase()
                    .contains(expectedFragment.toLowerCase()));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    private interface ThrowingCall {

        void run() throws Exception;
    }
}
