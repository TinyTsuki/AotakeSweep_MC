package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import java.lang.reflect.*;
import static org.junit.Assert.*;

public class SweepUiLifecycleTest {
    @Test public void unchangedDisplayedSecondReusesFormattedValueAcrossPresentations() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new xin.vanilla.aotake.client.minimap.SweepCountdownState(clock::get);
        state.accept(state.beginConnection(), 1000, 61000, 60000);
        String first = state.formattedTime();
        clock.set(500_000_000L);
        assertSame("Stable HUD seconds should not be formatted again", first, state.snapshot().formattedTime());
        assertSame(first, state.formattedTime());
        clock.set(1_000_000_000L); assertEquals("00:59", state.formattedTime());
        state.reset(); assertEquals("", state.formattedTime());
    }
    @Test public void detachRebindAndHiddenFramesDoNotResetClockOrRewriteStableValues() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new xin.vanilla.aotake.client.minimap.SweepCountdownState(clock::get);
        state.accept(state.beginConnection(), 1000, 61000, 60000);
        var visible = new java.util.concurrent.atomic.AtomicBoolean(true);
        var binding = new SweepProgressUiBinding(visible::get);
        var frame = new xin.vanilla.banira.api.client.ui.UiHudFrame(java.util.List.of(
                new xin.vanilla.banira.api.client.ui.UiHudFrame.Text("time", "01:00", 0, 0, 30, 9, 9, 1, 0, 0, 0, -1)));
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var type = xin.vanilla.banira.api.client.ui.UiView.class;
        var view = (xin.vanilla.banira.api.client.ui.UiView) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (p, m, a) -> { if (m.getName().equals("hudFrame")) writes.incrementAndGet(); return null; });
        assertFalse(binding.visible()); binding.prepare(frame); assertTrue(binding.visible());
        binding.bind(view); binding.update(view); binding.prepare(new xin.vanilla.banira.api.client.ui.UiHudFrame(frame.parts())); binding.update(view);
        assertEquals(1, writes.get());
        binding.detach(xin.vanilla.banira.api.client.ui.UiDetachReason.SWITCH);
        clock.set(20_000_000_000L); binding.update(view); assertEquals(1, writes.get());
        binding.bind(view); assertEquals(2, writes.get()); assertEquals("00:40", state.formattedTime());
        visible.set(false); assertFalse(binding.visible()); binding.update(view); assertEquals(2, writes.get());
        binding.clear(); visible.set(true); assertFalse(binding.visible());
        state.reset(); assertEquals("", state.formattedTime());
    }
    @Test public void authoritativeProgressClampsExtendedSchedulesAndDisabledIntervals() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new xin.vanilla.aotake.client.minimap.SweepCountdownState(clock::get);
        long g = state.beginConnection();
        state.accept(g, 1000, 121000, 60000); assertEquals(0, state.progress(), 0);
        state.accept(g, 1000, 61000, 0); assertEquals("", state.snapshot().formattedTime()); assertEquals(0, state.progress(), 0);
        state.accept(g, 1000, 61000, 60000); var snap = state.snapshot();
        assertEquals("01:00", snap.formattedTime()); assertEquals(0, snap.progress(), 0);
        state.beginConnection(); assertEquals("", state.snapshot().formattedTime());
        assertFalse(state.accept(g, 1000, 61000, 60000));
    }
    @Test public void heldToggleAndGuiUseOneEdgeStateAndReset() throws Exception {
        Class<?> type;
        try { type = Class.forName("xin.vanilla.aotake.client.ui.SweepProgressUiBinding$KeyState"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing shared progress key state", missing); }
        Object key = type.getConstructor().newInstance();
        Method tick = type.getMethod("tick", boolean.class, boolean.class, boolean.class);
        assertEquals(true, tick.invoke(key, true, false, true));
        assertEquals(true, tick.invoke(key, true, false, true));
        assertEquals(true, tick.invoke(key, false, false, true));
        assertEquals(false, tick.invoke(key, true, false, true));
        assertEquals(false, tick.invoke(key, false, true, false));
        assertEquals(false, tick.invoke(key, true, true, true));
        type.getMethod("reset").invoke(key);
        assertEquals(false, tick.invoke(key, false, false, false));
        assertEquals(true, tick.invoke(key, true, false, false));
        assertEquals(false, tick.invoke(key, false, false, false));
    }
    @Test public void progressUsesSameAuthoritativeStateWithoutExtrapolation() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new xin.vanilla.aotake.client.minimap.SweepCountdownState(clock::get);
        long generation = state.beginConnection();
        Method progress;
        try { progress = state.getClass().getMethod("progress"); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Progress must share the existing countdown state", missing); }
        state.accept(generation, 1000, 61000, 60000);
        assertEquals(0.0, (Double) progress.invoke(state), 0);
        clock.set(30_000_000_000L);
        assertEquals("00:30", state.formattedTime());
        assertEquals(.5, (Double) progress.invoke(state), .00001);
        clock.set(120_000_000_000L);
        assertEquals(1.0, (Double) progress.invoke(state), 0);
        assertEquals("00:00", state.formattedTime());
        state.reset();
        assertEquals(0.0, (Double) progress.invoke(state), 0);
    }
}
