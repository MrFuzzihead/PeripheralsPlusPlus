package com.austinv11.peripheralsplusplus.items;

import net.minecraft.creativetab.CreativeTabs;

import com.austinv11.collectiveframework.minecraft.items.ItemBase;
import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.reference.Reference;

public class ItemPPP extends ItemBase {

    @Override
    public CreativeTabs getTab() {
        return CreativeTabPPP.PPP_TAB;
    }

    @Override
    public String getModId() {
        return Reference.MOD_ID;
    }
}
