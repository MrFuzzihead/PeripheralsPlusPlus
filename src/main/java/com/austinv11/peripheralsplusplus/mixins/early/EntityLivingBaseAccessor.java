package com.austinv11.peripheralsplusplus.mixins.early;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code EntityLivingBase#isJumping}, which used to be made public by {@code ppp_at.cfg}.
 */
@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseAccessor {

    @Accessor("isJumping")
    boolean ppp$isJumping();
}
