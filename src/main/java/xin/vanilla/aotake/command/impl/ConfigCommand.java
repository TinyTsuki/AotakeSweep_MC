package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.config.ForgeConfigAdapter;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.util.*;

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
                                            CommonConfig.resetConfigWithMode0();
                                            break;
                                        case 1:
                                            CommonConfig.resetConfigWithMode1();
                                            break;
                                        case 2:
                                            CommonConfig.resetConfigWithMode2();
                                            break;
                                        default: {
                                            throw new IllegalArgumentException("Mode " + mode + " does not exist");
                                        }
                                    }
                                    source.sendSuccess(AotakeComponent.get().transLang(lang, EnumI18nType.FORMAT, "server_config_mode", mode).toChat(lang), false);

                                    // 更新权限信息
                                    source.getServer().getPlayerList().getPlayers().forEach(CommandUtils::refreshPermission);
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
                                    MessageUtils.broadcastNotification(AotakeComponent.get().trans(EnumI18nType.FORMAT
                                                    , "mod_status"
                                                    , AotakeComponent.get().trans(EnumI18nType.PLAIN, "key.aotake_sweep.categories")
                                                    , AotakeLang.get().enabled(CommonConfig.get().base().common().defaultLanguage(), !AotakeSweep.isDisable())
                                            )
                                            , AotakeNotificationTypes.ADMIN_BROADCAST);
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
                                    CommandUtils.configKeySuggestion(
                                            ForgeConfigAdapter.getHolder(CommonConfig.class), builder, input);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("configValue", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String configKey = StringArgumentType.getString(context, "configKey");
                                            CommandUtils.configValueSuggestion(
                                                    ForgeConfigAdapter.getHolder(CommonConfig.class), builder, configKey);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> CommandUtils.executeModifyConfig(
                                                ForgeConfigAdapter.getHolder(CommonConfig.class), context))
                                )
                        )
                )// endregion 修改server配置
                // region 修改common配置
                .then(Commands.literal("common")
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                        .then(Commands.argument("configKey", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = CommandUtils.getStringEmpty(context, "configKey");
                                    CommandUtils.configKeySuggestion(
                                            ForgeConfigAdapter.getHolder(CommonConfig.class), builder, input);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("configValue", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String configKey = StringArgumentType.getString(context, "configKey");
                                            CommandUtils.configValueSuggestion(
                                                    ForgeConfigAdapter.getHolder(CommonConfig.class), builder, configKey);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> CommandUtils.executeModifyConfig(
                                                ForgeConfigAdapter.getHolder(CommonConfig.class), context))
                                )
                        )
                )// endregion 修改common配置
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable))
                                                return 0;
                                            CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), AotakeComponent.get().trans(EnumI18nType.WORD, "title"), String.format("/%s help", AotakeUtils.getCommandPrefix()));
                                            String show = CommandUtils.getStringDefault(context, "show", "change");
                                            PlayerSweepData data = PlayerSweepData.getData(player);
                                            boolean r = "change".equalsIgnoreCase(show) ? !data.isShowSweepResult() : Boolean.parseBoolean(show);
                                            data.setShowSweepResult(r);
                                            MessageUtils.sendNotification(player
                                                    , AotakeComponent.get().trans(EnumI18nType.FORMAT
                                                            , "show_sweep_result"
                                                            , AotakeLang.get().enabled(Translator.getServerPlayerLanguage(player), r)
                                                            , String.format("/%s config player showSweepResult [<status>]", AotakeUtils.getCommandPrefix())
                                                    )
                                                    , AotakeNotificationTypes.PLAYER_PREFERENCE);
                                            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                                                PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepDataSyncToClient(player), player);
                                            }
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable))
                                                return 0;
                                            CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), AotakeComponent.get().trans(EnumI18nType.WORD, "title"), String.format("/%s help", AotakeUtils.getCommandPrefix()));
                                            String enable = CommandUtils.getStringDefault(context, "enable", "change");
                                            PlayerSweepData data = PlayerSweepData.getData(player);
                                            boolean r = "change".equalsIgnoreCase(enable) ? !data.isEnableWarningVoice() : Boolean.parseBoolean(enable);
                                            data.setEnableWarningVoice(r);
                                            MessageUtils.sendNotification(player
                                                    , AotakeComponent.get().trans(EnumI18nType.FORMAT
                                                            , "warning_voice"
                                                            , AotakeLang.get().enabled(Translator.getServerPlayerLanguage(player), r)
                                                            , String.format("/%s config player enableWarningVoice [<status>]", AotakeUtils.getCommandPrefix())
                                                    )
                                                    , AotakeNotificationTypes.PLAYER_PREFERENCE);
                                            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                                                PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepDataSyncToClient(player), player);
                                            }
                                            return 1;
                                        })
                                )
                        )
                );
    }
}
