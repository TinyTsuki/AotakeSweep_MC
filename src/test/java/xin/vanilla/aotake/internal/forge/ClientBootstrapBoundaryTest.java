package xin.vanilla.aotake.internal.forge;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.*;

public class ClientBootstrapBoundaryTest {
    private static final String ENTRY = "xin/vanilla/aotake/AotakeSweep";
    private static final String CLIENT = "xin/vanilla/aotake/client/AotakeClientBootstrap";

    @Test
    public void constructorUsesSideGateWithoutDevelopmentReferentValidation() throws Exception {
        MethodNode constructor = method(entry(), "<init>", "()V");
        int gates = 0;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            assertNotEquals("Client bootstrap must not run before the side gate", CLIENT, call.owner);
            if (!"net/minecraftforge/fml/DistExecutor".equals(call.owner)) continue;
            assertEquals("Development referent validation resolves client classes on a server",
                    "unsafeRunWhenOn", call.name);
            assertEquals("(Lnet/minecraftforge/api/distmarker/Dist;Ljava/util/function/Supplier;)V", call.desc);
            gates++;
        }
        assertEquals("Expected one deferred client side gate", 1, gates);
    }

    @Test
    public void clientMethodReferenceIsCreatedInsideTheDeferredSupplier() throws Exception {
        ClassNode entry = entry();
        MethodNode constructor = method(entry, "<init>", "()V");
        Handle factory = null;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (!(instruction instanceof InvokeDynamicInsnNode)) continue;
            InvokeDynamicInsnNode lambda = (InvokeDynamicInsnNode) instruction;
            for (Object argument : lambda.bsmArgs) {
                if (argument instanceof Handle) assertNotEquals(CLIENT, ((Handle) argument).getOwner());
            }
            if (!"()Ljava/util/function/Supplier;".equals(lambda.desc)) continue;
            assertNull("Expected a single client supplier", factory);
            factory = (Handle) lambda.bsmArgs[1];
        }
        assertNotNull("Client initializer must stay deferred", factory);
        assertEquals(ENTRY, factory.getOwner());
        MethodNode supplier = method(entry, factory.getName(), factory.getDesc());
        int references = 0;
        for (AbstractInsnNode instruction : supplier.instructions) {
            if (!(instruction instanceof InvokeDynamicInsnNode)) continue;
            for (Object argument : ((InvokeDynamicInsnNode) instruction).bsmArgs) {
                if (!(argument instanceof Handle)) continue;
                Handle target = (Handle) argument;
                if (CLIENT.equals(target.getOwner()) && "init".equals(target.getName())) references++;
            }
        }
        assertEquals("Client initializer reference belongs inside the side-gated supplier", 1, references);
    }

    private static ClassNode entry() throws Exception {
        try (InputStream stream = ClientBootstrapBoundaryTest.class.getClassLoader()
                .getResourceAsStream(ENTRY + ".class")) {
            assertNotNull(stream);
            ClassNode result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new AssertionError("Missing method " + name + descriptor);
    }
}
