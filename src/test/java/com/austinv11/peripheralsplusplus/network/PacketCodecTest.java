package com.austinv11.peripheralsplusplus.network;

import org.junit.Assert;
import org.junit.Test;

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
            0,
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
        SynthPacket out = new SynthPacket("text", "kevin16", null, null, null, null, null, 0, 0, 0, 0, null);
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
        AudioPacket out = new AudioPacket("en", "a duck", 1, 2, 3, 0, TurtleSide.Right);
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
        AudioPacket out = new AudioPacket("en", "text", 0, 0, 0, 0, null);
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
}
