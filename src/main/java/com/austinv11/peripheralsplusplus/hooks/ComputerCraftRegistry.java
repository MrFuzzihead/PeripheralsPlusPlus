package com.austinv11.peripheralsplusplus.hooks;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.austinv11.peripheralsplusplus.mixins.late.CCTurtleProxyCommonAccessor;
import com.austinv11.peripheralsplusplus.reference.Reference;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;

/**
 * This is the entrance point for any registry-related objects including peripherals and APIs
 */
public class ComputerCraftRegistry {

    public static HashMap<Integer, IPocketComputerUpgrade> pocketUpgrades = new HashMap<Integer, IPocketComputerUpgrade>();
    public static HashMap<ItemStack, Integer> craftingRecipes = new HashMap<ItemStack, Integer>();

    /**
     * Read access to ComputerCraft's turtle upgrade map.
     *
     * <p>
     * This used to unwrap the private {@code CCTurtleProxyCommon.m_turtleUpgrades} field and the
     * {@code ComputerCraft.turtleProxy} field reflectively; the mixin accessor replaces the reflection.
     */
    public static Map<Integer, ITurtleUpgrade> getTurtleUpgrades() throws ComputerCraftNotFoundException {
        checkCC();
        if (ComputerCraft.turtleProxy instanceof CCTurtleProxyCommonAccessor) {
            return ((CCTurtleProxyCommonAccessor) ComputerCraft.turtleProxy).ppp$getTurtleUpgrades();
        }
        return new HashMap<Integer, ITurtleUpgrade>();
    }

    /**
     * Use this to register pocket computer upgrades
     *
     * <p>
     * Ids below {@link Reference#FIRST_UNRESERVED_POCKET_UPGRADE_ID} are refused: ComputerCraft reserves 1-6 for its
     * own upgrades and would treat a lower id as one of its own.
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
        craftingRecipes.put(upgrade.getCraftingItem(), upgrade.getUpgradeID());
    }

    private static void checkCC() throws ComputerCraftNotFoundException {
        if (!Loader.isModLoaded("ComputerCraft")) throw new ComputerCraftNotFoundException();
    }
}
