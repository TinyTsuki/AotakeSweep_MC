package xin.vanilla.aotake.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.packet.CustomConfigSyncToClient;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("resource")
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
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 刷新帮助信息
        refreshHelpMessage();

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
        SuggestionProvider<CommandSourceStack> helpSuggestions = (context, builder) -> {
            String input = CommandUtils.getStringEmpty(context, "command");
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
                    .toList()) {
                builder.suggest(type.name());
            }
            return builder.buildFuture();
        };


        Command<CommandSourceStack> languageCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
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
        Command<CommandSourceStack> virtualOpCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            CommandSourceStack source = context.getSource();
            // 如果命令来自玩家
            if (source.getEntity() == null || source.getEntity() instanceof ServerPlayer) {
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
                List<ServerPlayer> targetList = new ArrayList<>();
                try {
                    targetList.addAll(EntityArgument.getPlayers(context, "player"));
                } catch (IllegalArgumentException ignored) {
                }
                String language = CommandUtils.getLanguage(source);
                for (ServerPlayer target : targetList) {
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
                    if (source.getEntity() != null && source.getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = source.getPlayerOrException();
                        if (!target.getStringUUID().equalsIgnoreCase(player.getStringUUID())) {
                            AotakeUtils.sendTranslatableMessage(player, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                        }
                    } else {
                        source.sendSuccess(() -> Component.translatable(language, EnumI18nType.MESSAGE, "player_virtual_op", target.getDisplayName().getString(), permissions).toChatComponent(), true);
                    }
                    // 更新权限信息
                    source.getServer().getPlayerList().sendPlayerPermissionLevel(target);
                    for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                        if (AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(player))) {
                            AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
                        }
                    }
                }
            }
            return 1;
        };
        Command<CommandSourceStack> openDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);

            int totalPage = AotakeUtils.getDustbinTotalPage();
            if (totalPage <= 0) {
                AotakeUtils.sendTranslatableMessage(context.getSource(), false, I18nUtils.getKey(EnumI18nType.MESSAGE, "dustbin_page_empty"));
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
        Command<CommandSourceStack> sweepCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = StringUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerLevel dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
            List<Entity> entities;
            if (range > 0) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                entities = new ArrayList<>(player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
            } else if (dimension != null) {
                entities = AotakeUtils.getAllEntities().stream()
                        .filter(entity -> entity.level() == dimension)
                        .collect(Collectors.toList());
            } else {
                entities = AotakeUtils.getAllEntities();
            }

            AotakeScheduler.schedule(context.getSource().getServer(), 1, () -> AotakeUtils.sweep(entities, false));
            return 1;
        };
        Command<CommandSourceStack> clearDropCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = StringUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerLevel dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
            boolean withEntity = CommandUtils.getBooleanDefault(context, "withEntity", false);
            boolean ignoreFilter = CommandUtils.getBooleanDefault(context, "ignoreFilter", false);

            List<Entity> entities;
            if (range > 0) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                entities = new ArrayList<>(player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
            } else if (dimension != null) {
                entities = AotakeUtils.getAllEntities().stream()
                        .filter(entity -> entity.level() == dimension)
                        .collect(Collectors.toList());
            } else {
                entities = AotakeUtils.getAllEntities();
            }
            entities = entities.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> !(entity instanceof Player))
                    .filter(entity -> withEntity || entity instanceof ItemEntity)
                    .collect(Collectors.toList());
            AotakeUtils.sweep(entities, ignoreFilter);
            return 1;
        };
        Command<CommandSourceStack> clearDustbinCommand = context -> {
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
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        Command<CommandSourceStack> dropDustbinCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);

            ServerPlayer player = context.getSource().getPlayerOrException();
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
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        Command<CommandSourceStack> clearCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_cleared"
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        Command<CommandSourceStack> dropCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            boolean originalPos = CommandUtils.getBooleanDefault(context, "originalPos", false);
            ServerPlayer player = context.getSource().getPlayerOrException();
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
                    ServerLevel level = AotakeUtils.getWorld(coordinate.getDimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.getValue());
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_dropped"
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };
        Command<CommandSourceStack> delaySweepCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            Date current = new Date();
            long delay = CommandUtils.getLongDefault(context, "seconds", ServerConfig.SWEEP_INTERVAL.get() / 1000);
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
                    , context.getSource().getEntity() instanceof ServerPlayer
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


        LiteralArgumentBuilder<CommandSourceStack> language = // region language
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
        LiteralArgumentBuilder<CommandSourceStack> virtualOp = // region virtualOp
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
                                                    String input = CommandUtils.getStringEmpty(context, "rules").replace(" ", ",");
                                                    String[] split = input.split(",");
                                                    String current = input.endsWith(",") ? "" : split[split.length - 1];
                                                    for (EnumCommandType value : Arrays.stream(EnumCommandType.values())
                                                            .filter(EnumCommandType::isOp)
                                                            .filter(type -> Arrays.stream(split).noneMatch(in -> in.equalsIgnoreCase(type.name())))
                                                            .filter(type -> StringUtils.isNullOrEmptyEx(current) || type.name().toLowerCase().contains(current.toLowerCase()))
                                                            .sorted(Comparator.comparing(EnumCommandType::getSort))
                                                            .toList()) {
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
        LiteralArgumentBuilder<CommandSourceStack> openDustbin = // region openDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_OPEN.get())
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
                        ); // endregion openDustbin
        LiteralArgumentBuilder<CommandSourceStack> sweep = // region sweep
                Commands.literal(CommonConfig.COMMAND_SWEEP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.SWEEP))
                        .executes(sweepCommand)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(sweepCommand)
                        )
                        .then(Commands.argument("range", IntegerArgumentType.integer(0))
                                .executes(sweepCommand)
                        ); // endregion sweep
        LiteralArgumentBuilder<CommandSourceStack> clearDrop = // region killItems
                Commands.literal(CommonConfig.COMMAND_CLEAR_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CLEAR_DROP))
                        .executes(clearDropCommand)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(clearDropCommand)
                                .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                        .executes(clearDropCommand)
                                        .then(Commands.argument("ignoreFilter", BoolArgumentType.bool())
                                                .executes(clearDropCommand)
                                        )
                                )
                        )
                        .then(Commands.argument("range", IntegerArgumentType.integer(0))
                                .executes(clearDropCommand)
                                .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                        .executes(clearDropCommand)
                                        .then(Commands.argument("ignoreFilter", BoolArgumentType.bool())
                                                .executes(clearDropCommand)
                                        )
                                )
                        ); // endregion killItems
        LiteralArgumentBuilder<CommandSourceStack> clearDustbin = // region clearDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_CLEAR.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_CLEAR))
                        .executes(clearDustbinCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> {
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
                                })
                                .executes(clearDustbinCommand)
                        ); // endregion clearDustbin
        LiteralArgumentBuilder<CommandSourceStack> dropDustbin = // region dropDustbin
                Commands.literal(CommonConfig.COMMAND_DUSTBIN_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DUSTBIN_DROP))
                        .executes(dropDustbinCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> {
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
                                })
                                .executes(dropDustbinCommand)
                        ); // endregion dropDustbin
        LiteralArgumentBuilder<CommandSourceStack> clearCache = // region clearCache
                Commands.literal(CommonConfig.COMMAND_CACHE_CLEAR.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_CLEAR))
                        .executes(clearCacheCommand); // endregion clearCache
        LiteralArgumentBuilder<CommandSourceStack> dropCache = // region dropCache
                Commands.literal(CommonConfig.COMMAND_CACHE_DROP.get())
                        .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_DROP))
                        .executes(dropCacheCommand)
                        .then(Commands.argument("originalPos", BoolArgumentType.bool())
                                .executes(dropCacheCommand)
                        ); // endregion dropCache
        LiteralArgumentBuilder<CommandSourceStack> delaySweep = // region delaySweep
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
                                                CommandSourceStack source = context.getSource();
                                                String lang = CommandUtils.getLanguage(source);
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
                                                source.sendSuccess(() -> component.toChatComponent(lang), false);

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
                            // 临时禁用
                            .then(Commands.literal("disable")
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
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
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
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
                                    .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
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
                            // region 玩家设置
                            // 显示打扫结果信息
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

}
