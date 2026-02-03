package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@SuppressWarnings("resource")
public class DustbinCommand {

    static SuggestionProvider<CommandSource> pageSuggest = (context, builder) -> {
        int totalPage = AotakeUtils.getDustbinTotalPage();
        List<Inventory> inventories = WorldTrashData.get().getInventoryList();
        IntStream.range(1, totalPage + 1)
                .filter(i -> {
                    Inventory inventory = CollectionUtils.getOrDefault(inventories, i - 1, null);
                    return i == 1
                            || i == totalPage
                            || (inventory != null && !inventory.isEmpty());
                })
                .forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> open() {
        Command<CommandSource> openDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);

            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                AotakeUtils.sendTranslatableMessage(context.getSource(), false, I18nUtils.getKey(EnumI18nType.MESSAGE, "dustbin_page_empty"));
                return 0;
            }

            List<ServerPlayerEntity> targetList = new ArrayList<>();
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

            for (ServerPlayerEntity player : targetList) {
                AotakeUtils.dustbin(player, page);
            }
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_DUSTBIN_OPEN.get())
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

    public static LiteralArgumentBuilder<CommandSource> clear() {
        Command<CommandSource> clearDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);

            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                AotakeUtils.sendTranslatableMessage(context.getSource(), false, I18nUtils.getKey(EnumI18nType.MESSAGE, "dustbin_page_empty"));
                return 0;
            }

            int page = CommandUtils.getIntDefault(context, "page", 0);
            int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
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
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "dustbin_cleared"
                    , page == 0 ? "" : String.format(" %s ", page)
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        return Commands.literal(CommonConfig.COMMAND_DUSTBIN_CLEAR.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_CLEAR))
                .executes(clearDustbinCommand)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(pageSuggest)
                        .executes(clearDustbinCommand)
                );
    }

    public static LiteralArgumentBuilder<CommandSource> drop() {
        Command<CommandSource> dropDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);

            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                AotakeUtils.sendTranslatableMessage(player, I18nUtils.getKey(EnumI18nType.MESSAGE, "dustbin_page_empty"));
                return 0;
            }

            int page = CommandUtils.getIntDefault(context, "page", 0);
            int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
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
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "dustbin_dropped"
                    , page == 0 ? "" : String.format(" %s ", page)
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        return Commands.literal(CommonConfig.COMMAND_DUSTBIN_DROP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_DROP))
                .executes(dropDustbinCommand)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(pageSuggest)
                        .executes(dropDustbinCommand)
                );
    }
}
