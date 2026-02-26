package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int aotake$getLeftPos();

    @Accessor("topPos")
    int aotake$getTopPos();
}
