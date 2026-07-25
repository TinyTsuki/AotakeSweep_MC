package xin.vanilla.aotake.internal.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import xin.vanilla.aotake.mixin.CommandNodeAccessor;

/**
 * 只移除由竹叶清记录并确认仍属于自己的 Brigadier 根节点。
 */
public final class BrigadierCommandTree {
    private BrigadierCommandTree() {
    }

    @SuppressWarnings("unchecked")
    public static void removeRoot(CommandDispatcher<CommandSourceStack> dispatcher, String name,
                                  CommandNode<CommandSourceStack> expectedNode) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        if (root.getChild(name) != expectedNode) {
            return;
        }
        CommandNodeAccessor<CommandSourceStack> accessor = (CommandNodeAccessor<CommandSourceStack>) root;
        accessor.aotake$getChildren().remove(name);
        accessor.aotake$getLiterals().remove(name);
        accessor.aotake$getArguments().remove(name);
    }
}
