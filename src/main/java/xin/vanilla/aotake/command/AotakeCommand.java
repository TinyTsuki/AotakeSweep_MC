package xin.vanilla.aotake.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.impl.HelpCommand;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.internal.common.BrigadierCommandTree;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class AotakeCommand {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, CommandNode<CommandSource>> OWNED_ROOTS = new LinkedHashMap<>();
    private static CommandDispatcher<CommandSource> registeredDispatcher;

    public static List<KeyValue<String, EnumCommandType>> HELP_MESSAGE = new ArrayList<>();

    private static void refreshHelpMessage() {
        HELP_MESSAGE = Arrays.stream(EnumCommandType.values())
                .map(type -> {
                    String command = AotakeUtils.getCommand(type);
                    if (StringUtils.isNotNullOrEmpty(command)) {
                        return new KeyValue<>(command, type);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(command -> !command.value().isIgnore())
                .sorted(Comparator.comparing(command -> command.value().getSort()))
                .collect(Collectors.toList());
    }

    /**
     * 注册命令
     *
     * @param dispatcher 命令调度器
     */
    public static synchronized void register(CommandDispatcher<CommandSource> dispatcher) {
        if (registeredDispatcher == dispatcher) {
            for (Map.Entry<String, CommandNode<CommandSource>> root : OWNED_ROOTS.entrySet()) {
                BrigadierCommandTree.removeRoot(dispatcher, root.getKey(), root.getValue());
            }
        }
        registeredDispatcher = dispatcher;
        OWNED_ROOTS.clear();
        // 刷新帮助信息
        refreshHelpMessage();

        // 注册有前缀的指令
        LiteralArgumentBuilder<CommandSource> mainCommand = Commands.literal(AotakeUtils.getCommandPrefix());

        // 主指令直接执行显示帮助
        mainCommand.executes(HelpCommand.help().getCommand());

        for (EnumCommandType type : EnumCommandType.values()) {
            if (type.getInstance() != null) {
                // 注册简短的指令
                if (AotakeUtils.isConciseEnabled(type)) {
                    registerOwnedRoot(dispatcher, type.getInstance().get());
                }
                // 注册完整的指令
                mainCommand.then(type.getInstance().get());
            }
        }

        registerOwnedRoot(dispatcher, mainCommand);
    }

    /**
     * 配置保存后重建本 mod 的根节点，并向在线玩家重新发送命令树。
     */
    public static void refreshConfiguredCommands(MinecraftServer server) {
        if (server == null) {
            return;
        }
        server.execute(() -> {
            CommandDispatcher<CommandSource> dispatcher;
            synchronized (AotakeCommand.class) {
                dispatcher = registeredDispatcher;
            }
            if (dispatcher == null) {
                return;
            }
            register(dispatcher);
            for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        });
    }

    private static void registerOwnedRoot(CommandDispatcher<CommandSource> dispatcher,
                                          LiteralArgumentBuilder<CommandSource> builder) {
        String rootName = builder.getLiteral();
        CommandNode<CommandSource> existing = dispatcher.getRoot().getChild(rootName);
        if (existing != null) {
            LOGGER.warn("Skip Aotake command root '{}' because it is already registered", rootName);
            return;
        }
        OWNED_ROOTS.put(rootName, dispatcher.register(builder));
    }
}
