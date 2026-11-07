package com.austinv11.peripheralsplusplus.pocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.hooks.ComputerCraftRegistry;
import com.austinv11.peripheralsplusplus.hooks.IPocketComputerUpgrade;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.pocket.peripherals.PeripheralPeripheralContainer;
import com.austinv11.peripheralsplusplus.reference.Reference;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;

/**
 * The pocket computer equivalent of the peripheral container: one pocket upgrade slot carrying several peripherals.
 *
 * <p>
 * ComputerCraft only attaches the single peripheral {@link #getUpgradeID()} names, so the peripherals of the upgrades
 * in the stack's {@code upgrades} list are kept here, in the {@link PeripheralPeripheralContainer} handed to the
 * computer. They used to live in a static map keyed by computer id, filled in by the {@code ItemPocketComputer} mixin.
 * </p>
 */
public class PocketPeripheralContainer implements IPocketComputerUpgrade {

    @Override
    public int getUpgradeID() {
        return Reference.PERIPHERAL_CONTAINER;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "peripheralsplusplus.pocketUpgrade.peripheralContainer";
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ModBlocks.peripheralContainer);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        Map<Integer, IPeripheral> contained = new HashMap<Integer, IPeripheral>();
        for (IPocketComputerUpgrade upgrade : getUpgrades(access.getStack())) {
            IPeripheral peripheral = upgrade.createPeripheral(access);
            if (peripheral != null) contained.put(upgrade.getUpgradeID(), peripheral);
        }
        return new PeripheralPeripheralContainer(contained);
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        if (!(peripheral instanceof PeripheralPeripheralContainer)) return;
        Map<Integer, IPeripheral> contained = ((PeripheralPeripheralContainer) peripheral).getContainedPeripherals();
        for (IPocketComputerUpgrade upgrade : getUpgrades(access.getStack())) {
            IPeripheral containedPeripheral = contained.get(upgrade.getUpgradeID());
            // The upgrade was removed from the list after the peripherals were built, or it attached nothing.
            if (containedPeripheral != null) upgrade.update(access, containedPeripheral);
        }
    }

    @Override
    public boolean onRightClick(World world, EntityPlayer player, ItemStack stack) {
        boolean handled = false;
        for (IPocketComputerUpgrade upgrade : getUpgrades(stack))
            // Deliberately not short-circuiting: every contained upgrade gets the click, and any of them may claim it.
            if (upgrade.onRightClick(world, player, stack)) handled = true;
        return handled;
    }

    public List<IPocketComputerUpgrade> getUpgrades(ItemStack item) {
        ArrayList<IPocketComputerUpgrade> upgrades = new ArrayList<IPocketComputerUpgrade>();
        if (NBTHelper.hasTag(item, "upgrades")) {
            NBTTagList list = NBTHelper.getList(item, "upgrades", Constants.NBT.TAG_FLOAT);
            for (int i = 0; i < list.tagCount(); i++) {
                IPocketComputerUpgrade upgrade = ComputerCraftRegistry.pocketUpgrades.get((int) list.func_150308_e(i));
                // An id from a mod that is no longer installed: skip it rather than NPE below.
                if (upgrade != null) upgrades.add(upgrade);
            }
        }
        return upgrades;
    }
}
