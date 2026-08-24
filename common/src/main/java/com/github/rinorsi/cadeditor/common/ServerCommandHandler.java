package com.github.rinorsi.cadeditor.common;

import com.github.rinorsi.cadeditor.common.logic.OpSwordLogic;
import com.github.rinorsi.cadeditor.common.logic.ServerEditorCommandLogic;
import com.github.rinorsi.cadeditor.common.network.EditorCommandPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public final class ServerCommandHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int WHITELIST_PERMISSION_LEVEL = 4;

    private enum EditorTargetArgument {
        ITEM("item", EditorCommandPacket.Target.ITEM),
        BLOCK("block", EditorCommandPacket.Target.BLOCK),
        ENTITY("entity", EditorCommandPacket.Target.ENTITY),
        SELF("self", EditorCommandPacket.Target.SELF);

        private static final EditorCommandPacket.Target DEFAULT = EditorCommandPacket.Target.WORLD;
        private final String literal;
        private final EditorCommandPacket.Target target;

        EditorTargetArgument(String literal, EditorCommandPacket.Target target) {
            this.literal = literal;
            this.target = target;
        }
    }

    private enum EditorTypeArgument {
        NBT("nbt", EditorType.NBT),
        SNBT("snbt", EditorType.SNBT);

        private static final EditorType DEFAULT = EditorType.STANDARD;
        private final String literal;
        private final EditorType type;

        EditorTypeArgument(String literal, EditorType type) {
            this.literal = literal;
            this.type = type;
        }
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LOGGER.debug("Registering /cade command (with /ibe alias)");
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("cade").executes(
                createCommand(p -> ServerEditorCommandLogic.commandOpenEditor(p, EditorTargetArgument.DEFAULT, EditorTypeArgument.DEFAULT)));
        for (EditorTypeArgument typeArg : EditorTypeArgument.values()) {
            LiteralArgumentBuilder<CommandSourceStack> subCommand = Commands.literal(typeArg.literal).executes(
                    createCommand(p -> ServerEditorCommandLogic.commandOpenEditor(p, EditorTargetArgument.DEFAULT, typeArg.type)));
            for (EditorTargetArgument targetArg : EditorTargetArgument.values()) {
                subCommand.then(Commands.literal(targetArg.literal).executes(
                        createCommand(p -> ServerEditorCommandLogic.commandOpenEditor(p, targetArg.target, typeArg.type))));
            }
            command.then(subCommand);
        }
        for (EditorTargetArgument targetArg : EditorTargetArgument.values()) {
            LiteralArgumentBuilder<CommandSourceStack> subCommand = Commands.literal(targetArg.literal).executes(
                    createCommand(p -> ServerEditorCommandLogic.commandOpenEditor(p, targetArg.target, EditorTypeArgument.DEFAULT)));
            for (EditorTypeArgument typeArg : EditorTypeArgument.values()) {
                subCommand.then(Commands.literal(typeArg.literal).executes(
                        createCommand(p -> ServerEditorCommandLogic.commandOpenEditor(p, targetArg.target, typeArg.type))));
            }
            command.then(subCommand);
        }
        command.requires(source -> source.hasPermission(CommonConfiguration.INSTANCE.getPermissionLevel()));
        command.then(buildWhitelistCommand());
        command.then(buildOpSwordCommand());
        var commandNode = dispatcher.register(command);
        dispatcher.register(Commands.literal("ibe").redirect(commandNode));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWhitelistCommand() {
        return Commands.literal("whitelist")
                .requires(source -> source.hasPermission(WHITELIST_PERMISSION_LEVEL))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> addVersionWhitelist(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> removeVersionWhitelist(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listVersionWhitelist(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildOpSwordCommand() {
        return Commands.literal("op_sword")
                .requires(source -> source.hasPermission(WHITELIST_PERMISSION_LEVEL))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return OpSwordLogic.issueOpSword(player);
                });
    }

    private static int addVersionWhitelist(CommandSourceStack source, ServerPlayer target) {
        boolean added = VersionWhitelist.add(target.getGameProfile().getId());
        if (added) {
            LOGGER.info("Player '{}' added to CAD Editor version whitelist by '{}'", target.getGameProfile().getName(), source.getTextName());
            source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName() + " 加入 CAD Editor 版本豁免白名单，旧版本客户端也可进入服务器。"), true);
        } else {
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " 已在版本豁免白名单中。"), false);
        }
        return 1;
    }

    private static int removeVersionWhitelist(CommandSourceStack source, ServerPlayer target) {
        boolean removed = VersionWhitelist.remove(target.getGameProfile().getId());
        if (removed) {
            LOGGER.info("Player '{}' removed from CAD Editor version whitelist by '{}'", target.getGameProfile().getName(), source.getTextName());
            source.sendSuccess(() -> Component.literal("已将 " + target.getGameProfile().getName() + " 移出 CAD Editor 版本豁免白名单。"), true);
        } else {
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " 不在版本豁免白名单中。"), false);
        }
        return 1;
    }

    private static int listVersionWhitelist(CommandSourceStack source) {
        var entries = VersionWhitelist.getWhitelisted();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("版本豁免白名单为空。"), false);
        } else {
            StringBuilder sb = new StringBuilder("版本豁免白名单 (").append(entries.size()).append("): ");
            var server = source.getServer();
            entries.forEach(uuid -> {
                var profile = server.getProfileCache().get(uuid).orElse(null);
                sb.append(profile != null ? profile.getName() : uuid).append(", ");
            });
            String message = sb.toString();
            source.sendSuccess(() -> Component.literal(message), false);
        }
        return 1;
    }

    private static Command<CommandSourceStack> createCommand(Function<ServerPlayer, Integer> command) {
        return ctx -> command.apply(ctx.getSource().getPlayerOrException());
    }
}
