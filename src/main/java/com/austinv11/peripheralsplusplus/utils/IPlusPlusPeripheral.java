package com.austinv11.peripheralsplusplus.utils;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.peripheral.IPeripheralTargeted;

/**
 * Implement this on any TileEntity in Peripherals++ instead of {@code IPeripheral} to have a way of detecting if a
 * peripheral is from this mod.
 *
 * <p>
 * It is also {@link IPeripheralTargeted}, because every implementor is a {@link TileEntity} that other peripherals need
 * to reach. ComputerCraft's {@code inventory} peripheral resolves the name in {@code pushItems}/{@code pullItems}
 * through {@link IPeripheralTargeted#getTarget()} to find the inventory behind it, and OpenPeripheral's
 * {@code AdapterPeripheral} is built on the interface so Java code can get back to the tile. Without it a tile is only
 * addressable by the {@code peripheral.wrap} name it was given, never as an inventory target.
 * </p>
 */
public interface IPlusPlusPeripheral extends IPeripheralTargeted {

    /**
     * The tile entity behind this peripheral.
     *
     * <p>
     * Always the peripheral itself: Peripherals++ peripherals are TileEntities that implement {@link IPeripheral}
     * directly, not adapters over one.
     * </p>
     *
     * @return {@code this}, as a {@link TileEntity}
     */
    @Override
    default Object getTarget() {
        return this;
    }

    /**
     * This is the common provider for all Peripherals++ TileEntities
     */
    public static class Provider implements IPeripheralProvider {

        @Override
        public IPeripheral getPeripheral(World world, int x, int y, int z, int side) {
            TileEntity tile = world.getTileEntity(x, y, z);
            return tile instanceof IPlusPlusPeripheral ? (IPlusPlusPeripheral) tile : null;
        }
    }
}
