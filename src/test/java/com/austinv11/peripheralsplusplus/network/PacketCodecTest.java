package com.austinv11.peripheralsplusplus.network;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.smarthelmet.DrawStringCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.HelmetCommand;

import cpw.mods.fml.common.network.ByteBufUtils;
import dan200.computercraft.api.turtle.TurtleSide;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Round-trips the mod's packets through their real {@code toBytes}/{@code fromBytes} implementation.
 *
 * <p>
 * Packet codecs are pure data, so they need no game — and they are where the mod's least visible bugs live:
 * {@code SynthPacket} used to read the voice {@code rate} out of the pitch key and write the volume into the pitch
 * (CODE_REVIEW.md section 4.5), which no amount of in-game play would attribute to the packet.
 * </p>
 */
public class PacketCodecTest {

    @Test
    public void synthPacketPreservesEveryVoiceParameter() {
        SynthPacket out = new SynthPacket(
            "hello world",
            "kevin16",
            0.5f,
            1.5f,
            2.5f,
            3.5f,
            4.5f,
            10,
            64,
            -30,
            TurtleSide.Left);

        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        SynthPacket in = new SynthPacket();
        in.fromBytes(buf);

        Assert.assertEquals("hello world", in.text);
        Assert.assertEquals("kevin16", in.voice);
        Assert.assertEquals(0.5f, in.pitch, 0f);
        Assert.assertEquals(1.5f, in.pitchRange, 0f);
        Assert.assertEquals(2.5f, in.pitchShift, 0f);
        // Regression: this used to come back as the pitch value.
        Assert.assertEquals(3.5f, in.rate, 0f);
        Assert.assertEquals(4.5f, in.volume, 0f);
        Assert.assertEquals(10, in.x);
        Assert.assertEquals(64, in.y);
        Assert.assertEquals(-30, in.z);
        Assert.assertEquals(TurtleSide.Left, in.side);
    }

    @Test
    public void synthPacketPreservesNulls() {
        SynthPacket out = new SynthPacket("text", "kevin16", null, null, null, null, null, 0, 0, 0, null);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        SynthPacket in = new SynthPacket();
        in.fromBytes(buf);
        Assert.assertNull(in.pitch);
        Assert.assertNull(in.pitchRange);
        Assert.assertNull(in.pitchShift);
        Assert.assertNull(in.rate);
        Assert.assertNull(in.volume);
        Assert.assertNull(in.side);
    }

    @Test
    public void audioPacketRoundTrips() {
        AudioPacket out = new AudioPacket("en", "a duck", 1, 2, 3, TurtleSide.Right);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        AudioPacket in = new AudioPacket();
        in.fromBytes(buf);
        Assert.assertEquals("en", in.lang);
        Assert.assertEquals("a duck", in.text);
        Assert.assertEquals(1, in.x);
        Assert.assertEquals(2, in.y);
        Assert.assertEquals(3, in.z);
        Assert.assertEquals(TurtleSide.Right, in.side);
    }

    @Test
    public void audioPacketToleratesANullSide() {
        AudioPacket out = new AudioPacket("en", "text", 0, 0, 0, null);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        AudioPacket in = new AudioPacket();
        in.fromBytes(buf);
        Assert.assertNull(in.side);
    }

    @Test
    public void guiPacketRoundTrips() {
        GuiPacket close = new GuiPacket(true);
        ByteBuf buf = Unpooled.buffer();
        close.toBytes(buf);
        GuiPacket decoded = new GuiPacket();
        decoded.fromBytes(buf);
        Assert.assertTrue(decoded.close);

        GuiPacket open = new GuiPacket(false);
        buf = Unpooled.buffer();
        open.toBytes(buf);
        decoded = new GuiPacket();
        decoded.fromBytes(buf);
        Assert.assertFalse(decoded.close);
    }

    @Test
    public void robotEventPacketRoundTripsEveryShape() {
        RobotEventPacket keyboard = new RobotEventPacket(
            RobotEventPacket.ActionType.KEYBOARD,
            RobotEventPacket.PressType.PRESS,
            "W");
        ByteBuf buf = Unpooled.buffer();
        keyboard.toBytes(buf);
        RobotEventPacket decoded = new RobotEventPacket();
        decoded.fromBytes(buf);
        Assert.assertEquals(RobotEventPacket.ActionType.KEYBOARD, decoded.action);
        Assert.assertEquals(RobotEventPacket.PressType.PRESS, decoded.type);
        Assert.assertEquals("W", decoded.info);

        RobotEventPacket click = new RobotEventPacket(
            RobotEventPacket.ActionType.MOUSE_CLICK,
            RobotEventPacket.PressType.RELEASE,
            1);
        buf = Unpooled.buffer();
        click.toBytes(buf);
        decoded = new RobotEventPacket();
        decoded.fromBytes(buf);
        Assert.assertEquals(RobotEventPacket.ActionType.MOUSE_CLICK, decoded.action);
        Assert.assertEquals(RobotEventPacket.PressType.RELEASE, decoded.type);
        Assert.assertEquals(Integer.valueOf(1), decoded.info);

        RobotEventPacket move = new RobotEventPacket(
            RobotEventPacket.ActionType.MOUSE_MOVE,
            RobotEventPacket.PressType.PRESS,
            new int[] { 12, 34 });
        buf = Unpooled.buffer();
        move.toBytes(buf);
        decoded = new RobotEventPacket();
        decoded.fromBytes(buf);
        Assert.assertArrayEquals(new int[] { 12, 34 }, (int[]) decoded.info);
    }

    @Test
    public void commandPacketRoundTripsItsCommands() {
        UUID uuid = UUID.randomUUID();
        DrawStringCommand command = new DrawStringCommand();
        command.message = "hello";
        command.x = 3;
        command.y = 4;
        command.color = 0xFFFFFFFF;
        command.shadow = true;

        CommandPacket out = new CommandPacket(new HelmetCommand[] { command }, uuid);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        CommandPacket in = new CommandPacket();
        in.fromBytes(buf);

        Assert.assertEquals(uuid, in.uuid);
        Assert.assertEquals(1, in.commands.length);
        Assert.assertNotNull(in.commands[0]);
        Assert.assertEquals("DrawStringCommand", in.commands[0].getCommandName());
    }

    @Test
    public void commandPacketIgnoresAnUnknownCommandType() {
        // A server used to be able to name any class here; the registry drops unknown names instead.
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "uuid",
            UUID.randomUUID()
                .toString());
        tag.setInteger("num", 1);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("type", "NotACommand");
        tag.setTag("0", entry);

        ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeTag(buf, tag);

        CommandPacket in = new CommandPacket();
        in.fromBytes(buf);

        Assert.assertEquals(1, in.commands.length);
        Assert.assertNull(in.commands[0]);
    }

    @Test
    public void commandPacketToleratesAMissingField() {
        // A command whose string field was never set reads back as "", and re-serialising it used to throw
        // IllegalArgumentException("Empty string not allowed") on the server thread.
        DrawStringCommand empty = new DrawStringCommand();
        ByteBuf buf = Unpooled.buffer();
        new CommandPacket(new HelmetCommand[] { empty }, UUID.randomUUID()).toBytes(buf);
        CommandPacket in = new CommandPacket();
        in.fromBytes(buf);

        ByteBuf second = Unpooled.buffer();
        in.toBytes(second);
        CommandPacket again = new CommandPacket();
        again.fromBytes(second);
        Assert.assertNotNull(again.commands[0]);
    }

    @Test
    public void commandPacketSurvivesANullCommandArray() {
        CommandPacket packet = new CommandPacket();
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CommandPacket in = new CommandPacket();
        in.fromBytes(buf);
        Assert.assertEquals(0, in.commands.length);
    }

    @Test
    public void chatPacketRoundTrips() {
        ChatPacket out = new ChatPacket("hello, world");
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        ChatPacket in = new ChatPacket();
        in.fromBytes(buf);
        Assert.assertEquals("hello, world", in.text);
    }

    @Test
    public void inputEventPacketRoundTrips() {
        UUID uuid = UUID.randomUUID();
        InputEventPacket out = new InputEventPacket(uuid, 42, true, "keyInput", "alice");
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        InputEventPacket in = new InputEventPacket();
        in.fromBytes(buf);
        Assert.assertEquals(uuid, in.uuid);
        Assert.assertEquals(42, in.key);
        Assert.assertTrue(in.state);
        Assert.assertEquals("keyInput", in.event);
        Assert.assertEquals("alice", in.player);
    }

    @Test
    public void textFieldInputEventPacketRoundTrips() {
        UUID uuid = UUID.randomUUID();
        TextFieldInputEventPacket out = new TextFieldInputEventPacket(uuid, "1", "typed", "textInput", "bob");
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        TextFieldInputEventPacket in = new TextFieldInputEventPacket();
        in.fromBytes(buf);
        Assert.assertEquals(uuid, in.uuid);
        Assert.assertEquals("1", in.key);
        Assert.assertEquals("typed", in.text);
        Assert.assertEquals("textInput", in.event);
        Assert.assertEquals("bob", in.player);
    }

    @Test
    public void scaleRequestPacketRoundTrips() {
        net.minecraft.tileentity.TileEntity tile = org.mockito.Mockito.mock(net.minecraft.tileentity.TileEntity.class);
        tile.xCoord = 1;
        tile.yCoord = 2;
        tile.zCoord = 3;
        ScaleRequestPacket out = new ScaleRequestPacket(tile, 7, -1);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        ScaleRequestPacket in = new ScaleRequestPacket();
        in.fromBytes(buf);
        Assert.assertEquals(1, in.coords.posX);
        Assert.assertEquals(3, in.coords.posZ);
        Assert.assertEquals(7, in.id);
        Assert.assertEquals(-1, in.dim);
    }

    @Test
    public void scaleRequestResponsePacketRoundTrips() {
        net.minecraft.util.ChunkCoordinates coords = new net.minecraft.util.ChunkCoordinates(4, 5, 6);
        ScaleRequestResponsePacket out = new ScaleRequestResponsePacket(coords, 3, 320, 200, 0);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        ScaleRequestResponsePacket in = new ScaleRequestResponsePacket();
        in.fromBytes(buf);
        Assert.assertEquals(4, in.coords.posX);
        Assert.assertEquals(3, in.id);
        Assert.assertEquals(320, in.width);
        Assert.assertEquals(200, in.height);
    }

    @Test
    public void ridableTurtlePacketRoundTrips() {
        RidableTurtlePacket out = new RidableTurtlePacket(9, RidableTurtlePacket.MovementCode.TURN_LEFT.code, 0);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);
        RidableTurtlePacket in = new RidableTurtlePacket();
        in.fromBytes(buf);
        Assert.assertEquals(9, in.getEntityId());
        Assert.assertEquals(RidableTurtlePacket.MovementCode.TURN_LEFT.code, in.getMovementCode());
        Assert.assertEquals(0, in.getDimension());
    }

    @Test
    public void movementCodesAreStable() {
        // These ordinals are the wire format and are also used to index MovementCode#values() on the server, so a
        // reordered enum silently swaps the controls (batch 5 shipped A/D and S/X swapped exactly that way).
        Assert.assertEquals(0, RidableTurtlePacket.MovementCode.FORWARD.code);
        Assert.assertEquals(1, RidableTurtlePacket.MovementCode.TURN_LEFT.code);
        Assert.assertEquals(2, RidableTurtlePacket.MovementCode.TURN_RIGHT.code);
        Assert.assertEquals(3, RidableTurtlePacket.MovementCode.ASCEND.code);
        Assert.assertEquals(4, RidableTurtlePacket.MovementCode.DESCEND.code);
        for (RidableTurtlePacket.MovementCode code : RidableTurtlePacket.MovementCode.values()) {
            Assert.assertEquals(
                "code and ordinal must agree: EntityRidableTurtle indexes values() by code",
                code.ordinal(),
                code.code);
        }
    }
}
