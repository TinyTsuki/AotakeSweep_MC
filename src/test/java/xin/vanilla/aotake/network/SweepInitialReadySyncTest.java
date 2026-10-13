package xin.vanilla.aotake.network;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import static org.junit.Assert.*;

public class SweepInitialReadySyncTest {
    @Test
    public void initialRuleCompletionRefreshesPendingScheduleWithoutPolling() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/aotake/internal/server/AotakeRuleRuntime.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        boolean refreshed = false;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals("xin/vanilla/aotake/network/SweepScheduleSync") && call.name.equals("broadcast")) {
                assertTrue("Schedule refresh must belong to initial-load completion", method.name.startsWith("lambda$start$"));
                refreshed = true;
            }
        }
        assertTrue("A pending deadline must become visible when initial rules finish loading", refreshed);
    }
}
