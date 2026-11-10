package com.austinv11.peripheralsplusplus.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.tiles.containers.ContainerAnalyzer;
import com.austinv11.peripheralsplusplus.tiles.containers.ContainerInteractiveSorter;
import com.austinv11.peripheralsplusplus.tiles.containers.ContainerPlayerInterface;
import com.austinv11.peripheralsplusplus.tiles.containers.ContainerResupplyStation;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class GuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // The block may have been replaced between the packet being sent and the GUI opening, so the tile entity is
        // checked rather than cast (CODE_REVIEW.md section 6.14).
        IInventory inventory = inventoryAt(world, x, y, z);
        if (id == Reference.GUIs.ANALYZER.ordinal()) {
            return inventory == null ? null : new ContainerAnalyzer(player, inventory, 176, 166);
        } else if (id == Reference.GUIs.INTERACTIVE_SORTER.ordinal()) {
            return inventory == null ? null : new ContainerInteractiveSorter(player, inventory, 176, 166);
        } else if (id == Reference.GUIs.PLAYER_INTERFACE.ordinal()) {
            return inventory == null ? null : new ContainerPlayerInterface(player, inventory, 176, 133);
        } else if (id == Reference.GUIs.RESUPPLY_STATION.ordinal()) {
            return inventory == null ? null : new ContainerResupplyStation(player.inventory, inventory);
        }
        return null;
    }

    /**
     * @param world the world the GUI was requested in
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     * @return the inventory at that position, or {@code null}
     */
    private static IInventory inventoryAt(World world, int x, int y, int z) {
        if (world == null) return null;
        net.minecraft.tileentity.TileEntity tileEntity = world.getTileEntity(x, y, z);
        return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == Reference.GUIs.ANALYZER.ordinal()) {
            return new GuiAnalyzer(player, world, x, y, z);
        } else if (id == Reference.GUIs.HELMET.ordinal()) {
            return new GuiHelmet();
        } else if (id == Reference.GUIs.INTERACTIVE_SORTER.ordinal()) {
            return new GuiInteractiveSorter(player, world, x, y, z);
        } else if (id == Reference.GUIs.PLAYER_INTERFACE.ordinal()) {
            return new GuiPlayerInterface(player, x, y, z);
        } else if (id == Reference.GUIs.PERMCARD.ordinal()) {
            return player.getCurrentEquippedItem() == null ? null : new GuiPermCard(player.getCurrentEquippedItem());
        } else if (id == Reference.GUIs.RESUPPLY_STATION.ordinal()) {
            IInventory inventory = inventoryAt(world, x, y, z);
            return inventory == null ? null : new GuiResupplyStation(player.inventory, inventory);
        }
        return null;
    }
}
