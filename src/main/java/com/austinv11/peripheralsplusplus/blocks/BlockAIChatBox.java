package com.austinv11.peripheralsplusplus.blocks;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.tiles.TileEntityAIChatBox;

public class BlockAIChatBox extends BlockPPP implements ITileEntityProvider {

    public BlockAIChatBox() {
        super();
        this.setBlockName("aiChatBox");
    }

    @Override
    public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
        return new TileEntityAIChatBox();
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }
}
