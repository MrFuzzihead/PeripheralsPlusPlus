package com.austinv11.peripheralsplusplus.gametest;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.oredict.OreDictionary;

import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.init.ModItems;
import com.austinv11.peripheralsplusplus.mount.DynamicMount;
import com.austinv11.peripheralsplusplus.recipe.ContainerRecipe;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.tiles.TileEntityChatBox;
import com.austinv11.peripheralsplusplus.tiles.TileEntityInteractiveSorter;
import com.austinv11.peripheralsplusplus.tiles.TileEntitySpeaker;
import com.austinv11.peripheralsplusplus.utils.ChatUtil;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * The in-game assertions. Each {@code check*} method returns the number of failures it produced and logs every
 * failure with the {@code [PPP-GAMETEST] FAIL} prefix so the exit code and the log agree.
 *
 * <p>
 * Everything here needs the real game: block and item registries, a live {@link World}, and ComputerCraft's own
 * provider chain. Pure logic is covered by the JUnit suite in {@code src/test/java}.
 * </p>
 */
public final class ServerGameTests {

    private static final String FAIL = "[PPP-GAMETEST] FAIL ";
    private static final String PASS = "[PPP-GAMETEST] ok   ";

    private static int failures;
    private static int checks;

    private ServerGameTests() {}

    /**
     * Runs every assertion.
     *
     * @return the number of failed assertions
     */
    public static int runAll() {
        failures = 0;
        checks = 0;

        checkRegistries();
        checkTurtleUpgradeIds();
        checkPeripheralProviderResolvesAPlacedBlock();
        checkProviderIgnoresNonPeripheralBlocks();
        checkDocsMountIsPerType();
        checkPeripheralContainerNbtRoundTrip();
        checkOreDictionaryComparison();
        checkItemRegistryNames();
        checkSorterMovesExactlyTheRequestedAmount();
        checkChatBoxFormattingInGame();
        checkContainerRecipe();

        return failures;
    }

    /**
     * @return how many assertions {@link #runAll()} executed, for the combined summary
     */
    public static int checkCount() {
        return checks;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Registry smoke checks
    // ---------------------------------------------------------------------------------------------------------

    private static void checkRegistries() {
        Block[] blocks = { ModBlocks.chatBox, ModBlocks.aiChatBox, ModBlocks.playerSensor, ModBlocks.oreDictionary,
            ModBlocks.teleporter, ModBlocks.teleporterT2, ModBlocks.environmentScanner, ModBlocks.speaker,
            ModBlocks.antenna, ModBlocks.peripheralContainer, ModBlocks.dummyBlock, ModBlocks.noteBlock,
            ModBlocks.turtle, ModBlocks.timeSensor, ModBlocks.interactiveSorter, ModBlocks.playerInterface,
            ModBlocks.resupplyStation };
        for (Block block : blocks) {
            String name = block == null ? "null" : Block.blockRegistry.getNameForObject(block);
            check("block registered: " + name, block != null && Block.blockRegistry.getNameForObject(block) != null);
        }

        Item[] items = { ModItems.feederUpgrade, ModItems.tank, ModItems.smartHelmet, ModItems.nanoSwarm,
            ModItems.chunkLoaderUpgrade, ModItems.permCard, ModItems.resupplyUpgrade, ModItems.motionDetector };
        for (Item item : items) {
            String name = item == null ? "null" : Item.itemRegistry.getNameForObject(item);
            check("item registered: " + name, item != null && Item.itemRegistry.getNameForObject(item) != null);
        }

        // The config category names must round-trip, which fails loudly if a field was renamed.
        check("config maxNumberOfPeripherals > 0", Config.maxNumberOfPeripherals > 0);
        check("config maxNumberOfMillibuckets > 0", Config.maxNumberOfMillibuckets > 0);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Turtle upgrades (ComputerCraftAPI swallows registration failures, so ids must be checked here)
    // ---------------------------------------------------------------------------------------------------------

    private static void checkTurtleUpgradeIds() {
        Set<Integer> ids = new HashSet<Integer>();
        // PeripheralsPlusPlus registers its upgrades through ComputerCraftAPI, which hides them; assert the ids the
        // mod's own reference class claims are all distinct and above ComputerCraft's reserved range.
        int[] declared = { Reference.CHAT_BOX_UPGRADE, Reference.PLAYER_SENSOR_UPGRADE, Reference.COMPASS_UPGRADE,
            Reference.XP_UPGRADE, Reference.BARREL_UPGRADE, Reference.ORE_DICTIONARY_UPGRADE, Reference.SHEAR_UPGRADE,
            Reference.ENVIRONMENT_UPGRADE, Reference.FEEDER_UPGRADE, Reference.SPEAKER_UPGRADE, Reference.TANK_UPGRADE,
            Reference.NOTE_BLOCK_UPGRADE, Reference.SIGN_READER_UPGRADE, Reference.GARDEN_UPGRADE,
            Reference.RIDABLE_UPGRADE, Reference.DISPENSER_UPGRADE, Reference.CHUNK_LOADER_UPGRADE,
            Reference.RESUPPLY_UPGRADE };
        for (int id : declared) {
            check("turtle upgrade id " + id + " is unique", ids.add(id));
            check("turtle upgrade id " + id + " avoids ComputerCraft's range", id > 100);
        }
        // The RedPower-like families are allocated as base + index and must not collide with the fixed ids.
        for (int i = 0; i < 15; i++) {
            check(
                "projectred turtle id " + (Reference.BASE_PROJ_RED_UPGRADE + i),
                ids.add(Reference.BASE_PROJ_RED_UPGRADE + i));
            check(
                "bluepower turtle id " + (Reference.BASE_BLUEPOWER_UPGRADE + i),
                ids.add(Reference.BASE_BLUEPOWER_UPGRADE + i));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Peripheral provider
    // ---------------------------------------------------------------------------------------------------------

    private static void checkPeripheralProviderResolvesAPlacedBlock() {
        WorldServer world = overworld();
        if (world == null) {
            check("overworld is available", false);
            return;
        }
        int x = 0, y = 120, z = 0;
        world.setBlock(x, y, z, ModBlocks.chatBox);
        TileEntity tile = world.getTileEntity(x, y, z);
        check("placed chat box creates a tile entity", tile instanceof TileEntityChatBox);
        if (!(tile instanceof TileEntityChatBox)) return;

        IPeripheral peripheral = new IPlusPlusPeripheral.Provider().getPeripheral(world, x, y, z, 0);
        check("provider resolves the chat box", peripheral instanceof TileEntityChatBox);
        check("peripheral type matches the block name", TileEntityChatBox.publicName.equals(peripheral.getType()));
        check("peripheral is targeted", peripheral instanceof dan200.computercraft.api.peripheral.IPeripheralTargeted);
        check(
            "getTarget() returns the tile entity",
            peripheral instanceof dan200.computercraft.api.peripheral.IPeripheralTargeted
                && ((dan200.computercraft.api.peripheral.IPeripheralTargeted) peripheral).getTarget() == tile);

        // The speaker is a different peripheral and must resolve to a different object.
        world.setBlock(x + 2, y, z, ModBlocks.speaker);
        IPeripheral speaker = new IPlusPlusPeripheral.Provider().getPeripheral(world, x + 2, y, z, 0);
        check("provider resolves the speaker", speaker instanceof TileEntitySpeaker);

        world.setBlockToAir(x, y, z);
        world.setBlockToAir(x + 2, y, z);
    }

    private static void checkProviderIgnoresNonPeripheralBlocks() {
        WorldServer world = overworld();
        if (world == null) return;
        int x = 4, y = 120, z = 0;
        world.setBlock(x, y, z, Blocks.stone);
        check(
            "provider ignores plain stone",
            new IPlusPlusPeripheral.Provider().getPeripheral(world, x, y, z, 0) == null);
        check("provider ignores air", new IPlusPlusPeripheral.Provider().getPeripheral(world, x, y + 1, z, 0) == null);
        world.setBlockToAir(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Docs mount
    // ---------------------------------------------------------------------------------------------------------

    private static void checkDocsMountIsPerType() {
        // CODE_REVIEW.md 3.3: a shared /ppp mount meant only the first peripheral's scripts were visible.
        String chatBox = new DynamicMount("chatBox").mountPath("");
        String speaker = new DynamicMount("speaker").mountPath("");
        check("docs mount paths are per peripheral", !chatBox.equals(speaker));
        check("docs mount path is below /ppp", chatBox.startsWith("/ppp/"));
        boolean exists;
        try {
            exists = new DynamicMount("chatBox").exists("chatBox");
        } catch (Exception e) {
            exists = false;
        }
        check("docs directory exists for the chat box", exists);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Peripheral container
    // ---------------------------------------------------------------------------------------------------------

    private static void checkPeripheralContainerNbtRoundTrip() {
        WorldServer world = overworld();
        if (world == null) return;
        int x = 6, y = 120, z = 0;
        world.setBlock(x, y, z, ModBlocks.peripheralContainer);
        TileEntity tile = world.getTileEntity(x, y, z);
        check("placed container creates a tile entity", tile != null);
        if (!(tile instanceof IPeripheral)) {
            check("container tile entity is a peripheral", false);
            world.setBlockToAir(x, y, z);
            return;
        }
        check("container type", "peripheralContainer".equals(((IPeripheral) tile).getType()));
        try {
            Object[] listed = ((IPeripheral) tile).callMethod(null, null, 0, new Object[0]);
            check("container lists an (empty) peripheral table", listed.length == 1);
        } catch (Exception e) {
            check("container lists an (empty) peripheral table: " + e, false);
        }
        world.setBlockToAir(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Ore dictionary: Item.itemRegistry and OreDictionary cannot be bootstrapped outside FML, so Util.compare and
    // Util.getEntries are asserted here rather than in src/test.
    // ---------------------------------------------------------------------------------------------------------

    private static void checkOreDictionaryComparison() {
        ItemStack iron = new ItemStack(Items.iron_ingot);
        ItemStack gold = new ItemStack(Items.gold_ingot);
        ItemStack stone = new ItemStack(Blocks.stone);

        check("Util.compare(iron, iron)", Util.compare(iron, new ItemStack(Items.iron_ingot)));
        check("Util.compare(iron, gold) is false", !Util.compare(iron, gold));
        check("Util.compare(iron, stone) is false", !Util.compare(iron, stone));
        check("Util.compare(null, iron) is false", !Util.compare(null, iron));

        check(
            "Util.getEntries is 1-based",
            Util.getEntries(iron)
                .containsKey(1));
        check(
            "Util.getEntries counts every ore id",
            Util.getEntries(iron)
                .size() == OreDictionary.getOreIDs(iron).length);
        check(
            "Util.getEntries never uses key 0",
            !Util.getEntries(iron)
                .containsKey(0));
        check("the ore dictionary name for iron is registered", OreDictionary.getOreIDs(iron).length > 0);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Item registry names
    // ---------------------------------------------------------------------------------------------------------

    private static void checkItemRegistryNames() {
        Item[] items = { ModItems.feederUpgrade, ModItems.tank, ModItems.smartHelmet, ModItems.nanoSwarm,
            ModItems.chunkLoaderUpgrade, ModItems.permCard, ModItems.resupplyUpgrade, ModItems.motionDetector };
        for (Item item : items) {
            String name = Item.itemRegistry.getNameForObject(item);
            check("item has a registry name: " + name, name != null && name.startsWith("PeripheralsPlusPlus:"));
            check("item registry round-trips: " + name, name != null && Item.itemRegistry.getObject(name) == item);
        }
        check(
            "a vanilla item still resolves",
            "minecraft:iron_ingot".equals(Item.itemRegistry.getNameForObject(Items.iron_ingot)));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Inventory moves against a real chest (the unit tests use a fake inventory)
    // ---------------------------------------------------------------------------------------------------------

    private static void checkSorterMovesExactlyTheRequestedAmount() {
        WorldServer world = overworld();
        if (world == null) return;
        int x = 0, y = 120, z = 8;
        world.setBlock(x, y - 1, z, Blocks.stone);
        world.setBlock(x + 1, y - 1, z, Blocks.stone);
        world.setBlock(x, y, z, ModBlocks.interactiveSorter);
        world.setBlock(x + 1, y, z, Blocks.chest);

        TileEntity sorterTile = world.getTileEntity(x, y, z);
        TileEntity chestTile = world.getTileEntity(x + 1, y, z);
        check("sorter tile entity exists", sorterTile instanceof TileEntityInteractiveSorter);
        check("chest tile entity exists", chestTile instanceof IInventory);
        if (!(sorterTile instanceof TileEntityInteractiveSorter) || !(chestTile instanceof IInventory)) {
            world.setBlockToAir(x, y, z);
            world.setBlockToAir(x + 1, y, z);
            return;
        }

        TileEntityInteractiveSorter sorter = (TileEntityInteractiveSorter) sorterTile;
        IInventory chest = (IInventory) chestTile;
        sorter.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 10));
        try {
            Object[] pushed = sorter.callMethod(null, null, 1, new Object[] { "east", 4.0d });
            check("sorter push reports success", pushed.length == 1 && Boolean.TRUE.equals(pushed[0]));
            // The old arithmetic subtracted a clamped *total*, which produced a negative stack size.
            check(
                "sorter kept the remainder",
                sorter.getStackInSlot(0) != null && sorter.getStackInSlot(0).stackSize == 6);
            check(
                "chest received exactly four",
                chest.getStackInSlot(0) != null && chest.getStackInSlot(0).stackSize == 4);

            Object[] pulled = sorter.callMethod(null, null, 2, new Object[] { "east", 2.0d });
            check("sorter pull reports success", pulled.length == 1 && Boolean.TRUE.equals(pulled[0]));
            check(
                "sorter received exactly two",
                sorter.getStackInSlot(0) != null && sorter.getStackInSlot(0).stackSize == 8);
            check(
                "chest kept the remainder",
                chest.getStackInSlot(0) != null && chest.getStackInSlot(0).stackSize == 2);
        } catch (Exception e) {
            check("sorter push/pull without an exception: " + e, false);
        }

        world.setBlockToAir(x, y, z);
        world.setBlockToAir(x + 1, y, z);
        world.setBlockToAir(x, y - 1, z);
        world.setBlockToAir(x + 1, y - 1, z);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Chat formatting with a real coordinate prefix
    // ---------------------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------------------
    // Container crafting recipe (CODE_REVIEW.md section 3.2: it could never match)
    // ---------------------------------------------------------------------------------------------------------

    private static void checkContainerRecipe() {
        WorldServer world = overworld();
        if (world == null) return;

        InventoryCrafting grid = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3);

        ContainerRecipe recipe = new ContainerRecipe();
        check("an empty grid does not match", !recipe.matches(grid, world));

        grid.setInventorySlotContents(0, new ItemStack(ModBlocks.peripheralContainer));
        check("a lone container does not match", !recipe.matches(grid, world));

        grid.setInventorySlotContents(1, new ItemStack(Blocks.stone));
        check("a container plus a non-peripheral block does not match", !recipe.matches(grid, world));

        grid.setInventorySlotContents(1, new ItemStack(ModBlocks.chatBox));
        check("a container plus a peripheral matches", recipe.matches(grid, world));

        ItemStack result = recipe.getCraftingResult(grid);
        check("the result is a peripheral container", result != null && result.getItem() != null);
        check(
            "the result records the contained peripheral",
            result != null && result.stackTagCompound != null
                && result.stackTagCompound.getIntArray("ids").length == 1);

        grid.setInventorySlotContents(2, new ItemStack(ModBlocks.peripheralContainer));
        check("two containers do not match", !recipe.matches(grid, world));

        // The antenna is excluded on purpose.
        grid.setInventorySlotContents(2, null);
        grid.setInventorySlotContents(1, new ItemStack(ModBlocks.antenna));
        check("an excluded peripheral does not match", !recipe.matches(grid, world));
    }

    private static void checkChatBoxFormattingInGame() {
        WorldServer world = overworld();
        if (world == null) return;
        int x = 4, y = 120, z = 8;
        world.setBlock(x, y, z, ModBlocks.chatBox);
        TileEntity tile = world.getTileEntity(x, y, z);
        check("chat box tile entity exists", tile instanceof TileEntityChatBox);
        if (tile instanceof TileEntityChatBox) {
            String prefix = ChatUtil.getCoordsPrefix(tile);
            check("coordinate prefix matches the block", prefix.equals("[#" + x + "," + y + "," + z + "] "));
            check(
                "formatMessage prepends the coordinate prefix",
                ChatUtil.formatMessage("hi", "bot", true, prefix)
                    .equals(prefix + "hi"));
            check(
                "formatMessage prefers the label when logging is off",
                ChatUtil.formatMessage("hi", "bot", false, prefix)
                    .equals("[bot] hi"));
            check("clampRange caps at the configured maximum", ChatUtil.clampRange(1000000.0d, 64.0d) == 64.0d);
        }
        world.setBlockToAir(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static WorldServer overworld() {
        try {
            return DimensionManager.getWorld(0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (condition) {
            System.out.println(PASS + description);
        } else {
            failures++;
            System.out.println(FAIL + description);
        }
    }
}
