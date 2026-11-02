package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

import static org.junit.Assert.*;

public class UiDevelopmentPackagingTest {
    private boolean guarded(MethodNode method, int callIndex) {
        for (int i = 0; i < callIndex - 1; i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (!field.owner.equals("net/minecraftforge/fml/loading/FMLEnvironment")
                    || !field.name.equals("production") || !field.desc.equals("Z")) continue;
            AbstractInsnNode next = instruction.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (!(next instanceof JumpInsnNode) || next.getOpcode() != Opcodes.IFNE) continue;
            if (method.instructions.indexOf(((JumpInsnNode) next).label) > callIndex) return true;
        }
        return false;
    }

    @Test
    public void releaseEntryPointsSkipEveryExcludedDevelopmentCall() throws Exception {
        int calls = 0;
        for (String name : new String[]{"AotakeSweep", "client/AotakeClientBootstrap", "event/ClientGameEventHandler"}) {
            ClassNode node = new ClassNode();
            try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + name + ".class")) {
                assertNotNull(input);
                new ClassReader(input).accept(node, 0);
            }
            for (MethodNode method : node.methods)
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode instruction = method.instructions.get(i);
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    String owner = ((MethodInsnNode) instruction).owner;
                    if (!owner.startsWith("xin/vanilla/aotake/internal/dev/")
                            && !owner.startsWith("xin/vanilla/aotake/internal/client/dev/")
                            && !owner.startsWith("xin/vanilla/aotake/internal/server/dev/")) continue;
                    assertTrue(name + "." + method.name + " calls " + owner, guarded(method, i));
                    calls++;
                }
        }
        assertEquals(9, calls);
    }

    @Test
    public void guardCheckRejectsAnUnconditionalCall() {
        MethodNode method = new MethodNode();
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, "example/Runner", "tick", "()V", false);
        method.instructions.add(call);
        assertFalse(guarded(method, 0));
        LabelNode end = new LabelNode();
        method.instructions.insertBefore(call, new FieldInsnNode(Opcodes.GETSTATIC,
                "net/minecraftforge/fml/loading/FMLEnvironment", "production", "Z"));
        method.instructions.insertBefore(call, new JumpInsnNode(Opcodes.IFNE, end));
        method.instructions.add(end);
        assertTrue(guarded(method, method.instructions.indexOf(call)));
    }
}
