package com.austinv11.peripheralsplusplus.hooks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dan200.computercraft.api.pocket.IPocketUpgrade;

/**
 * A Peripherals++ pocket computer upgrade: ComputerCraft's {@link IPocketUpgrade}, plus the only hook it lacks.
 *
 * <p>
 * Since ComputerCraft 3.3.0 an upgrade registered with
 * {@link dan200.computercraft.api.ComputerCraftAPI#registerPocketUpgrade(IPocketUpgrade)} is created, ticked and named
 * by ComputerCraft itself, so {@code createPeripheral} and {@code update} are the API's own methods and this interface
 * only adds {@link #onRightClick}. Before 3.3.0 this mod attached and ticked the peripheral itself with an
 * {@code ItemPocketComputer} mixin.
 * </p>
 */
public interface IPocketComputerUpgrade extends IPocketUpgrade {

    /**
     * Called when the pocket computer is right clicked on something.
     *
     * <p>
     * {@link IPocketUpgrade} has no equivalent. Upstream's {@code onRightClick} also suppresses the pocket computer's
     * GUI, which 1.7.10's {@code ItemPocketComputer} opens on both sides without a packet of its own, so this is still
     * driven by this mod's own {@code PlayerInteractEvent} handler in {@link ComputerCraftHooks}.
     * </p>
     *
     * @param world  The world the computer is in
     * @param player The player right clicking
     * @param stack  The stack representing the computer
     * @return True to stop the gui from opening or vice versa
     */
    public boolean onRightClick(World world, EntityPlayer player, ItemStack stack);
}
