package com.austinv11.peripheralsplusplus.mixins.early;

import net.minecraft.client.gui.Gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Gui#drawGradientRect}, which used to be made public by {@code ppp_at.cfg}.
 *
 * <p>
 * {@code drawVerticalLine} and {@code drawHorizontalLine} were also listed in the access transformer, but nothing
 * calls them (the Lua HUD draws lines as one pixel tall/wide rectangles), so they are not exposed here.
 */
@Mixin(Gui.class)
public interface GuiAccessor {

    @Invoker("drawGradientRect")
    void ppp$drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor);
}
