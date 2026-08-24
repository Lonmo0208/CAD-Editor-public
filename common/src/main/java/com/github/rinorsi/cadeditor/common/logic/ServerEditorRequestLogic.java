package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.CommonUtil;
import com.github.rinorsi.cadeditor.common.ModTexts;
import com.github.rinorsi.cadeditor.common.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerEditorRequestLogic {
    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean checkRequestRateLimit(ServerPlayer player) {
        if (!RateLimiter.allowRequest(player)) {
            CommonUtil.showMessage(player, net.minecraft.network.chat.Component.literal("CAD Editor: Request rate limit exceeded. Please wait before trying again."));
            return false;
        }
        return true;
    }

    private static boolean isBlockInRange(ServerPlayer player, BlockPos pos, double maxDistance) {
        return player.blockPosition().distSqr(pos) <= maxDistance * maxDistance;
    }

    private static boolean isEntityInRange(ServerPlayer player, Entity entity, double maxDistance) {
        return player.distanceToSqr(entity) <= maxDistance * maxDistance;
    }

    private static boolean isAdminOrAllowedEntity(ServerPlayer player, PermissionLevel level, Entity target) {
        return level.isAdmin() || !(target instanceof ServerPlayer);
    }

    public static void onMainHandItemEditorRequest(ServerPlayer player, MainHandItemEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseItemEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "main_hand_item", request.getEditorType());
        }
        NetworkManager.sendToClient(player, NetworkManager.MAIN_HAND_ITEM_EDITOR_RESPONSE,
            new MainHandItemEditorPacket.Response(request, canAccess, canAccess ? player.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY));
    }

    public static void onPlayerInventoryItemEditorRequest(ServerPlayer player, PlayerInventoryItemEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseItemEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "player_inventory_item", request.getEditorType());
        }
        NetworkManager.sendToClient(player, NetworkManager.PLAYER_INVENTORY_ITEM_EDITOR_RESPONSE,
            new PlayerInventoryItemEditorPacket.Response(request, canAccess, canAccess ? player.getInventory().getItem(request.getSlot()) : net.minecraft.world.item.ItemStack.EMPTY));
    }

    public static void onBlockInventoryItemEditorRequest(ServerPlayer player, BlockInventoryItemEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseItemEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "block_inventory_item", request.getEditorType());
            NetworkManager.sendToClient(player, NetworkManager.BLOCK_INVENTORY_ITEM_EDITOR_RESPONSE,
                new BlockInventoryItemEditorPacket.Response(request, false, net.minecraft.world.item.ItemStack.EMPTY));
            return;
        }
        if (player.level().getBlockEntity(request.getBlockPos()) instanceof Container container && isBlockInRange(player, request.getBlockPos(), 6.0D)) {
            int slot = request.getSlot();
            if (slot >= 0 && slot < container.getContainerSize()) {
                NetworkManager.sendToClient(player, NetworkManager.BLOCK_INVENTORY_ITEM_EDITOR_RESPONSE,
                    new BlockInventoryItemEditorPacket.Response(request, true, container.getItem(slot)));
                return;
            }
        }
        CommonUtil.showTargetError(player, ModTexts.ITEM);
    }

    public static void onEntityInventoryItemEditorRequest(ServerPlayer player, EntityInventoryItemEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseItemEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "entity_inventory_item", request.getEditorType());
            NetworkManager.sendToClient(player, NetworkManager.ENTITY_INVENTORY_ITEM_EDITOR_RESPONSE,
                new EntityInventoryItemEditorPacket.Response(request, false, net.minecraft.world.item.ItemStack.EMPTY));
            return;
        }
        Entity target = player.level().getEntity(request.getEntityId());
        if (target instanceof Container container && isEntityInRange(player, target, 6.0D) && isAdminOrAllowedEntity(player, level, target)) {
            int slot = request.getSlot();
            if (slot >= 0 && slot < container.getContainerSize()) {
                NetworkManager.sendToClient(player, NetworkManager.ENTITY_INVENTORY_ITEM_EDITOR_RESPONSE,
                    new EntityInventoryItemEditorPacket.Response(request, true, container.getItem(slot)));
                return;
            }
        }
        CommonUtil.showTargetError(player, ModTexts.ITEM);
    }

    public static void onBlockEditorRequest(ServerPlayer player, BlockEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseBlockEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "block", request.getEditorType());
        }
        var level2 = player.level();
        var blockState = level2.getBlockState(request.getBlockPos());
        CompoundTag tag = null;
        if (canAccess && isBlockInRange(player, request.getBlockPos(), 128.0D)) {
            var blockEntity = level2.getBlockEntity(request.getBlockPos());
            if (blockEntity != null) {
                tag = blockEntity.saveWithId(player.registryAccess());
            }
        } else if (canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "block", request.getEditorType());
        }
        NetworkManager.sendToClient(player, NetworkManager.BLOCK_EDITOR_RESPONSE,
            new BlockEditorPacket.Response(request, canAccess, blockState, tag));
    }

    public static void onEntityEditorRequest(ServerPlayer player, EntityEditorPacket.Request request) {
        if (!checkRequestRateLimit(player)) {
            return;
        }
        PermissionLevel level = PermissionLogic.getPermissionLevel(player);
        boolean canAccess = level.canUseEntityEditor();
        if (!canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "entity", request.getEditorType());
        }
        var entity = player.level().getEntity(request.getEntityId());
        CompoundTag tag = null;
        if (canAccess && entity != null && isEntityInRange(player, entity, 128.0D)) {
            tag = new CompoundTag();
            if (!entity.save(tag)) {
                entity.saveWithoutId(tag);
            }
        } else if (canAccess) {
            SecurityAuditLog.logDeniedRequest(player, "entity", request.getEditorType());
        }
        NetworkManager.sendToClient(player, NetworkManager.ENTITY_EDITOR_RESPONSE,
            new EntityEditorPacket.Response(request, canAccess, tag));
    }
}
