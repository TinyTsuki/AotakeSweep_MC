package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import xin.vanilla.aotake.client.minimap.SweepCountdownConnection;
import xin.vanilla.aotake.client.minimap.SweepCountdownState;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.common.network.BaniraNetworkContext;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FabricProgressHudTest {
    private UiHudFrame frame(String value) {
        return new UiHudFrame(com.google.common.collect.ImmutableList.of(new UiHudFrame.Text("cleanup-time", value, 10, 20, 30, 9, 9,
                1, 0, 0, 0, 0xffffffff)));
    }

    private UiView view(List<UiHudFrame> applied) {
        return (UiView) Proxy.newProxyInstance(UiView.class.getClassLoader(), new Class<?>[]{UiView.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hudFrame")) applied.add((UiHudFrame) args[0]);
                    return method.getReturnType() == boolean.class ? false : null;
                });
    }

    @Test
    public void staticFrameIsAppliedOnceAndChangedTimeUpdatesOnce() {
        lombok.var binding = new SweepProgressUiBinding();
        lombok.var applied = new ArrayList<UiHudFrame>();
        UiView view = view(applied);
        binding.prepare(frame("01:00"));
        binding.bind(view);
        binding.update(view);
        binding.prepare(frame("01:00"));
        binding.update(view);
        assertEquals(1, applied.size());
        binding.prepare(frame("00:59"));
        binding.update(view);
        assertEquals(com.google.common.collect.ImmutableList.of(frame("01:00"), frame("00:59")), applied);
    }

    @Test
    public void reloadRebindsCurrentFrameAndRejectsOldView() {
        lombok.var binding = new SweepProgressUiBinding();
        lombok.var before = new ArrayList<UiHudFrame>();
        lombok.var after = new ArrayList<UiHudFrame>();
        UiView old = view(before), replacement = view(after);
        binding.prepare(frame("00:10"));
        binding.bind(old);
        binding.detach(UiDetachReason.RELOAD);
        binding.bind(replacement);
        binding.prepare(frame("00:09"));
        binding.update(old);
        binding.update(replacement);
        assertEquals(com.google.common.collect.ImmutableList.of(frame("00:10")), before);
        assertEquals(com.google.common.collect.ImmutableList.of(frame("00:10"), frame("00:09")), after);
    }

    @Test
    public void contextEmptySelectionAndDisconnectHideWithoutLosingRegistration() {
        AtomicBoolean visible = new AtomicBoolean(true);
        lombok.var binding = new SweepProgressUiBinding(visible::get);
        assertFalse(binding.visible());
        binding.prepare(frame("00:10"));
        assertTrue(binding.visible());
        visible.set(false);
        assertFalse(binding.visible());
        visible.set(true);
        binding.prepare(new UiHudFrame(com.google.common.collect.ImmutableList.of()));
        assertFalse(binding.visible());
        binding.prepare(frame("00:09"));
        binding.clear();
        assertFalse(binding.visible());
        assertTrue(binding.spec().manual());
        assertFalse(binding.spec().shared());
        assertEquals("aotake_sweep/progress.html", binding.spec().template());
    }

    @Test
    public void holdAndToggleAreEdgeTriggeredAndResetBetweenConnections() {
        lombok.var key = new SweepProgressUiBinding.KeyState();
        assertTrue(key.tick(true, false, false));
        assertFalse(key.tick(false, false, false));
        assertTrue(key.tick(true, false, true));
        for (int tick = 0; tick < 100; tick++) assertTrue(key.tick(true, false, true));
        assertTrue(key.tick(false, false, true));
        assertFalse(key.tick(true, false, true));
        key.reset();
        assertFalse(key.tick(false, false, true));
        assertTrue(key.tick(true, false, true));
        assertFalse(key.tick(false, false, false));
    }

    @Test
    public void keysPressedInMenusDoNotToggleOnReturnToGame() {
        lombok.var key = new SweepProgressUiBinding.KeyState();
        assertFalse(key.tick(true, true, true));
        assertFalse(key.tick(true, false, true));
        assertFalse(key.tick(false, false, true));
        assertTrue(key.tick(true, false, true));
        assertFalse(key.tick(false, true, true));
        assertTrue(key.tick(false, false, true));
    }

    @Test
    public void countdownFrameHasOneClockReadAndClampedProgress() {
        AtomicLong time = new AtomicLong();
        AtomicLong reads = new AtomicLong();
        lombok.var state = new SweepCountdownState(() -> { reads.incrementAndGet(); return time.get(); });
        long generation = state.beginConnection();
        assertEquals(new SweepCountdownState.Snapshot("", 0), state.snapshot());
        assertTrue(state.accept(generation, 1000, 61000, 60000));
        time.set(30_000_000_000L);
        reads.set(0);
        assertEquals(new SweepCountdownState.Snapshot("00:30", .5), state.snapshot());
        assertEquals(1, reads.get());
        time.set(90_000_000_000L);
        assertEquals(new SweepCountdownState.Snapshot("00:00", 1), state.snapshot());
        assertTrue(state.accept(generation, 1000, 121000, 60000));
        assertEquals(new SweepCountdownState.Snapshot("02:00", 0), state.snapshot());
        assertTrue(state.accept(generation, 1000, 0, 60000));
        assertEquals(new SweepCountdownState.Snapshot("", 0), state.snapshot());
    }

    @Test
    public void connectionIdentityCannotLeakThePreviousServersProgress() {
        AtomicReference<Object> current = new AtomicReference<>(new Object());
        lombok.var receiver = new SweepCountdownConnection(() -> 0L, current::get);
        BaniraNetworkContext context = new BaniraNetworkContext() {
            private final Object key = current.get();
            public Object connectionKey() { return key; }
            public void enqueueWork(Runnable work) { work.run(); }
            public void markHandled() { }
            public boolean isClientSide() { return true; }
            public boolean isServerSide() { return false; }
            public Object sender() { return null; }
        };
        receiver.accept(context, 1000, 31000, 60000, () -> { });
        assertEquals(new SweepCountdownState.Snapshot("00:30", .5), receiver.snapshot());
        current.set(new Object());
        assertEquals(new SweepCountdownState.Snapshot("", 0), receiver.snapshot());
        receiver.beginConnection();
        receiver.accept(context, 1000, 61000, 60000, () -> fail("Stale packet accepted"));
        assertEquals(new SweepCountdownState.Snapshot("", 0), receiver.snapshot());
        receiver.reset();
        assertEquals(new SweepCountdownState.Snapshot("", 0), receiver.snapshot());
    }

    @Test
    public void degreeConfigurationConvertsExactlyOnceForBothBackends() {
        assertEquals(Math.PI / 2, SweepProgressUiBinding.textAngleRadians(90), 0);
        assertEquals(-Math.PI / 2, SweepProgressUiBinding.textAngleRadians(-90), 0);
        assertEquals(0, SweepProgressUiBinding.textAngleRadians(-360), 0);
        assertEquals(0, SweepProgressUiBinding.textAngleRadians(.00001), 0);
    }

    @Test
    public void fabricHudBridgeKeepsOneRenderAndNoForgeOrSystemInputCalls() throws Exception {
        lombok.var bootstrap = node("client/AotakeClientBootstrap");
        assertEquals(1, calls(bootstrap, "screen/ProgressRender", "register"));
        lombok.var events = node("event/ClientGameEventHandler");
        assertEquals(1, calls(events, "screen/ProgressRender", "render"));
        assertEquals(1, calls(events, "screen/ProgressRender", "reset"));
        lombok.var render = node("screen/ProgressRender");
        assertEquals(2, calls(render, "client/minimap/AotakeMinimapCountdown", "snapshot"));
        lombok.var draw = render.methods.stream().filter(method -> method.name.equals("render")).findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, java.util.Arrays.stream(draw.instructions.toArray())
                .filter(instruction -> instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith("client/minimap/AotakeMinimapCountdown")
                        && ((MethodInsnNode) instruction).name.equals("snapshot")).count());
        assertEquals(1, calls(render, "api/client/ui/BaniraUi", "renderHud"));
        for (lombok.var method : render.methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode) {
                    assertFalse(((MethodInsnNode) instruction).owner, ((MethodInsnNode) instruction).owner.startsWith("net/minecraftforge/")
                            || ((MethodInsnNode) instruction).owner.startsWith("com/sighs/") || ((MethodInsnNode) instruction).owner.startsWith("org/lwjgl/")
                            || ((MethodInsnNode) instruction).owner.startsWith("java/awt/") || ((MethodInsnNode) instruction).owner.contains("PacketUtils"));
                    assertNotEquals("getSweepTime", ((MethodInsnNode) instruction).name);
                    assertNotEquals("getClientServerTime", ((MethodInsnNode) instruction).name);
                }
    }

    private ClassNode node(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (lombok.var stream = getClass().getResourceAsStream("/xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(name, stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private long calls(ClassNode node, String owner, String name) {
        return node.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith(owner) && ((MethodInsnNode) instruction).name.equals(name)).count();
    }
}
