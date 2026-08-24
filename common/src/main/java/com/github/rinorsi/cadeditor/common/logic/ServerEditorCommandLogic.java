package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.CommonConfiguration;
import com.github.rinorsi.cadeditor.common.EditorType;
import com.github.rinorsi.cadeditor.common.ServerContext;
import com.github.rinorsi.cadeditor.common.network.EditorCommandPacket;
import com.github.rinorsi.cadeditor.common.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import static com.github.franckyi.guapi.api.GuapiHelper.text;

public class ServerEditorCommandLogic {
    private static final MutableComponent MUST_INSTALL = text("You must install CAD Editor in order to use this command.").withStyle(ChatFormatting.RED);
    private static final MutableComponent DOWNLOAD = text("Click here to download CAD Editor!").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/cad-editor"))).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
    private static final MutableComponent NO_PERMISSION = text("You must be in creative mode or OP (level 4) to use this command.").withStyle(ChatFormatting.RED);
    private static final MutableComponent ADMIN_ONLY = text("This editor requires OP (level 4) permissions.").withStyle(ChatFormatting.RED);

    public static int commandOpenEditor(ServerPlayer player, EditorCommandPacket.Target target, EditorType type) {
        if (ServerContext.isClientModded(player)) {
            if (CommonConfiguration.INSTANCE.isDisabled()) {
                player.displayClientMessage(NO_PERMISSION, false);
                return 2;
            }
            PermissionLevel level = PermissionLogic.getPermissionLevel(player);
            if (!level.hasAnyAccess()) {
                player.displayClientMessage(NO_PERMISSION, false);
                return 2;
            }
            if (type != EditorType.STANDARD && !level.canUseEditor(type)) {
                player.displayClientMessage(ADMIN_ONLY, false);
                return 2;
            }
            NetworkManager.sendToClient(player, NetworkManager.EDITOR_COMMAND, new EditorCommandPacket(target, type));
            return 0;
        }
        player.displayClientMessage(MUST_INSTALL, false);
        player.displayClientMessage(DOWNLOAD, false);
        return 1;
    }
}
