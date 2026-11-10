package com.austinv11.peripheralsplusplus.client.gui;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.items.ItemSmartHelmet;
import com.austinv11.peripheralsplusplus.network.InputEventPacket;
import com.austinv11.peripheralsplusplus.network.TextFieldInputEventPacket;
import com.austinv11.peripheralsplusplus.smarthelmet.AddButtonCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.AddTextFieldCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.HelmetCommand;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiHelmet extends GuiScreen {

    public static final Map<UUID, ArrayDeque<HelmetCommand>> renderStack = new ConcurrentHashMap<UUID, ArrayDeque<HelmetCommand>>();
    public HashMap<Integer, GuiTextField> textFields = new HashMap<Integer, GuiTextField>();

    @Override
    public void drawScreen(int mouseX, int mouseY, float renderPartialTicks) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (Minecraft.getMinecraft().thePlayer.getCurrentArmor(3) != null
            && Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                .getItem() instanceof ItemSmartHelmet)
            if (NBTHelper.hasTag(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier")) {
                UUID uuid = UUID.fromString(
                    NBTHelper.getString(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier"));
                // Read once: the packet handler on the netty thread can replace this entry concurrently.
                ArrayDeque<HelmetCommand> queued = renderStack.get(uuid);
                if (queued != null) {
                    ArrayDeque<HelmetCommand> commands = new ArrayDeque<HelmetCommand>(queued);
                    while (!commands.isEmpty()) {
                        HelmetCommand command = commands.poll();
                        if (!(command instanceof AddTextFieldCommand) && !(command instanceof AddButtonCommand))
                            command.call(this);
                    }
                }
            }
        super.drawScreen(mouseX, mouseY, renderPartialTicks);
        for (GuiTextField text : textFields.values()) text.drawTextBox();
    }

    @Override
    public void initGui() {
        super.initGui();
        textFields.clear();
        buttonList.clear();
        if (Minecraft.getMinecraft().thePlayer.getCurrentArmor(3) != null
            && Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                .getItem() instanceof ItemSmartHelmet)
            if (NBTHelper.hasTag(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier")) {
                UUID uuid = UUID.fromString(
                    NBTHelper.getString(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier"));
                // Read once: the packet handler on the netty thread can replace this entry concurrently.
                ArrayDeque<HelmetCommand> queued = renderStack.get(uuid);
                if (queued != null) {
                    ArrayDeque<HelmetCommand> commands = new ArrayDeque<HelmetCommand>(queued);
                    while (!commands.isEmpty()) {
                        HelmetCommand command = commands.poll();
                        if (command instanceof AddTextFieldCommand || command instanceof AddButtonCommand)
                            command.call(this);
                    }
                }
            }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public void addButton(GuiButton button) {
        buttonList.add(button);
    }

    public void addTextField(int id, GuiTextField field) {
        textFields.put(id, field);
    }

    @Override
    protected void mouseClicked(int x, int y, int mouseEvent) {
        super.mouseClicked(x, y, mouseEvent);
        for (GuiTextField text : textFields.values()) text.mouseClicked(x, y, mouseEvent);
        PeripheralsPlusPlus.NETWORK.sendToServer(
            new InputEventPacket(
                UUID.fromString(
                    Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                        .getTagCompound()
                        .getString("identifier")),
                Mouse.getEventButton(),
                Mouse.getEventButtonState(),
                "mouseInput",
                Minecraft.getMinecraft().thePlayer.getDisplayName()));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField text : textFields.values()) text.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char eventChar, int eventKey) {
        super.keyTyped(eventChar, eventKey);
        for (GuiTextField text : textFields.values())
            if (text.textboxKeyTyped(eventChar, eventKey)) PeripheralsPlusPlus.NETWORK.sendToServer(
                new TextFieldInputEventPacket(
                    UUID.fromString(
                        Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                            .getTagCompound()
                            .getString("identifier")),
                    eventChar + "",
                    text.getText(),
                    "textboxEntry",
                    Minecraft.getMinecraft().thePlayer.getDisplayName()));
        PeripheralsPlusPlus.NETWORK.sendToServer(
            new InputEventPacket(
                UUID.fromString(
                    Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                        .getTagCompound()
                        .getString("identifier")),
                Keyboard.getEventKey(),
                Keyboard.getEventKeyState(),
                "keyInput",
                Minecraft.getMinecraft().thePlayer.getDisplayName()));
    }
}
