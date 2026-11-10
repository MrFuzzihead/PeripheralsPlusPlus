package com.austinv11.peripheralsplusplus.tiles;

import java.util.Calendar;
import java.util.HashMap;

import com.austinv11.collectiveframework.utils.TimeProfiler;
import com.austinv11.peripheralsplusplus.reference.Config;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityTimeSensor extends MountedTileEntity {

    public static String publicName = "timeSensor";
    private String name = "tileEntityTimeSensor";

    private TimeProfiler profiler;

    public TileEntityTimeSensor() {
        super();
    }

    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return publicName;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "getDate", "getTime", "startTimer", "stopTimer" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        if (!Config.enableTimeSensor) throw new LuaException("Time Sensors have been disabled!");
        if (method == 0) {
            // Fields read straight off a Calendar: the old code allocated a SimpleDateFormat and a String[] per call
            // and depended on the default locale (CODE_REVIEW.md section 5.22).
            Calendar now = Calendar.getInstance();
            HashMap<String, Integer> map = new HashMap<String, Integer>();
            map.put("year", now.get(Calendar.YEAR));
            map.put("month", now.get(Calendar.MONTH) + 1);
            map.put("day", now.get(Calendar.DAY_OF_MONTH));
            map.put("hour", now.get(Calendar.HOUR_OF_DAY));
            map.put("minute", now.get(Calendar.MINUTE));
            map.put("second", now.get(Calendar.SECOND));
            return new Object[] { map };
        } else if (method == 1) {
            return new Object[] { System.currentTimeMillis() };
        } else if (method == 2) {
            profiler = new TimeProfiler();
            return new Object[] { true };
        } else if (method == 3) {
            long time = profiler == null ? 0 : profiler.getTime();
            profiler = null;
            return new Object[] { time };
        }
        return new Object[0];
    }

    @Override
    public boolean equals(IPeripheral other) {
        return (other == this);
    }
}
