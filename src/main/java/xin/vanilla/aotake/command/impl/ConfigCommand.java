package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.I18nUtils;

public class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSource> config() {
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
                                    CommandSource source = context.getSource();
                                    String lang = CommandUtils.getLanguage(source);
                                    switch (mode) {
                                        case 0:
                                            ServerConfig.resetConfigWithMode0();
                                            CommonConfig.resetConfigWithMode0();
                                            break;
                                        case 1:
                                            ServerConfig.resetConfigWithMode1();
                                            CommonConfig.resetConfigWithMode1();
                                            break;
                                        case 2:
                                            ServerConfig.resetConfigWithMode2();
                                            CommonConfig.resetConfigWithMode2();
                                            break;
                                        default: {
                                            throw new IllegalArgumentException("Mode " + mode + " does not exist");
                                        }
                                    }
                                    Component component = Component.translatable(lang, EnumI18nType.MESSAGE, "server_config_mode", mode);
                                    source.sendSuccess(component.toChatComponent(lang), false);

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
                                    AotakeSweep.setDisable(BoolArgumentType.getBool(context, "disable"));
                                    AotakeUtils.broadcastMessage(context.getSource().getServer()
                                            , Component.translatable(EnumI18nType.MESSAGE
                                                    , "mod_status"
                                                    , Component.translatable(EnumI18nType.KEY, "categories")
                                                    , I18nUtils.enabled(ServerConfig.DEFAULT_LANGUAGE.get(), !AotakeSweep.isDisable())
                                            )
                                    );
                                    return 1;
                                })
                        )
                )
                // region 修改server配置
                .then(Commands.literal("server")
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                        .then(Commands.argument("configKey", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = CommandUtils.getStringEmpty(context, "configKey");
                                    CommandUtils.configKeySuggestion(ServerConfig.class, builder, input);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("configValue", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String configKey = StringArgumentType.getString(context, "configKey");
                                            CommandUtils.configValueSuggestion(ServerConfig.class, builder, configKey);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> CommandUtils.executeModifyConfig(ServerConfig.class, context))
                                )
                        )
                )// endregion 修改server配置
                // region 修改common配置
                .then(Commands.literal("common")
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                        .then(Commands.argument("configKey", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = CommandUtils.getStringEmpty(context, "configKey");
                                    CommandUtils.configKeySuggestion(CommonConfig.class, builder, input);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("configValue", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String configKey = StringArgumentType.getString(context, "configKey");
                                            CommandUtils.configValueSuggestion(CommonConfig.class, builder, configKey);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> CommandUtils.executeModifyConfig(CommonConfig.class, context))
                                )
                        )
                )// endregion 修改common配置
                // region 修改client配置
                .then(Commands.literal("client")
                        .requires(source -> {
                            try {
                                return AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(source.getPlayerOrException()));
                            } catch (CommandSyntaxException e) {
                                return false;
                            }
                        })
                        .then(Commands.argument("configKey", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = CommandUtils.getStringEmpty(context, "configKey");
                                    CommandUtils.configKeySuggestion(ClientConfig.class, builder, input);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("configValue", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String configKey = StringArgumentType.getString(context, "configKey");
                                            CommandUtils.configValueSuggestion(ClientConfig.class, builder, configKey);
                                            return builder.buildFuture();
                                        })
                                )
                        )
                )// endregion 修改client配置
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
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
