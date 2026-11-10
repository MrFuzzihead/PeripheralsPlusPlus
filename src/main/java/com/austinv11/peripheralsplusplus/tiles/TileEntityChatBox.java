package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.ChatUtil;
import com.austinv11.peripheralsplusplus.utils.RateLimiter;
import com.austinv11.peripheralsplusplus.utils.Util;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityChatBox extends MountedTileEntity {

    public static String publicName = "chatBox";
    private HashMap<IComputerAccess, Boolean> computers = new HashMap<IComputerAccess, Boolean>();
    /** Per-second message budget, shared by {@code say} and {@code tell}. */
    private final RateLimiter rateLimiter = new RateLimiter();
    private ITurtleAccess turtle;

    public TileEntityChatBox() {
        super();
    }

    public TileEntityChatBox(ITurtleAccess turtle) {
        this.xCoord = turtle.getPosition().posX;
        this.yCoord = turtle.getPosition().posY;
        this.zCoord = turtle.getPosition().posZ;
        this.setWorldObj(turtle.getWorld());
        this.turtle = turtle;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
    }

    @Override
    public void updateEntity() {
        rateLimiter.tick();
    }

    public void updateEntity(boolean turtle) {
        if (turtle) {
            this.xCoord = this.turtle.getPosition().posX;
            this.yCoord = this.turtle.getPosition().posY;
            this.zCoord = this.turtle.getPosition().posZ;
        }
        updateEntity();
    }

    public void onChat(EntityPlayer player, String message) {
        for (IComputerAccess computer : computers.keySet())
            computer.queueEvent("chat", new Object[] { player.getDisplayName(), message });
    }

    public void onDeath(EntityPlayer player, DamageSource source) {
        String killer = null;
        if (source instanceof EntityDamageSource) {
            Entity ent = source.getEntity();
            if (ent != null) killer = ent.getCommandSenderName();
        }
        for (IComputerAccess computer : computers.keySet())
            computer.queueEvent("death", new Object[] { player.getDisplayName(), killer, source.damageType });
    }

    public void onCommand(EntityPlayerMP player, String message) {
        for (IComputerAccess computer : computers.keySet()) computer
            .queueEvent("command", new Object[] { player.getDisplayName(), Util.arrayToMap(message.split(" ")) });
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "say", "tell" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        if (!Config.enableChatBox) throw new LuaException("Chat boxes have been disabled");
        LuaArgs arguments = LuaArgs.of(rawArguments);
        switch (method) {
            case 0:
                return new Object[] { say(arguments) };
            case 1:
                return new Object[] { tell(arguments) };
            default:
                return new Object[0];
        }
    }

    /** {@code say(text, [range], [unlimitedY], [label])} */
    private boolean say(LuaArgs arguments) throws LuaException {
        String text = arguments.atLeast(1)
            .string(0);
        double requested = arguments.number(1, Config.sayRange);
        boolean unlimitedY = arguments.bool(2, false) && Config.allowUnlimitedVertical;
        String label = arguments.string(3, null);
        requireBelowRateLimit();
        ChatUtil.sendMessage(this, prefix(text, label), ChatUtil.clampRange(requested, Config.sayRange), unlimitedY);
        return true;
    }

    /** {@code tell(player, text, [range], [unlimitedY], [label])} */
    private boolean tell(LuaArgs arguments) throws LuaException {
        arguments.atLeast(2);
        String target = arguments.string(0);
        String text = arguments.string(1);
        double requested = arguments.number(2, Config.sayRange);
        boolean unlimitedY = arguments.bool(3, false) && Config.allowUnlimitedVertical;
        String label = arguments.string(4, null);
        if (label != null && Config.logCoords)
            throw new LuaException("Coordinate logging is enabled, disable this to enable naming");
        requireBelowRateLimit();
        // This used to take the raw argument, so a Lua program could address the whole dimension regardless of
        // Config.sayRange (CODE_REVIEW.md section 5.8).
        return ChatUtil.sendMessage(
            target,
            this,
            prefix(text, label),
            ChatUtil.clampRange(requested, Config.sayRange),
            unlimitedY);
    }

    /**
     * @param text  the message body
     * @param label the optional "[label]" prefix, ignored when coordinate logging is on
     * @return the message to send
     */
    private String prefix(String text, String label) {
        return ChatUtil
            .formatMessage(text, label, Config.logCoords, Config.logCoords ? ChatUtil.getCoordsPrefix(this) : null);
    }

    /**
     * @throws LuaException when {@link Config#sayRate} messages have already been sent this second
     */
    private void requireBelowRateLimit() throws LuaException {
        if (!rateLimiter.tryAcquire(Config.sayRate))
            throw new LuaException("Please try again later, you are sending messages too often");
    }

    @Override
    public void attach(IComputerAccess computer) {
        if (computers.size() == 0) ChatListener.chatBoxMap.put(this, true);
        computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        if (computers.size() == 0) ChatListener.chatBoxMap.remove(this);
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    public static class ChatListener {

        private static final Map<TileEntityChatBox, Boolean> chatBoxMap = new ConcurrentHashMap<TileEntityChatBox, Boolean>();

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onChat(ServerChatEvent event) {
            if (!event.isCanceled()) {
                if (Config.enableChatBox) {
                    String commandPrefix = Config.chatboxCommandPrefix.trim();
                    if (!commandPrefix.equals("") && !commandPrefix.equals(" ")
                        && event.message.startsWith(commandPrefix)) {
                        event.setCanceled(true);

                        // Strip the prefix once, not every occurrence in the message (CODE_REVIEW.md section 5.9).
                        String command = event.message.substring(commandPrefix.length());
                        for (TileEntityChatBox box : chatBoxMap.keySet()) {
                            if (Config.readRange < 0 || Vec3.createVectorHelper(box.xCoord, box.yCoord, box.zCoord)
                                .distanceTo(event.player.getPosition(1.0f)) <= Config.readRange)
                                box.onCommand(event.player, command);
                        }
                    } else {
                        for (TileEntityChatBox box : chatBoxMap.keySet()) {
                            if (Config.readRange < 0 || Vec3.createVectorHelper(box.xCoord, box.yCoord, box.zCoord)
                                .distanceTo(event.player.getPosition(1.0f)) <= Config.readRange) {
                                box.onChat(event.player, event.message);
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onDeath(LivingDeathEvent event) {
            if (!event.isCanceled()) {
                if (Config.enableChatBox) {
                    if (event.entity instanceof EntityPlayer) {
                        for (TileEntityChatBox box : chatBoxMap.keySet()) {
                            if (Config.readRange < 0 || Vec3.createVectorHelper(box.xCoord, box.yCoord, box.zCoord)
                                .distanceTo(((EntityPlayer) event.entity).getPosition(1.0f)) <= Config.readRange)
                                box.onDeath((EntityPlayer) event.entity, event.source);
                        }
                    }
                }
            }
        }
    }
}
