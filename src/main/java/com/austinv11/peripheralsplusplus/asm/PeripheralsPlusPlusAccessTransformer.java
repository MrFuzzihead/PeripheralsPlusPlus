package com.austinv11.peripheralsplusplus.asm;

import java.io.IOException;

import cpw.mods.fml.common.asm.transformers.AccessTransformer;

public class PeripheralsPlusPlusAccessTransformer extends AccessTransformer {

    public PeripheralsPlusPlusAccessTransformer() throws IOException {
        super("META-INF/ppp_at.cfg");
    }
}
