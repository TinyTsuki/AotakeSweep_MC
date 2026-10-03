package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.sweep.SweepDefaults;

import java.util.List;

/**
 * Override only the required hooks. Calling super preserves the built-in behavior.
 */
public class DefaultSweepPolicy<S> implements SweepPolicy<S> {
    public DefaultSweepPolicy() {
    }

    public S createState(SweepContext c) {
        return null;
    }

    public StageControl beforeCollect(SweepContext c, S s) {
        return StageControl.CONTINUE;
    }

    public List<EntityAccess> collectEntities(SweepContext c, S s) {
        return SweepDefaults.collect(c);
    }

    public void afterCollect(SweepContext c, S s, List<EntityAccess> candidates) {
    }

    public StageControl beforeFilter(SweepContext c, S s, List<EntityAccess> candidates) {
        return StageControl.CONTINUE;
    }

    public FlowControl beforeEntity(SweepEntityContext c, S s) {
        return FlowControl.CONTINUE;
    }

    public EntityDisposition evaluateEntity(SweepEntityContext c, S s) {
        return EntityDisposition.DEFAULT;
    }

    public EntityEvaluation afterEntity(SweepEntityContext c, S s, EntityEvaluation e) {
        return e;
    }

    public List<EntityAccess> finishFiltering(SweepContext c, S s, List<EntityEvaluation> evaluations) {
        return SweepDefaults.filter(c, evaluations);
    }

    public void afterFilter(SweepContext c, S s, List<EntityAccess> selected) {
    }

    public StageControl beforeCleanup(SweepContext c, S s, List<EntityAccess> selected) {
        return StageControl.CONTINUE;
    }

    public FlowControl beforeBatch(BatchContext c, S s) {
        return FlowControl.CONTINUE;
    }

    public void afterBatch(BatchContext c, S s, BatchResult result) {
    }

    public void onFinish(SweepContext c, S s, SweepOutcome outcome) {
    }
}
