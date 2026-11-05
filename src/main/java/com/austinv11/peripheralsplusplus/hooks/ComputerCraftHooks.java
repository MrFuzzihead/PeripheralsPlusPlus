package com.austinv11.peripheralsplusplus.hooks;

import java.util.HashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.pocket.PocketPeripheralContainer;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;

public class ComputerCraftHooks {

    public static HashMap<Integer, IPeripheral> cachedPeripherals = new HashMap<Integer, IPeripheral>();
    public static HashMap<Integer, HashMap<Integer, IPeripheral>> cachedExtraPeripherals = new HashMap<Integer, HashMap<Integer, IPeripheral>>();

    public static void onPocketComputerCreate(ServerComputer computer, ItemStack stack, IInventory inventory) {
        if (computer == null || stack == null) return;
        if (!NBTHelper.hasTag(stack, "upgrade")) return;
        int upgrade = (int) NBTHelper.getShort(stack, "upgrade");
        IPocketComputerUpgrade pocketUpgrade = ComputerCraftRegistry.pocketUpgrades.get(upgrade);
        // Not one of ours: ComputerCraft's own upgrades (ids 1-6) and other mods' upgrades are none of our business.
        if (pocketUpgrade == null) return;
        IPeripheral peripheral = cachedPeripherals.containsKey(computer.getID())
            ? cachedPeripherals.get(computer.getID())
            : pocketUpgrade.createPeripheral(inventory == null ? null : ((InventoryPlayer) inventory).player, stack);
        cachedPeripherals.put(computer.getID(), peripheral);
        computer.setPeripheral(2, peripheral);
        if (pocketUpgrade instanceof PocketPeripheralContainer && NBTHelper.hasTag(stack, "upgrades")) {
            if (!cachedExtraPeripherals.containsKey(computer.getID()))
                cachedExtraPeripherals.put(computer.getID(), new HashMap<Integer, IPeripheral>());
            NBTTagList list = NBTHelper.getList(stack, "upgrades", Constants.NBT.TAG_FLOAT);
            HashMap<Integer, IPeripheral> peripherals = cachedExtraPeripherals.get(computer.getID());
            for (int i = 0; i < list.tagCount(); i++) {
                int id = (int) list.func_150308_e(i);
                IPocketComputerUpgrade extraUpgrade = ComputerCraftRegistry.pocketUpgrades.get(id);
                if (extraUpgrade == null || peripherals.containsKey(id)) continue;
                peripherals.put(
                    id,
                    extraUpgrade
                        .createPeripheral(inventory == null ? null : ((InventoryPlayer) inventory).player, stack));
            }
        }
    }

    public static String getName(String baseName, ItemStack stack) {
        if (!NBTHelper.hasTag(stack, "upgrade")) return StatCollector.translateToLocal(baseName + ".name");
        IPocketComputerUpgrade pocketUpgrade = ComputerCraftRegistry.pocketUpgrades
            .get((int) NBTHelper.getShort(stack, "upgrade"));
        if (pocketUpgrade == null) return StatCollector.translateToLocal(baseName + ".name");
        return StatCollector.translateToLocal(baseName + ".upgraded.name")
            .replace("%s", StatCollector.translateToLocal(pocketUpgrade.getUnlocalisedAdjective()));
    }

    /**
     * The display name hook used by the pocket computer mixin. The unlocalised name is looked up here so the mixin
     * itself does not reference (and therefore cannot mis-remap) Minecraft members.
     *
     * @return the name this mod gives the computer, or {@code null} if it carries one of ComputerCraft's own upgrades
     *         (or no upgrade at all), in which case the caller must keep the name ComputerCraft produced.
     */
    public static String getPocketComputerName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (!NBTHelper.hasTag(stack, "upgrade")) return null;
        if (!ComputerCraftRegistry.pocketUpgrades.containsKey((int) NBTHelper.getShort(stack, "upgrade"))) return null;
        return getName(
            stack.getItem()
                .getUnlocalizedName(stack),
            stack);
    }

    public static void update(Entity entity, ItemStack stack, ServerComputer computer) {
        if (computer == null || stack == null) return;
        if (!NBTHelper.hasTag(stack, "upgrade")) return;
        int upgrade = (int) NBTHelper.getShort(stack, "upgrade");
        IPeripheral peripheral = cachedPeripherals.get(computer.getID());
        IPocketComputerUpgrade pocketUpgrade = ComputerCraftRegistry.pocketUpgrades.get(upgrade);
        if (peripheral == null || pocketUpgrade == null) return;
        pocketUpgrade.update(entity, stack, peripheral);
    }

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
            .get((int) NBTHelper.getShort(stack, "upgrade"));
        // Not one of ours (ComputerCraft's own upgrades, or another mod's): leave the click to them.
        if (pocketUpgrade == null) return false;
        return pocketUpgrade
            .onRightClick(world, player, stack, cachedPeripherals.get(NBTHelper.getInt(stack, "computerID")));
    }
}
