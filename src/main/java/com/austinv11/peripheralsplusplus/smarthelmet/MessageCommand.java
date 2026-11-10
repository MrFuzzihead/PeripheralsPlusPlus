package com.austinv11.peripheralsplusplus.smarthelmet;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the lines a chat box sent to this helmet.
 *
 * <p>
 * The lines used to live in a single {@code static Stack} that every {@code MessageCommand} shared, so on a client
 * with more than one helmet wearer (or more than one command in flight) one player's messages were drawn on the
 * other's HUD and {@link #readFromNBT} cleared everybody's list. The payload now travels with the command, as a proper
 * NBT list rather than a {@code Stack#toString()} round trip that mangled any message containing a comma
 * (CODE_REVIEW.md section 5.31).
 * </p>
 */
public class MessageCommand extends HelmetCommand {

    /** Per-player queue of pending lines, filled by {@code LuaObjectHUD} on the server. */
    public static final ConcurrentHashMap<String, Stack<String>> messageStacks = new ConcurrentHashMap<String, Stack<String>>();

    /** The player whose queue this command serialises, or {@code null} for a command with no owner. */
    public String player;

    /** The lines this command draws; populated by {@link #readFromNBT} on the client. */
    private final List<String> lines = new ArrayList<String>();

    @SideOnly(Side.CLIENT)
    @Override
    public void call(Gui gui) {
        int y = 1;
        // Insertion order, top to bottom. The old code popped a Stack, so the newest line was drawn first.
        for (String line : lines) {
            gui.drawString(Minecraft.getMinecraft().fontRenderer, line, 1, y, Color.white.getRGB());
            y += 8;
        }
    }

    @Override
    public String getCommandName() {
        return "MessageCommand";
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        lines.clear();
        NBTTagList list = tagCompound.getTagList("lines", 8);
        for (int i = 0; i < list.tagCount(); i++) lines.add(list.getStringTagAt(i));
    }

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        NBTTagList list = new NBTTagList();
        Stack<String> queued = player == null ? null : messageStacks.get(player);
        if (queued != null) {
            // NBTTagString rejects ""; a blank line draws nothing anyway.
            for (String line : queued) if (line != null && !line.isEmpty()) list.appendTag(new NBTTagString(line));
        }
        tagCompound.setTag("lines", list);
    }
}
