package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;

public class HelpCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> help() {
        Command<CommandSourceStack> helpCommand = context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String command;
            int page;
            try {
                command = StringArgumentType.getString(context, "command");
                page = StringUtils.toInt(command);
            } catch (IllegalArgumentException ignored) {
                command = "";
                page = 1;
            }
            Component helpInfo;
            if (page > 0) {
                int pages = (int) Math.ceil((double) AotakeCommand.HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
                helpInfo = Component.literal(StringUtils.format(ServerConfig.HELP_HEADER.get() + "\n", page, pages));
                for (int i = 0; (page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i < AotakeCommand.HELP_MESSAGE.size() && i < ServerConfig.HELP_INFO_NUM_PER_PAGE.get(); i++) {
                    KeyValue<String, EnumCommandType> keyValue = AotakeCommand.HELP_MESSAGE.get((page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i);
                    Component commandTips;
                    if (keyValue.getValue().name().toLowerCase().contains("concise")) {
                        commandTips = Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, "concise", AotakeUtils.getCommand(keyValue.getValue().replaceConcise()));
                    } else {
                        commandTips = Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, keyValue.getValue().name().toLowerCase());
                    }
                    commandTips.setColor(EnumMCColor.GRAY.getColor());
                    String com = "/" + keyValue.getKey();
                    helpInfo.append(Component.literal(com)
                                    .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "click_to_suggest").toTextComponent()))
                            )
                            .append(new Component(" -> ").setColor(EnumMCColor.YELLOW.getColor()))
                            .append(commandTips);
                    if (i != AotakeCommand.HELP_MESSAGE.size() - 1) {
                        helpInfo.append("\n");
                    }
                }
                // 添加翻页按钮
                if (pages > 1) {
                    helpInfo.append("\n");
                    Component prevButton = Component.literal("<<< ");
                    if (page > 1) {
                        prevButton.setColor(EnumMCColor.AQUA.getColor())
                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", AotakeUtils.getCommandPrefix(), "help", page - 1)))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "previous_page").toTextComponent()));
                    } else {
                        prevButton.setColor(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(prevButton);

                    helpInfo.append(Component.literal(String.format(" %s/%s "
                                    , StringUtils.padOptimizedLeft(page, String.valueOf(pages).length(), " ")
                                    , pages))
                            .setColor(EnumMCColor.WHITE.getColor()));

                    Component nextButton = Component.literal(" >>>");
                    if (page < pages) {
                        nextButton.setColor(EnumMCColor.AQUA.getColor())
                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", AotakeUtils.getCommandPrefix(), "help", page + 1)))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "next_page").toTextComponent()));
                    } else {
                        nextButton.setColor(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(nextButton);
                }
            } else {
                EnumCommandType type = EnumCommandType.valueOf(command);
                helpInfo = Component.empty();
                String com = "/" + AotakeUtils.getCommand(type);
                helpInfo.append(Component.literal(com)
                                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                        , Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "click_to_suggest").toTextComponent()))
                        )
                        .append("\n")
                        .append(Component.translatable(AotakeUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, command.toLowerCase() + "_detail").setColor(EnumMCColor.GRAY.getColor()));
            }
            AotakeUtils.sendMessage(player, helpInfo);
            return 1;
        };
        SuggestionProvider<CommandSourceStack> helpSuggestions = (context, builder) -> {
            String input = CommandUtils.getStringEmpty(context, "command");
            boolean isInputEmpty = StringUtils.isNullOrEmpty(input);
            int totalPages = (int) Math.ceil((double) AotakeCommand.HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
            for (int i = 0; i < totalPages && isInputEmpty; i++) {
                builder.suggest(i + 1);
            }
            for (EnumCommandType type : Arrays.stream(EnumCommandType.values())
                    .filter(type -> type != EnumCommandType.HELP)
                    .filter(type -> !type.isIgnore())
                    .filter(type -> !type.name().toLowerCase().contains("concise"))
                    .filter(type -> isInputEmpty || type.name().toLowerCase().contains(input.toLowerCase()))
                    .sorted(Comparator.comparing(EnumCommandType::getSort))
                    .toList()) {
                builder.suggest(type.name());
            }
            return builder.buildFuture();
        };

        return Commands.literal("help")
                .executes(helpCommand)
                .then(Commands.argument("command", StringArgumentType.word())
                        .suggests(helpSuggestions)
                        .executes(helpCommand)
                );
    }
}
