package xin.vanilla.banira.generated;

import net.minecraft.entity.Entity;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.EntityRuleContext;

public final class NativeEntityProbe implements EntityRule {
    public interface ReadX { double get(Entity entity); }
    public interface ReadTick { int get(Entity entity); }
    public interface WritePosition { void set(Entity entity, double x, double y, double z); }
    private final java.util.Map<Entity, Integer> visits = new java.util.IdentityHashMap<Entity, Integer>();
    private int retirementVisits;
    private String retirementMode;

    public boolean test(EntityRuleContext context) {
        Entity entity = (Entity) context.nativeEntity(Entity.class);
        String retireAt = System.getProperty("aotake.policySmoke.retireAt");
        if (!java.util.Objects.equals(retireAt, retirementMode)) {
            retirementMode = retireAt;
            retirementVisits = 0;
        }
        if (retireAt != null && ++retirementVisits == (retireAt.equals("batch") ? 3 : 1))
            xin.vanilla.aotake.internal.server.dev.AotakeSweepPolicySmokeServerRunner.retireForSmoke();
        if (Boolean.getBoolean("aotake.policySmoke.moveDuringRecycle")) {
            Integer previous = (Integer) visits.get(entity);
            int count = previous == null ? 1 : previous.intValue() + 1;
            visits.put(entity, Integer.valueOf(count));
            if (count == 2) ((WritePosition) context.members().require("position", WritePosition.class)).set(entity, 80, 100, 8);
            return false;
        }
        return entity == context.entity().nativeEntity(Entity.class)
                && ((ReadX) context.members().require("x", ReadX.class)).get(entity) == context.entity().x()
                && ((ReadTick) context.members().require("tick", ReadTick.class)).get(entity) == context.tick();
    }
}
