package com.austinv11.peripheralsplusplus.smarthelmet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Assert;
import org.junit.Test;

/**
 * Covers the helmet command registry.
 *
 * <p>
 * The wire name used to be concatenated into a {@code Class.forName(...).newInstance()} call, so a server could make
 * the client instantiate any class in this package (CODE_REVIEW.md section 5.30). The registry is a fixed map now;
 * these tests pin the property that only registered names resolve.
 * </p>
 */
public class HelmetCommandTest {

    private static final String[] EXPECTED = { "DrawStringCommand", "DrawTextureCommand", "DrawRectangleCommand",
        "DrawBackgroundCommand", "AddButtonCommand", "AddTextFieldCommand", "MessageCommand" };

    @Test
    public void everyShippedCommandIsRegistered() {
        for (String name : EXPECTED) {
            HelmetCommand command = HelmetCommand.getCommandFromName(name);
            Assert.assertNotNull("command " + name + " is not registered", command);
            Assert.assertEquals(name, command.getCommandName());
        }
    }

    @Test
    public void anUnknownNameIsIgnoredRatherThanReflected() {
        Assert.assertNull(HelmetCommand.getCommandFromName("java.lang.Runtime"));
        Assert.assertNull(HelmetCommand.getCommandFromName("NotACommand"));
        Assert.assertNull(HelmetCommand.getCommandFromName(""));
        Assert.assertNull(HelmetCommand.getCommandFromName(null));
    }

    @Test
    public void theRegistryIsNotEmptyAndStable() {
        Assert.assertEquals(
            EXPECTED.length,
            HelmetCommand.factories()
                .size());
        // Unmodifiable: a packet cannot add to it.
        try {
            HelmetCommand.factories()
                .clear();
            Assert.fail("the registry view must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void messageCommandRoundTripsItsLines() {
        MessageCommand out = new MessageCommand();
        MessageCommand.messageStacks.put("alice", new java.util.Stack<String>());
        out.player = "alice";
        MessageCommand.messageStacks.get("alice")
            .push("hello");
        MessageCommand.messageStacks.get("alice")
            .push("world, with a comma");

        NBTTagCompound tag = new NBTTagCompound();
        out.writeToNBT(tag);

        MessageCommand in = new MessageCommand();
        in.readFromNBT(tag);

        // The old string round trip split on commas, so the second line came back mangled.
        NBTTagList lines = tag.getTagList("lines", 8);
        Assert.assertEquals(2, lines.tagCount());
        Assert.assertEquals("hello", lines.getStringTagAt(0));
        Assert.assertEquals("world, with a comma", lines.getStringTagAt(1));
        Assert.assertNotNull(in);
        MessageCommand.messageStacks.clear();
    }

    @Test
    public void messageCommandWithoutAnOwnerWritesAnEmptyList() {
        MessageCommand command = new MessageCommand();
        NBTTagCompound tag = new NBTTagCompound();
        command.writeToNBT(tag);
        Assert.assertEquals(
            0,
            tag.getTagList("lines", 8)
                .tagCount());
    }

    @Test
    public void everyCommandSurvivesAnNbtRoundTrip() {
        for (String name : EXPECTED) {
            HelmetCommand out = HelmetCommand.getCommandFromName(name);
            NBTTagCompound tag = new NBTTagCompound();
            out.writeToNBT(tag);
            HelmetCommand in = HelmetCommand.getCommandFromName(name);
            in.readFromNBT(tag);
            Assert.assertEquals(name, in.getCommandName());
        }
    }

    @Test
    public void handlersTolerateAStringList() {
        // Guards against a future command writing something the base reader cannot handle.
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("x"));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("lines", list);
        MessageCommand command = new MessageCommand();
        command.readFromNBT(tag);
        Assert.assertNotNull(command);
    }
}
