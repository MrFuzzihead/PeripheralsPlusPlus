package com.austinv11.peripheralsplusplus.tiles;

import java.util.HashMap;
import java.util.Stack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.austinv11.peripheralsplusplus.lua.LuaArgs;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.TurtleUtil;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;

public class TileEntityTeleporter extends MountedTileEntity {

    public static String publicName = "teleporter";
    public Stack<LinkData> links = new Stack<LinkData>();
    public String tag = null;

    public TileEntityTeleporter() {
        super();
    }

    public int getMaxLinks() {
        return 1;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("tTag")) tag = nbttagcompound.getString("tTag");
        NBTTagList links = nbttagcompound.getTagList("links", Constants.NBT.TAG_COMPOUND);
        // Clear first: appending to the previous contents duplicated every link on each chunk reload, and addLink
        // then threw the new link away (CODE_REVIEW.md section 4.12).
        this.links.clear();
        for (int i = 0; i < links.tagCount() && this.links.size() < getMaxLinks(); i++) {
            NBTTagCompound link = links.getCompoundTagAt(i);
            if (link.hasKey("linkX") && link.hasKey("linkY") && link.hasKey("linkZ") && link.hasKey("linkDim")) {
                this.links.add(
                    new LinkData(
                        link.getInteger("linkDim"),
                        new ChunkCoordinates(
                            link.getInteger("linkX"),
                            link.getInteger("linkY"),
                            link.getInteger("linkZ"))));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        if (tag != null) nbttagcompound.setString("tTag", tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < links.size(); i++) {
            LinkData link = links.get(i);
            if (link != null) {
                NBTTagCompound lcompound = new NBTTagCompound();
                lcompound.setInteger("linkX", link.link.posX);
                lcompound.setInteger("linkY", link.link.posY);
                lcompound.setInteger("linkZ", link.link.posZ);
                lcompound.setInteger("linkDim", link.linkDim);
                list.appendTag(lcompound);
            }
        }
        nbttagcompound.setTag("links", list);
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "teleport", "tp", "getLinks", "setName" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        if (!Config.enableTurtleTeleporter) throw new LuaException("Turtle teleporters have been disabled");
        LuaArgs arguments = LuaArgs.of(rawArguments);
        if (method == 0 || method == 1) {
            // Lua link numbers start at 1.
            int index = arguments.integer(0, 1) - 1;
            if (index < 0 || index >= getMaxLinks())
                throw new LuaException("Bad link " + (index + 1) + " (expected 1-" + getMaxLinks() + ")");
            if (index >= links.size()) throw new LuaException("No such link");
            LinkData link = links.get(index);
            if (link == null) throw new LuaException("No such link");
            TileEntity te = worldObj.getTileEntity(
                xCoord + Facing.offsetsXForSide[getBlockMetadata()],
                yCoord + Facing.offsetsYForSide[getBlockMetadata()],
                zCoord + Facing.offsetsZForSide[getBlockMetadata()]);
            if (TurtleUtil.getTurtle(te) == null) throw new LuaException("No turtle in front");
            World destWorld = MinecraftServer.getServer()
                .worldServerForDimension(link.linkDim);
            if (destWorld == null) throw new LuaException("Destination world missing");
            TileEntity dest = destWorld.getTileEntity(link.link.posX, link.link.posY, link.link.posZ);
            if (!(dest instanceof TileEntityTeleporter)) throw new LuaException("Destination is not a teleporter");
            TileEntityTeleporter teleporter = (TileEntityTeleporter) dest;
            int linkToX = link.link.posX + Facing.offsetsXForSide[teleporter.getBlockMetadata()];
            int linkToY = link.link.posY + Facing.offsetsYForSide[teleporter.getBlockMetadata()];
            int linkToZ = link.link.posZ + Facing.offsetsZForSide[teleporter.getBlockMetadata()];
            if ((destWorld.blockExists(linkToX, linkToY, linkToZ) && !destWorld.isAirBlock(linkToX, linkToY, linkToZ))
                || linkToY < 0
                || linkToY > 254) // TODO: improve Block.blocksList[id] == null ||
                                  // Block.blocksList[id].isAirBlock(world, x, y, z) || Block.blocksList[id] instanceof
                                  // BlockFluid || Block.blocksList[id] instanceof BlockSnow || Block.blocksList[id]
                                  // instanceof BlockTallGrass;
                throw new LuaException("Destination obstructed");
            int xdif = Math.abs(xCoord - link.link.posX);
            int ydif = Math.abs(yCoord - link.link.posY);
            int zdif = Math.abs(zCoord - link.link.posZ);
            ITurtleAccess turtle = TurtleUtil.getTurtle(te);
            if (turtle == null) throw new LuaException("No turtle in front");
            if (!turtle.consumeFuel(
                Math.abs(
                    (int) Math.ceil(
                        (xdif + ydif + zdif)
                            * (Math.abs(worldObj.provider.dimensionId - destWorld.provider.dimensionId) + 1)
                            * Config.teleporterPenalty))))
                throw new LuaException("Not enough fuel");
            boolean result = turtle.teleportTo(destWorld, linkToX, linkToY, linkToZ);
            if (result) {
                destWorld.markBlockForUpdate(linkToX, linkToY, linkToZ);
                destWorld.notifyBlockChange(linkToX, linkToY, linkToZ, destWorld.getBlock(linkToX, linkToY, linkToZ));
                worldObj.markBlockForUpdate(
                    xCoord + Facing.offsetsXForSide[getBlockMetadata()],
                    yCoord + Facing.offsetsYForSide[getBlockMetadata()],
                    zCoord + Facing.offsetsZForSide[getBlockMetadata()]);
                worldObj.notifyBlockChange(
                    xCoord + Facing.offsetsXForSide[getBlockMetadata()],
                    yCoord + Facing.offsetsYForSide[getBlockMetadata()],
                    zCoord + Facing.offsetsZForSide[getBlockMetadata()],
                    null);
            }
            return new Object[] { result };
        } else if (method == 2) {
            HashMap<Integer, Object> map1 = new HashMap<Integer, Object>();
            for (int i = 0; i < links.size(); i++) {
                HashMap<String, Object> map2 = new HashMap<String, Object>();
                map2.put("dim", links.get(i).linkDim);
                map2.put("x", links.get(i).link.posX);
                map2.put("y", links.get(i).link.posY);
                map2.put("z", links.get(i).link.posZ);
                map2.put("name", tag);
                // Lua tables are 1-based everywhere else in this mod; getLinks used to start at 0 while
                // teleport(index) was 1-based (CODE_REVIEW.md section 4.12).
                map1.put(i + 1, map2.clone());
            }
            return new Object[] { map1 };
        } else if (method == 3) {
            this.tag = arguments.string(0);
            return new Object[] { tag };
        }
        return new Object[0];
    }

    public void onTeleport(byte flag) {// FIXME
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer()) {
            int facing = getBlockMetadata();
            for (int j = 0; j < 32; j++) {
                worldObj.spawnParticle(
                    "portal",
                    xCoord + 0.5D + 1.0D * Facing.offsetsXForSide[facing],
                    yCoord + 1.0D * Facing.offsetsYForSide[facing] + worldObj.rand.nextDouble() * 2.0D,
                    zCoord + 0.5D + 1.0D * Facing.offsetsZForSide[getBlockMetadata()],
                    worldObj.rand.nextGaussian(),
                    0.0D,
                    worldObj.rand.nextGaussian());
            }
        } else {
            String sound = "mob.endermen.portal";
            if (Loader.isModLoaded("Mystcraft")) sound = flag == 1 ? "myst.sound.link-portal" : "myst.sound.link-intra";
            worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, sound, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    public void blockActivated(EntityPlayer player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.isItemEqual(new ItemStack(Items.repeater))) {
            if (held.stackTagCompound != null) {
                if (held.stackTagCompound.hasKey("p++LinkX") && held.stackTagCompound.hasKey("p++LinkY")
                    && held.stackTagCompound.hasKey("p++LinkZ")
                    && held.stackTagCompound.hasKey("p++LinkDim")) {
                    ChunkCoordinates link = new ChunkCoordinates(
                        held.stackTagCompound.getInteger("p++LinkX"),
                        held.stackTagCompound.getInteger("p++LinkY"),
                        held.stackTagCompound.getInteger("p++LinkZ"));
                    int linkDim = held.stackTagCompound.getInteger("p++LinkDim");
                    World srcWorld = MinecraftServer.getServer()
                        .worldServerForDimension(linkDim);
                    if (srcWorld == null) {
                        player.addChatComponentMessage(new ChatComponentText("Link failed: World is missing"));
                    } else {
                        TileEntity te = srcWorld.getTileEntity(link.posX, link.posY, link.posZ);
                        if (!(te instanceof TileEntityTeleporter)) {
                            player.addChatComponentMessage(
                                new ChatComponentText("Link failed: Teleporter no longer exists"));
                        } else {
                            TileEntityTeleporter src = (TileEntityTeleporter) te;
                            if (link.posX == xCoord && link.posY == yCoord && link.posZ == zCoord) {
                                player.addChatComponentMessage(new ChatComponentText("Link canceled"));
                            } else {
                                boolean unlinked = false;
                                for (int i = 0; i < src.links.size(); i++) {
                                    LinkData rlink = src.links.get(i);
                                    if (rlink.link.posX == xCoord && rlink.link.posY == yCoord
                                        && rlink.link.posZ == zCoord
                                        && rlink.linkDim == worldObj.provider.dimensionId) {
                                        player.addChatComponentMessage(
                                            new ChatComponentText(
                                                "Unlinked teleporter at " + rlink.linkDim
                                                    + ":("
                                                    + rlink.link.posX
                                                    + ","
                                                    + rlink.link.posY
                                                    + ","
                                                    + rlink.link.posZ
                                                    + ") (link "
                                                    + (i + 1)
                                                    + ") from this teleporter"));
                                        src.links.remove(i);
                                        unlinked = true;
                                        break;
                                    }
                                }
                                if (!unlinked) {
                                    src.addLink(
                                        worldObj.provider.dimensionId,
                                        new ChunkCoordinates(xCoord, yCoord, zCoord));
                                    player.addChatComponentMessage(
                                        new ChatComponentText(
                                            "Linked teleporter at " + linkDim
                                                + ":("
                                                + link.posX
                                                + ","
                                                + link.posY
                                                + ","
                                                + link.posZ
                                                + ") (link "
                                                + src.links.size()
                                                + ") to this teleporter"));
                                }
                            }
                        }
                    }
                    held.stackTagCompound.removeTag("p++LinkX");
                    held.stackTagCompound.removeTag("p++LinkY");
                    held.stackTagCompound.removeTag("p++LinkZ");
                    held.stackTagCompound.removeTag("p++LinkDim");
                    if (held.stackTagCompound.hasKey("display")) {
                        NBTTagCompound display = held.stackTagCompound.getCompoundTag("display");
                        display.removeTag("Lore");
                        if (display.hasNoTags()) {
                            held.stackTagCompound.removeTag("display");
                        } else {
                            held.stackTagCompound.setTag("display", display);
                        }
                    }
                    return;
                }
            }
            if (held.stackTagCompound == null) held.stackTagCompound = new NBTTagCompound();
            held.stackTagCompound.setInteger("p++LinkX", xCoord);
            held.stackTagCompound.setInteger("p++LinkY", yCoord);
            held.stackTagCompound.setInteger("p++LinkZ", zCoord);
            held.stackTagCompound.setInteger("p++LinkDim", worldObj.provider.dimensionId);
            NBTTagCompound display = new NBTTagCompound();
            NBTTagList lore = new NBTTagList();
            lore.appendTag(new NBTTagString("Turtle Teleporter Link"));
            lore.appendTag(
                new NBTTagString(worldObj.provider.dimensionId + ":(" + xCoord + "," + yCoord + "," + zCoord + ")"));
            display.setTag("Lore", lore);
            held.stackTagCompound.setTag("display", display);
            player.addChatComponentMessage(new ChatComponentText("Link started"));
        }
    }

    /**
     * Adds a link, keeping the oldest one when the teleporter is already at {@link #getMaxLinks()}.
     *
     * @param linkDim the dimension of the target teleporter
     * @param link    the target teleporter's position
     * @return the number of links after the call
     */
    public int addLink(int linkDim, ChunkCoordinates link) {
        // The old `while (size > max) pop()` popped the link that had just been pushed, so adding a second link
        // silently did nothing.
        while (!links.isEmpty() && links.size() >= getMaxLinks()) links.remove(0);
        links.add(new LinkData(linkDim, link));
        return links.size();
    }

    public static class LinkData {

        public int linkDim;
        public ChunkCoordinates link;

        public LinkData(int linkDim, ChunkCoordinates link) {
            this.linkDim = linkDim;
            this.link = link;
        }
    }
}
