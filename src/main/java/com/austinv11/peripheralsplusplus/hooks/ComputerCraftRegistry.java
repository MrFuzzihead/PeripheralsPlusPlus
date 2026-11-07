package com.austinv11.peripheralsplusplus.hooks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.austinv11.peripheralsplusplus.reference.Reference;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;

/**
 * This is the entrance point for any registry-related objects including peripherals and APIs
 */
public class ComputerCraftRegistry {

    public static HashMap<Integer, IPocketComputerUpgrade> pocketUpgrades = new HashMap<Integer, IPocketComputerUpgrade>();
    public static HashMap<ItemStack, Integer> craftingRecipes = new HashMap<ItemStack, Integer>();

    /**
     * Read access to ComputerCraft's turtle upgrade map, used by the NEI recipe handler.
     *
     * <p>
     * ComputerCraft 3.3.0 made {@code CCTurtleProxyCommon.getTurtleUpgrades()} public, so this used to unwrap the
     * private {@code m_turtleUpgrades} field through an accessor mixin and no longer needs to. The method is declared
     * on the implementation, not on the {@code ICCTurtleProxy} interface {@code ComputerCraft.turtleProxy} is typed as,
     * hence the cast. An empty map means the proxy is not a {@code CCTurtleProxyCommon}, which cannot happen with
     * turtles enabled.
     * </p>
     */
    public static Map<Integer, ITurtleUpgrade> getTurtleUpgrades() throws ComputerCraftNotFoundException {
        checkCC();
        if (ComputerCraft.turtleProxy instanceof CCTurtleProxyCommon) {
            return ((CCTurtleProxyCommon) ComputerCraft.turtleProxy).getTurtleUpgrades();
        }
        return Collections.emptyMap();
    }

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
        craftingRecipes.put(upgrade.getCraftingItem(), upgrade.getUpgradeID());
        ComputerCraftAPI.registerPocketUpgrade(upgrade);
    }

    private static void checkCC() throws ComputerCraftNotFoundException {
        if (!Loader.isModLoaded("ComputerCraft")) throw new ComputerCraftNotFoundException();
    }
}
