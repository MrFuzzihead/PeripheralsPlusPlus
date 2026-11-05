package com.austinv11.peripheralsplusplus.mixins.early;

import net.minecraft.block.BlockDispenser;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the dispenser's top icon, which used to be made public by {@code ppp_at.cfg}.
 *
 * <p>
 * {@code dispenseBehaviorRegistry} was also in the access transformer, but it is already public in vanilla.
 */
@Mixin(BlockDispenser.class)
public interface BlockDispenserAccessor {

    @Accessor("field_149945_N")
    IIcon ppp$getDispenserTopIcon();
}
