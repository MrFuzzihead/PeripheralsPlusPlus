package com.austinv11.peripheralsplusplus.event.handler;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;

import com.austinv11.peripheralsplusplus.PeripheralsPlusPlus;
import com.austinv11.peripheralsplusplus.network.RobotEventPacket;
import com.austinv11.peripheralsplusplus.reference.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class RobotHandler {

    private Robot robot;
    public static CopyOnWriteArrayList<RobotOperation> operationList = new CopyOnWriteArrayList<RobotOperation>();

    public RobotHandler() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            PeripheralsPlusPlus.LOGGER.error("Unable to create the input Robot, nano-bot input is disabled: " + e);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        doOperations();
    }

    private void doOperations() {
        if (robot == null) {
            // No AWT Robot (headless client, no display): drop the queue rather than NPE every tick.
            operationList.clear();
            return;
        }
        for (RobotOperation operation : operationList) {
            if (operation.isFinished()) {
                operation.onFinish(robot);
                operationList.remove(operation);
            } else operation.tick(robot);
        }
    }

    public static abstract class RobotOperation {

        private int ticker;
        private int maxTick;

        public RobotOperation() {
            maxTick = ticker = Math.round((float) (Config.secondsBeforeReversal * 20));
        }

        public boolean isFinished() {
            return ticker <= 0;
        }

        public void tick(Robot robot) {
            // A non-positive reversal delay must still press once, not finish before it ever operates.
            if (maxTick == ticker) operate(robot);
            if (ticker > 0) ticker--;
        }

        public abstract void onFinish(Robot robot);

        public abstract void operate(Robot robot);
    }

    public static class KeyBoardOperation extends RobotOperation {

        public RobotEventPacket.PressType pressType;
        public String key;

        public KeyBoardOperation() {
            super();
        }

        @Override
        public void onFinish(Robot robot) {
            int code = keyCode();
            if (code >= 0 && pressType == RobotEventPacket.PressType.PRESS) robot.keyRelease(code);
        }

        @Override
        public void operate(Robot robot) {
            int code = keyCode();
            if (code >= 0) robot.keyPress(code);
        }

        /**
         * @return the {@code KeyEvent.VK_*} code for this operation's key name, or {@code -1} when it is not one
         */
        public int keyCode() {
            if (key == null) return -1;
            try {
                Field field = KeyEvent.class.getField("VK_" + key.toUpperCase(java.util.Locale.ROOT));
                return field.getInt(null);
            } catch (NoSuchFieldException e) {
                PeripheralsPlusPlus.LOGGER.error("Unknown key name '" + key + "', ignoring the robot event");
            } catch (IllegalAccessException e) {
                PeripheralsPlusPlus.LOGGER.error("Unable to read the key code for '" + key + "': " + e);
            }
            return -1;
        }
    }

    public static class MouseClickOperation extends RobotOperation {

        public RobotEventPacket.PressType pressType;
        public int button;

        public MouseClickOperation() {
            super();
        }

        @Override
        public void onFinish(Robot robot) {
            if (pressType == RobotEventPacket.PressType.PRESS) robot.mouseRelease(button);
        }

        @Override
        public void operate(Robot robot) {
            robot.mousePress(button);
        }
    }

    public static class MouseMoveOperation extends RobotOperation {

        public int x, y;

        public MouseMoveOperation() {
            super();
        }

        @Override
        public void onFinish(Robot robot) {

        }

        @Override
        public void operate(Robot robot) {
            robot.mouseMove(x, y);
        }
    }
}
