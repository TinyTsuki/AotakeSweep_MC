package xin.vanilla.aotake.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import xin.vanilla.aotake.command.impl.HelpCommand;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class AotakeCommand {

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
                .filter(command -> !command.getValue().isIgnore())
                .sorted(Comparator.comparing(command -> command.getValue().getSort()))
                .collect(Collectors.toList());
    }

    /**
     * 注册命令
     *
     * @param dispatcher 命令调度器
     */
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
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
                    dispatcher.register(type.getInstance().get());
                }
                // 注册完整的指令
                mainCommand.then(type.getInstance().get());
            }
        }

        dispatcher.register(mainCommand);
    }
}
