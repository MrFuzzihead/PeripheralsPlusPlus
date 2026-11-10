package com.austinv11.peripheralsplusplus.tiles;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.InventoryUtils;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

@Optional.InterfaceList(
    value = {
        @Optional.Interface(
            modid = "appliedenergistics2",
            iface = "appeng.api.networking.security.IActionHost",
            striprefs = true),
        @Optional.Interface(
            modid = "appliedenergistics2",
            iface = "appeng.api.networking.IGridBlock",
            striprefs = true) })
public class TileEntityMEBridge extends MountedTileEntity implements IActionHost, IGridBlock {

    public static String publicName = "meBridge";
    private HashMap<IComputerAccess, Boolean> computers = new HashMap<IComputerAccess, Boolean>();
    private IGridNode node;
    private boolean initialized = false;
    public EntityPlayer placed;

    public TileEntityMEBridge() {
        super();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (node != null) node.destroy();
        node = AEApi.instance()
            .createGridNode(this);
        node.loadFromNBT("node", nbttagcompound);
        initialized = false;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        if (node != null) node.saveToNBT("node", nbttagcompound);
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote || initialized) return;
        IGridNode gridNode = getNode();
        if (gridNode == null) return;
        if (placed != null) gridNode.setPlayerID(
            AEApi.instance()
                .registries()
                .players()
                .getID(placed.getGameProfile()));
        gridNode.updateState();
        initialized = true;
    }

    @Override
    public String getType() {
        return publicName;
    }

    /** Cached: ComputerCraft asks for the method list on every lookup. */
    private static final String[] METHODS = new String[] { "listAll", "listItems", "listCraft", "retrieve", "craft" };

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableMEBridge) throw new LuaException("ME Bridges have been disabled");
        if (node == null || node.getGrid() == null)
            throw new LuaException("The ME Bridge is not connected to a network");
        IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid()
            .getCache(IStorageGrid.class)).getItemInventory();
        // try {
        switch (method) {
            case 0:
                return new Object[] { iteratorToMap(
                    monitor.getStorageList()
                        .iterator(),
                    0) };
            case 1:
                return new Object[] { iteratorToMap(
                    monitor.getStorageList()
                        .iterator(),
                    1) };
            case 2:
                return new Object[] { iteratorToMap(
                    monitor.getStorageList()
                        .iterator(),
                    2) };
            case 3: {
                if (arguments.length < 3) throw new LuaException("Too few arguments");
                if (!(arguments[0] instanceof String)) throw new LuaException("Bad argument #1 (expected string)");
                if (!(arguments[1] instanceof Double)) throw new LuaException("Bad argument #2 (expected number)");
                if (!(arguments[2] instanceof String) && !(arguments[2] instanceof Double))
                    throw new LuaException("Bad argument #3 (expected string or number)");
                String spec = (String) arguments[0];
                String[] parts = spec.split(":", 2);
                if (parts.length < 2) throw new LuaException("Bad argument #1 (expected 'mod:item [meta]')");
                String itemName = parts[1].split(" ")[0];
                Item item = GameRegistry.findItem(parts[0], itemName);
                if (item == null) throw new LuaException("Unknown item " + spec);
                int meta = spec.contains(" ") ? Integer.parseInt(spec.split(" ")[1]) : 0;
                long amount = (long) (int) (double) (Double) arguments[1];
                ForgeDirection dir = arguments[2] instanceof String
                    ? ForgeDirection.valueOf(((String) arguments[2]).toUpperCase())
                    : ForgeDirection.getOrientation((int) (double) (Double) arguments[2]);
                IInventory inventory = getInventoryForSide(dir);
                if (inventory == null) throw new LuaException("Block is not a valid inventory");
                IAEItemStack stack = findAEStackFromItemStack(monitor, new ItemStack(item, 1, meta));
                if (stack == null) return new Object[] { 0L };

                ItemStack template = new ItemStack(item, 1, meta);
                int[] slots = InventoryUtils.slotsFor(inventory, dir.getOpposite());
                long capacity = 0;
                for (int slot : slots) {
                    if (!inventory.isItemValidForSlot(slot, template)) continue;
                    ItemStack existing = inventory.getStackInSlot(slot);
                    if (existing != null && !InventoryUtils.matches(existing, template)) continue;
                    capacity += InventoryUtils.room(existing, template, inventory.getInventoryStackLimit());
                }
                amount = Math.min(amount, Math.min(stack.getStackSize(), capacity));
                if (amount <= 0) return new Object[] { 0L };

                IAEItemStack request = stack.copy();
                request.setStackSize(amount);
                IAEItemStack extracted = monitor.extractItems(request, Actionable.MODULATE, new MachineSource(this));
                if (extracted == null) return new Object[] { 0L };

                long remaining = extracted.getStackSize();
                for (int slot : slots) {
                    if (remaining <= 0) break;
                    if (!inventory.isItemValidForSlot(slot, template)) continue;
                    ItemStack existing = inventory.getStackInSlot(slot);
                    if (existing != null && !InventoryUtils.matches(existing, template)) continue;
                    int room = InventoryUtils.room(existing, template, inventory.getInventoryStackLimit());
                    if (room <= 0) continue;
                    int take = (int) Math.min(remaining, room);
                    if (existing == null) {
                        inventory.setInventorySlotContents(slot, new ItemStack(item, take, meta));
                    } else {
                        ItemStack merged = existing.copy();
                        merged.stackSize = existing.stackSize + take;
                        inventory.setInventorySlotContents(slot, merged);
                    }
                    remaining -= take;
                }
                inventory.markDirty();
                if (remaining > 0) {
                    // Never destroy items: whatever did not fit goes back into the network.
                    IAEItemStack refund = extracted.copy();
                    refund.setStackSize(remaining);
                    monitor.injectItems(refund, Actionable.MODULATE, new MachineSource(this));
                }
                return new Object[] { extracted.getStackSize() - remaining };
            }
            case 4:
                if (arguments.length < 2) throw new LuaException("Too few arguments");
                if (!(arguments[0] instanceof String)) throw new LuaException("Bad argument #1 (expected string)");
                if (!(arguments[1] instanceof Double)) throw new LuaException("Bad argument #2 (expected number");
                final ICraftingGrid craftingGrid = node.getGrid()
                    .getCache(ICraftingGrid.class);
                Item toCraft = GameRegistry.findItem(
                    ((String) arguments[0]).split(":")[0],
                    ((String) arguments[0]).split(":")[1].split(" ")[0]);
                int meta_ = ((String) arguments[0]).contains(" ")
                    ? Integer.valueOf(((String) arguments[0]).split(" ")[1])
                    : 0;
                IAEItemStack aeToCraft_ = findAEStackFromItemStack(monitor, new ItemStack(toCraft, 1, meta_));
                if (aeToCraft_ != null && aeToCraft_.isCraftable()) {
                    IAEItemStack aeToCraft = aeToCraft_.copy();
                    aeToCraft.setStackSize((long) (int) (double) (Double) arguments[1]);
                    synchronized (this) {
                        craftingGrid.beginCraftingJob(
                            worldObj,
                            node.getGrid(),
                            new MachineSource(this),
                            aeToCraft,
                            new ICraftingCallback() {

                                @Override
                                public void calculationComplete(ICraftingJob job) {
                                    craftingGrid.submitJob(
                                        job,
                                        null,
                                        null,
                                        false,
                                        new MachineSource((IActionHost) getMachine()));
                                    for (IComputerAccess comp : computers.keySet()) comp.queueEvent(
                                        "craftingComplete",
                                        new Object[] {
                                            Item.itemRegistry
                                                .getNameForObject(((IAEItemStack) job.getOutput()).getItem()),
                                            job.getOutput()
                                                .getStackSize(),
                                            job.getByteTotal() });
                                }
                            });
                    }
                }
                return new Object[] {};
        }
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        return new Object[0];
    }

    /**
     * @param dir the side, relative to this tile entity
     * @return the inventory on that side, or {@code null}
     */
    private IInventory getInventoryForSide(ForgeDirection dir) {
        if (worldObj == null) return null;
        return InventoryUtils.at(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.put(computer, true);
        super.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        super.detach(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (node != null) {
            node.destroy();
            node = null;
            initialized = false;
        }
    }

    private IAEItemStack findAEStackFromItemStack(IMEMonitor<IAEItemStack> monitor, ItemStack item) {
        IAEItemStack stack = null;
        for (IAEItemStack temp : monitor.getStorageList()) {
            if (temp.isSameType(item)) {
                stack = temp;
                break;
            }
        }
        return stack;
    }

    private HashMap<Integer, Object> iteratorToMap(Iterator<IAEItemStack> iterator, int flag) {
        HashMap<Integer, Object> map = new HashMap<Integer, Object>();
        int i = 1;
        while (iterator.hasNext()) {
            Object o = getObjectFromStack(iterator.next(), flag);
            if (o != null) map.put(i++, o);
        }
        return map;
    }

    private Object getObjectFromStack(IAEItemStack stack, int flag) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        String itemName = Item.itemRegistry.getNameForObject(stack.getItem());
        int meta = stack.getItemDamage();
        long amount = stack.getStackSize();
        String displayName = stack.getItemStack()
            .getDisplayName();
        map.put("name", itemName);
        map.put("meta", meta);
        map.put("amount", amount);
        map.put("displayName", displayName);
        if (flag == 0) {
            return map;
        } else if (flag == 1) {
            if (stack.getStackSize() > 0) return map;
        } else if (flag == 2) {
            if (stack.isCraftable()) return map;
        }
        return null;
    }

    private IGridNode getNode() {
        if (worldObj == null || worldObj.isRemote) return null;
        // Create once. This used to call createGridNode(this) on every invocation, which orphaned the node loaded
        // from NBT (leaking its AE channel) and created a second one for updateState (CODE_REVIEW.md section 3.4).
        if (node == null) node = AEApi.instance()
            .createGridNode(this);
        return node;
    }

    @Override
    public double getIdlePowerUsage() {
        return 1;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return EnumSet.of(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public boolean isWorldAccessible() {
        return true;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public AEColor getGridColor() {
        return AEColor.Transparent;
    }

    @Override
    public void onGridNotification(GridNotification notification) {
        for (IComputerAccess computer : computers.keySet())
            computer.queueEvent("gridNotification", new Object[] { notification.toString() });
    }

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {}

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.allOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return this;
    }

    @Override
    public void gridChanged() {
        for (IComputerAccess computer : computers.keySet()) computer.queueEvent("gridChanged", new Object[0]);
    }

    @Override
    public ItemStack getMachineRepresentation() {
        return new ItemStack(ModBlocks.meBridge);
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.COVERED;
    }

    @Override
    public void securityBreak() {
        for (IComputerAccess computer : computers.keySet()) computer.queueEvent("securityBreak", new Object[0]);
        worldObj.setBlockToAir(xCoord, yCoord, zCoord);
    }

    @Override
    public IGridNode getActionableNode() {
        return node;
    }
}
