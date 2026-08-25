package com.github.rinorsi.opswordvisual.fabric;

import com.github.rinorsi.cadeditor.client.RainbowNameHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricOpswordVisualModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RainbowNameHandler.onClientTick();
        });
    }
}
