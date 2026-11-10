package com.austinv11.peripheralsplusplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client-only half of the ridable turtle.
 *
 * <p>
 * {@code EntityRidableTurtle} used to import {@code net.minecraft.client.Minecraft} and
 * {@code org.lwjgl.input.Keyboard} directly. That works only because Java resolves those classes lazily and every use
 * happened to be behind a {@code worldObj.isRemote} guard; one refactor that drops a guard turns it into a
 * {@code NoClassDefFoundError} on a dedicated server (CODE_REVIEW.md section 5.32). All client calls live here, and
 * this class is only loaded from a {@code @SideOnly(Side.CLIENT)} path.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class RidableTurtleInput {

    /** Cached so the per-tick "is the player holding a stick" test does not allocate an ItemStack every tick. */
    private static final ItemStack CARROT_ON_A_STICK = new ItemStack(Items.carrot_on_a_stick);
    private static final ItemStack STICK = new ItemStack(Items.stick);

    private RidableTurtleInput() {}

    /**
     * @return the local player, or {@code null} before the world is joined
     */
    private static EntityPlayer localPlayer() {
        return Minecraft.getMinecraft().thePlayer;
    }

    /**
     * Whether the player is asking to mount: left control is held, or they are holding a carrot on a stick / stick.
     *
     * @return true when the turtle should be collidable
     */
    public static boolean wantsToRide() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) return true;
        EntityPlayer player = localPlayer();
        if (player == null) return false;
        ItemStack item = player.getHeldItem();
        return item != null && (item.isItemEqual(CARROT_ON_A_STICK) || item.isItemEqual(STICK));
    }

    /**
     * @param riddenBy the entity riding the turtle, or {@code null}
     * @return the movement code the local player is requesting, or {@code -1} for none
     */
    public static int requestedMovement(Entity riddenBy) {
        EntityPlayer player = localPlayer();
        if (player == null || riddenBy != player) return -1;
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) return 0; // FORWARD
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) return 1; // TURN_RIGHT
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) return 2; // TURN_LEFT
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) return 3; // DESCEND
        if (Keyboard.isKeyDown(Keyboard.KEY_X)) return 4; // ASCEND
        return -1;
    }
}
