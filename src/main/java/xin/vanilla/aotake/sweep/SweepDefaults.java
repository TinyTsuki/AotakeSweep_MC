package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.EntityEvaluation;
import xin.vanilla.aotake.api.sweep.SweepContext;

import java.util.List;

/**
 * Internal bridge used by DefaultSweepPolicy; access checks live in the task context.
 */
public final class SweepDefaults {
    private SweepDefaults() {
    }

    interface Access extends SweepContext {
        List<EntityAccess> collectDefault();

        List<EntityAccess> filterDefault(List<EntityEvaluation> evaluations);
    }

    public static List<EntityAccess> collect(SweepContext context) {
        return ((Access) context).collectDefault();
    }

    public static List<EntityAccess> filter(SweepContext context, List<EntityEvaluation> evaluations) {
        return ((Access) context).filterDefault(evaluations);
    }
}
