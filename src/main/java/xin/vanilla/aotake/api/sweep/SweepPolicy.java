package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;

import java.util.List;

/**
 * One trusted policy instance and state per task; callbacks run on the server owner thread.
 */
public interface SweepPolicy<S> {
    S createState(SweepContext context);

    StageControl beforeCollect(SweepContext context, S state);

    List<EntityAccess> collectEntities(SweepContext context, S state);

    void afterCollect(SweepContext context, S state, List<EntityAccess> candidates);

    StageControl beforeFilter(SweepContext context, S state, List<EntityAccess> candidates);

    FlowControl beforeEntity(SweepEntityContext context, S state);

    EntityDisposition evaluateEntity(SweepEntityContext context, S state);

    EntityEvaluation afterEntity(SweepEntityContext context, S state, EntityEvaluation evaluation);

    List<EntityAccess> finishFiltering(SweepContext context, S state, List<EntityEvaluation> evaluations);

    void afterFilter(SweepContext context, S state, List<EntityAccess> selected);

    StageControl beforeCleanup(SweepContext context, S state, List<EntityAccess> selected);

    FlowControl beforeBatch(BatchContext context, S state);

    void afterBatch(BatchContext context, S state, BatchResult result);

    void onFinish(SweepContext context, S state, SweepOutcome outcome);
}
