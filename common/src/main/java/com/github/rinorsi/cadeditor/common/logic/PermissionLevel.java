package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.EditorType;

public enum PermissionLevel {
    NONE,
    CREATIVE,
    ADMIN;

    public boolean canUseEditor(EditorType editorType) {
        return switch (editorType) {
            case STANDARD -> this == CREATIVE || this == ADMIN;
            case NBT, SNBT -> this == ADMIN;
        };
    }

    public boolean canUseEntityEditor() {
        return this == ADMIN;
    }

    public boolean canUseBlockEditor() {
        return this == ADMIN;
    }

    public boolean canUseItemEditor() {
        return this == CREATIVE || this == ADMIN;
    }

    public boolean canUseVault() {
        return this == ADMIN;
    }

    public boolean canModifyPlayerAbilities() {
        return this == ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean hasAnyAccess() {
        return this != NONE;
    }
}
