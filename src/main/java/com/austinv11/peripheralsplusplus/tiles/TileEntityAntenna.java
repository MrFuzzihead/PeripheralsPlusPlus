package com.austinv11.peripheralsplusplus.tiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.items.ItemSmartHelmet;
import com.austinv11.peripheralsplusplus.lua.LuaObjectEntityControl;
import com.austinv11.peripheralsplusplus.lua.LuaObjectHUD;
import com.austinv11.peripheralsplusplus.network.ScaleRequestPacket;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityAntenna extends MountedTileEntity {

    public static String publicName = "antenna";
    private int world = 0;
    /** Attached computers. Filled from computer threads, so it must be concurrent (CODE_REVIEW.md section 5.11). */
    public final Map<IComputerAccess, Boolean> computers = new ConcurrentHashMap<IComputerAccess, Boolean>();
    /** HUD handles by computer id. */
    private final Map<Integer, LuaObjectHUD> huds = new ConcurrentHashMap<Integer, LuaObjectHUD>();
    public static final Map<UUID, TileEntityAntenna> antenna_registry = new ConcurrentHashMap<UUID, TileEntityAntenna>();
    public UUID identifier;
    public String label;
    public final List<Entity> associatedEntities = new CopyOnWriteArrayList<Entity>();

    public TileEntityAntenna() {
        super();
        identifier = UUID.randomUUID();
        while (antenna_registry.containsKey(identifier)) {
            if (antenna_registry.get(identifier)
                .equals(this)) break;
            identifier = UUID.randomUUID();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("identifier")) identifier = UUID.fromString(nbttagcompound.getString("identifier"));
        if (nbttagcompound.hasKey("label")) label = nbttagcompound.getString("label");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setString("identifier", identifier.toString());
        if (label != null) nbttagcompound.setString("label", label);
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "getPlayers", /*
                                                                          * Lists players wearing smart helmets linked
                                                                          * to this antenna
                                                                          */
        "getHUD", /* Returns a hud handle for the given player */
        "setLabel", /* Sets the label of the antenna */
        "getLabel", /* Gets the current label of the antenna */
        // ==Smart Helmet APIs end===
        "getInfectedEntities", /* Lists the entity ids for all entities currently infected by a nanobot swarm */
        "getInfectedEntity"/* Gets the handle for a specified entity */ };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        // Smart-helmet methods are 0-3 and the nano-bot methods are 4-5; the two config flags gate different halves of
        // the API, and the old `else if` blocked the helmet methods when nano bots were disabled (section 4.14).
        boolean isHelmetMethod = method < 4;
        if (isHelmetMethod && !Config.enableSmartHelmet) throw new LuaException("Smart Helmets have been disabled");
        if (!isHelmetMethod && !Config.enableNanoBots) throw new LuaException("Nano bots have been disabled");
        // try {
        switch (method) {
            case 0:// getPlayers
                synchronized (this) {
                    List<String> players = new ArrayList<String>();
                    for (Object player : MinecraftServer.getServer()
                        .getConfigurationManager().playerEntityList)
                        if (player instanceof EntityPlayer) if (((EntityPlayer) player).getCurrentArmor(3) != null
                            && ((EntityPlayer) player).getCurrentArmor(3)
                                .getItem() instanceof ItemSmartHelmet
                            && NBTHelper.hasTag(((EntityPlayer) player).getCurrentArmor(3), "identifier"))
                            if (identifier.equals(
                                UUID.fromString(
                                    NBTHelper.getString(((EntityPlayer) player).getCurrentArmor(3), "identifier"))))
                                players.add(((EntityPlayer) player).getCommandSenderName());
                    return new Object[] { Util.arrayToMap(players.toArray()) };
                }
            case 1:
                synchronized (this) {
                    if (arguments.length < 1) throw new LuaException("Not enough arguments");
                    if (!(arguments[0] instanceof String)) throw new LuaException("Bad argument #1 (expected string)");
                    if (Util.getPlayer((String) arguments[0]) == null) return new Object[] { null };
                    LuaObjectHUD obj = new LuaObjectHUD((String) arguments[0], identifier);
                    huds.put(computer.getID(), obj);
                    PeripheralsPlusPlus.NETWORK.sendTo(
                        new ScaleRequestPacket(this, computer.getID(), world),
                        (EntityPlayerMP) Util.getPlayer((String) arguments[0]));
                    // ComputerCraft 3.x: suspend until the client reports the HUD resolution instead of blocking
                    return new Object[] { MethodResult.pullEvent(event -> {
                        if (event.length > 0 && "resolution".equals(event[0])) return MethodResult.of(obj);
                        // Not ours, keep waiting
                        return null;
                    }) };
                }
            case 2:
                synchronized (this) {
                    if (arguments.length != 1) throw new LuaException("Incorrect Arguments!");
                    this.setLabel(arguments[0].toString());
                    return new Object[] { this.getLabel() };
                }
            case 3:
                synchronized (this) {
                    return new Object[] { this.getLabel() };
                }
            case 4:
                HashMap<Integer, Integer> entities = new HashMap<Integer, Integer>();
                int i = 1;
                for (Entity entity : associatedEntities) {
                    // A despawned or killed entity kept reporting forever; drop it instead.
                    if (entity == null || entity.isDead) {
                        associatedEntities.remove(entity);
                        continue;
                    }
                    entities.put(i++, entity.getEntityId());
                }
                return new Object[] { entities };
            case 5:
                if (arguments.length < 1) throw new LuaException("Too few arguments");
                if (!(arguments[0] instanceof Double)) throw new LuaException("Bad argument #1 (expected number)");
                Entity ent = entityFromId((int) (double) (Double) arguments[0]);
                if (ent != null) return new Object[] { new LuaObjectEntityControl(identifier, ent) };
                else throw new LuaException("Entity with id " + arguments[0] + " not found");
        }
        // }catch (Exception e) {
        // e.printStackTrace();
        // }
        return new Object[0];
    }

    private Entity entityFromId(int id) {
        for (Entity entity : associatedEntities) if (entity.getEntityId() == id) return entity;
        return null;
    }

    @Override
    public void updateEntity() {
        if (worldObj != null) {
            world = worldObj.provider.dimensionId;
            if (!antenna_registry.containsKey(identifier)) antenna_registry.put(identifier, this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        antenna_registry.remove(identifier);
    }

    @Override
    public void validate() {
        super.validate();
        antenna_registry.put(identifier, this);
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        // HUD handles are keyed by computer id; without this they accumulated for the tile entity's lifetime.
        huds.remove(computer.getID());
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    public void onResponse(int id, int width, int height) {
        if (huds.containsKey(id)) {
            huds.get(id).height = height;
            huds.get(id).width = width;
            for (IComputerAccess comp : computers.keySet())
                if (comp.getID() == id) comp.queueEvent("resolution", new Object[] { height, width });
        }
    }

    public void setLabel(String newLabel) {
        this.label = newLabel;
    }

    public String getLabel() {
        return this.label;
    }
}
