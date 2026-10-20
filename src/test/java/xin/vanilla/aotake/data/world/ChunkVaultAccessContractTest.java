package xin.vanilla.aotake.data.world;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class ChunkVaultAccessContractTest {
    private static final String ROOT = "xin/vanilla/aotake/";

    private MethodNode method(String owner, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + owner + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + name));
    }

    private boolean calls(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(ROOT + owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }

    private void guarded(String name) throws Exception {
        MethodNode method = method("data/world/ChunkVaultSession", name);
        boolean checked = false, rejected = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(ROOT + "data/world/ChunkVaultGrants") && call.name.equals("canOpen")) checked = true;
                if (call.owner.equals(ROOT + "data/world/ChunkVaultStorage") && call.name.equals("readAllItems")
                        || call.name.equals("openMenu") || call.name.equals("m_5893_")) {
                    assertTrue("Access must be checked before reading/opening in " + name, checked && rejected);
                }
            }
            if (checked && instruction.getOpcode() == Opcodes.RETURN) rejected = true;
        }
        assertTrue("Missing authority check in " + name, checked);
        assertTrue("Denied access must return in " + name, rejected);
    }

    @Test public void openingRequiresCurrentServerAuthority() throws Exception { guarded("open"); }
    @Test public void navigationAndRefreshRequireCurrentServerAuthority() throws Exception { guarded("navigateOrReload"); }
    @Test public void scheduledReopenRequiresCurrentServerAuthority() throws Exception { guarded("reopenAfterPageChange"); }

    @Test public void revocationInvalidatesOnlyTheAffectedSession() throws Exception {
        assertTrue("Revocation must invalidate live access", calls(method("data/world/ChunkVaultGrants", "revoke"),
                "data/world/ChunkVaultSession", "checkAccess"));
        MethodNode check = method("data/world/ChunkVaultSession", "checkAccess");
        assertTrue(calls(check, "data/world/ChunkVaultGrants", "canOpen"));
    }

    @Test public void serverTicksRecheckOpenSessions() throws Exception {
        assertTrue("Permission loss must not leave a live container", calls(method("event/EventHandlerProxy", "onServerTick"),
                "data/world/ChunkVaultSession", "checkAccess"));
    }

    @Test public void grantsKeepTheirExistingPersistence() throws Exception {
        assertTrue(calls(method("data/world/ChunkVaultGrants", "grant"), "data/world/ChunkVaultGrants", "save"));
    }

    @Test public void revocationsKeepTheirExistingPersistence() throws Exception {
        assertTrue(calls(method("data/world/ChunkVaultGrants", "revoke"), "data/world/ChunkVaultGrants", "save"));
    }

    @Test public void disabledCleanupCannotSkipLiveAuthorityChecks() throws Exception {
        boolean checked = false, disabledGate = false;
        for (AbstractInsnNode instruction : method("event/EventHandlerProxy", "onServerTick").instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(ROOT + "data/world/ChunkVaultSession") && call.name.equals("checkAccess")) checked = true;
            if (call.owner.equals(ROOT + "AotakeSweep") && call.name.equals("isDisable")) {
                disabledGate = true;
                assertTrue("Disabled cleanup must not skip live vault authority", checked);
            }
        }
        assertTrue(disabledGate && checked);
    }
}
