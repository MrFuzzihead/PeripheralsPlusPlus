package com.austinv11.peripheralsplusplus.hooks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.item.ItemStack;

import com.austinv11.peripheralsplusplus.reference.Reference;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.api.ComputerCraftAPI;

/**
 * This is the entrance point for any registry-related objects including peripherals and APIs
 */
public class ComputerCraftRegistry {

    /**
     * Registered pocket upgrades by id. Concurrent because ComputerCraft reads this from the computer's server thread
     * while {@code equipBack()} and the crafting recipe can touch it from the player's thread.
     */
    public static final Map<Integer, IPocketComputerUpgrade> pocketUpgrades = new ConcurrentHashMap<Integer, IPocketComputerUpgrade>();

    /**
     * Registered pocket upgrades, in registration order.
     *
     * <p>
     * This used to be a {@code HashMap<ItemStack, Integer>} iterated with {@code isItemEqual}, which was both a
     * misuse of a hash map (as a list) and silently lossy: {@link ItemStack} does not override {@code hashCode}, so
     * two upgrades with equal items collided by identity. Reading the crafting item off each upgrade is exact and
     * allocation free.
     * </p>
     */
    public static final List<IPocketComputerUpgrade> pocketUpgradeList = new CopyOnWriteArrayList<IPocketComputerUpgrade>();

    /**
     * Use this to register pocket computer upgrades
     *
     * <p>
     * The upgrade is also handed to ComputerCraft's own pocket upgrade registry. That is what makes
     * {@code pocket.equipBack()}/{@code pocket.unequipBack()}, the upgraded item name and the peripheral attached when
     * a pocket computer's server computer is created all work for this mod, replacing the {@code ItemPocketComputer}
     * and {@code PocketAPI} mixins ComputerCraft 3.3.0 supersedes.
     * </p>
     *
     * <p>
     * Ids below {@link Reference#FIRST_UNRESERVED_POCKET_UPGRADE_ID} are refused: ComputerCraft reserves 1-6 for its
     * own upgrades and would reject a lower id from a mod anyway.
     * </p>
     *
     * @param upgrade The upgrade
     * @throws ComputerCraftNotFoundException
     * @throws InvalidUpgradeIDException
     */
    public static void registerPocketUpgrade(IPocketComputerUpgrade upgrade)
        throws ComputerCraftNotFoundException, InvalidUpgradeIDException {
        checkCC();
        if (upgrade.getUpgradeID() < Reference.FIRST_UNRESERVED_POCKET_UPGRADE_ID
            || pocketUpgrades.containsKey(upgrade.getUpgradeID())) throw new InvalidUpgradeIDException(upgrade);
        pocketUpgrades.put(upgrade.getUpgradeID(), upgrade);
        pocketUpgradeList.add(upgrade);
        ComputerCraftAPI.registerPocketUpgrade(upgrade);
    }

    /**
     * @param stack the item to match against every registered upgrade's crafting item
     * @return the upgrade that item installs, or {@code null}
     */
    public static IPocketComputerUpgrade findUpgradeFor(ItemStack stack) {
        if (stack == null) return null;
        for (IPocketComputerUpgrade upgrade : pocketUpgradeList) {
            ItemStack crafting = upgrade.getCraftingItem();
            if (crafting != null && stack.isItemEqual(crafting)) return upgrade;
        }
        return null;
    }

    private static void checkCC() throws ComputerCraftNotFoundException {
        if (!Loader.isModLoaded("ComputerCraft")) throw new ComputerCraftNotFoundException();
    }
}
