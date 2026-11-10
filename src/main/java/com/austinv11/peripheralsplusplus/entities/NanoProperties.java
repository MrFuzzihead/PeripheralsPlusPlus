package com.austinv11.peripheralsplusplus.entities;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

import com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna;

public class NanoProperties implements IExtendedEntityProperties {

    public static String IDENTIFIER = "NANOBOT_PROPERTIES";

    public int numOfBots = 0;
    public UUID antennaID;
    public Entity entity;

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("bots", numOfBots);
        // antennaID is null until the entity is associated with an antenna.
        if (antennaID != null) tag.setString("antenna", antennaID.toString());
        compound.setTag(IDENTIFIER, tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        if (compound.hasKey(IDENTIFIER)) {
            NBTTagCompound tag = compound.getCompoundTag(IDENTIFIER);
            numOfBots = tag.getInteger("bots");

            try {
                antennaID = UUID.fromString(tag.getString("antenna"));
            } catch (IllegalArgumentException e) {
                // antennaID is null meaning this entity has not yet been associated with any antenna.
            }

            // antenna_registry is a ConcurrentHashMap, which rejects a null key. Before the map was made
            // thread-safe this silently did nothing for an unassociated entity; now it threw on every chunk load.
            if (antennaID == null) return;
            TileEntityAntenna antenna = TileEntityAntenna.antenna_registry.get(antennaID);
            if (antenna != null && entity != null && !antenna.associatedEntities.contains(entity)) {
                antenna.associatedEntities.add(entity);
            }
        }
    }

    @Override
    public void init(Entity entity, World world) {
        this.entity = entity;
    }
}
