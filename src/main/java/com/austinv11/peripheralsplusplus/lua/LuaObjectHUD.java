package com.austinv11.peripheralsplusplus.lua;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Stack;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.network.CommandPacket;
import com.austinv11.peripheralsplusplus.network.GuiPacket;
import com.austinv11.peripheralsplusplus.smarthelmet.AddButtonCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.AddTextFieldCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.DrawBackgroundCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.DrawRectangleCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.DrawStringCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.DrawTextureCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.HelmetCommand;
import com.austinv11.peripheralsplusplus.smarthelmet.MessageCommand;
import com.austinv11.peripheralsplusplus.utils.Util;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

public class LuaObjectHUD implements ILuaObject {

    public ArrayDeque<HelmetCommand> renderStack = new ArrayDeque<HelmetCommand>();
    private EntityPlayer player;
    public int width = -1;
    public int height = -1;
    private UUID uuid;
    private boolean isGui = false;
    public MessageCommand messageCommand = new MessageCommand();

    /** The name this HUD was created for; every map lookup uses it so a rename cannot split the queue. */
    private final String playerName;

    public LuaObjectHUD(String playerName, UUID uuid) {
        this.playerName = playerName;
        this.player = Util.getPlayer(playerName);
        this.uuid = uuid;
        messagesFor(playerName);
        messageCommand.player = playerName;
        renderStack.add(messageCommand);
    }

    /**
     * The pending message queue for a player, created on demand.
     *
     * @param name the player name
     * @return the live stack
     */
    private static Stack<String> messagesFor(String name) {
        if (name == null) name = "";
        Stack<String> existing = MessageCommand.messageStacks.get(name);
        if (existing != null) return existing;
        Stack<String> created = new Stack<String>();
        Stack<String> previous = MessageCommand.messageStacks.putIfAbsent(name, created);
        return previous == null ? created : previous;
    }

    /**
     * @return the player, never {@code null}
     * @throws LuaException when the player has logged off since this HUD was created
     */
    private EntityPlayer requirePlayer() throws LuaException {
        EntityPlayer resolved = player == null ? Util.getPlayer(playerName) : player;
        if (resolved == null) throw new LuaException("Player " + playerName + " is no longer online");
        player = resolved;
        return resolved;
    }

    /** Sends nothing when the player is gone, instead of NPE-ing on the cast. */
    private void sendToPlayer(cpw.mods.fml.common.network.simpleimpl.IMessage message) throws LuaException {
        PeripheralsPlusPlus.NETWORK.sendTo(message, (EntityPlayerMP) requirePlayer());
    }

    /** Empties this player's pending chat lines and leaves only the message command on the stack. */
    private void clearAndReset() {
        messagesFor(playerName).clear();
        renderStack.clear();
        renderStack.add(messageCommand);
    }

    public LuaObjectHUD(String player, UUID uuid, boolean isGui) {
        this(player, uuid);
        this.isGui = isGui;
    }

    /** The HUD methods available to an overlay program. */
    private static final String[] OVERLAY_METHODS = new String[] { "getResolution", "sendMessage", "drawString",
        "drawTexture", "drawRectangle", "drawHorizontalLine", "drawVerticalLine", "getColorFromRGB", "sync", "clear",
        "add", "getGUI" };

    /** The HUD methods available to a GUI program; adds the interactive commands. */
    private static final String[] GUI_METHODS = new String[] { "getResolution", "sendMessage", "drawString",
        "drawTexture", "drawRectangle", "drawHorizontalLine", "drawVerticalLine", "getColorFromRGB", "sync", "clear",
        "add", "open", "close", "drawBackground", "addButton", "addTextField" };

    @Override
    public String[] getMethodNames() {
        return isGui ? GUI_METHODS : OVERLAY_METHODS;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] rawArguments)
        throws LuaException, InterruptedException {
        LuaArgs arguments = LuaArgs.of(rawArguments);
        switch (method) {
            case 0: // getResolution
                return new Object[] { width, height };
            case 1: // sendMessage(text)
                messagesFor(playerName).push(arguments.string(0));
                break;
            case 2: { // drawString(text, x, y, [color], [shadow])
                DrawStringCommand command = new DrawStringCommand();
                command.message = arguments.string(0);
                command.x = arguments.integer(1);
                command.y = arguments.integer(2);
                command.color = arguments.integer(3, Color.WHITE.getRGB());
                command.shadow = arguments.bool(4, true);
                renderStack.add(command);
                break;
            }
            case 3: { // drawTexture(resource, x, y, [width], [height], [u], [v])
                DrawTextureCommand command = new DrawTextureCommand();
                command.resource = arguments.string(0);
                command.x = arguments.integer(1);
                command.y = arguments.integer(2);
                command.width = arguments.integer(3, 256);
                command.height = arguments.integer(4, 256);
                command.u = arguments.integer(5, -1);
                // This used to be read only above 36 arguments, so every tiled texture rendered the whole sheet.
                command.v = arguments.integer(6, -1);
                renderStack.add(command);
                break;
            }
            case 4: { // drawRectangle(x1, y1, x2, y2, color, [color2])
                DrawRectangleCommand command = new DrawRectangleCommand();
                command.x1 = arguments.integer(0);
                command.y1 = arguments.integer(1);
                command.x2 = arguments.integer(2);
                command.y2 = arguments.integer(3);
                command.color = new Color(arguments.integer(4), true);
                if (arguments.has(5)) command.color2 = new Color(arguments.integer(5), true);
                renderStack.add(command);
                break;
            }
            case 5: { // drawHorizontalLine(x, y, length, color)
                DrawRectangleCommand command = new DrawRectangleCommand();
                command.x1 = arguments.integer(0);
                command.y1 = arguments.integer(1);
                command.x2 = arguments.integer(2) + 1;
                command.y2 = command.y1 + 1;
                command.color = new Color(arguments.integer(3), true);
                renderStack.add(command);
                break;
            }
            case 6: { // drawVerticalLine(x, y, length, color)
                DrawRectangleCommand command = new DrawRectangleCommand();
                command.x1 = arguments.integer(0);
                command.y1 = arguments.integer(1) + 1;
                command.x2 = command.x1 + 1;
                command.y2 = arguments.integer(2);
                command.color = new Color(arguments.integer(3), true);
                renderStack.add(command);
                break;
            }
            case 7: // getColorFromRGB(r, g, b, [a])
                if (arguments.has(3)) return new Object[] { new Color(
                    arguments.integer(0),
                    arguments.integer(1),
                    arguments.integer(2),
                    Math.max(0, Math.min(255, arguments.integer(3)))).getRGB() };
                return new Object[] {
                    new Color(arguments.integer(0), arguments.integer(1), arguments.integer(2)).getRGB() };
            case 8: // sync()
                sendToPlayer(new CommandPacket(stackToArray(), uuid, false, isGui));
                clearAndReset();
                break;
            case 9: // clear()
                sendToPlayer(new CommandPacket(new HelmetCommand[0], uuid, false, isGui));
                clearAndReset();
                break;
            case 10: // add()
                sendToPlayer(new CommandPacket(stackToArray(), uuid, true, isGui));
                clearAndReset();
                break;
            case 11: // open() for a GUI program, getGUI() for an overlay program
                if (isGui) sendToPlayer(new GuiPacket(false));
                else return new Object[] { new LuaObjectHUD(this.playerName, this.uuid, true) };
                break;
            case 12: // close()
                if (!isGui) throw new LuaException("close() is not available to HUD programs");
                sendToPlayer(new GuiPacket(true));
                break;
            case 13: { // drawBackground([defaultBackground])
                DrawBackgroundCommand command = new DrawBackgroundCommand();
                if (arguments.has(0)) command.defaultBackground = arguments.bool(0);
                renderStack.add(command);
                break;
            }
            case 14: { // addButton(id, x, y, width, height, text)
                AddButtonCommand command = new AddButtonCommand();
                command.id = arguments.integer(0);
                command.x = arguments.integer(1);
                command.y = arguments.integer(2);
                command.width = arguments.integer(3);
                command.height = arguments.integer(4);
                command.message = arguments.string(5);
                renderStack.add(command);
                break;
            }
            case 15: { // addTextField(id, x, y, width, height, [defaultText])
                AddTextFieldCommand command = new AddTextFieldCommand();
                command.id = arguments.integer(0);
                command.x = arguments.integer(1);
                command.y = arguments.integer(2);
                command.width = arguments.integer(3);
                command.height = arguments.integer(4);
                command.message = arguments.string(5, command.message);
                renderStack.add(command);
                break;
            }
            default:
                return new Object[0];
        }
        return new Object[0];
    }

    private HelmetCommand[] stackToArray() {
        // Iterate rather than drain: the old version emptied the deque even when the packet failed to send.
        return renderStack.toArray(new HelmetCommand[0]);
    }
}
