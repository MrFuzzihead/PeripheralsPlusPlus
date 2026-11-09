package com.austinv11.peripheralsplusplus.cleverbot;

import com.austinv11.collectiveframework.multithreading.SimpleRunnable;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAIChatBox;
import com.austinv11.peripheralsplusplus.tiles.TileEntityAIChatBox.BotSessionLuaObject;

public class AIChatRequest {

    public static final String EVENT = "ai_response";

    private final TileEntityAIChatBox tileEntity;
    private final BotSessionLuaObject session;
    private final String message;

    // private boolean complete = false;
    private boolean success = false;
    private String response = null;

    public AIChatRequest(final TileEntityAIChatBox tileEntity, final BotSessionLuaObject session,
        final String message) {
        this.tileEntity = tileEntity;
        this.session = session;
        this.message = message;

        new SimpleRunnable() {

            @Override
            public void run() {
                try {
                    response = session.think(message);
                    success = true;
                } catch (Exception e) {
                    response = e.toString();
                    success = false;
                }

                // Queue event
                // ai_response: string side, bool success, string response, string uuid
                tileEntity.sendEvent(
                    new Object[] { success, response, session.getUUID()
                        .toString() });
            }

            @Override
            public String getName() {
                return "PPP AI Chat Request";
            }
        }.startOnce();
        // startOnce(), not start(): CollectiveFramework 1.0.1 turned start() into a 1 ms scheduler loop (and
        // made RESTRICT_THREAD_USAGE a no-op), so start() here would re-issue the Cleverbot request forever.
        // See CODE_REVIEW.md section 3.1.
    }
}
