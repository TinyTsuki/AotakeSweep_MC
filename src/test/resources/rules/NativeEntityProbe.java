package xin.vanilla.banira.generated;

import net.minecraft.entity.Entity;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.EntityRuleContext;

public final class NativeEntityProbe implements EntityRule {
    public interface ReadX { double get(Entity entity); }
    public interface ReadTick { int get(Entity entity); }

    public boolean test(EntityRuleContext context) {
        Entity entity = (Entity) context.nativeEntity(Entity.class);
        return entity == context.entity().nativeEntity(Entity.class)
                && ((ReadX) context.members().require("x", ReadX.class)).get(entity) == context.entity().x()
                && ((ReadTick) context.members().require("tick", ReadTick.class)).get(entity) == context.tick();
    }
}
