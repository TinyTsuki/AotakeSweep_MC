package xin.vanilla.aotake.client;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.*;

public class ProgressKeyDefaultTest {
    @Test
    public void progressRegistrationKeepsTabDefault() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/aotake/event/ClientModEventHandler.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        boolean found = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<clinit>")) continue;
            boolean progress = false;
            Integer key = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode && "progress".equals(((LdcInsnNode) instruction).cst)) {
                    progress = true;
                    key = null;
                } else if (progress && instruction instanceof IntInsnNode) {
                    key = ((IntInsnNode) instruction).operand;
                } else if (progress && instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.name.equals("registerKey")) {
                        assertEquals("Progress must default to TAB", Integer.valueOf(258), key);
                        found = true;
                        progress = false;
                    }
                }
            }
        }
        assertTrue("Progress key registration missing", found);
    }
}
