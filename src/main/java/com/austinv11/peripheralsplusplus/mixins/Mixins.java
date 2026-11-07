package com.austinv11.peripheralsplusplus.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * The mixin table. Early mixins are loaded through {@link EarlyMixinsLoader}.
 *
 * <p>
 * There is no late-mixin set any more: ComputerCraft 3.3.0 made everything this mod used to reach through
 * {@code ItemPocketComputer}, {@code PocketAPI} and {@code CCTurtleProxyCommon} mixins available directly. Pocket
 * computer upgrades are registered with {@code ComputerCraftAPI.registerPocketUpgrade}, which lets ComputerCraft
 * create,
 * tick and name their peripherals, refuse to equip over an installed upgrade, and hand out the installed upgrade item
 * from {@code pocket.unequipBack()}.
 * </p>
 */
public enum Mixins implements IMixins {

    /** Accessors and invokers that replace the old {@code ppp_at.cfg} access transformer. */
    VANILLA_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("EntityLivingAccessor", "EntityLivingBaseAccessor", "ItemBucketAccessor")
        .addClientMixins("GuiAccessor"));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
