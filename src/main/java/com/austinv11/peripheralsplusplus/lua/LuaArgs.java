package com.austinv11.peripheralsplusplus.lua;

import java.util.HashMap;

import dan200.computercraft.api.lua.LuaException;

/**
 * Reads ComputerCraft Lua arguments with the validation every peripheral in this mod needs.
 *
 * <p>
 * Every {@code callMethod} used to inline the same three lines ("too few arguments", "bad argument #n (expected
 * number)") roughly 150 times. That copy-paste is where several bugs in this mod came from — an off-by-one in the
 * argument-length test, or a missing {@code instanceof} before a cast, is invisible in a wall of {@code if}s.
 * </p>
 *
 * <p>
 * Lua numbers arrive as {@link Double}, booleans as {@link Boolean} and strings as {@link String}; ComputerCraft
 * passes them 1-based with the first method argument at index {@code 0} in the Java array.
 * </p>
 */
public final class LuaArgs {

    private final Object[] arguments;

    private LuaArgs(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    /**
     * @param arguments the array handed to {@code IPeripheral#callMethod}
     * @return a reader over that array
     */
    public static LuaArgs of(Object[] arguments) {
        return new LuaArgs(arguments);
    }

    /**
     * @return the number of arguments supplied
     */
    public int size() {
        return arguments.length;
    }

    /**
     * @param index zero-based argument index
     * @return true when an argument exists at that index
     */
    public boolean has(int index) {
        return index >= 0 && index < arguments.length;
    }

    private LuaException badArgument(int index, String expected, String got) {
        // Lua is 1-based, so the message index is Java index + 1.
        return new LuaException("Bad argument #" + (index + 1) + " (" + expected + " expected, got " + got + ")");
    }

    private LuaException missing(int index) {
        return new LuaException("Bad argument #" + (index + 1) + " (expected a value, got nil)");
    }

    private String describe(Object value) {
        if (value == null) return "nil";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Double) return "number";
        if (value instanceof Object[]) return "table";
        return "table";
    }

    /**
     * Requires {@code count} arguments.
     *
     * @param count the minimum number of arguments
     * @throws LuaException when fewer were supplied
     */
    public LuaArgs atLeast(int count) throws LuaException {
        if (arguments.length < count) throw new LuaException("Too few arguments (expected " + count + ")");
        return this;
    }

    /**
     * Reads a number argument.
     *
     * @param index zero-based index
     * @return the value
     * @throws LuaException when the argument is missing or is not a number
     */
    public double number(int index) throws LuaException {
        if (!has(index)) throw missing(index);
        Object value = arguments[index];
        if (!(value instanceof Double)) throw badArgument(index, "number", describe(value));
        return (Double) value;
    }

    /**
     * Reads a number argument, or a default when the argument is absent.
     *
     * @param index        zero-based index
     * @param defaultValue returned when the argument is absent
     * @return the value
     * @throws LuaException when the argument is present but is not a number
     */
    public double number(int index, double defaultValue) throws LuaException {
        if (!has(index)) return defaultValue;
        Object value = arguments[index];
        if (!(value instanceof Double)) throw badArgument(index, "number", describe(value));
        return (Double) value;
    }

    /**
     * Reads a number argument as a rounded-down integer.
     *
     * @param index zero-based index
     * @return the value
     * @throws LuaException when the argument is missing or is not a number
     */
    public int integer(int index) throws LuaException {
        return (int) Math.floor(number(index));
    }

    /**
     * Reads a number argument as a rounded-down integer, or a default when it is absent.
     *
     * @param index        zero-based index
     * @param defaultValue returned when the argument is absent
     * @return the value
     * @throws LuaException when the argument is present but is not a number
     */
    public int integer(int index, int defaultValue) throws LuaException {
        return (int) Math.floor(number(index, defaultValue));
    }

    /**
     * Reads a string argument.
     *
     * @param index zero-based index
     * @return the value
     * @throws LuaException when the argument is missing or is not a string
     */
    public String string(int index) throws LuaException {
        if (!has(index)) throw missing(index);
        Object value = arguments[index];
        if (!(value instanceof String)) throw badArgument(index, "string", describe(value));
        return (String) value;
    }

    /**
     * Reads a string argument, or a default when the argument is absent.
     *
     * @param index        zero-based index
     * @param defaultValue returned when the argument is absent
     * @return the value
     * @throws LuaException when the argument is present but is not a string
     */
    public String string(int index, String defaultValue) throws LuaException {
        if (!has(index)) return defaultValue;
        Object value = arguments[index];
        if (!(value instanceof String)) throw badArgument(index, "string", describe(value));
        return (String) value;
    }

    /**
     * Reads a boolean argument.
     *
     * @param index zero-based index
     * @return the value
     * @throws LuaException when the argument is missing or is not a boolean
     */
    public boolean bool(int index) throws LuaException {
        if (!has(index)) throw missing(index);
        Object value = arguments[index];
        if (!(value instanceof Boolean)) throw badArgument(index, "boolean", describe(value));
        return (Boolean) value;
    }

    /**
     * Reads a boolean argument, or a default when the argument is absent.
     *
     * @param index        zero-based index
     * @param defaultValue returned when the argument is absent
     * @return the value
     * @throws LuaException when the argument is present but is not a boolean
     */
    public boolean bool(int index, boolean defaultValue) throws LuaException {
        if (!has(index)) return defaultValue;
        Object value = arguments[index];
        if (!(value instanceof Boolean)) throw badArgument(index, "boolean", describe(value));
        return (Boolean) value;
    }

    /**
     * Reads an argument that may be either a number or a string, as used by the "side" arguments of the sorter and
     * the teleporter.
     *
     * @param index zero-based index
     * @return the value, a {@link Double} or a {@link String}
     * @throws LuaException when the argument is missing or is neither a number nor a string
     */
    public Object numberOrString(int index) throws LuaException {
        if (!has(index)) throw missing(index);
        Object value = arguments[index];
        if (!(value instanceof Double) && !(value instanceof String))
            throw badArgument(index, "string or number", describe(value));
        return value;
    }

    /**
     * Builds a Lua table (a {@code Map<Integer, Object>} keyed from 1) from up to four values, stopping at the
     * first {@code null}.
     *
     * @param values the values, in Lua order
     * @return a 1-based table
     */
    public static HashMap<Integer, Object> table(Object... values) {
        HashMap<Integer, Object> map = new HashMap<Integer, Object>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) break;
            map.put(i + 1, values[i]);
        }
        return map;
    }
}
