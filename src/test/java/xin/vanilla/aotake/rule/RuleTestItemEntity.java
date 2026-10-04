package xin.vanilla.aotake.rule;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

public final class RuleTestItemEntity extends ItemEntity {
    public RuleTestItemEntity(Level level) { super(EntityType.ITEM, level); }
    public void moveToWorld(Level level) { setLevel(level); }
}
