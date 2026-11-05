package com.austinv11.peripheralsplusplus.mixins.early;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBucket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code ItemBucket#isFull}, which used to be made public by {@code ppp_at.cfg}.
 */
@Mixin(ItemBucket.class)
public interface ItemBucketAccessor {

    @Accessor("isFull")
    Block ppp$getFullBlock();
}
