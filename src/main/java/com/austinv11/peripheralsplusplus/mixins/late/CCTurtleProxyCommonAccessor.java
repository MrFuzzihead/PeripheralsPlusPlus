package com.austinv11.peripheralsplusplus.mixins.late;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;

/**
 * Exposes ComputerCraft's turtle upgrade map, which {@code hooks.ComputerCraftRegistry} used to read via reflection
 * so the NEI handler can list the registered upgrades.
 */
@Mixin(value = CCTurtleProxyCommon.class, remap = false)
public interface CCTurtleProxyCommonAccessor {

    @Accessor("m_turtleUpgrades")
    Map<Integer, ITurtleUpgrade> ppp$getTurtleUpgrades();
}
