package com.austinv11.peripheralsplusplus.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.reference.Reference;

public abstract class BlockAnalyzer extends BlockContainerPPP {

    public BlockAnalyzer() {
        super(Material.rock);
        this.setCreativeTab(CreativeTabPPP.PPP_TAB);
        this.setHardness(4f);
    }

    public abstract Block getBlock();

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int meta, float hitX,
        float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote) {
            if (te != null)
                player.openGui(PeripheralsPlusPlus.instance, Reference.GUIs.ANALYZER.ordinal(), world, x, y, z);
        }
        return true;
    }

    @Override
    public abstract TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_);

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

}
