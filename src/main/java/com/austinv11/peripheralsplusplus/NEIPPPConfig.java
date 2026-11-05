package com.austinv11.peripheralsplusplus;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.austinv11.peripheralsplusplus.creativetab.CreativeTabPPP;
import com.austinv11.peripheralsplusplus.nei.TurtleRecipeHandler;
import com.austinv11.peripheralsplusplus.reference.Reference;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.api.turtle.ITurtleUpgrade;

public class NEIPPPConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        List<ITurtleUpgrade> list = CreativeTabPPP.upgrades;
        ItemStack base = GameRegistry.findItemStack("ComputerCraft", "CC-TurtleExpanded", 1);
        if (base != null) {
            for (ITurtleUpgrade upgrade : list) {
                ItemStack upg1 = base.copy();
                upg1.stackTagCompound = new NBTTagCompound();
                upg1.stackTagCompound.setShort("leftUpgrade", (short) upgrade.getUpgradeID());
                API.addItemListEntry(upg1);
            }
        }
        ItemStack base2 = GameRegistry.findItemStack("ComputerCraft", "CC-TurtleAdvanced", 1);
        if (base2 == null) return;
        API.addItemListEntry(base2);
        for (ITurtleUpgrade upgrade : list) {
            ItemStack upg1 = base2.copy();
            upg1.stackTagCompound = new NBTTagCompound();
            upg1.stackTagCompound.setShort("leftUpgrade", (short) upgrade.getUpgradeID());
            API.addItemListEntry(upg1);
        }

        API.registerRecipeHandler(new TurtleRecipeHandler());
        API.registerUsageHandler(new TurtleRecipeHandler());
    }

    @Override
    public String getName() {
        return Reference.MOD_NAME + " NEI Plugin";
    }

    @Override
    public String getVersion() {
        return "2.0";
    }
}
