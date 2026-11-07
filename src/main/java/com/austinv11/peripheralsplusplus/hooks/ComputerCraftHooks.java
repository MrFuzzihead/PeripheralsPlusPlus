package com.austinv11.peripheralsplusplus.hooks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;

/**
 * The one pocket computer hook ComputerCraft 3.3.0 does not provide.
 *
 * <p>
 * Everything else this class used to do is now ComputerCraft's job: the peripheral is attached by
 * {@code ItemPocketComputer.createServerComputer} and ticked by {@code onUpdate} through
 * {@link dan200.computercraft.api.pocket.IPocketUpgrade}, {@code pocket.equipBack()} refuses to equip over an installed
 * upgrade, and the upgraded item name comes from
 * {@link dan200.computercraft.api.pocket.IPocketUpgrade#getUnlocalisedAdjective()}.
 * The {@code cachedPeripherals}/{@code cachedExtraPeripherals} maps that supported that are gone with the mixins.
 * </p>
 */
public class ComputerCraftHooks {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.isCanceled()) if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
            if (event.useItem != Event.Result.DENY)
                if (event.entityPlayer.getCurrentEquippedItem() != null) if (event.entityPlayer.getCurrentEquippedItem()
                    .getItem() instanceof ItemPocketComputer)
                    event.setCanceled(
                        rightClick(event.world, event.entityPlayer, event.entityPlayer.getCurrentEquippedItem()));
    }

    public static boolean rightClick(World world, EntityPlayer player, ItemStack stack) {
        if (!NBTHelper.hasTag(stack, "upgrade")) return false;
        IPocketComputerUpgrade pocketUpgrade = ComputerCraftRegistry.pocketUpgrades
            .get(NBTHelper.getInt(stack, "upgrade"));
        // Not one of ours (ComputerCraft's own upgrades, or another mod's): leave the click to them.
        if (pocketUpgrade == null) return false;
        return pocketUpgrade.onRightClick(world, player, stack);
    }
}
