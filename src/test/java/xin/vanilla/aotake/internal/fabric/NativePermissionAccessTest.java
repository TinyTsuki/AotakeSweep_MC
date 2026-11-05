package xin.vanilla.aotake.internal.fabric;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.junit.Test;

import static org.junit.Assert.*;

public class NativePermissionAccessTest {
    @Test public void levelsKeepTheFullCommandPermissionLadder() {
        for (int granted = 0; granted <= 4; granted++) {
            var permissions = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(granted));
            for (int needed = 0; needed <= 4; needed++) {
                assertEquals(granted + ":" + needed, granted >= needed, NativePermissionAccess.has(permissions, needed));
            }
            assertFalse(NativePermissionAccess.has(permissions, 5));
        }
    }

    @Test public void missingPermissionsCannotGrantPrivilegedCommands() {
        assertTrue(NativePermissionAccess.has(null, 0));
        for (int level = 1; level <= 5; level++) assertFalse(NativePermissionAccess.has(null, level));
    }
}
