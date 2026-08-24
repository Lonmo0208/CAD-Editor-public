package com.github.rinorsi.cadeditor.common;

import com.github.rinorsi.cadeditor.common.logic.RateLimiter;
import com.github.rinorsi.cadeditor.common.network.ModNotificationPacket;
import com.github.rinorsi.cadeditor.common.network.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void onPlayerJoin(ServerPlayer player) {
        try {
            NetworkManager.sendToClient(player, NetworkManager.SERVER_NOTIFICATION, ModNotificationPacket.Server.INSTANCE);
        } catch (Exception e) {
            LOGGER.debug("Failed to send CAD Editor notification to {} (client likely does not have the mod)", player.getGameProfile().getName());
        }
    }

    public static void onPlayerLeave(ServerPlayer player) {
        ServerContext.removeModdedClient(player);
        RateLimiter.removePlayer(player.getUUID());
    }
}
