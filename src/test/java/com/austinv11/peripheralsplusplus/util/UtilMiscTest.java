package com.austinv11.peripheralsplusplus.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.utils.Util;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Covers the remaining {@link Util} helpers: books, damage attributes and player lists.
 *
 * <p>
 * {@code getPlayers} used to dereference {@code MinecraftServer.getServer()} unconditionally, so every caller NPE'd
 * outside a running server (CODE_REVIEW.md section 5.5).
 * </p>
 */
public class UtilMiscTest {

    @After
    public void tearDown() {
        // nothing to reset
    }

    // ---------------------------------------------------------------------------------------------------------
    // Books
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void aBookCarriesItsPages() {
        List<String> pages = new ArrayList<String>();
        pages.add("page one");
        pages.add("page two");

        NBTTagCompound tag = Util.writeToBookNBT("t", "author", pages);
        Assert.assertEquals("author", tag.getString("author"));
        Assert.assertEquals("t", tag.getString("title"));
        NBTTagList written = tag.getTagList("pages", 8);
        Assert.assertEquals(2, written.tagCount());
        Assert.assertEquals("page one", written.getStringTagAt(0));
    }

    @Test
    public void aBookDefaultsToTheModAsAuthor() {
        List<String> pages = new ArrayList<String>();
        pages.add("x");
        NBTTagCompound tag = Util.writeToBookNBT("t", pages);
        Assert.assertNotNull(tag.getString("author"));
        Assert.assertFalse(
            tag.getString("author")
                .isEmpty());
    }

    @Test
    public void anEmptyBookHasNoPages() {
        NBTTagCompound tag = Util.writeToBookNBT("t", new ArrayList<String>());
        Assert.assertEquals(
            0,
            tag.getTagList("pages", 8)
                .tagCount());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Damage attributes
    // ---------------------------------------------------------------------------------------------------------

    /** An item whose attribute map is fixed; a mock would have a null FML RegistryDelegate. */
    private static Item withModifiers(final Multimap<String, AttributeModifier> modifiers) {
        return new Item() {

            @Override
            public Multimap getAttributeModifiers(ItemStack stack) {
                return modifiers;
            }
        };
    }

    @Test
    public void theDamageAttributeIsReadFromTheItem() {
        Multimap<String, AttributeModifier> modifiers = HashMultimap.create();
        modifiers.put(
            SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
            new AttributeModifier("damage", 4.0d, 0));

        Assert.assertEquals(4.0d, Util.getDamageAttribute(new ItemStack(withModifiers(modifiers), 1)), 0.0001d);
    }

    @Test
    public void anItemWithoutAnAttributeReportsZero() {
        Assert.assertEquals(
            0.0d,
            Util.getDamageAttribute(new ItemStack(withModifiers(HashMultimap.<String, AttributeModifier>create()), 1)),
            0.0001d);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Player lists
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void thePlayerListOfAWorldIsItsOnlinePlayers() {
        World world = Mockito.mock(World.class);
        List<EntityPlayer> players = new ArrayList<EntityPlayer>();
        EntityPlayer first = Mockito.mock(EntityPlayer.class);
        Mockito.when(first.getCommandSenderName())
            .thenReturn("alice");
        players.add(first);
        world.playerEntities = players;

        List<String> names = Util.getPlayers(world);
        Assert.assertEquals(1, names.size());
        Assert.assertEquals("alice", names.get(0));
    }

    @Test
    public void thePlayerListWithoutAServerIsEmpty() {
        // No dedicated server in a unit test; this used to be an NPE.
        Assert.assertTrue(
            Util.getPlayers(null)
                .isEmpty());
    }

    @Test
    public void lookingUpAPlayerWithoutAServerIsSafe() {
        Assert.assertNull(Util.getPlayer("alice"));
        Assert.assertNull(Util.getPlayer(null));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void stringsAreSplitOnCommas() {
        Assert.assertArrayEquals(new String[] { "a", "b" }, Util.stringToArray("[a, b]"));
    }

    @Test
    public void anIntArrayBecomesAOneBasedTable() {
        Assert.assertEquals(
            Integer.valueOf(5),
            Util.arrayToMap(new int[] { 5, 6 })
                .get(1));
        Assert.assertEquals(
            Integer.valueOf(6),
            Util.arrayToMap(new int[] { 5, 6 })
                .get(2));
    }
}
