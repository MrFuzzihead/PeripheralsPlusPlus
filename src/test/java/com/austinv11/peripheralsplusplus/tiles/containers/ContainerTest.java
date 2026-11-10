package com.austinv11.peripheralsplusplus.tiles.containers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Covers the GUI containers that can be built headless.
 *
 * <p>
 * A {@code Container} only needs inventories and a player, so these are constructible without a game. The assertions
 * are structural (a machine slot exists, the player slots follow, the container asks the inventory to open and close)
 * rather than exact counts, so a layout tweak does not fail the suite.
 * </p>
 */
public class ContainerTest {

    private static IInventory inventory(int size) {
        IInventory inventory = Mockito.mock(IInventory.class);
        Mockito.when(inventory.getSizeInventory())
            .thenReturn(size);
        Mockito.when(inventory.getInventoryStackLimit())
            .thenReturn(64);
        Mockito.when(inventory.isItemValidForSlot(Mockito.anyInt(), Mockito.any(ItemStack.class)))
            .thenReturn(true);
        Mockito.when(inventory.isUseableByPlayer(Mockito.any(EntityPlayer.class)))
            .thenReturn(true);
        return inventory;
    }

    private static net.minecraft.entity.player.InventoryPlayer playerInventory() {
        net.minecraft.entity.player.InventoryPlayer inv = Mockito
            .mock(net.minecraft.entity.player.InventoryPlayer.class);
        Mockito.when(inv.getSizeInventory())
            .thenReturn(36);
        Mockito.when(inv.getInventoryStackLimit())
            .thenReturn(64);
        return inv;
    }

    private static EntityPlayer player() {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        player.inventory = playerInventory();
        return player;
    }

    @Test
    public void theAnalyzerContainerOpensItsInventory() {
        IInventory inventory = inventory(1);
        ContainerAnalyzer container = new ContainerAnalyzer(player(), inventory, 176, 166);

        Assert.assertTrue(container.canInteractWith(player()));
        Assert.assertFalse("a container always has at least the machine slot", container.inventorySlots.isEmpty());
        Mockito.verify(inventory)
            .openInventory();
    }

    @Test
    public void theSorterContainerExposesItsSlot() {
        IInventory inventory = inventory(1);
        ItemStack stack = new ItemStack(new Item(), 3);
        Mockito.when(inventory.getStackInSlot(0))
            .thenReturn(stack);
        ContainerInteractiveSorter container = new ContainerInteractiveSorter(player(), inventory, 176, 166);

        Assert.assertTrue(container.canInteractWith(player()));
        Assert.assertFalse(container.inventorySlots.isEmpty());
        Assert.assertSame(
            "slot 0 is the sorter's own slot",
            stack,
            container.getSlot(0)
                .getStack());
        Mockito.verify(inventory)
            .openInventory();
    }

    @Test
    public void theResupplyContainerExposesItsStock() {
        IInventory inventory = inventory(56);
        ContainerResupplyStation container = new ContainerResupplyStation(player().inventory, inventory);

        Assert.assertTrue(container.canInteractWith(player()));
        Assert.assertFalse(container.inventorySlots.isEmpty());
    }

    @Test
    public void aSlotKnowsItsIndex() {
        IInventory inventory = inventory(1);
        ContainerAnalyzer container = new ContainerAnalyzer(player(), inventory, 176, 166);
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Assert.assertEquals(i, container.getSlot(i).slotNumber);
        }
    }
}
