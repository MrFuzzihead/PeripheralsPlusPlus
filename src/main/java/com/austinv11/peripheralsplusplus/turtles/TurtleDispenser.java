package com.austinv11.peripheralsplusplus.turtles;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.turtles.peripherals.PeripheralDispenser;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;

public class TurtleDispenser implements ITurtleUpgrade {

    @Override
    public int getUpgradeID() {
        return Reference.DISPENSER_UPGRADE;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "peripheralsplusplus.turtleUpgrade.dispenser";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(Blocks.dispenser);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new PeripheralDispenser(turtle);
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int direction) {
        return null;
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        // The dispenser's horizontal front texture. BlockDispenser.getIcon returns it when the requested side matches
        // a horizontal facing, so (3, 3) selects it without touching the protected, unmapped icon field the old
        // access transformer exposed.
        return Blocks.dispenser.getIcon(3, 3);
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {

    }
}
