package xin.vanilla.banira.generated;

import java.util.List;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.rule.WorldAccess;
import xin.vanilla.aotake.api.sweep.*;

public final class SmokeSweepPolicy extends DefaultSweepPolicy<Object> {
    public Object createState(SweepContext context) { return new PolicyAudit(context); }
    public StageControl beforeCollect(SweepContext c, Object s) { ((PolicyAudit) s).event(c, "beforeCollect"); return super.beforeCollect(c, s); }
    public List<EntityAccess> collectEntities(SweepContext c, Object s) {
        PolicyAudit state = (PolicyAudit) s; state.event(c, "collect"); state.collectedAt = c.server().tick();
        if (c.trigger() == SweepTrigger.SCHEDULED)
            return ((WorldAccess) c.server().world("minecraft:overworld").get()).entities(0, 90, 0, 100, 110, 20);
        return super.collectEntities(c, s);
    }
    public void afterCollect(SweepContext c, Object s, List<EntityAccess> candidates) {
        ((PolicyAudit) s).event(c, "afterCollect");
        if (c.operation() == SweepOperation.DELETE)
            candidates.addAll(((WorldAccess) c.server().world("minecraft:overworld").get()).entities(0, 90, 0, 100, 110, 20));
    }
    public StageControl beforeFilter(SweepContext c, Object s, List<EntityAccess> candidates) {
        PolicyAudit state = (PolicyAudit) s; state.event(c, "beforeFilter");
        if (!candidates.isEmpty()) state.retained = (EntityAccess) candidates.get(0);
        if (state.mode.equals("removeBeforeFilter") && !candidates.isEmpty())
            xin.vanilla.aotake.internal.server.dev.AotakeSweepPolicySmokeServerRunner.removeForSmoke(((EntityAccess) candidates.get(0)).nativeEntity(Object.class));
        return super.beforeFilter(c, s, candidates);
    }
    public FlowControl beforeEntity(SweepEntityContext c, Object s) {
        PolicyAudit state = (PolicyAudit) s; state.event(c.task(), "beforeEntity");
        if (state.mode.equals("removeBeforeEntity") && c.index() == 0)
            xin.vanilla.aotake.internal.server.dev.AotakeSweepPolicySmokeServerRunner.removeForSmoke(c.entity().nativeEntity(Object.class));
        return FlowControl.CONTINUE;
    }
    public EntityDisposition evaluateEntity(SweepEntityContext c, Object s) {
        PolicyAudit state = (PolicyAudit) s; state.event(c.task(), "evaluate"); state.evaluated++;
        return "minecraft:diamond".equals(c.entity().itemResourceId()) ? EntityDisposition.KEEP : super.evaluateEntity(c, s);
    }
    public EntityEvaluation afterEntity(SweepEntityContext c, Object s, EntityEvaluation result) { ((PolicyAudit) s).event(c.task(), "afterEntity"); return result; }
    public List<EntityAccess> finishFiltering(SweepContext c, Object s, List<EntityEvaluation> results) { ((PolicyAudit) s).event(c, "filter"); return super.finishFiltering(c, s, results); }
    public void afterFilter(SweepContext c, Object s, List<EntityAccess> selected) { ((PolicyAudit) s).event(c, "afterFilter"); }
    public StageControl beforeCleanup(SweepContext c, Object s, List<EntityAccess> selected) {
        PolicyAudit state = (PolicyAudit) s; state.event(c, "beforeCleanup"); state.cleanupAt = c.server().tick();
        return state.mode.equals("cancel") ? StageControl.CANCEL : StageControl.CONTINUE;
    }
    public FlowControl beforeBatch(BatchContext c, Object s) {
        PolicyAudit state = (PolicyAudit) s; state.event(c.task(), "beforeBatch");
        return state.mode.equals("skip") && c.index() == 0 ? FlowControl.SKIP : FlowControl.CONTINUE;
    }
    public void afterBatch(BatchContext c, Object s, BatchResult result) { ((PolicyAudit) s).event(c.task(), "afterBatch"); }
    public void onFinish(SweepContext c, Object s, SweepOutcome result) { ((PolicyAudit) s).finish(c, result); }
}
