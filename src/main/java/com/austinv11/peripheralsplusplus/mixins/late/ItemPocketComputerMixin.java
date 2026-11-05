package com.austinv11.peripheralsplusplus.mixins.late;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.austinv11.peripheralsplusplus.hooks.ComputerCraftHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;

/**
 * Replaces the {@code ItemPocketComputer} ASM transformer: attaches this mod's peripheral to a pocket computer when
 * its server computer is created, refreshes it every tick and applies the upgraded item name.
 *
 * <p>
 * {@code remap = false} is required: {@code ItemPocketComputer} is a mod class, so its members have no obfuscation
 * mapping. That also means Minecraft members cannot be referenced from the handler bodies (they would keep their
 * deobfuscated names in production), so the handlers only call into {@link ComputerCraftHooks}.
 *
 * <p>
 * {@code onUpdate} and {@code getItemStackDisplayName} are Minecraft overrides, so they are named differently in
 * production (SRG) builds - both names are listed for each.
 *
 * <p>
 * Only ComputerCraft 2.x is supported; see {@code Mixins.COMPUTERCRAFT_POCKET_COMPUTER}.
 */
@Mixin(value = ItemPocketComputer.class, remap = false)
public abstract class ItemPocketComputerMixin {

    @Inject(method = "createServerComputer", at = @At("RETURN"), require = 1)
    private void ppp$attachPocketComputerPeripheral(World world, IInventory inventory, ItemStack stack,
        CallbackInfoReturnable<ServerComputer> cir) {
        ComputerCraftHooks.onPocketComputerCreate(cir.getReturnValue(), stack, inventory);
    }

    @ModifyExpressionValue(
        method = { "onUpdate", "func_77663_a" },
        at = @At(
            value = "INVOKE",
            target = "Ldan200/computercraft/shared/pocket/items/ItemPocketComputer;createServerComputer(Lnet/minecraft/world/World;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/item/ItemStack;)Ldan200/computercraft/shared/computer/core/ServerComputer;"),
        require = 1)
    private ServerComputer ppp$updatePocketComputerPeripheral(ServerComputer computer,
        @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) Entity entity) {
        ComputerCraftHooks.update(entity, stack, computer);
        return computer;
    }

    @Inject(method = { "getItemStackDisplayName", "func_77653_i" }, at = @At("RETURN"), cancellable = true, require = 1)
    private void ppp$pocketComputerDisplayName(ItemStack stack, CallbackInfoReturnable<String> cir) {
        String name = ComputerCraftHooks.getPocketComputerName(stack);
        // null means the computer carries one of ComputerCraft's own upgrades: keep the name ComputerCraft produced
        if (name != null) cir.setReturnValue(name);
    }
}
