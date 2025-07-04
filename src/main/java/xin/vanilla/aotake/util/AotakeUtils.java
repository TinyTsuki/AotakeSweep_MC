package xin.vanilla.aotake.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepDataCapability;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.network.ModNetworkHandler;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AotakeUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    // region 指令相关

    /**
     * 获取指令前缀
     */
    public static String getCommandPrefix() {
        String commandPrefix = CommonConfig.COMMAND_PREFIX.get();
        if (StringUtils.isNullOrEmptyEx(commandPrefix) || !commandPrefix.matches("^(\\w ?)+$")) {
            CommonConfig.COMMAND_PREFIX.set(AotakeSweep.DEFAULT_COMMAND_PREFIX);
        }
        return CommonConfig.COMMAND_PREFIX.get().trim();
    }

    /**
     * 获取完整的指令
     */
    public static String getCommand(EnumCommandType type) {
        String prefix = AotakeUtils.getCommandPrefix();
        return switch (type) {
            case HELP -> prefix + " help";
            case LANGUAGE -> prefix + " " + CommonConfig.COMMAND_LANGUAGE.get();
            case LANGUAGE_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_LANGUAGE.get() : "";
            case VIRTUAL_OP -> prefix + " " + CommonConfig.COMMAND_VIRTUAL_OP.get();
            case VIRTUAL_OP_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_VIRTUAL_OP.get() : "";
            case DUSTBIN_OPEN -> prefix + " " + CommonConfig.COMMAND_DUSTBIN_OPEN.get();
            case DUSTBIN_OPEN_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_OPEN.get() : "";
            case DUSTBIN_CLEAR -> prefix + " " + CommonConfig.COMMAND_DUSTBIN_CLEAR.get();
            case DUSTBIN_CLEAR_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_CLEAR.get() : "";
            case DUSTBIN_DROP -> prefix + " " + CommonConfig.COMMAND_DUSTBIN_DROP.get();
            case DUSTBIN_DROP_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_DROP.get() : "";
            case CACHE_CLEAR -> prefix + " " + CommonConfig.COMMAND_CACHE_CLEAR.get();
            case CACHE_CLEAR_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_CACHE_CLEAR.get() : "";
            case CACHE_DROP -> prefix + " " + CommonConfig.COMMAND_CACHE_DROP.get();
            case CACHE_DROP_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_CACHE_DROP.get() : "";
            case SWEEP -> prefix + " " + CommonConfig.COMMAND_SWEEP.get();
            case SWEEP_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_SWEEP.get() : "";
            case CLEAR_DROP -> prefix + " " + CommonConfig.COMMAND_CLEAR_DROP.get();
            case CLEAR_DROP_CONCISE -> isConciseEnabled(type) ? CommonConfig.COMMAND_CLEAR_DROP.get() : "";
            default -> "";
        };
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        return switch (type) {
            case VIRTUAL_OP, VIRTUAL_OP_CONCISE -> ServerConfig.PERMISSION_VIRTUAL_OP.get();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE -> ServerConfig.PERMISSION_DUSTBIN_OPEN.get();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> ServerConfig.PERMISSION_DUSTBIN_CLEAR.get();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> ServerConfig.PERMISSION_DUSTBIN_DROP.get();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> ServerConfig.PERMISSION_CACHE_CLEAR.get();
            case CACHE_DROP, CACHE_DROP_CONCISE -> ServerConfig.PERMISSION_CACHE_DROP.get();
            case SWEEP, SWEEP_CONCISE -> ServerConfig.PERMISSION_SWEEP.get();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> ServerConfig.PERMISSION_CLEAR_DROP.get();
            default -> 0;
        };
    }

    /**
     * 判断指令是否启用简短模式
     */
    public static boolean isConciseEnabled(EnumCommandType type) {
        return switch (type) {
            case LANGUAGE, LANGUAGE_CONCISE -> CommonConfig.CONCISE_LANGUAGE.get();
            case VIRTUAL_OP, VIRTUAL_OP_CONCISE -> CommonConfig.CONCISE_VIRTUAL_OP.get();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE -> CommonConfig.CONCISE_DUSTBIN_OPEN.get();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> CommonConfig.CONCISE_DUSTBIN_CLEAR.get();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> CommonConfig.CONCISE_DUSTBIN_DROP.get();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> CommonConfig.CONCISE_CACHE_CLEAR.get();
            case CACHE_DROP, CACHE_DROP_CONCISE -> CommonConfig.CONCISE_CACHE_DROP.get();
            case SWEEP, SWEEP_CONCISE -> CommonConfig.CONCISE_SWEEP.get();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> CommonConfig.CONCISE_CLEAR_DROP.get();
            default -> false;
        };
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(CommandSourceStack source, EnumCommandType type) {
        return source.hasPermission(getCommandPermissionLevel(type)) || hasVirtualPermission(source.getEntity(), type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(Player player, EnumCommandType type) {
        return player.hasPermissions(getCommandPermissionLevel(type)) || hasVirtualPermission(player, type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasVirtualPermission(Entity source, EnumCommandType type) {
        // 若为玩家
        if (source instanceof Player) {
            return VirtualPermissionManager.getVirtualPermission((Player) source).stream()
                    .filter(Objects::nonNull)
                    .anyMatch(s -> s.replaceConcise() == type.replaceConcise());
        } else {
            return false;
        }
    }

    // endregion 指令相关


    // region 消息相关

    /**
     * 广播消息
     *
     * @param player  发送者
     * @param message 消息
     */
    public static void broadcastMessage(ServerPlayer player, Component message) {
        player.server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable("chat.type.announcement", player.getDisplayName(), message.toChatComponent()), false);
    }

    /**
     * 广播消息
     *
     * @param server  发送者
     * @param message 消息
     */
    public static void broadcastMessage(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable("chat.type.announcement", "Server", message.toChatComponent()), false);
    }

    /**
     * 发送消息至所有玩家
     */
    public static void sendMessageToAll(Component message) {
        for (ServerPlayer player : AotakeSweep.getServerInstance().getPlayerList().getPlayers()) {
            sendMessage(player, message);
        }
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(Player player, Component message) {
        player.sendSystemMessage(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)));
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(Player player, String message) {
        player.sendSystemMessage(Component.literal(message).toChatComponent());
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(Player player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).setLanguageCode(AotakeUtils.getPlayerLanguage(player)).toChatComponent());
    }

    /**
     * 发送翻译消息
     *
     * @param source  指令来源
     * @param success 是否成功
     * @param key     翻译键
     * @param args    参数
     */
    public static void sendTranslatableMessage(CommandSourceStack source, boolean success, String key, Object... args) {
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayer) {
            try {
                sendTranslatableMessage(source.getPlayerOrException(), key, args);
            } catch (CommandSyntaxException ignored) {
            }
        } else if (success) {
            source.sendSuccess(Component.translatable(key, args).setLanguageCode(ServerConfig.DEFAULT_LANGUAGE.get()).toChatComponent(), false);
        } else {
            source.sendFailure(Component.translatable(key, args).setLanguageCode(ServerConfig.DEFAULT_LANGUAGE.get()).toChatComponent());
        }
    }

    /**
     * 发送操作栏消息至所有玩家
     */
    public static void sendActionBarMessageToAll(Component message) {
        for (ServerPlayer player : AotakeSweep.getServerInstance().getPlayerList().getPlayers()) {
            sendActionBarMessage(player, message);
        }
    }

    /**
     * 发送操作栏消息
     */
    public static void sendActionBarMessage(ServerPlayer player, Component message) {
        player.displayClientMessage(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)), true);
    }

    /**
     * 广播数据包至所有玩家
     *
     * @param packet 数据包
     */
    public static void broadcastPacket(Packet<?> packet) {
        AotakeSweep.getServerInstance().getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
    }

    /**
     * 发送数据包至服务器
     */
    public static <MSG> void sendPacketToServer(MSG msg) {
        ModNetworkHandler.INSTANCE.sendToServer(msg);
    }

    /**
     * 发送数据包至玩家
     */
    public static <MSG> void sendPacketToPlayer(MSG msg, ServerPlayer player) {
        ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    // endregion 消息相关


    // region 玩家语言相关

    public static String getPlayerLanguage(@NonNull Player player) {
        try {
            String language;
            if (player.isLocalPlayer()) {
                language = CustomConfig.getPlayerLanguageClient(getPlayerUUIDString(player));
            } else {
                language = CustomConfig.getPlayerLanguage(getPlayerUUIDString(player));
            }
            return AotakeUtils.getValidLanguage(player, language);
        } catch (IllegalArgumentException i) {
            return ServerConfig.DEFAULT_LANGUAGE.get();
        }
    }

    public static String getValidLanguage(@Nullable Player player, @Nullable String language) {
        String result;
        if (StringUtils.isNullOrEmptyEx(language) || "client".equalsIgnoreCase(language)) {
            if (player instanceof ServerPlayer) {
                result = AotakeUtils.getServerPlayerLanguage((ServerPlayer) player);
            } else {
                result = AotakeUtils.getClientLanguage();
            }
        } else if ("server".equalsIgnoreCase(language)) {
            result = ServerConfig.DEFAULT_LANGUAGE.get();
        } else {
            result = language;
        }
        return result;
    }

    public static String getServerPlayerLanguage(ServerPlayer player) {
        return player.getLanguage();
    }

    /**
     * 复制玩家语言设置
     *
     * @param originalPlayer 原始玩家
     * @param targetPlayer   目标玩家
     */
    public static void clonePlayerLanguage(ServerPlayer originalPlayer, ServerPlayer targetPlayer) {
        FieldUtils.setPrivateFieldValue(ServerPlayer.class, targetPlayer, FieldUtils.getPlayerLanguageFieldName(originalPlayer), getServerPlayerLanguage(originalPlayer));
    }

    public static String getClientLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    // endregion 玩家语言相关


    // region 扫地

    private static List<BlockState> SAFE_BLOCKS_STATE;
    private static List<String> SAFE_BLOCKS;
    private static List<BlockState> SAFE_BLOCKS_BELOW_STATE;
    private static List<String> SAFE_BLOCKS_BELOW;
    private static List<BlockState> SAFE_BLOCKS_ABOVE_STATE;
    private static List<String> SAFE_BLOCKS_ABOVE;

    private static void initSafeBlocks() {
        if (SAFE_BLOCKS_STATE == null) {
            SAFE_BLOCKS_STATE = CommonConfig.SAFE_BLOCKS.get().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS == null) {
            SAFE_BLOCKS = CommonConfig.SAFE_BLOCKS.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW_STATE == null) {
            SAFE_BLOCKS_BELOW_STATE = CommonConfig.SAFE_BLOCKS_BELOW.get().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW == null) {
            SAFE_BLOCKS_BELOW = CommonConfig.SAFE_BLOCKS_BELOW.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE_STATE == null) {
            SAFE_BLOCKS_ABOVE_STATE = CommonConfig.SAFE_BLOCKS_ABOVE.get().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE == null) {
            SAFE_BLOCKS_ABOVE = CommonConfig.SAFE_BLOCKS_ABOVE.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList();
        }
    }

    public static List<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        AotakeSweep.getServerInstance().getAllLevels()
                .forEach(level -> level.getEntities().getAll().forEach(entities::add)
                );
        return entities;
    }

    public static List<Entity> getAllEntitiesByFilter(List<Entity> entities) {
        if (CollectionUtils.isNullOrEmpty(entities)) {
            entities = getAllEntities();
        }
        initSafeBlocks();
        List<Entity> filtered = entities.stream()
                // 物品实体 与 垃圾实体
                .filter(entity -> entity instanceof ItemEntity
                        || ServerConfig.JUNK_ENTITY.get().contains(getEntityTypeRegistryName(entity))
                )
                .filter(entity -> !entity.hasCustomName())
                .filter(entity -> !(entity instanceof TamableAnimal) || ((TamableAnimal) entity).getOwnerUUID() == null)
                .toList();

        Map<KeyValue<Level, BlockPos>, BlockState> blockStateCache = filtered.stream()
                .flatMap(entity -> Stream.of(
                        new KeyValue<>(entity.level, entity.blockPosition().above()),
                        new KeyValue<>(entity.level, entity.blockPosition()),
                        new KeyValue<>(entity.level, entity.blockPosition().below())
                ))
                .distinct()
                .collect(Collectors.toMap(Function.identity()
                        , pair -> pair.getKey().getBlockState(pair.getValue())
                ));

        // 超过阈值的黑白名单物品
        List<Entity> exceededWhiteBlackList = filtered.stream()
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .filter(item -> (!ServerConfig.ITEM_WHITELIST.get().isEmpty()
                        && ServerConfig.ITEM_WHITELIST.get().contains(getItemRegistryName(item.getItem())))
                        || (!ServerConfig.ITEM_BLACKLIST.get().isEmpty()
                        && !ServerConfig.ITEM_BLACKLIST.get().contains(getItemRegistryName(item.getItem())))
                )
                .collect(Collectors.groupingBy(item -> getItemRegistryName(item.getItem()), Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > ServerConfig.ITEM_WHITE_BLACK_LIST_ENTITY_LIMIT.get())
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());

        // 超过阈值的安全方块实体
        List<Entity> exceededSafeList = filtered.stream()
                .filter(entity -> {
                    Level level = entity.level;
                    BlockState state = blockStateCache.get(new KeyValue<>(level, entity.blockPosition()));
                    BlockState below = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().below()));
                    BlockState above = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().above()));

                    boolean isUnsafe = state != null
                            && !SAFE_BLOCKS.contains(AotakeUtils.getBlockRegistryName(state))
                            && !SAFE_BLOCKS_STATE.contains(state)
                            && below != null
                            && !SAFE_BLOCKS_BELOW.contains(AotakeUtils.getBlockRegistryName(below))
                            && !SAFE_BLOCKS_BELOW_STATE.contains(below)
                            && above != null
                            && !SAFE_BLOCKS_ABOVE.contains(AotakeUtils.getBlockRegistryName(above))
                            && !SAFE_BLOCKS_ABOVE_STATE.contains(above);

                    return !isUnsafe;
                })
                .collect(Collectors.groupingBy(entity -> {
                    String dimension = entity.level.dimension().location().toString();
                    int chunkX = entity.blockPosition().getX() / 16;
                    int chunkZ = entity.blockPosition().getZ() / 16;
                    return dimension + "," + chunkX + "," + chunkZ;
                }, Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > CommonConfig.SAFE_BLOCKS_ENTITY_LIMIT.get())
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        // 过滤
        return filtered.stream().filter(entity -> {
            boolean isItem = entity instanceof ItemEntity;
            String itemName = isItem ? getItemRegistryName(((ItemEntity) entity).getItem()) : null;

            // 掉落时间过滤
            if (isItem && ServerConfig.SWEEP_ITEM_AGE.get() > 0) {
                if (entity.level.isAreaLoaded(entity.blockPosition(), 0)
                        && entity.tickCount < ServerConfig.SWEEP_ITEM_AGE.get()
                        && entity.tickCount > 0
                ) {
                    return false;
                }
            }

            // 白名单过滤
            if (isItem && !ServerConfig.ITEM_WHITELIST.get().isEmpty()) {
                if (ServerConfig.ITEM_WHITELIST.get().contains(itemName) &&
                        !exceededWhiteBlackList.contains(entity)
                ) {
                    return false;
                }
            }

            // 黑名单过滤
            if (isItem && !ServerConfig.ITEM_BLACKLIST.get().isEmpty()) {
                if (!ServerConfig.ITEM_BLACKLIST.get().contains(itemName) &&
                        !exceededWhiteBlackList.contains(entity)
                ) {
                    return false;
                }
            }

            // 安全方块过滤
            Level level = entity.level;
            BlockState state = blockStateCache.get(new KeyValue<>(level, entity.blockPosition()));
            BlockState below = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().below()));
            BlockState above = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().above()));

            boolean unsafe =
                    !SAFE_BLOCKS.contains(AotakeUtils.getBlockRegistryName(state)) &&
                            !SAFE_BLOCKS_STATE.contains(state) &&
                            !SAFE_BLOCKS_BELOW.contains(AotakeUtils.getBlockRegistryName(below)) &&
                            !SAFE_BLOCKS_BELOW_STATE.contains(below) &&
                            !SAFE_BLOCKS_ABOVE.contains(AotakeUtils.getBlockRegistryName(above)) &&
                            !SAFE_BLOCKS_ABOVE_STATE.contains(above);

            return unsafe || exceededSafeList.contains(entity);
        }).collect(Collectors.toList());
    }

    public static void sweep() {
        List<Entity> entities = getAllEntities();
        AotakeUtils.sweep(null, entities);
    }

    public static void sweep(@Nullable ServerPlayer player, List<Entity> entities) {
        List<ServerPlayer> players = AotakeSweep.getServerInstance().getPlayerList().getPlayers();
        // 若服务器没有玩家
        if (CollectionUtils.isNullOrEmpty(players) && !CommonConfig.SWEEP_WHEN_NO_PLAYER.get()) return;

        List<Entity> list = getAllEntitiesByFilter(entities);

        SweepResult result = null;
        if (CollectionUtils.isNotNullOrEmpty(list)) {
            // 清空旧的物品
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_CLEAR.name())) {
                WorldTrashData.get().getDropList().clear();
                WorldTrashData.get().getInventoryList().forEach(SimpleContainer::clearContent);
            }
            result = AotakeSweep.getEntitySweeper().addDrops(list);
        }

        for (ServerPlayer p : players) {
            Component msg = getWarningMessage(result == null || result.isEmpty() ? -1 : 0
                    , getPlayerLanguage(p)
                    , result);
            if (PlayerSweepDataCapability.getData(p).isShowSweepResult()) {
                String openCom = "/" + AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN);
                msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, openCom))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                , Component.literal(openCom).toTextComponent())
                        );
                AotakeUtils.sendMessage(p, Component.empty()
                        .append(msg)
                        .append(Component.translatable(EnumI18nType.MESSAGE, "not_show_button")
                                .setColor(EnumMCColor.RED.getColor())
                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                        , "/" + AotakeUtils.getCommandPrefix() + " config showSweepResult false")
                                )
                        )
                );
            } else {
                AotakeUtils.sendActionBarMessage(p, msg);
            }
        }

    }

    /**
     * 移除实体
     */
    public static void removeEntity(Entity entity, boolean keepData) {
        EntitySweeper.scheduleRemoveEntity(entity, keepData);
    }

    /**
     * 将物品转为实体
     */
    public static Entity getEntityFromItem(ServerLevel level, ItemStack itemStack) {
        Entity result = null;

        CompoundTag tag = itemStack.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
            if (aotake.contains("entity")) {
                try {
                    result = EntityType.loadEntityRecursive(aotake.getCompound("entity"), level, e -> e);
                } catch (Exception e) {
                    LOGGER.error("Failed to load entity from item stack: {}", itemStack, e);
                }
            }
        }
        if (result == null) {
            result = new ItemEntity(level, 0, 0, 0, itemStack);
            ((ItemEntity) result).setDefaultPickUpDelay();
        }
        return result;
    }

    public static Component getWarningMessage(int index, String lang, @Nullable SweepResult result) {
        Component msg = null;
        List<? extends String> warns = CommonConfig.SWEEP_WARNING_CONTENT.get();
        try {
            String text = warns.get(CommonConfig.SWEEP_WARNING_SECOND.get().indexOf(index));
            if (result != null) {
                text = text.replaceAll("\\[itemCount]", String.valueOf(result.getItemCount()))
                        .replaceAll("\\[entityCount]", String.valueOf(result.getEntityCount()))
                        .replaceAll("\\[recycledItemCount]", String.valueOf(result.getRecycledItemCount()))
                        .replaceAll("\\[recycledEntityCount]", String.valueOf(result.getRecycledEntityCount()));
            }
            msg = Component.literal(text);
            msg.appendArg((Object) index);
        } catch (Exception ignored) {
        }
        if (msg == null) {
            if (index > 0) {
                msg = Component.translatable(EnumI18nType.MESSAGE, "cleanup_will_start", index);
            } else if (index == 0) {
                String text = I18nUtils.getTranslation(EnumI18nType.MESSAGE, "cleanup_started", lang);
                if (result != null) {
                    text = text.replaceAll("\\[itemCount]", String.valueOf(result.getItemCount()))
                            .replaceAll("\\[entityCount]", String.valueOf(result.getEntityCount()))
                            .replaceAll("\\[recycledItemCount]", String.valueOf(result.getRecycledItemCount()))
                            .replaceAll("\\[recycledEntityCount]", String.valueOf(result.getRecycledEntityCount()));
                }
                msg = Component.literal(text);
            } else {
                msg = Component.empty();
            }
        }
        return msg;
    }

    // endregion 扫地


    // region 杂项

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayer player, @NonNull String command) {
        boolean result = false;
        try {
            result = player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command) > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result;
    }

    /**
     * 获取指定维度的世界实例
     */
    public static ServerLevel getWorld(ResourceKey<Level> dimension) {
        return AotakeSweep.getServerInstance().getLevel(dimension);
    }

    /**
     * 播放音效
     *
     * @param player 玩家
     * @param sound  音效
     * @param volume 音量
     * @param pitch  音调
     */
    public static void playSound(ServerPlayer player, ResourceLocation sound, float volume, float pitch) {
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(sound);
        if (soundEvent != null) {
            player.playNotifySound(soundEvent, SoundSource.PLAYERS, volume, pitch);
        }
    }

    /**
     * 序列化方块默认状态
     */
    public static String serializeBlockState(Block block) {
        return serializeBlockState(block.defaultBlockState());
    }

    /**
     * 序列化方块状态
     */
    public static String serializeBlockState(BlockState blockState) {
        return BlockStateParser.serialize(blockState);
    }

    /**
     * 反序列化方块状态
     */
    public static BlockState deserializeBlockState(String block) {
        try {
            return BlockStateParser.parseForBlock(AotakeSweep.getServerInstance().getAllLevels().iterator().next().holderLookup(Registries.BLOCK)
                    , new StringReader(block), false).blockState();
        } catch (Exception e) {
            LOGGER.error("Invalid unsafe block: {}", block, e);
            return null;
        }
    }

    /**
     * 获取方块注册ID
     */
    @NonNull
    public static String getBlockRegistryName(@NonNull BlockState blockState) {
        return getBlockRegistryName(blockState.getBlock());
    }

    /**
     * 获取方块注册ID
     */
    @NonNull
    public static String getBlockRegistryName(Block block) {
        Optional<ResourceKey<Block>> key = block.defaultBlockState().getBlockHolder().unwrapKey();
        return key.map(blockResourceKey -> blockResourceKey.location().toString()).orElse("");
    }

    /**
     * 反序列化ItemStack
     */
    public static ItemStack deserializeItemStack(@NonNull String item) {
        ItemStack itemStack;
        try {
            itemStack = ItemStack.of(TagParser.parseTag(item));
        } catch (Exception e) {
            itemStack = null;
            LOGGER.error("Invalid unsafe item: {}", item, e);
        }
        return itemStack;
    }

    /**
     * 反序列化Item
     */
    public static Item deserializeItem(@NonNull String item) {
        ItemStack itemStack = deserializeItemStack(item);
        if (itemStack != null) {
            return itemStack.getItem();
        } else {
            return null;
        }
    }

    /**
     * 获取物品注册ID
     */
    @NonNull
    public static String getItemRegistryName(@NonNull ItemStack itemStack) {
        return getItemRegistryName(itemStack.getItem());
    }

    /**
     * 获取物品注册ID
     */
    @NonNull
    public static String getItemRegistryName(@NonNull Item item) {
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
        return location == null ? "" : location.toString();
    }

    /**
     * 获取实体类型注册ID
     */
    @NonNull
    public static String getEntityTypeRegistryName(@NonNull Entity entity) {
        return getEntityTypeRegistryName(entity.getType());
    }

    /**
     * 获取实体类型注册ID
     */
    @NonNull
    public static String getEntityTypeRegistryName(@NonNull EntityType<?> entityType) {
        ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        return location == null ? AotakeSweep.emptyResource().toString() : location.toString();
    }

    public static String getItemCustomNameJson(@NonNull ItemStack itemStack) {
        String result = "";
        CompoundTag CompoundTag = itemStack.getTagElement("display");
        if (CompoundTag != null && CompoundTag.contains("Name", 8)) {
            result = CompoundTag.getString("Name");
        }
        return result;
    }

    public static net.minecraft.network.chat.Component getItemCustomName(@NonNull ItemStack itemStack) {
        net.minecraft.network.chat.Component result = null;
        String nameJson = getItemCustomNameJson(itemStack);
        if (StringUtils.isNotNullOrEmpty(nameJson)) {
            try {
                result = net.minecraft.network.chat.Component.Serializer.fromJson(nameJson);
            } catch (Exception e) {
                LOGGER.error("Invalid unsafe item name: {}", nameJson, e);
            }
        }
        return result;
    }

    public static net.minecraft.network.chat.Component textComponentFromJson(String json) {
        net.minecraft.network.chat.Component result = null;
        if (StringUtils.isNotNullOrEmpty(json)) {
            try {
                result = net.minecraft.network.chat.Component.Serializer.fromJson(json);
            } catch (Exception e) {
                LOGGER.error("Invalid unsafe item name: {}", json, e);
            }
        }
        return result;
    }

    public static String getPlayerUUIDString(@NonNull Player player) {
        return player.getUUID().toString();
    }

    // endregion 杂项

}
