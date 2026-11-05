package com.austinv11.peripheralsplusplus.event.handler;

import net.minecraftforge.event.entity.EntityEvent;

import com.austinv11.peripheralsplusplus.entities.NanoProperties;
import com.austinv11.peripheralsplusplus.reference.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PropertiesHandler {

    @SubscribeEvent
    public void onEntityConstruct(EntityEvent.EntityConstructing event) {
        if (Config.enableNanoBots) {
            if (event.entity.getExtendedProperties(NanoProperties.IDENTIFIER) == null) {
                event.entity.registerExtendedProperties(NanoProperties.IDENTIFIER, new NanoProperties());
            }
        }
    }
}
