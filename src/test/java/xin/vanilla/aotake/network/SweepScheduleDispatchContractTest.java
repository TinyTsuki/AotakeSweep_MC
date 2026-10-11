package xin.vanilla.aotake.network;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import static org.junit.Assert.*;

public class SweepScheduleDispatchContractTest {
    private ClassNode node(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + path + ".class")) {
            assertNotNull(input);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }

    @Test
    public void disableSetterDispatchesAfterActualStateChange() throws Exception {
        MethodNode setter = node("AotakeSweep").methods.stream().filter(m -> m.name.equals("setDisable")).findFirst().get();
        int write = -1, send = -1;
        boolean noOpBranch = false;
        for (AbstractInsnNode i : setter.instructions) {
            if (i.getOpcode() == Opcodes.IF_ICMPNE) noOpBranch = true;
            if (i instanceof FieldInsnNode && i.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) i).name.equals("disable"))
                write = setter.instructions.indexOf(i);
            if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.endsWith("/SweepScheduleSync")
                    && ((MethodInsnNode) i).name.equals("broadcast")) send = setter.instructions.indexOf(i);
        }
        assertTrue(noOpBranch && write >= 0 && send > write);
    }

    @Test
    public void intervalSaveCallbackContainsTargetedScheduleDispatch() throws Exception {
        ClassNode root = node("AotakeSweep");
        boolean path = false, dispatch = false;
        for (MethodNode method : root.methods) if (method.name.startsWith("lambda$")) {
            int anyMatch = -1;
            for (AbstractInsnNode i : method.instructions) {
                if (i instanceof LdcInsnNode && "base.sweep.sweepInterval".equals(((LdcInsnNode) i).cst)) path = true;
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("anyMatch"))
                    anyMatch = method.instructions.indexOf(i);
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.endsWith("/SweepScheduleSync")
                        && ((MethodInsnNode) i).name.equals("broadcast")) {
                    int send = method.instructions.indexOf(i);
                    for (AbstractInsnNode branch : method.instructions) {
                        if (branch instanceof JumpInsnNode && branch.getOpcode() == Opcodes.IFEQ
                                && method.instructions.indexOf(branch) > anyMatch && method.instructions.indexOf(branch) < send
                                && method.instructions.indexOf(((JumpInsnNode) branch).label) > send) dispatch |= anyMatch >= 0;
                    }
                }
            }
        }
        assertTrue(path && dispatch);
    }

    @Test
    public void broadcastIsScheduledAndFiltersClientsWithInstalledMod() throws Exception {
        ClassNode sync = node("network/SweepScheduleSync");
        boolean scheduled = false, filtered = false, sent = false;
        for (MethodNode method : sync.methods) for (AbstractInsnNode i : method.instructions) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) i;
            scheduled |= call.name.equals("execute");
            filtered |= call.name.equals("isRemoteClientModInstalled");
            sent |= call.name.equals("sendPacketToPlayer");
        }
        assertTrue(scheduled && filtered && sent);
    }

    @Test
    public void serverSidePacketReturnsBeforeEnteringClientReceiver() throws Exception {
        MethodNode handle = node("network/packet/SweepDataSyncToClient").methods.stream()
                .filter(m -> m.name.equals("handle")).findFirst().get();
        int side = -1, receiver = -1;
        boolean returnsEarly = false;
        for (AbstractInsnNode i : handle.instructions) {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) i;
                if (call.name.equals("isClientSide")) side = handle.instructions.indexOf(i);
                if (call.owner.endsWith("/AotakeMinimapCountdown") && call.name.equals("accept")) receiver = handle.instructions.indexOf(i);
            }
            if (side >= 0 && receiver < 0 && i.getOpcode() == Opcodes.RETURN) returnsEarly = true;
        }
        assertTrue(side >= 0 && receiver > side && returnsEarly);
    }
}
