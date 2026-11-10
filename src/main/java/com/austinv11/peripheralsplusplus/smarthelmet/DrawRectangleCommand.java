package com.austinv11.peripheralsplusplus.smarthelmet;

import java.awt.Color;

import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;

import com.austinv11.peripheralsplusplus.mixins.early.GuiAccessor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class DrawRectangleCommand extends HelmetCommand {

    public int x1, y1, x2, y2;
    /** Never null: {@link #writeToNBT} dereferenced it and a command built without a colour crashed the sender. */
    public Color color = Color.WHITE;
    public Color color2;

    @SideOnly(Side.CLIENT)
    @Override
    public void call(Gui gui) {
        // GL11.glColor4f(color.getRed()/255, color.getGreen()/255, color.getBlue()/255, color.getAlpha()/255);
        int start = (color == null ? Color.WHITE : color).getRGB();
        if (color2 == null) Gui.drawRect(x1, y1, x2, y2, start);
        else((GuiAccessor) gui).ppp$drawGradientRect(x1, y1, x2, y2, start, color2.getRGB());
    }

    @Override
    public String getCommandName() {
        return "DrawRectangleCommand";
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        x1 = tagCompound.getInteger("x1");
        x2 = tagCompound.getInteger("x2");
        y1 = tagCompound.getInteger("y1");
        y2 = tagCompound.getInteger("y2");
        color = new Color(tagCompound.getInteger("rgb"), true);
        if (color.getAlpha() == 0) color = Color.WHITE;
        if (tagCompound.hasKey("rgb2")) color2 = new Color(tagCompound.getInteger("rgb2"), true);
    }

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        tagCompound.setInteger("x1", x1);
        tagCompound.setInteger("x2", x2);
        tagCompound.setInteger("y1", y1);
        tagCompound.setInteger("y2", y2);
        tagCompound.setInteger("rgb", (color == null ? Color.WHITE : color).getRGB());
        if (color2 != null) tagCompound.setInteger("rgb2", color2.getRGB());
    }
}
