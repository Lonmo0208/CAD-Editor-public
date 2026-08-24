package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.CommonUtil;
import com.github.rinorsi.cadeditor.common.ModTexts;
import com.github.rinorsi.cadeditor.common.network.GiveVaultItemPacket;
import net.minecraft.server.level.ServerPlayer;

public class ServerVaultActionLogic {
    public static void onGiveVaultItem(ServerPlayer player, GiveVaultItemPacket response) {
        if (!RateLimiter.allowUpdate(player)) {
            CommonUtil.showMessage(player, net.minecraft.network.chat.Component.literal("CAD Editor: Rate limit exceeded. Please wait before trying again."));
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        if (!level.canUseVault()) {
            SecurityAuditLog.logDeniedVault(player);
            CommonUtil.showPermissionError(player, ModTexts.VAULT);
            return;
        }
        int slot = response.slot();
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            CommonUtil.showMessage(player, ModTexts.Messages.ERROR_GENERIC);
            return;
        }

        player.getInventory().setItem(slot, response.itemStack());
        SecurityAuditLog.logSuccessfulEdit(player, "vault_item", "slot=" + slot + ", item=" + response.itemStack().getItem().getDescriptionId(), null);
        CommonUtil.showVaultItemGiveSuccess(player);
    }
}
