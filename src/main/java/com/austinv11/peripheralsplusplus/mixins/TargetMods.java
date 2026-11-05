package com.austinv11.peripheralsplusplus.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

public enum TargetMods implements ITargetMod {

    COMPUTERCRAFT("ComputerCraft");

    private final TargetModBuilder builder;

    TargetMods(String modId) {
        this.builder = new TargetModBuilder().setModId(modId);
    }

    TargetMods(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass)
            .setModId(modId);
    }

    @Nonnull
    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
