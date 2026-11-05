package com.austinv11.peripheralsplusplus.blocks;

import net.minecraft.creativetab.CreativeTabs;

import com.austinv11.collectiveframework.minecraft.blocks.BlockBase;
import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.reference.Reference;

public class BlockPPP extends BlockBase {

    @Override
    public CreativeTabs getTab() {
        return CreativeTabPPP.PPP_TAB;
    }

    @Override
    public String getModId() {
        return Reference.MOD_ID;
    }
}
