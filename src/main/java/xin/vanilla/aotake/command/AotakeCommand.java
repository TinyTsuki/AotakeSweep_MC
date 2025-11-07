package xin.vanilla.aotake.command;

import com.electronwill.nightconfig.core.Config;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.NonNull;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.packet.CustomConfigSyncToClient;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AotakeCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    public static List<KeyValue<String, EnumCommandType>> HELP_MESSAGE;

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

        Command<CommandSource> helpCommand = context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
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
                int pages = (int) Math.ceil((double) HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
                helpInfo = Component.literal(StringUtils.format(ServerConfig.HELP_HEADER.get() + "\n", page, pages));
                for (int i = 0; (page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i < HELP_MESSAGE.size() && i < ServerConfig.HELP_INFO_NUM_PER_PAGE.get(); i++) {
                    KeyValue<String, EnumCommandType> keyValue = HELP_MESSAGE.get((page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i);
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
                    if (i != HELP_MESSAGE.size() - 1) {
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
        SuggestionProvider<CommandSource> helpSuggestions = (context, builder) -> {
            String input = getStringEmpty(context, "command");
            boolean isInputEmpty = StringUtils.isNullOrEmpty(input);
            int totalPages = (int) Math.ceil((double) HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
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


        Command<CommandSource> languageCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            String language = StringArgumentType.getString(context, "language");
            if (I18nUtils.getI18nFiles().contains(language)) {
                CustomConfig.setPlayerLanguage(AotakeUtils.getPlayerUUIDString(player), language);
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else if ("server".equalsIgnoreCase(language) || "client".equalsIgnoreCase(language)) {
                CustomConfig.setPlayerLanguage(AotakeUtils.getPlayerUUIDString(player), language);
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else {
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "language_not_exist").setColor(0xFFFF0000));
            }
            return 1;
        };
        Command<CommandSource> virtualOpCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            CommandSource source = context.getSource();
            // 如果命令来自玩家
            if (source.getEntity() == null || source.getEntity() instanceof ServerPlayerEntity) {
                EnumOperationType type = EnumOperationType.fromString(StringArgumentType.getString(context, "operation"));
                EnumCommandType[] rules;
                try {
                    rules = Arrays.stream(StringArgumentType.getString(context, "rules").split(","))
                            .filter(StringUtils::isNotNullOrEmpty)
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .map(EnumCommandType::valueOf).toArray(EnumCommandType[]::new);
                } catch (IllegalArgumentException ignored) {
                    rules = new EnumCommandType[]{};
                }
                List<ServerPlayerEntity> targetList = new ArrayList<>();
                try {
                    targetList.addAll(EntityArgument.getPlayers(context, "player"));
                } catch (IllegalArgumentException ignored) {
                }
                String language = getLanguage(source);
                for (ServerPlayerEntity target : targetList) {
                    switch (type) {
                        case ADD:
                            VirtualPermissionManager.addVirtualPermission(target, rules);
                            break;
                        case SET:
                            VirtualPermissionManager.setVirtualPermission(target, rules);
                            break;
                        case DEL:
                        case REMOVE:
                            VirtualPermissionManager.delVirtualPermission(target, rules);
                            break;
                        case CLEAR:
                            VirtualPermissionManager.clearVirtualPermission(target);
                            break;
                    }
                    String permissions = VirtualPermissionManager.buildPermissionsString(VirtualPermissionManager.getVirtualPermission(target));
                    AotakeUtils.sendTranslatableMessage(target, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                    if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = source.getPlayerOrException();
                        if (!target.getStringUUID().equalsIgnoreCase(player.getStringUUID())) {
                            AotakeUtils.sendTranslatableMessage(player, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                        }
                    } else {
                        source.sendSuccess(Component.translatable(language, EnumI18nType.MESSAGE, "player_virtual_op", target.getDisplayName().getString(), permissions).toChatComponent(), true);
                    }
                    // 更新权限信息
                    source.getServer().getPlayerList().sendPlayerPermissionLevel(target);
                    for (ServerPlayerEntity player : source.getServer().getPlayerList().getPlayers()) {
                        if (AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(player))) {
                            AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
                        }
                    }
                }
            }
            return 1;
        };
        Command<CommandSource> openDustbinCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            List<ServerPlayerEntity> targetList = new ArrayList<>();
            try {
                targetList.addAll(EntityArgument.getPlayers(context, "players"));
            } catch (IllegalArgumentException ignored) {
                targetList.add(context.getSource().getPlayerOrException());
            }
            int page = getIntDefault(context, "page", 1);
            int totalPage = getDustbinTotalPage();
            if (page > totalPage)
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(page, totalPage);
            if (page < 1)
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().create(page, 1);

            for (ServerPlayerEntity player : targetList) {
                dustbin(player, page);
            }
            return 1;
        };
        Command<CommandSource> sweepCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            int range = getIntDefault(context, "range", 0);
            ServerWorld dimension = getDimensionDefault(context, "dimension", null);
            List<Entity> entities;
            if (dimension == null) {
                entities = AotakeUtils.getAllEntities();
            } else {
                entities = new ArrayList<>();
                if (range > 0) {
                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                    entities.addAll(player.level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
                }
            }
            Entity entity = context.getSource().getEntity();
            AotakeScheduler.schedule(context.getSource().getServer(), 1, () -> AotakeUtils.sweep(entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null, entities, false));
            return 1;
        };
        Command<CommandSource> clearDropCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            boolean withEntity = getBooleanDefault(context, "withEntity", false);
            boolean greedyMode = getBooleanDefault(context, "greedyMode", false);
            boolean allEntity = getBooleanDefault(context, "allEntity", false);
            int range = getIntDefault(context, "range", 0);
            ServerWorld dimension = getDimensionDefault(context, "dimension", null);

            List<Entity> entities = new ArrayList<>();
            if (dimension == null) {
                entities = AotakeUtils.getAllEntities();
            } else if (range > 0) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                entities.addAll(player.level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
            }
            SweepResult result = new SweepResult();
            entities.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> (greedyMode && entity instanceof ItemEntity)
                            || (!greedyMode && AotakeUtils.isItem(entity))
                            || (withEntity && AotakeUtils.isJunkEntity(entity, false))
                            || (allEntity && !(entity instanceof PlayerEntity))
                    ).forEach(entity -> {
                        if (entity instanceof ItemEntity) {
                            result.plusItemCount(((ItemEntity) entity).getItem().getCount());
                        } else {
                            result.plusEntityCount();
                        }
                        AotakeUtils.removeEntity(entity, false);
                    });
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(player -> AotakeUtils.sendMessage(player
                            , AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
                                    , AotakeUtils.getPlayerLanguage(player)
                                    , result
                            )
                    ));
            return 1;
        };
        Command<CommandSource> clearDustbinCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            int page = getIntDefault(context, "page", 0);
            int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                case VIRTUAL: {
                    clearVirtualDustbin(page);
                }
                break;
                case BLOCK: {
                    clearDustbinBlock(page);
                }
                break;
                case VIRTUAL_BLOCK: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= vPage) {
                            clearVirtualDustbin(page);
                        } else {
                            clearDustbinBlock(page - vPage);
                        }
                    } else if (page == 0) {
                        clearVirtualDustbin(page);
                        clearDustbinBlock(page);
                    }
                }
                break;
                case BLOCK_VIRTUAL: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= bPage) {
                            clearDustbinBlock(page);
                        } else {
                            clearVirtualDustbin(page - bPage);
                        }
                    } else if (page == 0) {
                        clearDustbinBlock(page);
                        clearVirtualDustbin(page);
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
        Command<CommandSource> dropDustbinCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            int page = getIntDefault(context, "page", 0);
            int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                case VIRTUAL: {
                    dropVirtualDustbin(player, page);
                }
                break;
                case BLOCK: {
                    dropDustbinBlock(player, page);
                }
                break;
                case VIRTUAL_BLOCK: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= vPage) {
                            dropVirtualDustbin(player, page);
                        } else {
                            dropDustbinBlock(player, page - vPage);
                        }
                    } else if (page == 0) {
                        dropVirtualDustbin(player, page);
                        dropDustbinBlock(player, page);
                    }
                }
                break;
                case BLOCK_VIRTUAL: {
                    if (page > 0 && page <= vPage + bPage) {
                        if (page <= bPage) {
                            dropDustbinBlock(player, page);
                        } else {
                            dropVirtualDustbin(player, page - bPage);
                        }
                    } else if (page == 0) {
                        dropDustbinBlock(player, page);
                        dropVirtualDustbin(player, page);
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
        Command<CommandSource> clearCacheCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_cleared"
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
        Command<CommandSource> dropCacheCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            boolean originalPos = getBooleanDefault(context, "originalPos", false);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            List<KeyValue<WorldCoordinate, ItemStack>> items = WorldTrashData.get().getDropList().snapshot();
            WorldTrashData.get().getDropList().clear();
            items.forEach(kv -> {
                if (!kv.getValue().isEmpty()) {
                    WorldCoordinate coordinate;
                    if (originalPos) {
                        coordinate = kv.getKey();
                    } else {
                        coordinate = new WorldCoordinate(player);
                    }
                    ServerWorld level = AotakeUtils.getWorld(coordinate.getDimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.getValue());
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_dropped"
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
        Command<CommandSource> delaySweepCommand = context -> {
            if (checkModStatus(context)) return 0;
            notifyHelp(context);
            Date current = new Date();
            long delay = getLongDefault(context, "seconds", ServerConfig.SWEEP_INTERVAL.get() / 1000);
            if (delay > 0) {
                EventHandlerProxy.setNextSweepTime(current.getTime() + delay * 1000);
            } else {
                long nextSweepTime = EventHandlerProxy.getNextSweepTime() + delay * 1000;
                if (nextSweepTime < current.getTime())
                    nextSweepTime = current.getTime() + ServerConfig.SWEEP_INTERVAL.get();
                EventHandlerProxy.setNextSweepTime(nextSweepTime);
            }
            // 给已安装mod玩家同步扫地倒计时
            for (String uuid : AotakeSweep.getCustomConfigStatus()) {
                AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), AotakeUtils.getPlayerByUUID(uuid));
            }
            long seconds = (EventHandlerProxy.getNextSweepTime() - current.getTime()) / 1000;
            Component message = Component.translatable(EnumI18nType.MESSAGE, "next_sweep_time_set"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
                    , Component.literal(String.valueOf(seconds)).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                            , Component.literal(DateUtils.toDateTimeString(new Date(EventHandlerProxy.getNextSweepTime())) + " (Server Time)").toTextComponent())
                    )
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));

            return 1;
        };


        LiteralArgumentBuilder<CommandSource> language = // region language
                Commands.literal(CommonConfig.COMMAND_LANGUAGE.get())
                        .then(Commands.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("client");
                                    builder.suggest("server");
                                    I18nUtils.getI18nFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(languageCommand)
                        ); // endregion language
        LiteralArgumentBuilder<CommandSource> virtualOp = // region virtualOp
                Commands.literal(CommonConfig.COMMAND_VIRTUAL_OP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                        .then(Commands.argument("operation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest(EnumOperationType.ADD.name().toLowerCase());
                                    builder.suggest(EnumOperationType.SET.name().toLowerCase());
                                    builder.suggest(EnumOperationType.DEL.name().toLowerCase());
                                    builder.suggest(EnumOperationType.CLEAR.name().toLowerCase());
                                    builder.suggest(EnumOperationType.GET.name().toLowerCase());
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(virtualOpCommand)
                                        .then(Commands.argument("rules", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> {
                                                    String operation = StringArgumentType.getString(context, "operation");
                                                    if (operation.equalsIgnoreCase(EnumOperationType.GET.name().toLowerCase())
                                                            || operation.equalsIgnoreCase(EnumOperationType.CLEAR.name().toLowerCase())
                                                            || operation.equalsIgnoreCase(EnumOperationType.LIST.name().toLowerCase())) {
                                                        return builder.buildFuture();
                                                    }
                                                    String input = getStringEmpty(context, "rules").replace(" ", ",");
                                                    String[] split = input.split(",");
                                                    String current = input.endsWith(",") ? "" : split[split.length - 1];
                                                    for (EnumCommandType value : Arrays.stream(EnumCommandType.values())
                                                            .filter(EnumCommandType::isOp)
                                                            .filter(type -> Arrays.stream(split).noneMatch(in -> in.equalsIgnoreCase(type.name())))
                                                            .filter(type -> StringUtils.isNullOrEmptyEx(current) || type.name().toLowerCase().contains(current.toLowerCase()))
                                                            .sorted(Comparator.comparing(EnumCommandType::getSort))
                                                            .collect(Collectors.toList())) {
                                                        String suggest = value.name();
                                                        if (input.endsWith(",")) {
                                                            suggest = input + suggest;
                                                        }
                                                        builder.suggest(suggest);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(virtualOpCommand)
                                        )
                                )
                        ); // endregion virtualOp
        LiteralArgumentBuilder<CommandSource> openDustbin = // region openDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_OPEN.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_OPEN))
                        .executes(openDustbinCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> {
                                    int totalPage = getDustbinTotalPage();
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
                        ); // endregion openDustbin
        LiteralArgumentBuilder<CommandSource> sweep = // region sweep
                Commands.literal(CommonConfig.COMMAND_SWEEP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.SWEEP))
                        .executes(sweepCommand)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(sweepCommand)
                        )
                        .then(Commands.argument("range", IntegerArgumentType.integer(0))
                                .executes(sweepCommand)
                        ); // endregion sweep
        LiteralArgumentBuilder<CommandSource> clearDrop = // region clearDrop
                Commands.literal(CommonConfig.COMMAND_CLEAR_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CLEAR_DROP))
                        .executes(clearDropCommand)
                        .then(Commands.argument("greedyMode", BoolArgumentType.bool())
                                .executes(clearDropCommand)
                                .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                        .executes(clearDropCommand)
                                        .then(Commands.argument("allEntity", BoolArgumentType.bool())
                                                .executes(clearDropCommand)
                                        )
                                )
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(clearDropCommand)
                                        .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                                .executes(clearDropCommand)
                                                .then(Commands.argument("allEntity", BoolArgumentType.bool())
                                                        .executes(clearDropCommand)
                                                )
                                        )
                                )
                                .then(Commands.argument("range", IntegerArgumentType.integer(0))
                                        .executes(clearDropCommand)
                                        .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                                .executes(clearDropCommand)
                                                .then(Commands.argument("allEntity", BoolArgumentType.bool())
                                                        .executes(clearDropCommand)
                                                )
                                        )
                                )
                        ); // endregion clearDrop
        LiteralArgumentBuilder<CommandSource> clearDustbin = // region clearDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_CLEAR.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_CLEAR))
                        .executes(clearDustbinCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> {
                                    int totalPage = getDustbinTotalPage();
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
                                })
                                .executes(clearDustbinCommand)
                        ); // endregion clearDustbin
        LiteralArgumentBuilder<CommandSource> dropDustbin = // region dropDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_DROP))
                        .executes(dropDustbinCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> {
                                    int totalPage = getDustbinTotalPage();
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
                                })
                                .executes(dropDustbinCommand)
                        ); // endregion dropDustbin
        LiteralArgumentBuilder<CommandSource> clearCache = // region clearCache
                Commands.literal(CommonConfig.COMMAND_CACHE_CLEAR.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_CLEAR))
                        .executes(clearCacheCommand); // endregion clearCache
        LiteralArgumentBuilder<CommandSource> dropCache = // region dropCache
                Commands.literal(CommonConfig.COMMAND_CACHE_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_DROP))
                        .executes(dropCacheCommand)
                        .then(Commands.argument("originalPos", BoolArgumentType.bool())
                                .executes(dropCacheCommand)
                        ); // endregion dropCache
        LiteralArgumentBuilder<CommandSource> delaySweep = // region delaySweep
                Commands.literal(CommonConfig.COMMAND_DELAY_SWEEP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DELAY_SWEEP))
                        .executes(delaySweepCommand)
                        .then(Commands.argument("seconds", LongArgumentType.longArg())
                                .suggests((context, builder) -> {
                                    builder.suggest((int) (ServerConfig.SWEEP_INTERVAL.get() / 1000));
                                    return builder.buildFuture();
                                })
                                .executes(delaySweepCommand)
                        ); // endregion delaySweep


        // 注册简短的指令
        {
            // 设置语言 /language
            if (CommonConfig.CONCISE_LANGUAGE.get()) {
                dispatcher.register(language);
            }

            // 设置虚拟权限 /opv
            if (CommonConfig.CONCISE_VIRTUAL_OP.get()) {
                dispatcher.register(virtualOp);
            }

            // 打开垃圾箱 /dustbin
            if (CommonConfig.CONCISE_DUSTBIN_OPEN.get()) {
                dispatcher.register(openDustbin);
            }

            // 扫地 /sweep
            if (CommonConfig.CONCISE_SWEEP.get()) {
                dispatcher.register(sweep);
            }

            // 清除掉落物 /killitem
            if (CommonConfig.CONCISE_CLEAR_DROP.get()) {
                dispatcher.register(clearDrop);
            }

            // 清空垃圾箱 /cleardustbin
            if (CommonConfig.CONCISE_DUSTBIN_CLEAR.get()) {
                dispatcher.register(clearDustbin);
            }

            // 将垃圾箱物品掉落到世界 /dropdustbin
            if (CommonConfig.CONCISE_DUSTBIN_DROP.get()) {
                dispatcher.register(dropDustbin);
            }

            // 清空缓存 /clearcache
            if (CommonConfig.CONCISE_CACHE_CLEAR.get()) {
                dispatcher.register(clearCache);
            }

            // 将缓存内物品掉落至世界 /dropcache
            if (CommonConfig.CONCISE_CACHE_DROP.get()) {
                dispatcher.register(dropCache);
            }

            // 延迟扫地 /delay
            if (CommonConfig.CONCISE_DELAY_SWEEP.get()) {
                dispatcher.register(delaySweep);
            }

        }

        // 注册有前缀的指令
        {
            dispatcher.register(Commands.literal(AotakeUtils.getCommandPrefix())
                    .executes(helpCommand)
                    .then(Commands.literal("help")
                            .executes(helpCommand)
                            .then(Commands.argument("command", StringArgumentType.word())
                                    .suggests(helpSuggestions)
                                    .executes(helpCommand)
                            )
                    )
                    // 设置语言 /aotake language
                    .then(language)
                    // 设置虚拟权限 /aotake opv
                    .then(virtualOp)
                    // 打开垃圾箱 /aotake dustbin
                    .then(openDustbin)
                    // 扫地 /aotake sweep
                    .then(sweep)
                    // 清除掉落物 /aotake killitem
                    .then(clearDrop)
                    // 清空垃圾箱 /aotake cleardustbin
                    .then(clearDustbin)
                    // 将垃圾箱物品掉落到世界 /aotake dropdustbin
                    .then(dropDustbin)
                    // 清空缓存 /aotake clearcache
                    .then(clearCache)
                    // 将缓存内物品掉落至世界 /aotake dropcache
                    .then(dropCache)
                    // 延迟扫地 /aotake delay
                    .then(delaySweep)
                    // 获取服务器配置 /aotake config
                    .then(Commands.literal("config")
                            // 设置配置模式
                            .then(Commands.literal("mode")
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
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
                                                String lang = getLanguage(source);
                                                switch (mode) {
                                                    case 0:
                                                        ServerConfig.resetConfig();
                                                        CommonConfig.resetConfig();
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
                                                source.getServer().getPlayerList().getPlayers()
                                                        .forEach(player -> source.getServer()
                                                                .getPlayerList()
                                                                .sendPlayerPermissionLevel(player)
                                                        );
                                                return 1;
                                            })
                                    )
                            )
                            // region 修改server配置
                            .then(Commands.literal("server")
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                    .then(Commands.argument("configKey", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                String input = getStringEmpty(context, "configKey");
                                                configKeySuggestion(ServerConfig.class, builder, input);
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("configValue", StringArgumentType.word())
                                                    .suggests((context, builder) -> {
                                                        String configKey = StringArgumentType.getString(context, "configKey");
                                                        configValueSuggestion(ServerConfig.class, builder, configKey);
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(context -> executeModifyConfig(ServerConfig.class, context))
                                            )
                                    )
                            )// endregion 修改server配置
                            // region 修改common配置
                            .then(Commands.literal("common")
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                    .then(Commands.argument("configKey", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                String input = getStringEmpty(context, "configKey");
                                                configKeySuggestion(CommonConfig.class, builder, input);
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("configValue", StringArgumentType.word())
                                                    .suggests((context, builder) -> {
                                                        String configKey = StringArgumentType.getString(context, "configKey");
                                                        configValueSuggestion(CommonConfig.class, builder, configKey);
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(context -> executeModifyConfig(CommonConfig.class, context))
                                            )
                                    )
                            )// endregion 修改common配置
                            // region 玩家设置
                            // 显示打扫结果信息
                            .then(Commands.literal("player")
                                    .then(Commands.literal("showSweepResult")
                                            .then(Commands.argument("show", StringArgumentType.word())
                                                    .suggests((context, suggestion) -> {
                                                        String show = getStringDefault(context, "show", "");
                                                        addSuggestion(suggestion, show, "true");
                                                        addSuggestion(suggestion, show, "false");
                                                        addSuggestion(suggestion, show, "change");
                                                        return suggestion.buildFuture();
                                                    })
                                                    .executes(context -> {
                                                        if (checkModStatus(context)) return 0;
                                                        notifyHelp(context);
                                                        String show = getStringDefault(context, "show", "change");
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
                                                        String enable = getStringDefault(context, "enable", "");
                                                        addSuggestion(suggestion, enable, "true");
                                                        addSuggestion(suggestion, enable, "false");
                                                        addSuggestion(suggestion, enable, "change");
                                                        return suggestion.buildFuture();
                                                    })
                                                    .executes(context -> {
                                                        if (checkModStatus(context)) return 0;
                                                        notifyHelp(context);
                                                        String enable = getStringDefault(context, "enable", "change");
                                                        ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                        PlayerSweepData data = PlayerSweepData.getData(player);
                                                        boolean r = "change".equalsIgnoreCase(enable) ? !data.isEnableWarningVoice() : Boolean.parseBoolean(enable);
                                                        data.setEnableWarningVoice(r);
                                                        AotakeUtils.sendMessage(player
                                                                , Component.translatable(EnumI18nType.MESSAGE
                                                                        , "warning_voice"
                                                                        , I18nUtils.enabled(AotakeUtils.getPlayerLanguage(player), r)
                                                                        , String.format("/%s config player enableWarningVoice [<status>]", AotakeUtils.getCommandPrefix())
                                                                )
                                                        );
                                                        return 1;
                                                    })
                                            )
                                    )
                            )// endregion 玩家设置
                    )
            );
        }
    }

    public static String getStringEmpty(CommandContext<?> context, String name) {
        return getStringDefault(context, name, "");
    }

    public static String getStringDefault(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static int getIntDefault(CommandContext<?> context, String name, int defaultValue) {
        int result;
        try {
            result = IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static long getLongDefault(CommandContext<?> context, String name, long defaultValue) {
        long result;
        try {
            result = LongArgumentType.getLong(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static boolean getBooleanDefault(CommandContext<?> context, String name, boolean defaultValue) {
        boolean result;
        try {
            result = BoolArgumentType.getBool(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static ServerWorld getDimensionDefault(CommandContext<CommandSource> context, String name, ServerWorld defaultDimension) {
        ServerWorld result;
        try {
            result = DimensionArgument.getDimension(context, name);
        } catch (IllegalArgumentException | CommandSyntaxException e) {
            result = defaultDimension;
        }
        return result;
    }

    /**
     * 若为第一次使用指令则进行提示
     */
    public static void notifyHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            PlayerSweepData data = PlayerSweepData.getData(player);
            if (!data.isNotified()) {
                Component button = Component.literal("/" + AotakeUtils.getCommandPrefix())
                        .setColor(EnumMCColor.AQUA.getColor())
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + AotakeUtils.getCommandPrefix()))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + AotakeUtils.getCommandPrefix())
                                .toTextComponent())
                        );
                AotakeUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "notify_help", button));
                data.setNotified(true);
            }
        }
    }

    public static boolean checkModStatus(CommandContext<CommandSource> context) {
        if (AotakeSweep.isDisable()) {
            CommandSource source = context.getSource();
            Entity entity = source.getEntity();
            if (entity instanceof ServerPlayerEntity) {
                AotakeUtils.sendMessage((ServerPlayerEntity) entity, Component.translatable(EnumI18nType.MESSAGE, "mod_disabled"));
            }
        }
        return AotakeSweep.isDisable();
    }

    public static int dustbin(@NonNull ServerPlayerEntity player, int page) {
        int result = 0;
        int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
        int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
            case VIRTUAL: {
                result = openVirtualDustbin(player, page);
            }
            break;
            case BLOCK: {
                result = openDustbinBlock(player, page);
            }
            break;
            case VIRTUAL_BLOCK: {
                if (page > 0 && page <= vPage + bPage) {
                    if (page <= vPage) {
                        result = openVirtualDustbin(player, page);
                    } else {
                        result = openDustbinBlock(player, page - vPage);
                    }
                }
            }
            break;
            case BLOCK_VIRTUAL: {
                if (page > 0 && page <= vPage + bPage) {
                    if (page <= bPage) {
                        result = openDustbinBlock(player, page);
                    } else {
                        result = openVirtualDustbin(player, page - bPage);
                    }
                }
            }
            break;
        }

        if (result > 0) AotakeSweep.getPlayerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    private static int openVirtualDustbin(@NonNull ServerPlayerEntity player, int page) {
        INamedContainerProvider trashContainer = WorldTrashData.getTrashContainer(player, page);
        if (trashContainer == null) return 0;
        int result = player.openMenu(trashContainer).orElse(0);

        if (result > 0) AotakeSweep.getPlayerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    private static int openDustbinBlock(@NonNull ServerPlayerEntity player, int page) {
        int result = 0;
        List<? extends String> positions = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get();
        if (CollectionUtils.isNotNullOrEmpty(positions) && positions.size() >= page) {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(positions.get(page - 1));

            Direction direction = coordinate.getDirection();
            if (direction == null) direction = Direction.UP;
            // 命中点：方块中心或面上
            Vector3d center = coordinate.toVector3d().add(0.5, 0.5, 0.5);
            Vector3d hitVec = center.add(direction.getStepX() * 0.500001, direction.getStepY() * 0.500001, direction.getStepZ() * 0.500001);

            BlockRayTraceResult ray = new BlockRayTraceResult(hitVec, direction, coordinate.toBlockPos(), false);

            BlockState state = player.getLevel().getBlockState(coordinate.toBlockPos());
            ActionResultType res = state.use(player.getLevel(), player, Hand.MAIN_HAND, ray);
            if (res.consumesAction()) {
                result = 1;
            }
        }

        if (result > 0) AotakeSweep.getPlayerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    private static void addSuggestion(SuggestionsBuilder suggestion, String input, String suggest) {
        if (suggest.contains(input) || StringUtils.isNullOrEmpty(input)) {
            suggestion.suggest(suggest);
        }
    }

    private static void clearVirtualDustbin(int page) {
        List<Inventory> inventories = WorldTrashData.get().getInventoryList();
        if (page == 0) {
            inventories.forEach(Inventory::clearContent);
        } else {
            Inventory inventory = CollectionUtils.getOrDefault(inventories, page - 1, null);
            if (inventory != null) inventory.clearContent();
        }
        WorldTrashData.get().setDirty();
    }

    private static void clearDustbinBlock(int page) {
        if (page == 0) {
            for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
                WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
                if (coordinate != null) {
                    IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            handler.extractItem(i, handler.getSlotLimit(i), false);
                        }
                    }
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().get(page - 1));
            if (coordinate != null) {
                IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        handler.extractItem(i, handler.getSlotLimit(i), false);
                    }
                }
            }
        }
    }

    private static void dropVirtualDustbin(ServerPlayerEntity player, int page) {
        List<Inventory> inventoryList = new ArrayList<>();
        List<Inventory> inventories = WorldTrashData.get().getInventoryList();
        if (page == 0) {
            if (CollectionUtils.isNotNullOrEmpty(inventories)) inventoryList.addAll(inventories);
        } else {
            Inventory inventory = CollectionUtils.getOrDefault(inventories, page - 1, null);
            if (inventory != null) inventoryList.add(inventory);
        }
        inventoryList.forEach(inventory -> inventory.removeAllItems()
                .forEach(item -> {
                    if (!item.isEmpty()) {
                        Entity entity = AotakeUtils.getEntityFromItem(player.getLevel(), item);
                        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.yRot, player.xRot);
                        player.getLevel().addFreshEntity(entity);
                    }
                })
        );
        WorldTrashData.get().setDirty();
    }

    private static void dropDustbinBlock(ServerPlayerEntity player, int page) {
        if (page == 0) {
            for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
                WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
                if (coordinate != null) {
                    IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack stack = handler.extractItem(i, handler.getSlotLimit(i), false);
                            if (!stack.isEmpty()) {
                                Entity entity = AotakeUtils.getEntityFromItem(player.getLevel(), stack);
                                entity.moveTo(player.getX(), player.getY(), player.getZ(), player.yRot, player.xRot);
                                player.getLevel().addFreshEntity(entity);
                            }
                        }
                    }
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().get(page - 1));
            if (coordinate != null) {
                IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.extractItem(i, handler.getSlotLimit(i), false);
                        if (!stack.isEmpty()) {
                            Entity entity = AotakeUtils.getEntityFromItem(player.getLevel(), stack);
                            entity.moveTo(player.getX(), player.getY(), player.getZ(), player.yRot, player.xRot);
                            player.getLevel().addFreshEntity(entity);
                        }
                    }
                }
            }
        }
    }

    public static int getDustbinTotalPage() {
        int result = 0;
        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
            case VIRTUAL: {
                result = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            }
            break;
            case BLOCK: {
                result = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            }
            break;
            case VIRTUAL_BLOCK: {
                result = CommonConfig.DUSTBIN_PAGE_LIMIT.get() + ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
            }
            break;
            case BLOCK_VIRTUAL: {
                result = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size() + CommonConfig.DUSTBIN_PAGE_LIMIT.get();
            }
            break;
        }
        return result;
    }

    private static String getLanguage(CommandSource source) {
        String lang = ServerConfig.DEFAULT_LANGUAGE.get();
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
            try {
                lang = AotakeUtils.getPlayerLanguage(source.getPlayerOrException());
            } catch (Exception ignored) {
            }
        }
        return lang;
    }

    // region config modifier

    /**
     * 硬编码补全提示
     */
    private static final Map<String, List<String>> suggestionCache = new LinkedHashMap<String, List<String>>() {{
        put("base.batch.sweepBatchLimit", Arrays.asList("1", "2", "5", "10"));
        put("base.batch.sweepEntityInterval", Arrays.asList("1", "2", "5", "10"));
        put("base.batch.sweepEntityLimit", Arrays.asList("250", "500", "1000", "2000"));
        put("base.chunk.chunkCheckEntityListMode", Arrays.stream(EnumListType.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.chunk.chunkCheckInterval", Arrays.asList(String.valueOf(1000 * 5), String.valueOf(1000 * 10), String.valueOf(1000 * 60)));
        put("base.chunk.chunkCheckLimit", Arrays.asList("50", "100", "250", "500", "1000", "2000"));
        put("base.chunk.chunkCheckMode", Arrays.stream(EnumChunkCheckMode.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.chunk.chunkCheckRetain", Arrays.asList("0.1", "0.25", "0.5", "0.75"));
        put("base.common.defaultLanguage", I18nUtils.getI18nFiles());
        put("base.dimension.dimensionListMode", Arrays.stream(EnumListType.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.dustbin.cacheLimit", Arrays.asList("2500", "5000", "10000", "50000"));
        put("base.dustbin.dustbinBlockMode", Arrays.stream(EnumDustbinMode.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.dustbin.dustbinOverflowMode", Arrays.stream(EnumOverflowMode.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.dustbin.dustbinPageLimit", Arrays.asList("1", "2", "5", "10"));
        put("base.dustbin.selfCleanInterval", Arrays.asList(String.valueOf(1000 * 60 * 30), String.valueOf(1000 * 60 * 60), String.valueOf(1000 * 60 * 120)));
        put("base.dustbin.selfCleanMode", Arrays.stream(EnumSelfCleanMode.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.safe.safeBlocksEntityLimit", Arrays.asList("50", "100", "250", "500", "1000", "2000"));
        put("base.sweep.entity.entityListMode", Arrays.stream(EnumListType.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.sweep.entity.nbtWhiteBlackListEntityLimit", Arrays.asList("0", "250", "500"));
        put("base.sweep.item.itemListLimit", Arrays.asList("0", "250", "500"));
        put("base.sweep.item.itemListMode", Arrays.stream(EnumListType.values()).map(Enum::name).collect(Collectors.toList()));
        put("base.sweep.item.sweepItemDelay", Arrays.asList("0", "1", "2", "5", "10", "20"));
        put("base.sweep.sweepInterval", Arrays.asList(String.valueOf(1000 * 60 * 5), String.valueOf(1000 * 60 * 10)));
        put("base.sweep.sweepWarningVoiceVolume", Arrays.asList("10", "25", "33", "50", "75", "100"));
        put("permission.permissionCacheClear", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionCacheDrop", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionClearDrop", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionDelaySweep", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionDustbinClear", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionDustbinDrop", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionDustbinOpen", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionDustbinOpenOther", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionSweep", Arrays.asList("0", "1", "2", "3", "4"));
        put("permission.permissionVirtualOp", Arrays.asList("0", "1", "2", "3", "4"));
    }};

    private static void configKeySuggestion(Class<?> configClazz, SuggestionsBuilder builder, String configKey) {
        if (configKey == null) configKey = "";
        configKey = configKey.trim();
        boolean isEmpty = configKey.isEmpty();

        Map<String, ForgeConfigSpec.ConfigValue<?>> map = buildConfigKeyMap(configClazz);
        if (CollectionUtils.isNullOrEmpty(map)) return;

        String lowerInput = configKey.toLowerCase(Locale.ROOT);

        if (isEmpty) {
            for (String key : map.keySet()) {
                builder.suggest(key);
            }
            return;
        }

        if (configKey.indexOf('.') >= 0) {
            String[] inputParts = lowerInput.split("\\.");
            int prefixSegments = inputParts.length - 1;
            String lastInputPart = inputParts[inputParts.length - 1];

            for (String key : map.keySet()) {
                String lowerKey = key.toLowerCase(Locale.ROOT);
                String[] keyParts = lowerKey.split("\\.");

                if (keyParts.length < prefixSegments + 1) {
                    continue;
                }

                boolean prefixMatches = true;
                for (int i = 0; i < prefixSegments; i++) {
                    if (!keyParts[i].equals(inputParts[i])) {
                        prefixMatches = false;
                        break;
                    }
                }
                if (!prefixMatches) continue;

                String lastKeyPart = keyParts[keyParts.length - 1];
                if (lastKeyPart.contains(lastInputPart)) {
                    builder.suggest(key);
                }
            }
        } else {
            for (String key : map.keySet()) {
                if (key.toLowerCase(Locale.ROOT).contains(lowerInput)) {
                    builder.suggest(key);
                }
            }
        }

    }

    @SuppressWarnings("rawtypes")
    private static void configValueSuggestion(Class<?> configClazz, SuggestionsBuilder builder, String configKey) {
        ForgeConfigSpec.ConfigValue<?> cv = findConfigValueByKey(configClazz, configKey);
        if (cv == null) return;
        else builder.suggest(String.valueOf(cv.get()));

        Class<?> type = getConfigValueType(cv);
        if (type == Boolean.class || type == boolean.class) {
            builder.suggest("true").suggest("false");
            return;
        }
        if (Enum.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) type;
            for (Object c : enumClass.getEnumConstants()) {
                builder.suggest(((Enum<?>) c).name());
            }
        }
        String path = getConfigValuePath(cv);
        if (suggestionCache.containsKey(path)) {
            suggestionCache.get(path).forEach(builder::suggest);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int executeModifyConfig(Class<?> configClazz, CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        String lang = getLanguage(source);
        String configKey = StringArgumentType.getString(context, "configKey");
        String configValue = StringArgumentType.getString(context, "configValue");

        ForgeConfigSpec.ConfigValue<?> cv = findConfigValueByKey(configClazz, configKey);
        if (cv == null) {
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_key_absent", configKey);
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        Class<?> type = getConfigValueType(cv);
        Object parsed;
        try {
            parsed = parseStringToType(configValue, type);
        } catch (Exception e) {
            LOGGER.error(e);
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_parse_error", configValue, e.getMessage());
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        if (validateConfigValueWithSpec(cv, parsed)) {
            ((ForgeConfigSpec.ConfigValue) cv).set(parsed);
        } else {
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_set_error", configKey, configValue);
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        tryApplyServerConfigBake(configClazz);

        Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_set_success", configKey, parsed);
        source.sendSuccess(component.toChatComponent(lang), true);

        return 1;
    }


    private static final Map<Class<?>, List<Field>> allConfigValueFieldsCache = new HashMap<>();

    private static List<Field> getAllConfigValueFields(Class<?> configClazz) {
        return allConfigValueFieldsCache.computeIfAbsent(configClazz, (k) -> {
            List<Field> out = new ArrayList<>();
            for (Field f : k.getDeclaredFields()) {
                try {
                    if (ForgeConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        out.add(f);
                    }
                } catch (Throwable ignored) {
                }
            }
            return out;
        });
    }

    public static final Map<Class<?>, Map<String, ForgeConfigSpec.ConfigValue<?>>> configKeyMapCache = new HashMap<>();

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> buildConfigKeyMap(Class<?> configClazz) {
        return configKeyMapCache.computeIfAbsent(configClazz, (k) -> {
            Map<String, ForgeConfigSpec.ConfigValue<?>> map = new LinkedHashMap<>();
            for (Field f : getAllConfigValueFields(k)) {
                try {
                    Object raw = f.get(null);
                    if (raw instanceof ForgeConfigSpec.ConfigValue) {
                        ForgeConfigSpec.ConfigValue<?> cv = (ForgeConfigSpec.ConfigValue<?>) raw;
                        String path = getConfigValuePath(cv);
                        if (path != null) {
                            map.put(path, cv);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return map;
        });
    }

    private static String getConfigValuePath(ForgeConfigSpec.ConfigValue<?> cv) {
        return cv.getPath().stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    private static ForgeConfigSpec.ConfigValue<?> findConfigValueByKey(Class<?> configClazz, String key) {
        if (key == null) return null;
        Map<String, ForgeConfigSpec.ConfigValue<?>> map = buildConfigKeyMap(configClazz);

        if (map.containsKey(key)) return map.get(key);

        List<String> matches = map.keySet().stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        if (matches.size() == 1) return map.get(matches.get(0));

        return null;
    }

    private static Class<?> getConfigValueType(ForgeConfigSpec.ConfigValue<?> cv) {
        try {
            Object cur = cv.get();
            if (cur != null) return cur.getClass();
        } catch (Throwable ignored) {
        }
        return Object.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseStringToType(String parsedStr, Class<?> targetType) throws IllegalArgumentException {
        if (targetType == Boolean.class || targetType == boolean.class) {
            return StringUtils.stringToBoolean(parsedStr);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return StringUtils.toInt(parsedStr);
        }
        if (targetType == Long.class || targetType == long.class) {
            return StringUtils.toLong(parsedStr);
        }
        if (targetType == Double.class || targetType == double.class) {
            return StringUtils.toDouble(parsedStr);
        }
        if (Enum.class.isAssignableFrom(targetType)) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) targetType;
            for (Object c : enumClass.getEnumConstants()) {
                if (c.toString().equalsIgnoreCase(parsedStr) || ((Enum<?>) c).name().equalsIgnoreCase(parsedStr)) {
                    return Enum.valueOf(enumClass, ((Enum<?>) c).name());
                }
            }
            throw new IllegalArgumentException("Unknown enum constant: " + parsedStr);
        }
        if (targetType == String.class) {
            return parsedStr;
        }
        if (List.class.isAssignableFrom(targetType)) {
            String[] parts = parsedStr.split(",");
            return Arrays.stream(parts).map(String::trim).collect(Collectors.toList());
        }
        return parsedStr;
    }

    @SuppressWarnings("all")
    private static void tryApplyServerConfigBake(Class<?> configClazz) {
        try {
            Method m = configClazz.getDeclaredMethod("bake");
            if (m != null) {
                m.setAccessible(true);
                m.invoke(null);
                return;
            }
        } catch (Throwable ignored) {
        }

        String[] candidate = new String[]{"save", "sync", "write", "apply"};
        for (String name : candidate) {
            try {
                Method m = configClazz.getDeclaredMethod(name);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(null);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final Map<String, ForgeConfigSpec.ValueSpec> validateCache = new HashMap<>();

    public static boolean validateConfigValueWithSpec(ForgeConfigSpec.ConfigValue<?> cv, Object parsedValue) {
        if (cv == null) return false;
        String path = getConfigValuePath(cv);
        ForgeConfigSpec.ValueSpec vs = validateCache.computeIfAbsent(path, (k) -> {
            try {
                ForgeConfigSpec spec = null;
                for (String candidate : FieldUtils.getPrivateFieldNames(ForgeConfigSpec.ConfigValue.class, ForgeConfigSpec.class)) {
                    Object value = FieldUtils.getPrivateFieldValue(ForgeConfigSpec.ConfigValue.class, cv, candidate);
                    if (value != null) {
                        spec = (ForgeConfigSpec) value;
                        break;
                    }
                }
                if (spec != null) {
                    Map<String, Object> valueMap = spec.valueMap();
                    String[] split = k.split("\\.");
                    for (String s : Arrays.copyOfRange(split, 0, split.length - 1)) {
                        Object o = valueMap.get(s);
                        if (o instanceof Config) {
                            valueMap = ((Config) o).valueMap();
                        } else {
                            return null;
                        }
                    }
                    if (valueMap.containsKey(CollectionUtils.getLast(split))) {
                        Object o = valueMap.get(CollectionUtils.getLast(split));
                        if (o instanceof ForgeConfigSpec.ValueSpec) {
                            return (ForgeConfigSpec.ValueSpec) o;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        });
        return vs != null && vs.test(parsedValue);
    }

    // endregion config modifier

}
