package xin.vanilla.aotake.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ServerPlayerEntity.class, remap = false)
public interface ServerPlayerAccessor {
    @Accessor(value = "language")
    String language();

    @Accessor(value = "language")
    void language(String language);
}
