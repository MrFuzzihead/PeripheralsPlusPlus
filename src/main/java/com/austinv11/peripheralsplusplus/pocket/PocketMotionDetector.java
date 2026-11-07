package com.austinv11.peripheralsplusplus.pocket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.hooks.IPocketComputerUpgrade;
import com.austinv11.peripheralsplusplus.init.ModItems;
import com.austinv11.peripheralsplusplus.pocket.peripherals.PeripheralMotionDetector;
import com.austinv11.peripheralsplusplus.reference.Reference;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;

public class PocketMotionDetector implements IPocketComputerUpgrade {

    @Override
    public int getUpgradeID() {
        return Reference.MOTION_DETECTOR;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "peripheralsplusplus.pocketUpgrade.motionDetector";
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ModItems.motionDetector);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * ComputerCraft builds this from {@code ItemPocketComputer.createServerComputer} with no entity, because at that
     * point the computer may not be held by anything yet. The detector starts with no last known location and picks one
     * up from the first {@link #update} that has an entity.
     * </p>
     */
    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PeripheralMotionDetector(access.getEntity());
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        Entity entity = access.getEntity();
        // Not held by an entity (being crafted, lying in a chest, dropped): there is nothing to track, and the
        // peripheral keeps reporting its last known location, which is what IPocketAccess#getEntity documents.
        if (entity == null) return;
        ((PeripheralMotionDetector) peripheral).update(entity);
    }

    @Override
    public boolean onRightClick(World world, EntityPlayer player, ItemStack stack) {
        return false;
    }
}
