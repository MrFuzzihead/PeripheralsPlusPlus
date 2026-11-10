package com.austinv11.peripheralsplusplus.client.gui;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.items.ItemSmartHelmet;
import com.austinv11.peripheralsplusplus.smarthelmet.HelmetCommand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiSmartHelmetOverlay extends Gui {

    public static final Map<UUID, ArrayDeque<HelmetCommand>> renderStack = new ConcurrentHashMap<UUID, ArrayDeque<HelmetCommand>>();

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (/* event.isCanceled() || */event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        if (Minecraft.getMinecraft().thePlayer.getCurrentArmor(3) != null
            && Minecraft.getMinecraft().thePlayer.getCurrentArmor(3)
                .getItem() instanceof ItemSmartHelmet)
            if (NBTHelper.hasTag(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier")) {
                UUID uuid = UUID.fromString(
                    NBTHelper.getString(Minecraft.getMinecraft().thePlayer.getCurrentArmor(3), "identifier"));
                ArrayDeque<HelmetCommand> queued = renderStack.get(uuid);
                if (queued != null) {
                    ArrayDeque<HelmetCommand> commands = new ArrayDeque<HelmetCommand>(queued);
                    while (!commands.isEmpty()) commands.poll()
                        .call(this);
                }
            }
    }
}
