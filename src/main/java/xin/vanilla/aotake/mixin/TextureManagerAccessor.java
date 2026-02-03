package xin.vanilla.aotake.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = TextureManager.class, remap = false)
public interface TextureManagerAccessor {
    @Accessor(value = "byPath")
    Map<ResourceLocation, AbstractTexture> aotake$byPath();
}
