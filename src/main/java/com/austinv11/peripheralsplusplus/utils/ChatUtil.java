package com.austinv11.peripheralsplusplus.utils;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.network.ChatPacket;

import cpw.mods.fml.common.network.NetworkRegistry;

/**
 * Sends the chat box's messages to the players in range.
 *
 * <p>
 * The range used to be whatever the Lua program asked for (the chat box's {@code sayRange} config was not applied by
 * {@code tell}), and players were matched by {@code getDisplayName()}, which includes scoreboard team prefixes and
 * nickname-mod decorations (CODE_REVIEW.md sections 5.7 and 5.8).
 * </p>
 */
public final class ChatUtil {

    private ChatUtil() {}

    /**
     * Clamps a requested chat/speech range to the configured maximum.
     *
     * @param requested  the range the Lua program asked for
     * @param configured the configured maximum; negative means unlimited
     * @return the range to use, with {@link Double#MAX_VALUE} meaning "every player"
     */
    public static double clampRange(double requested, double configured) {
        if (configured < 0) return requested < 0 ? Double.MAX_VALUE : requested;
        if (requested < 0) return configured;
        return Math.min(requested, configured);
    }

    /**
     * Builds the chat line a peripheral sends.
     *
     * <p>
     * Pure, so the formatting rules are unit testable without a world: coordinate logging wins, then the explicit
     * label, then a generic {@code [@]} tag.
     * </p>
     *
     * @param text         the message body
     * @param label        the optional label, ignored when coordinate logging is on
     * @param logCoords    whether {@link #getCoordsPrefix(TileEntity)} should be used
     * @param coordsPrefix the already-computed coordinate prefix, may be {@code null} when logging is off
     * @return the message to send
     */
    public static String formatMessage(String text, String label, boolean logCoords, String coordsPrefix) {
        if (logCoords) return (coordsPrefix == null ? "" : coordsPrefix) + text;
        return label == null ? "[@] " + text : "[" + label + "] " + text;
    }

    /**
     * Broadcasts to every player in range.
     *
     * @param te         the sender, used for the position and dimension
     * @param text       the message
     * @param range      the range, {@link Double#MAX_VALUE} for every player
     * @param unlimitedY whether to ignore the vertical distance
     */
    public static void sendMessage(TileEntity te, String text, double range, boolean unlimitedY) {
        if (te == null || te.getWorldObj() == null) return;
        if (range == Double.MAX_VALUE) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return;
            for (EntityPlayer player : server.getConfigurationManager().playerEntityList) {
                PeripheralsPlusPlus.NETWORK.sendTo(new ChatPacket(text), (EntityPlayerMP) player);
            }
        } else if (unlimitedY) {
            for (EntityPlayer player : te.getWorldObj().playerEntities) {
                Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);
                playerPos.yCoord = te.yCoord;
                if (playerPos.distanceTo(Vec3.createVectorHelper(te.xCoord, te.yCoord, te.zCoord)) > range) continue;
                PeripheralsPlusPlus.NETWORK.sendTo(new ChatPacket(text), (EntityPlayerMP) player);
            }
        } else {
            PeripheralsPlusPlus.NETWORK.sendToAllAround(
                new ChatPacket(text),
                new NetworkRegistry.TargetPoint(
                    te.getWorldObj().provider.dimensionId,
                    te.xCoord,
                    te.yCoord,
                    te.zCoord,
                    range));
        }
    }

    /**
     * Sends to one named player, if they are in range.
     *
     * @param ign        the player's username
     * @param te         the sender, used for the position and dimension
     * @param text       the message
     * @param range      the range, {@link Double#MAX_VALUE} for any distance
     * @param unlimitedY whether to ignore the vertical distance
     * @return true when the message was delivered
     */
    public static boolean sendMessage(String ign, TileEntity te, String text, double range, boolean unlimitedY) {
        if (te == null || te.getWorldObj() == null) return false;
        EntityPlayer player = getPlayer(ign, range == Double.MAX_VALUE ? null : te.getWorldObj());
        if (player == null) return false;
        Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);
        if (unlimitedY) playerPos.yCoord = te.yCoord;
        if (playerPos.distanceTo(Vec3.createVectorHelper(te.xCoord, te.yCoord, te.zCoord)) > range) return false;
        PeripheralsPlusPlus.NETWORK.sendTo(new ChatPacket(text), (EntityPlayerMP) player);
        return true;
    }

    /**
     * @param ign the username to look for
     * @param w   the world to search, or {@code null} for every online player
     * @return the player, or {@code null}
     */
    public static EntityPlayer getPlayer(String ign, World w) {
        if (ign == null) return null;
        List<? extends EntityPlayer> players;
        if (w == null) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return null;
            players = server.getConfigurationManager().playerEntityList;
        } else {
            players = w.playerEntities;
        }
        for (EntityPlayer p : players) {
            // getCommandSenderName() is the username; getDisplayName() carries scoreboard prefixes.
            if (p.getCommandSenderName()
                .equalsIgnoreCase(ign)) return p;
        }
        return null;
    }

    public static String getCoordsPrefix(TileEntity te) {
        return "[#" + te.xCoord + "," + te.yCoord + "," + te.zCoord + "] ";
    }

    public static String getCoordsPrefix(Entity ent) {
        return "[#" + ent.posX + "," + ent.posY + "," + ent.posZ + "] ";
    }
}
