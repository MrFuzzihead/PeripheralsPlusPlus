package com.austinv11.peripheralsplusplus.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.PathNavigate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code EntityLiving#currentTarget} and {@code EntityLiving#navigator}, which used to be made public by
 * {@code ppp_at.cfg}.
 */
@Mixin(EntityLiving.class)
public interface EntityLivingAccessor {

    @Accessor("currentTarget")
    void ppp$setCurrentTarget(Entity target);

    @Accessor("navigator")
    PathNavigate ppp$getNavigator();
}
