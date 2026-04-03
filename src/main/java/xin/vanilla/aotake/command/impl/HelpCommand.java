package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class HelpCommand {
    public static LiteralArgumentBuilder<CommandSource> help() {
        Command<CommandSource> helpCommand = context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            String command;
            int page;
            try {
                command = StringArgumentType.getString(context, "command");
                page = NumberUtils.toInt(command);
            } catch (IllegalArgumentException ignored) {
                command = "";
                page = 1;
            }
            Component helpInfo;
            if (page > 0) {
                int pages = (int) Math.ceil((double) AotakeCommand.HELP_MESSAGE.size() / CommonConfig.get().base().common().helpInfoNumPerPage());
                helpInfo = AotakeComponent.get().literal(StringUtils.format(CommonConfig.get().base().common().helpHeader() + "\n", page, pages));
                for (int i = 0; (page - 1) * CommonConfig.get().base().common().helpInfoNumPerPage() + i < AotakeCommand.HELP_MESSAGE.size() && i < CommonConfig.get().base().common().helpInfoNumPerPage(); i++) {
                    KeyValue<String, EnumCommandType> keyValue = AotakeCommand.HELP_MESSAGE.get((page - 1) * CommonConfig.get().base().common().helpInfoNumPerPage() + i);
                    Component commandTips;
                    if (keyValue.val().name().toLowerCase().contains("concise")) {
                        commandTips = AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.FORMAT, "concise", AotakeUtils.getCommand(keyValue.val().replaceConcise()));
                    } else {
                        commandTips = AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, keyValue.val().name().toLowerCase());
                    }
                    commandTips.color(EnumMCColor.GRAY.getColor());
                    String com = "/" + keyValue.key();
                    helpInfo.append(AotakeComponent.get().literal(com)
                                    .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                    .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, "click_to_suggest").toVanilla()))
                            )
                            .append(AotakeComponent.get().literal(" -> ").color(EnumMCColor.YELLOW.getColor()))
                            .append(commandTips);
                    if (i != AotakeCommand.HELP_MESSAGE.size() - 1) {
                        helpInfo.append("\n");
                    }
                }
                // 添加翻页按钮
                if (pages > 1) {
                    helpInfo.append("\n");
                    Component prevButton = AotakeComponent.get().literal("<<< ");
                    if (page > 1) {
                        prevButton.color(EnumMCColor.AQUA.getColor())
                                .clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", AotakeUtils.getCommandPrefix(), "help", page - 1)))
                                .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, "previous_page").toVanilla()));
                    } else {
                        prevButton.color(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(prevButton);

                    helpInfo.append(AotakeComponent.get().literal(String.format(" %s/%s "
                                    , StringUtils.padOptimizedLeft(page, String.valueOf(pages).length(), " ")
                                    , pages))
                            .color(EnumMCColor.WHITE.getColor()));

                    Component nextButton = AotakeComponent.get().literal(" >>>");
                    if (page < pages) {
                        nextButton.color(EnumMCColor.AQUA.getColor())
                                .clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", AotakeUtils.getCommandPrefix(), "help", page + 1)))
                                .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, "next_page").toVanilla()));
                    } else {
                        nextButton.color(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(nextButton);
                }
            } else {
                EnumCommandType type = EnumCommandType.valueOf(command);
                helpInfo = AotakeComponent.get().empty();
                String com = "/" + AotakeUtils.getCommand(type);
                helpInfo.append(AotakeComponent.get().literal(com)
                                .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                        , AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, "click_to_suggest").toVanilla()))
                        )
                        .append("\n")
                        .append(AotakeComponent.get().transLang(Translator.getServerPlayerLanguage(player), EnumI18nType.WORD, command.toLowerCase() + "_detail").color(EnumMCColor.GRAY.getColor()));
            }
            MessageUtils.sendMessage(player, helpInfo);
            return 1;
        };
        SuggestionProvider<CommandSource> helpSuggestions = (context, builder) -> {
            String input = CommandUtils.getStringEmpty(context, "command");
            boolean isInputEmpty = StringUtils.isNullOrEmpty(input);
            int totalPages = (int) Math.ceil((double) AotakeCommand.HELP_MESSAGE.size() / CommonConfig.get().base().common().helpInfoNumPerPage());
            for (int i = 0; i < totalPages && isInputEmpty; i++) {
                builder.suggest(i + 1);
            }
            for (EnumCommandType type : Arrays.stream(EnumCommandType.values())
                    .filter(type -> type != EnumCommandType.HELP)
                    .filter(type -> !type.isIgnore())
                    .filter(type -> !type.name().toLowerCase().contains("concise"))
                    .filter(type -> isInputEmpty || type.name().toLowerCase().contains(input.toLowerCase()))
                    .sorted(Comparator.comparing(EnumCommandType::getSort))
                    .collect(Collectors.toList())) {
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
