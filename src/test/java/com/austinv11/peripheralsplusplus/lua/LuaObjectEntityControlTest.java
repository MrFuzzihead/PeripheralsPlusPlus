package com.austinv11.peripheralsplusplus.lua;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
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
        // The robot-input methods cast to EntityPlayerMP to send packets.
        EntityPlayer entity = Mockito.mock(EntityPlayerMP.class);
        entity.worldObj = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(entity.getExtendedProperties(NanoProperties.IDENTIFIER))
            .thenReturn(new NanoProperties());
        return entity;
    }

    private static LuaObjectEntityControl control(Entity entity) {
        return new LuaObjectEntityControl(UUID.randomUUID(), entity);
    }

    /** @param id the antenna id the entity is registered with */
    private static LuaObjectEntityControl control(Entity entity, UUID id) {
        return new LuaObjectEntityControl(id, entity);
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

    // ---------------------------------------------------------------------------------------------------------
    // Registered entities: every mutating branch
    //
    // ItemNanoSwarm#doInstruction only returns true when the entity is in an antenna's associatedEntities list and
    // has a NanoProperties with bots left, which is exactly what a live swarm sets up. Registering that by hand makes
    // every "the instruction ran" branch reachable without a game.
    // ---------------------------------------------------------------------------------------------------------

    /** Registers a fresh antenna and wires {@code entity} to it, returning the antenna id. */
    private static java.util.UUID infecting(net.minecraft.entity.Entity entity, int bots) {
        java.util.UUID id = java.util.UUID.randomUUID();
        NanoProperties properties = new NanoProperties();
        properties.numOfBots = bots;
        properties.antennaID = id;
        Mockito.when(entity.getExtendedProperties(NanoProperties.IDENTIFIER))
            .thenReturn(properties);

        com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna antenna = new com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna();
        antenna.identifier = id;
        antenna.associatedEntities.add(entity);
        com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna.antenna_registry.put(id, antenna);
        return id;
    }

    @org.junit.After
    public void tearDown() {
        com.austinv11.peripheralsplusplus.tiles.TileEntityAntenna.antenna_registry.clear();
        PeripheralsPlusPlus.NETWORK = null;
    }

    @Test
    public void anInfectedMobCanBeHurtAndHealed() throws Exception {
        EntityLiving mob = mob();
        LuaObjectEntityControl control = control(mob, infecting(mob, 20));

        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 2, new Object[0]));
        Mockito.verify(mob)
            .attackEntityFrom(Mockito.any(net.minecraft.util.DamageSource.class), Mockito.eq(1.0F));
        Mockito.verify(mob)
            .heal(1.0F);
    }

    @Test
    public void anInfectedMobReportsItsReadings() throws Exception {
        EntityLiving mob = mob();
        Mockito.when(mob.getHealth())
            .thenReturn(7.0F);
        Mockito.when(mob.getMaxHealth())
            .thenReturn(20.0F);
        Mockito.when(mob.getCustomNameTag())
            .thenReturn("Fido");
        LuaObjectEntityControl control = control(mob, infecting(mob, 5));

        Assert.assertArrayEquals(new Object[] { 7.0F }, control.callMethod(null, 3, new Object[0]));
        Assert.assertArrayEquals(new Object[] { 20.0F }, control.callMethod(null, 4, new Object[0]));
        Assert.assertArrayEquals(new Object[] { false }, control.callMethod(null, 5, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "Fido" }, control.callMethod(null, 7, new Object[0]));
        Assert.assertTrue(
            control.callMethod(null, 8, new Object[0])[0].toString()
                .startsWith("EntityLiving"));
    }

    @Test
    public void anInstructionConsumesOneBotAndThenRefuses() throws Exception {
        EntityLiving mob = mob();
        LuaObjectEntityControl control = control(mob, infecting(mob, 1));

        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 1, new Object[0]));
        // The last bot is gone, so the entity is dropped from the antenna and the next call refuses.
        assertLuaError(() -> control.callMethod(null, 3, new Object[0]), "cannot be interacted with");
    }

    @Test
    public void anInfectedPlayerReportsItsProfile() throws Exception {
        EntityPlayer player = player();
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
            java.util.UUID.randomUUID(),
            "alice");
        Mockito.when(player.getGameProfile())
            .thenReturn(profile);
        Mockito.when(player.getFoodStats())
            .thenReturn(Mockito.mock(net.minecraft.util.FoodStats.class));
        Mockito.when(player.getHealth())
            .thenReturn(19.0F);
        Mockito.when(player.getDisplayName())
            .thenReturn("alice");
        LuaObjectEntityControl control = control(player, infecting(player, 20));

        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 1, new Object[0]));
        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 2, new Object[0]));
        Assert.assertArrayEquals(new Object[] { 19.0F }, control.callMethod(null, 3, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "alice" }, control.callMethod(null, 7, new Object[0]));
        Assert.assertArrayEquals(new Object[] { "alice" }, control.callMethod(null, 8, new Object[0]));
        Assert.assertArrayEquals(
            new Object[] { profile.getId()
                .toString() },
            control.callMethod(null, 9, new Object[0]));
        Assert.assertArrayEquals(new Object[] { 0 }, control.callMethod(null, 10, new Object[0]));
    }

    @Test
    public void theRobotInputMethodsQueuePackets() throws Exception {
        PeripheralsPlusPlus.NETWORK = Mockito.mock(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper.class);
        EntityPlayer player = player();
        LuaObjectEntityControl control = control(player, infecting(player, 20));

        control.callMethod(null, 11, new Object[] { 0.0d });
        control.callMethod(null, 12, new Object[] { 0.0d });
        control.callMethod(null, 13, new Object[] { "W" });
        control.callMethod(null, 14, new Object[] { "W" });
        control.callMethod(null, 15, new Object[] { 10.0d, 20.0d });

        Mockito.verify(PeripheralsPlusPlus.NETWORK, Mockito.times(5))
            .sendTo(
                Mockito.any(com.austinv11.peripheralsplusplus.network.RobotEventPacket.class),
                Mockito.any(EntityPlayerMP.class));
    }

    @Test
    public void theRobotInputMethodsValidateTheirArguments() {
        PeripheralsPlusPlus.NETWORK = Mockito.mock(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper.class);
        EntityPlayer player = player();
        LuaObjectEntityControl control = control(player, infecting(player, 20));

        assertLuaError(() -> control.callMethod(null, 13, new Object[0]), "Too few arguments");
        assertLuaError(() -> control.callMethod(null, 15, new Object[] { 1.0d }), "Too few arguments");
        // click() takes a number: a string is rejected by the reader instead of being cast (section 4.7).
        assertLuaError(() -> control.callMethod(null, 11, new Object[] { "left" }), "number");
        assertLuaError(() -> control.callMethod(null, 13, new Object[] { 1.0d }), "string");
    }

    @Test
    public void anInfectedPlayerCanBeWhisperedTo() throws Exception {
        PeripheralsPlusPlus.NETWORK = Mockito.mock(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper.class);
        EntityPlayer player = player();
        LuaObjectEntityControl control = control(player, infecting(player, 5));

        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 16, new Object[] { "hello" }));
        Assert.assertArrayEquals(new Object[] { true }, control.callMethod(null, 16, new Object[] { "hello", "bob" }));
        Mockito.verify(player, Mockito.times(2))
            .addChatComponentMessage(Mockito.any(net.minecraft.util.IChatComponent.class));
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
