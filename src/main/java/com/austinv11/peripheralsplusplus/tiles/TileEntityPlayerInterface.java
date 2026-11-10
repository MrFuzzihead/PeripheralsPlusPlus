package com.austinv11.peripheralsplusplus.tiles;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.lua.LuaObjectPlayerInv;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityPlayerInterface extends MountedTileEntityInventory {

    public static final String publicName = "playerInterfacePPP";
    public ForgeDirection outputSide;
    public ForgeDirection inputSide;

    public TileEntityPlayerInterface() {
        super();
        this.invName = "PlayerInterface";
    }

    @Override
    public int getSize() {
        return 8;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        if (Config.enablePlayerInterface) {
            if (method == 0) {
                LuaArgs arguments = LuaArgs.of(rawArguments);
                String name = arguments.string(0);
                MinecraftServer server = MinecraftServer.getServer();
                if (server == null) throw new LuaException("No server is running");
                // Check that the specified player exists
                for (EntityPlayer player : (Iterable<? extends EntityPlayer>) server
                    .getConfigurationManager().playerEntityList) {
                    if (player.getCommandSenderName()
                        .equals(name)) {
                        // Check that the specified player has given permission for some sort of editing by putting
                        // their permissions card in the player interface
                        if (hasPermissionsCardFor(player) || !Config.enableInterfacePermissions) {
                            return new Object[] { new LuaObjectPlayerInv(player, this, getPermCardFor(player)) };
                        } else {
                            throw new LuaException("Missing permissions for player " + name);
                        }
                    }
                }
            } else if (method == 1 || method == 2) {
                LuaArgs arguments = LuaArgs.of(rawArguments);
                ForgeDirection side = arguments.direction(0);
                if (method == 1) outputSide = side;
                else inputSide = side;
            } else if (method == 3) {
                return new Object[] { outputSide == null ? null : outputSide.name() };
            } else if (method == 4) {
                return new Object[] { inputSide == null ? null : inputSide.name() };
            }
        } else {
            throw new LuaException("Player Interfaces have been disabled");
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getPlayerInv", "setOutputSide", "setInputSide",
        "getOutputSide", "getInputSide" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public String getType() {
        return publicName;
    }

    private boolean hasPermissionsCardFor(EntityPlayer player) {
        return getPermCardFor(player) != null;
    }

    private ItemStack getPermCardFor(EntityPlayer player) {
        for (ItemStack stack : items) {
            if (stack != null) {
                if (stack.hasTagCompound()) {
                    UUID uuid = NBTUtil.func_152459_a(NBTHelper.getCompoundTag(stack, "profile"))
                        .getId();
                    if (uuid.equals(
                        player.getGameProfile()
                            .getId())) {
                        return stack;
                    }
                }
            }
        }
        return null;
    }
}
