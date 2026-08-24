package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.CommonConfiguration;
import net.minecraft.server.level.ServerPlayer;

public class PermissionLogic {
    public static PermissionLevel getPermissionLevel(ServerPlayer player) {
        if (CommonConfiguration.INSTANCE.isDisabled()) {
            return PermissionLevel.NONE;
        }
        if (player.hasPermissions(4)) {
            return PermissionLevel.ADMIN;
        }
        if (player.isCreative()) {
            return PermissionLevel.CREATIVE;
        }
        return PermissionLevel.NONE;
    }

    public static boolean hasPermission(ServerPlayer player) {
        return getPermissionLevel(player).hasAnyAccess();
    }

    public static boolean isAdmin(ServerPlayer player) {
        return getPermissionLevel(player).isAdmin();
    }

    public static boolean isCreativeUser(ServerPlayer player) {
        return getPermissionLevel(player) == PermissionLevel.CREATIVE;
    }

    public static boolean canUseVault(ServerPlayer player) {
        return getPermissionLevel(player).canUseVault();
    }
}
