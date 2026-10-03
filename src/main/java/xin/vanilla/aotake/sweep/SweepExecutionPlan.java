package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.sweep.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * Prepared once per policy type and retained only by its execution generation.
 */
public final class SweepExecutionPlan {
    public enum Hook {
        STATE("createState", SweepContext.class),
        BEFORE_COLLECT("beforeCollect", SweepContext.class, Object.class),
        COLLECT("collectEntities", SweepContext.class, Object.class),
        AFTER_COLLECT("afterCollect", SweepContext.class, Object.class, List.class),
        BEFORE_FILTER("beforeFilter", SweepContext.class, Object.class, List.class),
        BEFORE_ENTITY("beforeEntity", SweepEntityContext.class, Object.class),
        EVALUATE("evaluateEntity", SweepEntityContext.class, Object.class),
        AFTER_ENTITY("afterEntity", SweepEntityContext.class, Object.class, EntityEvaluation.class),
        FILTER("finishFiltering", SweepContext.class, Object.class, List.class),
        AFTER_FILTER("afterFilter", SweepContext.class, Object.class, List.class),
        BEFORE_CLEANUP("beforeCleanup", SweepContext.class, Object.class, List.class),
        BEFORE_BATCH("beforeBatch", BatchContext.class, Object.class),
        AFTER_BATCH("afterBatch", BatchContext.class, Object.class, BatchResult.class),
        FINISH("onFinish", SweepContext.class, Object.class, SweepOutcome.class);

        private final String method;
        private final Class<?>[] parameters;

        Hook(String method, Class<?>... parameters) {
            this.method = method;
            this.parameters = parameters;
        }
    }

    private final Class<? extends SweepPolicy> type;
    private final int custom;

    private SweepExecutionPlan(Class<? extends SweepPolicy> type, int custom) {
        this.type = type;
        this.custom = custom;
    }

    public static SweepExecutionPlan resolve(Class<? extends SweepPolicy> type) {
        Objects.requireNonNull(type, "policy type");
        int custom = 0;
        try {
            for (Hook hook : Hook.values()) {
                Method method = type.getMethod(hook.method, hook.parameters);
                if (method.getDeclaringClass() != DefaultSweepPolicy.class) custom |= 1 << hook.ordinal();
            }
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("Incomplete policy type", failure);
        }
        return new SweepExecutionPlan(type, custom);
    }

    public boolean custom(Hook hook) {
        return (custom & (1 << hook.ordinal())) != 0;
    }

    public void requireType(SweepPolicy<?> policy) {
        if (policy.getClass() != type) throw new IllegalArgumentException("Policy execution plan type mismatch");
    }
}
