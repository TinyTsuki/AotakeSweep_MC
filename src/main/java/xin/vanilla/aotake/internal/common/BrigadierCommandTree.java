package xin.vanilla.aotake.internal.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 只移除由竹叶清记录并确认仍属于自己的 Brigadier 根节点。
 * Forge 1.16.5 会在 Mixin 之前由父加载器加载 Brigadier，因此此处使用局部反射桥。
 */
public final class BrigadierCommandTree {
    private static final Field CHILDREN = commandMapField("children");
    private static final Field LITERALS = commandMapField("literals");
    private static final Field ARGUMENTS = commandMapField("arguments");

    private BrigadierCommandTree() {
    }

    public static void removeRoot(CommandDispatcher<CommandSource> dispatcher, String name,
                                  CommandNode<CommandSource> expectedNode) {
        CommandNode<CommandSource> root = dispatcher.getRoot();
        if (root.getChild(name) != expectedNode) {
            return;
        }
        commandMap(CHILDREN, root).remove(name);
        commandMap(LITERALS, root).remove(name);
        commandMap(ARGUMENTS, root).remove(name);
    }

    private static Field commandMapField(String name) {
        try {
            Field field = CommandNode.class.getDeclaredField(name);
            if (!Map.class.isAssignableFrom(field.getType())) {
                throw new IllegalStateException("Brigadier field is not a map: " + name);
            }
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CommandNode<CommandSource>> commandMap(Field field,
                                                                      CommandNode<CommandSource> root) {
        try {
            return (Map<String, CommandNode<CommandSource>>) field.get(root);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Brigadier command map", exception);
        }
    }
}
