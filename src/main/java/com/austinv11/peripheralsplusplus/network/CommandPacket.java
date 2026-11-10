package com.austinv11.peripheralsplusplus.network;

import java.util.ArrayDeque;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.client.gui.GuiHelmet;
import com.austinv11.peripheralsplusplus.client.gui.GuiSmartHelmetOverlay;
import com.austinv11.peripheralsplusplus.smarthelmet.HelmetCommand;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CommandPacket implements IMessage {

    public HelmetCommand[] commands;
    public UUID uuid;
    public boolean doAdd = false;
    public boolean isGui = false;

    public CommandPacket() {}

    public CommandPacket(HelmetCommand[] commands, UUID uuid) {
        this.commands = commands;
        this.uuid = uuid;
    }

    public CommandPacket(HelmetCommand[] commands, UUID uuid, boolean doAdd) {
        this(commands, uuid);
        this.doAdd = doAdd;
    }

    public CommandPacket(HelmetCommand[] commands, UUID uuid, boolean doAdd, boolean isGui) {
        this(commands, uuid, doAdd);
        this.isGui = isGui;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tagCompound = ByteBufUtils.readTag(buf);
        String rawUuid = tagCompound.getString("uuid");
        if (rawUuid.isEmpty()) {
            uuid = null;
        } else {
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("CommandPacket carries a malformed uuid", e);
            }
        }
        int num = Math.max(0, tagCompound.getInteger("num"));
        commands = new HelmetCommand[num];
        for (int i = 0; i < num; i++) {
            NBTTagCompound tag = tagCompound.getCompoundTag(i + "");
            HelmetCommand command = HelmetCommand.getCommandFromName(tag.getString("type"));
            if (command == null) {
                PeripheralsPlusPlus.LOGGER
                    .error("Ignoring unknown helmet command type '" + tag.getString("type") + "'");
                continue;
            }
            try {
                command.readFromNBT(tag.getCompoundTag("command"));
                commands[i] = command;
            } catch (RuntimeException e) {
                PeripheralsPlusPlus.LOGGER.error("Ignoring malformed helmet command: " + e.getMessage());
            }
        }
        doAdd = tagCompound.getBoolean("doAdd");
        isGui = tagCompound.getBoolean("isGui");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tagCompound = new NBTTagCompound();
        // An absent uuid reads back as null on the other side.
        if (uuid != null) tagCompound.setString("uuid", uuid.toString());
        tagCompound.setInteger("num", commands == null ? 0 : commands.length);
        for (int i = 0; commands != null && i < commands.length; i++) {
            if (commands[i] == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", commands[i].getCommandName());
            NBTTagCompound command = new NBTTagCompound();
            commands[i].writeToNBT(command);
            tag.setTag("command", command);
            tagCompound.setTag(i + "", tag);
        }
        tagCompound.setBoolean("doAdd", doAdd);
        tagCompound.setBoolean("isGui", isGui);
        ByteBufUtils.writeTag(buf, tagCompound);
    }

    public static class CommandPacketHandler implements IMessageHandler<CommandPacket, IMessage> {

        @Override
        public IMessage onMessage(CommandPacket message, MessageContext ctx) {
            ArrayDeque<HelmetCommand> commands = new ArrayDeque<HelmetCommand>();
            if (message.doAdd) {
                if (!message.isGui) {
                    if (GuiSmartHelmetOverlay.renderStack.containsKey(message.uuid))
                        commands = GuiSmartHelmetOverlay.renderStack.get(message.uuid);
                } else {
                    if (GuiHelmet.renderStack.containsKey(message.uuid))
                        commands = GuiHelmet.renderStack.get(message.uuid);
                }
            }
            for (HelmetCommand command : message.commands) if (command != null) commands.offer(command);
            // A packet with no uuid cannot belong to a player, and the render stacks are ConcurrentHashMaps, which
            // reject a null key.
            if (message.uuid == null) return null;
            if (message.isGui) {
                GuiHelmet.renderStack.put(message.uuid, commands);
            } else {
                GuiSmartHelmetOverlay.renderStack.put(message.uuid, commands);
            }
            return null;
        }
    }
}
