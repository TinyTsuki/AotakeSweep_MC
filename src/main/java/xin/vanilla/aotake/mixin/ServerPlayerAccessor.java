package xin.vanilla.aotake.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ServerPlayer.class, remap = false)
public interface ServerPlayerAccessor {
    @Accessor(value = "language")
    String language();

    @Accessor(value = "language")
    void language(String language);
}
