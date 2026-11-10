package com.austinv11.peripheralsplusplus.utils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.oredict.OreDictionary;

import com.austinv11.peripheralsplusplus.reference.Reference;
import com.google.common.collect.Multimap;

public class Util {

    /**
     * @param iterable the plant types to convert
     * @return a 1-based Lua table
     */
    public static HashMap<Integer, Object> EnumSetToMap(EnumSet<EnumPlantType> iterable) {
        HashMap<Integer, Object> map = new HashMap<Integer, Object>();
        int i = 1;
        for (EnumPlantType type : iterable) map.put(i++, type);
        return map;
    }

    public static HashMap<Integer, Integer> arrayToMap(int[] array) {
        HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (int i = 0; i < array.length; i++) {
            map.put(i + 1, array[i]);
        }
        return map;
    }

    public static HashMap<Integer, Byte> arrayToMap(byte[] array) {
        HashMap<Integer, Byte> map = new HashMap<Integer, Byte>();
        for (int i = 0; i < array.length; i++) {
            map.put(i + 1, array[i]);
        }
        return map;
    }

    public static HashMap<Integer, Object> arrayToMap(Object[] array) {
        HashMap<Integer, Object> map = new HashMap<Integer, Object>();
        for (int i = 0; i < array.length; i++) {
            map.put(i + 1, array[i]);
        }
        return map;
    }

    public static HashMap<Integer, String> getEntries(ItemStack stack) {
        int[] ids = OreDictionary.getOreIDs(stack);
        HashMap<Integer, String> entries = new HashMap<Integer, String>();
        for (int i = 0; i < ids.length; i++) {
            entries.put(i + 1, OreDictionary.getOreName(ids[i]));
        }
        return entries;
    }

    /**
     * Whether two stacks share at least one ore dictionary entry.
     *
     * <p>
     * The old implementation called {@link #getEntries} again for every entry of the first stack, allocating a
     * {@code HashMap} and resolving the ore ids each time (CODE_REVIEW.md section 5.3). The ids are looked up once
     * per stack now.
     * </p>
     *
     * @param stack1 the first stack
     * @param stack2 the second stack
     * @return true when they share an ore dictionary name
     */
    public static boolean compare(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) return false;
        int[] ids1 = OreDictionary.getOreIDs(stack1);
        if (ids1.length == 0) return false;
        int[] ids2 = OreDictionary.getOreIDs(stack2);
        for (int id1 : ids1) {
            String name1 = OreDictionary.getOreName(id1);
            for (int id2 : ids2) if (name1.equals(OreDictionary.getOreName(id2))) return true;
        }
        return false;
    }

    public static NBTTagCompound writeToBookNBT(String title, String author, List<String> pageText) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("author", author);
        tag.setString("title", StatCollector.translateToLocal(title));
        NBTTagList list = new NBTTagList();
        for (String s : pageText) list.appendTag(new NBTTagString(StatCollector.translateToLocal(s)));
        tag.setTag("pages", list);
        return tag;
    }

    public static NBTTagCompound writeToBookNBT(String title, List<String> pageText) {
        return writeToBookNBT(title, Reference.MOD_NAME, pageText);
    }

    public static double getDamageAttribute(ItemStack item) {
        double val = 0;
        Multimap multimap = item.getItem()
            .getAttributeModifiers(item);
        if (multimap.containsKey(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName()))
            for (Object o : multimap.get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName()))
                if (o instanceof AttributeModifier) {
                    val = ((AttributeModifier) o).getAmount();
                    break;
                }
        return val;
    }

    /**
     * Finds an online player by name.
     *
     * @param ign the player's username
     * @return the player, or {@code null} when no server is running or nobody matches
     */
    public static EntityPlayer getPlayer(String ign) {
        if (ign == null) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        List<? extends EntityPlayer> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayer p : players) {
            // getCommandSenderName() is the username; getDisplayName() includes scoreboard team prefixes.
            if (p.getCommandSenderName()
                .equalsIgnoreCase(ign)) return p;
        }
        return null;
    }

    public static String[] stringToArray(String array) {
        String[] array_ = array.replace("]", "")
            .replace("[", "")
            .split(",");
        for (int i = 0; i < array_.length; i++) array_[i] = array_[i].trim();
        return array_;
    }

    /**
     * @param world the world to list, or {@code null} for every online player
     * @return the player usernames
     */
    public static List<String> getPlayers(World world) {
        List<String> list = new ArrayList<String>();
        if (world != null) {
            for (EntityPlayer player : (Iterable<EntityPlayer>) world.playerEntities)
                list.add(player.getCommandSenderName());
        } else {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) for (String player : server.getAllUsernames()) list.add(player);
        }
        return list;
    }
}
