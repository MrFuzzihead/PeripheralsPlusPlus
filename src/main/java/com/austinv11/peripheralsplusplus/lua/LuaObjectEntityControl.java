package com.austinv11.peripheralsplusplus.lua;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;

import com.austinv11.collectiveframework.minecraft.utils.Location;
import com.austinv11.collectiveframework.minecraft.utils.WorldUtils;
import com.austinv11.collectiveframework.utils.math.MathUtils;
import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.entities.NanoProperties;
import com.austinv11.peripheralsplusplus.items.ItemNanoSwarm;
import com.austinv11.peripheralsplusplus.mixins.early.EntityLivingAccessor;
import com.austinv11.peripheralsplusplus.mixins.early.EntityLivingBaseAccessor;
import com.austinv11.peripheralsplusplus.network.RobotEventPacket;
import com.austinv11.peripheralsplusplus.reference.Reference;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

public class LuaObjectEntityControl implements ILuaObject {

    /**
     * How far a Lua program may reach with {@code setTarget}/{@code setAttackTarget}. The world helper defaults its
     * range to {@link Double#MAX_VALUE}, so passing it explicitly is what stops a script from targeting an entity on
     * the other side of the world (CODE_REVIEW.md section 4.7).
     */
    private static final double MAX_TARGET_RANGE = 64.0D;

    private UUID id;

    private boolean isPlayer;
    private EntityPlayer player;
    private EntityLiving entity;

    public LuaObjectEntityControl(UUID id, Entity entity) {
        this.id = id;
        if (entity instanceof EntityPlayer) {
            isPlayer = true;
            player = (EntityPlayer) entity;
        } else {
            isPlayer = false;
            this.entity = (EntityLiving) entity;
        }
    }

    /**
     * @param arguments the Lua arguments; indices 0-2 are the coordinates
     * @param entity    the controlled entity, used for its world
     * @return the closest entity within {@link #MAX_TARGET_RANGE}, or {@code null}
     */
    private static Entity nearestEntity(LuaArgs arguments, Entity entity) throws LuaException {
        return WorldUtils.getNearestEntityToLocation(
            new Location(arguments.number(0), arguments.number(1), arguments.number(2), entity.worldObj),
            MAX_TARGET_RANGE);
    }

    @Override
    public String[] getMethodNames() {
        if (isPlayer) return new String[] { "isPlayer", "hurt", "heal", "getHealth", "getMaxHealth", "isDead",
            "getRemainingBots", "getDisplayName", "getPlayerName", "getUUID", "getHunger", "click", "clickRelease",
            "keyPress", "keyRelease", "mouseMove", "whisper" };
        else return new String[] { "isPlayer", "hurt", "heal", "getHealth", "getMaxHealth", "isDead",
            "getRemainingBots", "getDisplayName", "getEntityName", "setTarget", "setAttackTarget", "setMovementTarget",
            "setTurnAngle", "toggleJumping" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        LuaArgs arguments = LuaArgs.of(rawArguments);
        if (method < 8) {
            switch (method) {
                case 0:
                    return new Object[] { isPlayer };

                case 1:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player)) return new Object[] { false };
                        player.attackEntityFrom(
                            new DamageSource(Reference.MOD_ID.toLowerCase() + ".nanobot").setDamageBypassesArmor(),
                            1.0F);
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
                        entity.attackEntityFrom(
                            new DamageSource(Reference.MOD_ID.toLowerCase() + ".nanobot").setDamageBypassesArmor(),
                            1.0F);
                    }
                    break;

                case 2:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player)) return new Object[] { false };
                        player.heal(1.0F);
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
                        // This used to heal `player`, which is null in the mob branch (CODE_REVIEW.md section 4.8).
                        entity.heal(1.0F);
                    }
                    break;

                case 3:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getHealth() };
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { entity.getHealth() };
                    }

                case 4:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getMaxHealth() };
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { entity.getMaxHealth() };
                    }

                case 5:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.isDead };
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { entity.isDead };
                    }

                case 6:
                    if (isPlayer) {
                        return new Object[] {
                            ((NanoProperties) player.getExtendedProperties(NanoProperties.IDENTIFIER)).numOfBots };
                    } else {
                        return new Object[] {
                            ((NanoProperties) entity.getExtendedProperties(NanoProperties.IDENTIFIER)).numOfBots };
                    }

                case 7:
                    if (isPlayer) {
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getDisplayName() };
                    } else {
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { entity.getCustomNameTag() };
                    }
            }
        } else {
            if (isPlayer) {
                switch (method) {
                    case 8:
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getGameProfile()
                            .getName() };

                    case 9:
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getGameProfile()
                            .getId()
                            .toString() };

                    case 10:
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { player.getFoodStats()
                            .getFoodLevel() };

                    case 11:
                    case 12:
                    case 13:
                    case 14:
                    case 15:
                        // click/clickRelease take a button number, keyPress/keyRelease a key name and mouseMove two
                        // numbers. The old validation accepted either type for every method and then cast
                        // unconditionally, so click("left") threw a ClassCastException instead of a Lua error.
                        arguments.atLeast(method == 15 ? 2 : 1);

                        Object args;
                        RobotEventPacket.ActionType action;
                        if (method == 15) {
                            args = new int[] { arguments.integer(0), arguments.integer(1) };
                            action = RobotEventPacket.ActionType.MOUSE_MOVE;
                        } else if (method < 13) {
                            args = (Integer) arguments.integer(0);
                            action = RobotEventPacket.ActionType.MOUSE_CLICK;
                        } else {
                            args = arguments.string(0);
                            action = RobotEventPacket.ActionType.KEYBOARD;
                        }
                        if (!ItemNanoSwarm.doInstruction(id, player))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        RobotEventPacket.PressType type = MathUtils.isEvenNumber(method)
                            ? RobotEventPacket.PressType.RELEASE
                            : RobotEventPacket.PressType.PRESS;
                        PeripheralsPlusPlus.NETWORK
                            .sendTo(new RobotEventPacket(action, type, args), (EntityPlayerMP) player);
                        break;

                    case 16: {
                        String message = arguments.atLeast(1)
                            .string(0);
                        String from = arguments.string(1, null);
                        if (!ItemNanoSwarm.doInstruction(id, player)) return new Object[] { false };
                        String sender = from == null ? "" : "<" + from + "> ";
                        player.addChatComponentMessage(new ChatComponentText(sender + message));
                        break;
                    }
                }

            } else {
                switch (method) {
                    case 8:
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        return new Object[] { entity.getClass()
                            .getSimpleName() };

                    case 9:
                    case 10: {
                        // setTarget / setAttackTarget: a player name, or x y z coordinates.
                        Object selector = arguments.atLeast(1)
                            .numberOrString(0);
                        // Validate the coordinates before acting, so a bad call cannot half-apply.
                        if (selector instanceof Double) {
                            arguments.number(1);
                            arguments.number(2);
                        }
                        if (!ItemNanoSwarm.doInstruction(id, entity))
                            throw new LuaException("Entity with id " + id + " cannot be interacted with");
                        Entity target = selector instanceof String
                            ? WorldUtils.getPlayerForWorld((String) selector, entity.worldObj)
                            : nearestEntity(arguments, entity);
                        if (method == 9) {
                            ((EntityLivingAccessor) entity).ppp$setCurrentTarget(target);
                            return new Object[] { target != null };
                        }
                        if (!(target instanceof EntityLivingBase)) {
                            // Boats, minecarts and items can be "nearest"; they cannot be attacked (section 4.7).
                            entity.setAttackTarget(null);
                            return new Object[] { false };
                        }
                        entity.setAttackTarget((EntityLivingBase) target);
                        return new Object[] { true };
                    }

                    case 11: {
                        arguments.atLeast(3);
                        if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
                        PathNavigate navigator = ((EntityLivingAccessor) entity).ppp$getNavigator();
                        PathEntity path = navigator
                            .getPathToXYZ(arguments.number(0), arguments.number(1), arguments.number(2));
                        if (path == null) return new Object[] { false };
                        navigator.setPath(path, entity.getAIMoveSpeed());
                        break;
                    }

                    case 12: {
                        float yaw = (float) arguments.atLeast(1)
                            .number(0);
                        if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
                        entity.rotationYaw = yaw;
                        entity.rotationYawHead = yaw;
                        break;
                    }

                    case 13:
                        if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
                        entity.setJumping(!((EntityLivingBaseAccessor) entity).ppp$isJumping());
                        break;
                }
            }
        }
        return new Object[] { true };
    }
}
