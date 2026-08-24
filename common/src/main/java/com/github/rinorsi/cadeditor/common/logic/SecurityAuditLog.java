package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.EditorType;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SecurityAuditLog {
    private static final Logger SECURITY_LOG = LogManager.getLogger("CAD Editor Security");

    private SecurityAuditLog() {
    }

    public static void logDeniedRequest(ServerPlayer player, String targetType, EditorType editorType) {
        SECURITY_LOG.warn("[ACCESS_DENIED] Player '{}' ({}) attempted to request {} data without permission. Editor type: {}",
            player.getGameProfile().getName(), player.getGameProfile().getId(), targetType, editorType);
    }

    public static void logDeniedUpdate(ServerPlayer player, String targetType, EditorType editorType) {
        SECURITY_LOG.warn("[ACCESS_DENIED] Player '{}' ({}) attempted to update {} data without permission. Editor type: {}",
            player.getGameProfile().getName(), player.getGameProfile().getId(), targetType, editorType);
    }

    public static void logDeniedVault(ServerPlayer player) {
        SECURITY_LOG.warn("[ACCESS_DENIED] Player '{}' ({}) attempted to access vault without permission.",
            player.getGameProfile().getName(), player.getGameProfile().getId());
    }

    public static void logAbilityModification(ServerPlayer player, ServerPlayer target, String ability, boolean newValue) {
        SECURITY_LOG.warn("[ABILITY_MOD] Player '{}' ({}) modified ability '{}' on player '{}' ({}) to {}",
            player.getGameProfile().getName(), player.getGameProfile().getId(),
            ability, target.getGameProfile().getName(), target.getGameProfile().getId(), newValue);
    }

    public static void logSuspiciousAbilityModification(ServerPlayer player, ServerPlayer target, String ability) {
        SECURITY_LOG.warn("[SUSPICIOUS_ABILITY] Player '{}' ({}) attempted to set ability '{}' on player '{}' (possibly non-creative)",
            player.getGameProfile().getName(), player.getGameProfile().getId(),
            ability, target.getGameProfile().getName());
    }

    public static void logRateLimitExceeded(ServerPlayer player, String operation) {
        SECURITY_LOG.warn("[RATE_LIMIT] Player '{}' ({}) exceeded rate limit for operation: {}",
            player.getGameProfile().getName(), player.getGameProfile().getId(), operation);
    }

    public static void logSuccessfulEdit(ServerPlayer player, String targetType, String targetInfo, EditorType editorType) {
        SECURITY_LOG.info("[EDIT_SUCCESS] Player '{}' ({}) edited {}: {}. Editor type: {}",
            player.getGameProfile().getName(), player.getGameProfile().getId(), targetType, targetInfo,
            editorType != null ? editorType : "N/A");
    }

    public static void logOpSwordIssued(ServerPlayer player) {
        SECURITY_LOG.info("[OP_SWORD] Player '{}' ({}) was issued the op sword via /cad op_sword",
            player.getGameProfile().getName(), player.getGameProfile().getId());
    }
}
