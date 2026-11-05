package com.austinv11.peripheralsplusplus.blocks;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.tiles.TileEntityPlayerInterface;

public class BlockPlayerInterface extends BlockContainerPPP {

    public BlockPlayerInterface() {
        super(Material.rock);
        this.setBlockName("playerInterface");
        this.setCreativeTab(CreativeTabPPP.PPP_TAB);
        this.setHardness(4f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int p_149915_2_) {
        return new TileEntityPlayerInterface();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int meta, float hitX,
        float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote) {
            if (te != null) {
                player.openGui(PeripheralsPlusPlus.instance, Reference.GUIs.PLAYER_INTERFACE.ordinal(), world, x, y, z);
            }
        }
        return true;
    }
}
