package com.austinv11.peripheralsplusplus.event.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.items.ItemBlockPeripheralContainer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.api.peripheral.IPeripheral;

public class PeripheralContainerHandler {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        // Server-authoritative only: running this on the client popped a phantom item out of the stack.
        if (event.entityPlayer == null || event.entityPlayer.worldObj.isRemote) return;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR)
            if (event.entityPlayer.getCurrentEquippedItem() != null && event.entityPlayer.getCurrentEquippedItem()
                .getItem() instanceof ItemBlockPeripheralContainer) {
                    InventoryPlayer inv = event.entityPlayer.inventory;
                    List<String> text = new ArrayList<String>();
                    ItemStack stack = event.entityPlayer.getCurrentEquippedItem();
                    NBTHelper.removeInfo(stack);
                    text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
                    int[] ids = NBTHelper.getIntArray(stack, "ids");
                    if (ids.length > 0) {
                        Block popped = Block.getBlockById(ids[0]);
                        if (popped == null) {
                            // The block was removed with its mod; drop the stale id instead of creating a null stack.
                            NBTHelper.removeTag(stack, "ids");
                            return;
                        }
                        if (ids.length > 1) {
                            int[] newIds = new int[ids.length - 1];
                            for (int j = 0; j < ids.length - 1; j++) newIds[j] = ids[j + 1];
                            NBTHelper.setIntArray(stack, "ids", newIds);
                            for (int id : newIds) {
                                IPeripheral iPeripheral = peripheralOf(Block.getBlockById(id));
                                if (iPeripheral != null) text.add(Colors.RESET + iPeripheral.getType());
                            }
                            if (text.size() > 1) NBTHelper.setInfo(stack, text);
                        } else {
                            NBTHelper.removeTag(stack, "ids");
                        }
                        inv.addItemStackToInventory(new ItemStack(popped));
                    }
                }
    }

    /**
     * @param block the stored block, or {@code null} when the id no longer resolves
     * @return the block's peripheral, or {@code null}
     */
    private static IPeripheral peripheralOf(Block block) {
        if (block == null) return null;
        TileEntity tile = block.createTileEntity(null, 0);
        return tile instanceof IPeripheral ? (IPeripheral) tile : null;
    }
}
