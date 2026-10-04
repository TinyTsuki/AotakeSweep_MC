package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.sweep.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class SweepExecutionPlanTest {
    private static class Empty extends DefaultSweepPolicy<Void> {}
    private static class Finish extends DefaultSweepPolicy<Void> {
        public void onFinish(SweepContext c, Void s, SweepOutcome o) {}
    }
    private static class Descendant extends Finish {}
    private static class State extends DefaultSweepPolicy<String> {
        public String createState(SweepContext c) { return "state"; }
    }
    @Test public void defaultAndEmptySubclassHaveNoCustomHooks() {
        for (Class<? extends SweepPolicy> type : Arrays.asList(DefaultSweepPolicy.class, Empty.class))
            for (SweepExecutionPlan.Hook hook : SweepExecutionPlan.Hook.values())
                assertFalse(type + " " + hook, SweepExecutionPlan.resolve(type).custom(hook));
    }
    @Test public void inheritedOverrideAndGenericBridgeRemainCustom() {
        SweepExecutionPlan p = SweepExecutionPlan.resolve(Descendant.class);
        for (SweepExecutionPlan.Hook hook : SweepExecutionPlan.Hook.values())
            assertEquals(hook.toString(), hook == SweepExecutionPlan.Hook.FINISH, p.custom(hook));
        assertTrue(SweepExecutionPlan.resolve(State.class).custom(SweepExecutionPlan.Hook.STATE));
    }
    @Test public void unknownInterfaceImplementationIsAlwaysCustom() {
        Class<? extends SweepPolicy> proxy = Proxy.getProxyClass(getClass().getClassLoader(), SweepPolicy.class).asSubclass(SweepPolicy.class);
        for (SweepExecutionPlan.Hook hook : SweepExecutionPlan.Hook.values())
            assertTrue(SweepExecutionPlan.resolve(proxy).custom(hook));
    }
    @Test public void customHookCallingSuperKeepsItsSideEffectAndExpiredContext() {
        SweepFixture b = new SweepFixture(); b.add("a"); b.add("b");
        List<SweepEntityContext> held = new ArrayList<>();
        int[] calls = {0};
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(),
                SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.SCAN_ONLY).build(), b, new DefaultSweepPolicy<Void>() {
            public FlowControl beforeEntity(SweepEntityContext c, Void s) {
                calls[0]++; held.add(c); return super.beforeEntity(c, s);
            }
            public void onFinish(SweepContext c, Void s, SweepOutcome o) {
                assertEquals(2, calls[0]);
                try { held.get(0).index(); fail("Old callback must expire"); }
                catch (IllegalStateException expected) {}
            }
        });
        task.prepare();
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(2, calls[0]);
    }
}
