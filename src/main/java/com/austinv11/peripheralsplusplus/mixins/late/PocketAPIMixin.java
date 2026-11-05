package com.austinv11.peripheralsplusplus.mixins.late;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dan200.computercraft.shared.pocket.apis.PocketAPI;

/**
 * Makes {@code pocket.equipBack()} refuse to overwrite an upgrade it does not know about.
 *
 * <p>
 * ComputerCraft's own check is {@code PocketUpgrades.isUpgrade(currentId)} (2.x: {@code == 1}), i.e. it only refuses
 * when an upgrade <em>it</em> owns is installed. Because this mod's upgrades start at id
 * {@code Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID}, the Lua call would happily replace one of them with a modem
 * and orphan the peripheral - and {@code pocket.unequipBack()} could not give it back. Refuse whenever any upgrade is
 * installed, which is what the original check intends.
 */
@Mixin(value = PocketAPI.class, remap = false)
public abstract class PocketAPIMixin {

    @Shadow
    private ItemStack m_stack;

    @Inject(method = "doEquipBack", at = @At("HEAD"), cancellable = true, require = 1)
    private void ppp$refuseEquipOverInstalledUpgrade(CallbackInfoReturnable<Object[]> cir) {
        ItemStack stack = this.m_stack;
        if (stack == null) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("upgrade")) {
            cir.setReturnValue(new Object[] { false, "Upgrade already equipped" });
        }
    }
}
