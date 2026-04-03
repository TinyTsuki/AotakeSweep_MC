package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.CollectionUtils;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.MessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@SuppressWarnings("resource")
public class DustbinCommand {

    static SuggestionProvider<CommandSourceStack> pageSuggest = (context, builder) -> {
        int totalPage = AotakeUtils.getDustbinTotalPage();
        List<SimpleContainer> inventories = WorldTrashData.get().getInventoryList();
        IntStream.range(1, totalPage + 1)
                .filter(i -> {
                    SimpleContainer inventory = CollectionUtils.getOrDefault(inventories, i - 1, null);
                    return i == 1
                            || i == totalPage
                            || (inventory != null && !inventory.isEmpty());
                })
                .forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> open() {
        Command<CommandSourceStack> openDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }


            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                Component empty = AotakeComponent.get().transAuto("dustbin_page_empty");
                if (context.getSource().getEntity() instanceof ServerPlayer) {
                    MessageUtils.sendNotification(context.getSource().getPlayerOrException(), empty, AotakeNotificationTypes.DUSTBIN);
                } else {
                    MessageUtils.sendMessage(context.getSource(), false, empty);
                }
                return 0;
            }

            List<ServerPlayer> targetList = new ArrayList<>();
            try {
                targetList.addAll(EntityArgument.getPlayers(context, "players"));
            } catch (IllegalArgumentException ignored) {
                targetList.add(context.getSource().getPlayerOrException());
            }
            int page = CommandUtils.getIntDefault(context, "page", 1);
            if (page > totalPage)
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(page, totalPage);
            if (page < 1)
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().create(page, 1);

            for (ServerPlayer player : targetList) {
                AotakeUtils.dustbin(player, page);
            }
            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandDustbinOpen())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_OPEN))
                .executes(openDustbinCommand)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests((context, builder) -> {
                            int totalPage = AotakeUtils.getDustbinTotalPage();
                            IntStream.range(1, totalPage + 1)
                                    .filter(i -> i == 1
                                            || i % 5 == 0
                                            || i == totalPage
                                    )
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(openDustbinCommand)
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_OPEN_OTHER))
                                .executes(openDustbinCommand)
                        )
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> clear() {
        Command<CommandSourceStack> clearDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                Component empty = AotakeComponent.get().transAuto("dustbin_page_empty");
                if (context.getSource().getEntity() instanceof ServerPlayer) {
                    MessageUtils.sendNotification(context.getSource().getPlayerOrException(), empty, AotakeNotificationTypes.DUSTBIN);
                } else {
                    MessageUtils.sendMessage(context.getSource(), false, empty);
                }
                return 0;
            }

            int page = CommandUtils.getIntDefault(context, "page", 0);
            int vPage = CommonConfig.get().base().dustbin().dustbinPageLimit();
            int bPage = CommonConfig.get().base().dustbin().dustbinBlockPositions().size();
            switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
                case VIRTUAL: {
                    AotakeUtils.clearVirtualDustbin(page);
                }
                break;
                case BLOCK: {
                    AotakeUtils.clearDustbinBlock(page);
                }
                break;
                case VIRTUAL_BLOCK: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= vPage) {
                            AotakeUtils.clearVirtualDustbin(page);
                        } else {
                            AotakeUtils.clearDustbinBlock(page - vPage);
                        }
                    } else if (page == 0) {
                        AotakeUtils.clearVirtualDustbin(page);
                        AotakeUtils.clearDustbinBlock(page);
                    }
                }
                break;
                case BLOCK_VIRTUAL: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= bPage) {
                            AotakeUtils.clearDustbinBlock(page);
                        } else {
                            AotakeUtils.clearVirtualDustbin(page - bPage);
                        }
                    } else if (page == 0) {
                        AotakeUtils.clearDustbinBlock(page);
                        AotakeUtils.clearVirtualDustbin(page);
                    }
                }
                break;
            }
            Component message = AotakeComponent.get().transAuto("dustbin_cleared"
                    , page == 0 ? "" : String.format(" %s ", page)
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendNotification(p, message, AotakeNotificationTypes.DUSTBIN));
            return 1;
        };
        return Commands.literal(CommonConfig.get().command().commandDustbinClear())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_CLEAR))
                .executes(clearDustbinCommand)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(pageSuggest)
                        .executes(clearDustbinCommand)
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> drop() {
        Command<CommandSourceStack> dropDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            ServerPlayer player = context.getSource().getPlayerOrException();
            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                MessageUtils.sendNotification(player, AotakeComponent.get().transAuto("dustbin_page_empty"), AotakeNotificationTypes.DUSTBIN);
                return 0;
            }

            int page = CommandUtils.getIntDefault(context, "page", 0);
            int vPage = CommonConfig.get().base().dustbin().dustbinPageLimit();
            int bPage = CommonConfig.get().base().dustbin().dustbinBlockPositions().size();
            switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
                case VIRTUAL: {
                    AotakeUtils.dropVirtualDustbin(player, page);
                }
                break;
                case BLOCK: {
                    AotakeUtils.dropDustbinBlock(player, page);
                }
                break;
                case VIRTUAL_BLOCK: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= vPage) {
                            AotakeUtils.dropVirtualDustbin(player, page);
                        } else {
                            AotakeUtils.dropDustbinBlock(player, page - vPage);
                        }
                    } else if (page == 0) {
                        AotakeUtils.dropVirtualDustbin(player, page);
                        AotakeUtils.dropDustbinBlock(player, page);
                    }
                }
                break;
                case BLOCK_VIRTUAL: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= bPage) {
                            AotakeUtils.dropDustbinBlock(player, page);
                        } else {
                            AotakeUtils.dropVirtualDustbin(player, page - bPage);
                        }
                    } else if (page == 0) {
                        AotakeUtils.dropDustbinBlock(player, page);
                        AotakeUtils.dropVirtualDustbin(player, page);
                    }
                }
                break;
            }
            Component message = AotakeComponent.get().transAuto("dustbin_dropped"
                    , page == 0 ? "" : String.format(" %s ", page)
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendNotification(p, message, AotakeNotificationTypes.DUSTBIN));
            return 1;
        };
        return Commands.literal(CommonConfig.get().command().commandDustbinDrop())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_DROP))
                .executes(dropDustbinCommand)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(pageSuggest)
                        .executes(dropDustbinCommand)
                );
    }
}
