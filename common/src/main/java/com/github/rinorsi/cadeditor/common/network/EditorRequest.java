package com.github.rinorsi.cadeditor.common.network;

import net.minecraft.server.level.ServerPlayer;

public interface EditorRequest<R> {
    R createResponse(ServerPlayer player);

    NetworkHandler.Client<R> getResponseNetworkHandler();

    default void handleRequestAndSendResponse(ServerPlayer player) {
        NetworkManager.sendToClient(player, getResponseNetworkHandler(), createResponse(player));
    }
}
