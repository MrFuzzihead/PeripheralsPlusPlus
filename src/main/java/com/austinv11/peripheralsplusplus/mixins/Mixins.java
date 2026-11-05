package com.austinv11.peripheralsplusplus.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * The mixin table. Early mixins are loaded through {@link EarlyMixinsLoader}, late (mod-targeting) mixins through
 * {@link LateMixinsLoader}.
 */
public enum Mixins implements IMixins {

    /** Accessors and invokers that replace the old {@code ppp_at.cfg} access transformer. */
    VANILLA_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins(
            "EntityLivingAccessor",
            "EntityLivingBaseAccessor",
            "ItemBucketAccessor",
            "BlockDispenserAccessor")
        .addClientMixins("GuiAccessor")),

    /**
     * Hooks the two pocket computer methods the old ASM transformer used to patch, so pocket computer upgrades get
     * their peripheral attached and updated.
     *
     * <p>
     * The upgrade ids this mod uses start at {@code Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID} (7), above the
     * 1-6 ComputerCraft reserves for its own upgrades, so these hooks ignore ComputerCraft's upgrades entirely.
     */
    COMPUTERCRAFT_POCKET_COMPUTER(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("ItemPocketComputerMixin", "PocketAPIMixin")
        .addRequiredMod(TargetMods.COMPUTERCRAFT)),

    /** Read access to ComputerCraft's turtle upgrade map, used by the NEI recipe handler. */
    COMPUTERCRAFT_TURTLE_UPGRADES(new MixinBuilder().setPhase(Phase.LATE)
        .addCommonMixins("CCTurtleProxyCommonAccessor")
        .addRequiredMod(TargetMods.COMPUTERCRAFT));

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
