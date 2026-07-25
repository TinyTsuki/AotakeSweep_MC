package xin.vanilla.aotake.mixin;

import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessor<S> {

    @Accessor(value = "children", remap = false)
    Map<String, CommandNode<S>> aotake$getChildren();

    @Accessor(value = "literals", remap = false)
    Map<String, CommandNode<S>> aotake$getLiterals();

    @Accessor(value = "arguments", remap = false)
    Map<String, CommandNode<S>> aotake$getArguments();
}
