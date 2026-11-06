package xin.vanilla.aotake.internal.common;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

public final class NativePermissionAccess {
    private NativePermissionAccess() {
    }

    public static boolean has(PermissionSet permissions, int level) {
        if (level <= 0) return true;
        Permission permission = switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            case 4 -> Permissions.COMMANDS_OWNER;
            default -> null;
        };
        return permission != null && permissions != null && permissions.hasPermission(permission);
    }
}
