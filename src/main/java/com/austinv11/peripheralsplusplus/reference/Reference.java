package com.austinv11.peripheralsplusplus.reference;

import com.austinv11.peripheralsplusplus.Tags;

import dan200.computercraft.api.pocket.IPocketUpgrade;

public class Reference {

    public static final String MOD_ID = "PeripheralsPlusPlus";
    public static final String MOD_NAME = "Peripherals++";
    public static final String VERSION = Tags.VERSION;
    public static final String SERVER_PROXY_CLASS = "com.austinv11.peripheralsplusplus.proxy.CommonProxy";
    public static final String CLIENT_PROXY_CLASS = "com.austinv11.peripheralsplusplus.proxy.ClientProxy";
    public static final String GUI_FACTORY_CLASS = "com.austinv11.peripheralsplusplus.client.gui.GUIFactory";

    // Pocket Computer Upgrades
    // ComputerCraft owns ids 1-6 (1 wireless modem, 2 ender modem, 3 speaker, 4 chat box, 5 npc detector,
    // 6 npc interface); it attaches its own peripheral for those. Our upgrades must stay above that range, which is
    // what IPocketUpgrade#FIRST_MOD_UPGRADE_ID says.
    public static final int FIRST_UNRESERVED_POCKET_UPGRADE_ID = IPocketUpgrade.FIRST_MOD_UPGRADE_ID;
    public static final int MOTION_DETECTOR = 7;
    public static final int PERIPHERAL_CONTAINER = 8;

    // Turtle Upgrades
    public static final int CHAT_BOX_UPGRADE = 101;
    public static final int PLAYER_SENSOR_UPGRADE = 102;
    public static final int COMPASS_UPGRADE = 103;
    public static final int XP_UPGRADE = 104;
    public static final int BARREL_UPGRADE = 105;
    public static final int ORE_DICTIONARY_UPGRADE = 106;
    public static final int SHEAR_UPGRADE = 107;
    public static final int ENVIRONMENT_UPGRADE = 108;
    public static final int FEEDER_UPGRADE = 109;
    public static final int BASE_PROJ_RED_UPGRADE = 110;
    public static final int SPEAKER_UPGRADE = 125;
    public static final int TANK_UPGRADE = 126; // TODO Remember to update the id list on the wiki
    public static final int NOTE_BLOCK_UPGRADE = 127;
    public static final int SIGN_READER_UPGRADE = 128;
    public static final int GARDEN_UPGRADE = 129;
    public static final int RIDABLE_UPGRADE = 130;
    public static final int DISPENSER_UPGRADE = 131;
    public static final int BASE_BLUEPOWER_UPGRADE = 132;
    public static final int CHUNK_LOADER_UPGRADE = 147;
    public static final int RESUPPLY_UPGRADE = 148;

    public static enum GUIs {
        ANALYZER,
        HELMET,
        INTERACTIVE_SORTER,
        PLAYER_INTERFACE,
        PERMCARD,
        RESUPPLY_STATION
    }
}
