package xin.vanilla.aotake.internal.fabric;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NativeMixinContractTest {
    private ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(name, stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    @Test
    public void everyInjectionUsesAnActualNativeMethodAndCorrectHandlerOrder() throws Exception {
        int checked = 0;
        for (String name : List.of("ChestScreenMixin", "ContainerScreenMixin", "ScreenMixin", "MinecraftMixin",
                "ServerPlayerMixin", "SmokeScreenshotFrameMixin", "ClientCaptureInteractionMixin")) {
            var mixin = read("xin/vanilla/aotake/mixin/" + name);
            var declaration = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                    .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    .findFirst().orElseThrow();
            var target = read(((Type) ((List<?>) value(declaration, "value")).getFirst()).getInternalName());
            for (var method : mixin.methods) {
                for (var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    for (Object item : (List<?>) value(annotation, "method")) {
                        String selector = (String) item;
                        var nativeMethods = target.methods.stream()
                                .filter(candidate -> selector.equals(candidate.name) || selector.equals(candidate.name + candidate.desc)).toList();
                        assertFalse(name + ":" + selector, nativeMethods.isEmpty());
                        Type[] handler = Type.getArgumentTypes(method.desc);
                        assertTrue(method.name, handler.length > 0);
                        assertTrue(method.name + " " + method.desc, nativeMethods.stream().anyMatch(candidate -> {
                            String callback = Type.getReturnType(candidate.desc).equals(Type.VOID_TYPE)
                                    ? "CallbackInfo" : "CallbackInfoReturnable";
                            if (!handler[handler.length - 1].getInternalName().equals(
                                    "org/spongepowered/asm/mixin/injection/callback/" + callback)) return false;
                            Type[] arguments = Type.getArgumentTypes(candidate.desc);
                            if (handler.length == 1) return true;
                            if (handler.length != arguments.length + 1) return false;
                            for (int i = 0; i < arguments.length; i++)
                                if (!arguments[i].equals(handler[i])) return false;
                            return true;
                        }));
                        checked++;
                    }
                }
            }
        }
        assertEquals(10, checked);
    }

    @Test
    public void clientCaptureLeavesTheEntityPacketBeforeTheConsumedResult() throws Exception {
        var gameMode = read("net/minecraft/client/multiplayer/MultiPlayerGameMode");
        var interact = gameMode.methods.stream().filter(method -> method.name.equals("interact")).findFirst().orElseThrow();
        var calls = calls(interact);
        int packet = -1;
        int prediction = -1;
        for (int i = 0; i < calls.size(); i++) {
            var call = calls.get(i);
            if (call.owner.equals("net/minecraft/client/multiplayer/ClientPacketListener") && call.name.equals("send"))
                packet = i;
            if (call.name.equals("interactOn")) prediction = i;
        }
        assertTrue(packet >= 0 && packet < prediction);

        var mixin = read("xin/vanilla/aotake/mixin/ClientCaptureInteractionMixin");
        var handler = mixin.methods.stream().filter(method -> method.name.equals("aotake$consumeLocalCapture")).findFirst().orElseThrow();
        var injection = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, value(injection, "cancellable"));
        assertEquals("RETURN", value((AnnotationNode) ((List<?>) value(injection, "at")).getFirst(), "value"));
    }

    @Test
    public void containerAccessorsStillReadTheRealMenuGeometryFields() throws Exception {
        var nativeScreen = read("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");
        var accessors = read("xin/vanilla/aotake/mixin/ContainerScreenAccessor");
        int checked = 0;
        for (var method : accessors.methods) {
            for (var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")) continue;
                String field = (String) value(annotation, "value");
                assertTrue(field, nativeScreen.fields.stream().anyMatch(candidate -> candidate.name.equals(field)
                        && candidate.desc.equals(Type.getReturnType(method.desc).getDescriptor())));
                checked++;
            }
        }
        assertEquals(4, checked);
    }

    @Test
    public void cursorRestoreStillFollowsTheActualNativeMouseReleaseCall() throws Exception {
        var minecraft = read("net/minecraft/client/Minecraft");
        assertTrue(minecraft.methods.stream().filter(method -> method.name.equals("setScreen"))
                .anyMatch(method -> calls(method).stream().anyMatch(call -> call.owner.equals("net/minecraft/client/MouseHandler")
                        && call.name.equals("releaseMouse") && call.desc.equals("()V"))));
    }

    @Test
    public void ordinaryItemUseKeepsThePassPathAndTheNativeStackIdentity() throws Exception {
        var proxy = read("xin/vanilla/aotake/event/EventHandlerProxy");
        var use = proxy.methods.stream().filter(method -> method.name.equals("onPlayerUseItem")).findFirst().orElseThrow();
        assertTrue(use.desc.endsWith(")Lnet/minecraft/world/InteractionResult;"));
        assertTrue(calls(use).stream().anyMatch(call -> call.name.equals("getItemInHand")));
        assertTrue(calls(use).stream().noneMatch(call -> call.name.equals("setItemInHand") || call.name.equals("copy")));
        assertTrue(java.util.stream.StreamSupport.stream(use.instructions.spliterator(), false)
                .filter(node -> node instanceof org.objectweb.asm.tree.FieldInsnNode)
                .map(node -> (org.objectweb.asm.tree.FieldInsnNode) node)
                .anyMatch(field -> field.owner.equals("net/minecraft/world/InteractionResult") && field.name.equals("PASS")));
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError(key);
    }
}
