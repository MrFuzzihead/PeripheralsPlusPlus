package com.austinv11.peripheralsplusplus.reference;

import com.austinv11.collectiveframework.minecraft.config.Description;

@com.austinv11.collectiveframework.minecraft.config.Config(
    fileName = "PeripheralsPlusPlus.cfg",
    exclude = { "ENABLE_CONFIG_MESSAGE" })
public class Config {

    private static final String ENABLE_CONFIG_MESSAGE = "Disabling this removes the recipe and makes the existing blocks stop responding";

    @Description(category = "Chatbox", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableChatBox = true;

    @Description(
        category = "Chatbox",
        comment = "Prefix chat box messages with the block coordinates. Disable this to label messages with the optional name argument instead")
    public static boolean logCoords = true;

    @Description(category = "Chatbox", comment = "How far a chat box can hear chat. A negative value means unlimited")
    public static double readRange = -1.0;

    @Description(
        category = "Chatbox",
        comment = "How far a chat box's say/tell messages reach. A negative value means unlimited")
    public static double sayRange = 64;

    @Description(category = "Chatbox", comment = "Maximum number of messages per second a Chat Box peripheral can say")
    public static int sayRate = 1;

    @Description(
        category = "Chatbox",
        comment = "Allow a chat box to ignore the vertical distance when a program asks it to")
    public static boolean allowUnlimitedVertical = true;

    @Description(
        category = "Chatbox",
        comment = "Prefix that turns a chat line into a chat-box-only command. Leave empty to disable")
    public static String chatboxCommandPrefix = "\\";

    @Description(category = "Player Sensor", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enablePlayerSensor = true;

    @Description(category = "Player Sensor", comment = "Enable the getNearbyPlayers and getAllPlayers methods")
    public static boolean additionalMethods = true;

    @Description(category = "Player Sensor", comment = "Default range a player sensor searches for players")
    public static double sensorRange = 64.0;

    @Description(category = "RF Turtle Charger", comment = "If disabled, the recipe will be disabled")
    public static boolean enableRFCharger = true;

    @Description(category = "RF Turtle Charger", comment = "RF charged per unit of turtle fuel")
    public static int fuelRF = 200;

    @Description(category = "Navigational Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableNavigationTurtle = true;

    @Description(category = "XP Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableXPTurtle = true;

    @Description(category = "Barrel Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableBarrelTurtle = true;

    @Description(category = "Ore Dictionary", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableOreDictionary = true;

    @Description(
        category = "Ore Dictionary",
        comment = "If enabled, the Ore Dictionary peripheral will display a chat message with the Ore Dictionary entries - useful for debugging")
    public static boolean oreDictionaryMessage = false;

    @Description(category = "Shearing Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableShearTurtle = true;

    @Description(category = "Forestry Analyzers", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableAnalyzers = true;

    @Description(category = "Turtle Teleporters", comment = "If disabled, the recipe will be disabled")
    public static boolean enableTurtleTeleporter = true;

    @Description(
        category = "Turtle Teleporters",
        comment = "Fuel multiplier for turtle teleporters. 1.0 is no penalty; 2.0 (the default) doubles the fuel cost")
    public static double teleporterPenalty = 2.0;

    @Description(category = "Environment Scanner", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableEnvironmentScanner = true;

    @Description(category = "Feeder Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableFeederTurtle = true;

    @Description(category = "Villagers", comment = "Whether to enable villagers from this mod")
    public static boolean enableVillagers = true;

    @Description(category = "RedPower-Like Turtles", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableRedPowerLikeTurtles = true;

    @Description(category = "Speaker", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableSpeaker = true;

    @Description(category = "Speaker", comment = "How far a speaker's messages carry. A negative value means unlimited")
    public static double speechRange = 64;

    @Description(category = "Peripheral Container", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enablePeripheralContainer = true;

    @Description(category = "Peripheral Container", comment = "Maximum number of peripherals a container may hold")
    public static int maxNumberOfPeripherals = 6;

    @Description(category = "ME Bridge", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableMEBridge = true;

    @Description(category = "Tank Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableTankTurtle = true;

    @Description(category = "Tank Turtle", comment = "Maximum mB the tank turtle can store")
    public static int maxNumberOfMillibuckets = 10000;

    @Description(category = "Smart Helmet", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableSmartHelmet = true;

    @Description(category = "Iron Note Block", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableNoteBlock = true;

    @Description(category = "Iron Note Block", comment = "How far the iron note block is audible")
    public static double noteBlockRange = 16;

    @Description(category = "Sign Reading Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableReaderTurtle = true;

    @Description(category = "Gardening Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableGardeningTurtle = true;

    @Description(category = "Ridable Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableRidableTurtle = true;

    @Description(category = "Ridable Turtle", comment = "The amount of fuel used after each turtle movement")
    public static int fuelPerTurtleMovement = 1;

    @Description(category = "Nano Bots", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableNanoBots = true;

    @Description(
        category = "Nano Bots",
        comment = "Instructions a nano swarm grants per use. A negative value means unlimited")
    public static int numberOfInstructions = 8;

    @Description(
        category = "Nano Bots",
        comment = "Seconds before a synthesised key press is released. A negative value means never")
    public static double secondsBeforeReversal = 5.0;

    @Description(category = "Flinging Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableFlingingTurtle = true;

    @Description(category = "Time Sensor", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableTimeSensor = true;

    @Description(category = "Chunky Turtle", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableChunkyTurtle = true;

    @Description(
        category = "Chunky Turtle",
        comment = "Chunk radius the loader turtle keeps loaded: 0 is 1x1, 1 is 3x3, 2 is 5x5. The default Forge chunk loading limit is 5x5; see config/forgeChunkLoading.cfg")
    public static int chunkLoadingRadius = 1;

    @Description(category = "Interactive Sorter", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableInteractiveSorter = true;

    @Description(category = "Resupply Station", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableResupplyStation = true;

    @Description(category = "Player Interface", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enablePlayerInterface = true;

    @Description(category = "Motion Detector", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableMotionDetector = true;

    @Description(category = "AI Chat Box", comment = ENABLE_CONFIG_MESSAGE)
    public static boolean enableAIChatBox = true;

    @Description(
        category = "Player Interface",
        comment = "Require a permission card. When disabled, player interfaces work without one and may access any inventory")
    public static boolean enableInterfacePermissions = true;
}
