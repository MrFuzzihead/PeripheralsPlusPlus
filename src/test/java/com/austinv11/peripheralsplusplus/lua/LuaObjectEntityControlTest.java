package com.austinv11.peripheralsplusplus.lua;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.entities.NanoProperties;

import dan200.computercraft.api.lua.LuaException;

/**
 * Covers the nano-bot entity handle.
 *
 * <p>
 * Every mutating method goes through {@code ItemNanoSwarm.doInstruction}, which returns false unless the entity is
 * registered with an antenna, so a fresh mock exercises the "cannot be interacted with" branch of each of them — the
 * branch that used to NPE for mobs (CODE_REVIEW.md section 4.8).
 * </p>
 */
public class LuaObjectEntityControlTest {

    private static EntityLiving mob() {
        EntityLiving entity = Mockito.mock(EntityLiving.class);
        entity.worldObj = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(entity.getExtendedProperties(NanoProperties.IDENTIFIER))
            .thenReturn(new NanoProperties());
        return entity;
    }

    private static EntityPlayer player() {
        EntityPlayer entity = Mockito.mock(EntityPlayer.class);
        entity.worldObj = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(entity.getExtendedProperties(NanoProperties.IDENTIFIER))
            .thenReturn(new NanoProperties());
        return entity;
    }

    private static LuaObjectEntityControl control(Entity entity) {
        return new LuaObjectEntityControl(UUID.randomUUID(), entity);
    }

    @Test
    public void theMethodListDependsOnTheTargetKind() {
        String[] mobMethods = control(mob()).getMethodNames();
        String[] playerMethods = control(player()).getMethodNames();
        Assert.assertEquals(14, mobMethods.length);
        Assert.assertEquals(17, playerMethods.length);
        Assert.assertTrue(
            java.util.Arrays.asList(playerMethods)
                .contains("keyPress"));
        Assert.assertTrue(
            java.util.Arrays.asList(mobMethods)
                .contains("setAttackTarget"));
        // The same command index means different things, which is why the arrays are per-kind.
        Assert.assertEquals("getEntityName", mobMethods[8]);
        Assert.assertEquals("getPlayerName", playerMethods[8]);
    }

    @Test
    public void isPlayerReportsTheKind() throws Exception {
        Assert.assertArrayEquals(new Object[] { false }, control(mob()).callMethod(null, 0, new Object[0]));
        Assert.assertArrayEquals(new Object[] { true }, control(player()).callMethod(null, 0, new Object[0]));
    }

    @Test
    public void anUnregisteredMobRefusesTheReadings() throws Exception {
        LuaObjectEntityControl control = control(mob());
        // hurt/heal report false; the readings raise, because there is no value to return.
        Assert.assertArrayEquals(new Object[] { false }, control.callMethod(null, 1, new Object[0]));
        assertLuaError(() -> control.callMethod(null, 3, new Object[0]), "cannot be interacted with");
        assertLuaError(() -> control.callMethod(null, 4, new Object[0]), "cannot be interacted with");
        assertLuaError(() -> control.callMethod(null, 5, new Object[0]), "cannot be interacted with");
        assertLuaError(() -> control.callMethod(null, 8, new Object[0]), "cannot be interacted with");
    }

    @Test
    public void healReturnsFalseRatherThanTouchingAPlayer() throws Exception {
        // The mob branch used to call player.heal(), which NPE'd because player is null there.
        Assert.assertArrayEquals(new Object[] { false }, control(mob()).callMethod(null, 2, new Object[0]));
    }

    @Test
    public void hurtReturnsFalseForAnUnregisteredTarget() throws Exception {
        Assert.assertArrayEquals(new Object[] { false }, control(mob()).callMethod(null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { false }, control(player()).callMethod(null, 1, new Object[0]));
    }

    @Test
    public void theRemainingBotCountIsReadable() throws Exception {
        NanoProperties properties = new NanoProperties();
        properties.numOfBots = 5;
        EntityLiving entity = mob();
        Mockito.when(entity.getExtendedProperties(NanoProperties.IDENTIFIER))
            .thenReturn(properties);
        Assert.assertArrayEquals(new Object[] { 5 }, control(entity).callMethod(null, 6, new Object[0]));
    }

    @Test
    public void theMobNameIsReadableWithInstructions() throws Exception {
        Assert.assertArrayEquals(new Object[] { 0 }, control(mob()).callMethod(null, 6, new Object[0]));
    }

    @Test
    public void setTargetValidatesItsCoordinates() {
        LuaObjectEntityControl control = control(mob());
        assertLuaError(() -> control.callMethod(null, 9, new Object[0]), "Too few arguments");
        assertLuaError(() -> control.callMethod(null, 9, new Object[] { 1.0d, 2.0d }), "Bad argument #3");
        assertLuaError(() -> control.callMethod(null, 9, new Object[] { true }), "string or number");
    }

    @Test
    public void whisperValidatesItsArguments() {
        LuaObjectEntityControl control = control(player());
        assertLuaError(() -> control.callMethod(null, 16, new Object[0]), "Too few arguments");
        assertLuaError(() -> control.callMethod(null, 16, new Object[] { 1.0d }), "Bad argument #1");
    }

    @Test
    public void anUnknownMethodReturnsTrue() throws Exception {
        // The fall-through returns {true}; the index guard is the method table's job.
        Assert.assertEquals(1, control(mob()).callMethod(null, 99, new Object[0]).length);
    }

    @Test
    public void theNanoPropertiesSurviveAnNbtRoundTrip() {
        NanoProperties properties = new NanoProperties();
        properties.numOfBots = 7;
        properties.antennaID = UUID.randomUUID();

        NBTTagCompound tag = new NBTTagCompound();
        properties.saveNBTData(tag);

        NanoProperties loaded = new NanoProperties();
        loaded.loadNBTData(tag);
        Assert.assertEquals(7, loaded.numOfBots);
        Assert.assertEquals(properties.antennaID, loaded.antennaID);
    }

    @Test
    public void nanoPropertiesTolerateAMissingAntenna() {
        // saveNBTData used to guard this with try/catch (NullPointerException) (CODE_REVIEW.md section 5.34).
        NanoProperties properties = new NanoProperties();
        NBTTagCompound tag = new NBTTagCompound();
        properties.saveNBTData(tag);

        NanoProperties loaded = new NanoProperties();
        loaded.loadNBTData(tag);
        Assert.assertEquals(0, loaded.numOfBots);
        Assert.assertNull(loaded.antennaID);
    }

    @Test
    public void nanoPropertiesTolerateGarbage() {
        NBTTagCompound outer = new NBTTagCompound();
        NBTTagCompound inner = new NBTTagCompound();
        inner.setString("antenna", "not-a-uuid");
        outer.setTag(NanoProperties.IDENTIFIER, inner);
        NanoProperties loaded = new NanoProperties();
        loaded.loadNBTData(outer);
        Assert.assertNull(loaded.antennaID);
    }

    private static void assertLuaError(ThrowingCall call, String expectedFragment) {
        try {
            call.run();
            Assert.fail("expected a LuaException mentioning " + expectedFragment);
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .toLowerCase()
                    .contains(expectedFragment.toLowerCase()));
        } catch (Exception e) {
            Assert.fail("expected a LuaException, got " + e);
        }
    }

    private interface ThrowingCall {

        void run() throws Exception;
    }
}
