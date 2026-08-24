package com.github.rinorsi.cadeditor.common;

import com.github.rinorsi.cadeditor.common.logic.PermissionLogic;
import com.github.rinorsi.cadeditor.common.network.ModNotificationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerContext {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<UUID> moddedClients = new HashSet<>();

    /**
     * Minimum allowed client version: clients at or below this version (including old-format empty versions)
     * will be kicked, as those versions may have security vulnerabilities.
     */
    private static final String MIN_SECURE_VERSION = "0.1.1";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]+)*)");

    private ServerContext() {
    }

    public static void removeModdedClient(ServerPlayer player) {
        LOGGER.debug("Removing {} from modded clients", player.getGameProfile().getName());
        moddedClients.remove(player.getGameProfile().getId());
    }

    public static void addModdedClient(ServerPlayer player, ModNotificationPacket.Client packet) {
        // OP players (ADMIN) or players on the version whitelist bypass the version warning
        if (PermissionLogic.isAdmin(player) || VersionWhitelist.isWhitelisted(player.getGameProfile().getId())) {
            LOGGER.debug("Player {} ({}) bypasses CAD Editor version check (OP or whitelisted)", player.getGameProfile().getName(), player.getGameProfile().getId());
            moddedClients.add(player.getGameProfile().getId());
            return;
        }
        String version = packet.modVersion();
        if (!isSecureVersion(version)) {
            LOGGER.warn("[VULNERABLE_VERSION] Kicking player '{}' ({}) because they use CAD Editor version '{}' (<= {})",
                    player.getGameProfile().getName(), player.getGameProfile().getId(), version, MIN_SECURE_VERSION);
            player.connection.disconnect(buildVulnerableVersionKickMessage());
            return;
        }
        LOGGER.debug("Adding {} to modded clients (via CLIENT_NOTIFICATION, version {})", player.getGameProfile().getName(), version);
        moddedClients.add(player.getGameProfile().getId());
    }

    public static void markClientModded(ServerPlayer player) {
        moddedClients.add(player.getGameProfile().getId());
    }

    public static boolean isClientModded(ServerPlayer player) {
        return moddedClients.contains(player.getGameProfile().getId());
    }

    /**
     * Check whether the client version is secure (strictly greater than the minimum secure version).
     * Empty/unknown/unparseable versions are treated as insecure (old clients or not reported).
     */
    private static boolean isSecureVersion(String version) {
        int[] client = parseVersion(version);
        int[] min = parseVersion(MIN_SECURE_VERSION);
        if (client == null || min == null) {
            return false;
        }
        for (int i = 0; i < client.length; i++) {
            int cv = i < client.length ? client[i] : 0;
            int mv = i < min.length ? min[i] : 0;
            if (cv > mv) {
                return true;
            }
            if (cv < mv) {
                return false;
            }
        }
        // Exactly equal to the minimum version, treated as insecure (including 0.1.1)
        return false;
    }

    private static int[] parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    private static Component buildVulnerableVersionKickMessage() {
        return Component.literal("检测到你可能使用了，违法模组 CAD Editor，请前往模组文件夹删除该模组！\n")
                .append(Component.literal("Detected that you may be using the illegal mod CAD Editor. Please delete this mod from your mods folder!"));
    }
}
