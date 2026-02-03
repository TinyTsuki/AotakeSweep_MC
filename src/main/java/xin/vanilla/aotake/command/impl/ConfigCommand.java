package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.I18nUtils;

public class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> config() {
        return Commands.literal("config")
                // 设置配置模式
                .then(Commands.literal("mode")
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                        .then(Commands.argument("mode", IntegerArgumentType.integer(0, 2))
                                .suggests((context, builder) -> {
                                    builder.suggest(0);
                                    builder.suggest(1);
                                    builder.suggest(2);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    int mode = IntegerArgumentType.getInteger(context, "mode");
                                    CommandSourceStack source = context.getSource();
                                    String lang = CommandUtils.getLanguage(source);
                                    switch (mode) {
                                        case 0:
                                            ServerConfig.resetConfigWithMode0();
                                            break;
                                        case 1:
                                            ServerConfig.resetConfigWithMode1();
                                            break;
                                        case 2:
                                            ServerConfig.resetConfigWithMode2();
                                            break;
                                        default: {
                                            throw new IllegalArgumentException("Mode " + mode + " does not exist");
                                        }
                                    }
                                    Component component = Component.translatable(lang, EnumI18nType.MESSAGE, "server_config_mode", mode);
                                    source.sendSuccess(() -> component.toChatComponent(lang), false);

                                    // 更新权限信息
                                    source.getServer().getPlayerList().getPlayers().forEach(AotakeUtils::refreshPermission);
                                    return 1;
                                })
                        )
                )
                // 临时禁用
                .then(Commands.literal("disable")
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                        .then(Commands.argument("disable", BoolArgumentType.bool())
                                .executes(context -> {
                                    AotakeSweep.disable(BoolArgumentType.getBool(context, "disable"));
                                    AotakeUtils.broadcastMessage(context.getSource().getServer()
                                            , Component.translatable(EnumI18nType.MESSAGE
                                                    , "mod_status"
                                                    , Component.translatable(EnumI18nType.KEY, "categories")
                                                    , I18nUtils.enabled(ServerConfig.get().defaultLanguage(), !AotakeSweep.disable())
                                            )
                                    );
                                    return 1;
                                })
                        )
                )
                // region 修改玩家配置
                .then(Commands.literal("player")
                        .then(Commands.literal("showSweepResult")
                                .then(Commands.argument("show", StringArgumentType.word())
                                        .suggests((context, suggestion) -> {
                                            String show = CommandUtils.getStringDefault(context, "show", "");
                                            CommandUtils.addSuggestion(suggestion, show, "true");
                                            CommandUtils.addSuggestion(suggestion, show, "false");
                                            CommandUtils.addSuggestion(suggestion, show, "change");
                                            return suggestion.buildFuture();
                                        })
                                        .executes(context -> {
                                            if (CommandUtils.checkModStatus(context)) return 0;
                                            CommandUtils.notifyHelp(context);
                                            String show = CommandUtils.getStringDefault(context, "show", "change");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            PlayerSweepData data = PlayerSweepData.getData(player);
                                            boolean r = "change".equalsIgnoreCase(show) ? !data.isShowSweepResult() : Boolean.parseBoolean(show);
                                            data.setShowSweepResult(r);
                                            AotakeUtils.sendMessage(player
                                                    , Component.translatable(EnumI18nType.MESSAGE
                                                            , "show_sweep_result"
                                                            , I18nUtils.enabled(AotakeUtils.getPlayerLanguage(player), r)
                                                            , String.format("/%s config player showSweepResult [<status>]", AotakeUtils.getCommandPrefix())
                                                    )
                                            );
                                            return 1;
                                        })
                                )
                        )
                        // 播放提示语音
                        .then(Commands.literal("enableWarningVoice")
                                .then(Commands.argument("enable", StringArgumentType.word())
                                        .suggests((context, suggestion) -> {
                                            String enable = CommandUtils.getStringDefault(context, "enable", "");
                                            CommandUtils.addSuggestion(suggestion, enable, "true");
                                            CommandUtils.addSuggestion(suggestion, enable, "false");
                                            CommandUtils.addSuggestion(suggestion, enable, "change");
                                            return suggestion.buildFuture();
                                        })
                                        .executes(context -> {
                                            if (CommandUtils.checkModStatus(context)) return 0;
                                            CommandUtils.notifyHelp(context);
                                            String enable = CommandUtils.getStringDefault(context, "enable", "change");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            PlayerSweepData data = PlayerSweepData.getData(player);
                                            boolean r = "change".equalsIgnoreCase(enable) ? !data.isEnableWarningVoice() : Boolean.parseBoolean(enable);
                                            data.setEnableWarningVoice(r);
                                            AotakeUtils.sendMessage(player
                                                    , Component.translatable(EnumI18nType.MESSAGE
                                                            , "enable_warning_voice"
                                                            , I18nUtils.enabled(AotakeUtils.getPlayerLanguage(player), r)
                                                            , String.format("/%s config player enableWarningVoice [<status>]", AotakeUtils.getCommandPrefix())
                                                    )
                                            );
                                            return 1;
                                        })
                                )
                        )
                );
    }
}
