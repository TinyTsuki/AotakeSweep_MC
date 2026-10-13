package xin.vanilla.aotake.network;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import static org.junit.Assert.*;

public class DelayScheduleSyncTest {
    @Test
    public void changedDeadlineIsBroadcastToInstalledClientsIncludingPreviouslySyncedPlayers() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/aotake/command/impl/DelayCommand.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        boolean broadcasts = false;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            assertFalse("Already synchronized players still need the changed deadline", call.name.equals("isPlayerDataSynced"));
            broadcasts |= call.owner.equals("xin/vanilla/aotake/network/SweepScheduleSync") && call.name.equals("broadcast");
        }
        assertTrue("Delay must use the installed-client schedule broadcaster", broadcasts);
    }
}
