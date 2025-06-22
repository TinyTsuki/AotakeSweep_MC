package xin.vanilla.aotake.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepDataCapability;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.network.ModNetworkHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
     * 判断指令类型是否开启
     */
    public static boolean isCommandEnabled(EnumCommandType type) {
        switch (type) {
            default:
                return true;
        }
    }

    /**
     * 获取完整的指令
     */
    public static String getCommand(EnumCommandType type) {
        String prefix = AotakeUtils.getCommandPrefix();
        switch (type) {
            case HELP:
                return prefix + " help";
            case LANGUAGE:
                return prefix + " " + CommonConfig.COMMAND_LANGUAGE.get();
            case LANGUAGE_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_LANGUAGE.get() : "";
            case VIRTUAL_OP:
                return prefix + " " + CommonConfig.COMMAND_VIRTUAL_OP.get();
            case VIRTUAL_OP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_VIRTUAL_OP.get() : "";
            case DUSTBIN_OPEN:
                return prefix + " " + CommonConfig.COMMAND_DUSTBIN_OPEN.get();
            case DUSTBIN_OPEN_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_OPEN.get() : "";
            case DUSTBIN_CLEAR:
                return prefix + " " + CommonConfig.COMMAND_DUSTBIN_CLEAR.get();
            case DUSTBIN_CLEAR_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_CLEAR.get() : "";
            case DUSTBIN_DROP:
                return prefix + " " + CommonConfig.COMMAND_DUSTBIN_DROP.get();
            case DUSTBIN_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_DUSTBIN_DROP.get() : "";
            case CACHE_CLEAR:
                return prefix + " " + CommonConfig.COMMAND_CACHE_CLEAR.get();
            case CACHE_CLEAR_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_CACHE_CLEAR.get() : "";
            case CACHE_DROP:
                return prefix + " " + CommonConfig.COMMAND_CACHE_DROP.get();
            case CACHE_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_CACHE_DROP.get() : "";
            case SWEEP:
                return prefix + " " + CommonConfig.COMMAND_SWEEP.get();
            case SWEEP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_SWEEP.get() : "";
            case CLEAR_DROP:
                return prefix + " " + CommonConfig.COMMAND_CLEAR_DROP.get();
            case CLEAR_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_CLEAR_DROP.get() : "";
            default:
                return "";
        }
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        switch (type) {
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return ServerConfig.PERMISSION_VIRTUAL_OP.get();
            case DUSTBIN_OPEN:
            case DUSTBIN_OPEN_CONCISE:
                return ServerConfig.PERMISSION_DUSTBIN_OPEN.get();
            case DUSTBIN_CLEAR:
            case DUSTBIN_CLEAR_CONCISE:
                return ServerConfig.PERMISSION_DUSTBIN_CLEAR.get();
            case DUSTBIN_DROP:
            case DUSTBIN_DROP_CONCISE:
                return ServerConfig.PERMISSION_DUSTBIN_DROP.get();
            case CACHE_CLEAR:
            case CACHE_CLEAR_CONCISE:
                return ServerConfig.PERMISSION_CACHE_CLEAR.get();
            case CACHE_DROP:
            case CACHE_DROP_CONCISE:
                return ServerConfig.PERMISSION_CACHE_DROP.get();
            case SWEEP:
            case SWEEP_CONCISE:
                return ServerConfig.PERMISSION_SWEEP.get();
            case CLEAR_DROP:
            case CLEAR_DROP_CONCISE:
                return ServerConfig.PERMISSION_CLEAR_DROP.get();
            default:
                return 0;
        }
    }

    /**
     * 判断指令是否启用简短模式
     */
    public static boolean isConciseEnabled(EnumCommandType type) {
        switch (type) {
            case LANGUAGE:
            case LANGUAGE_CONCISE:
                return CommonConfig.CONCISE_LANGUAGE.get();
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return CommonConfig.CONCISE_VIRTUAL_OP.get();
            case DUSTBIN_OPEN:
            case DUSTBIN_OPEN_CONCISE:
                return CommonConfig.CONCISE_DUSTBIN_OPEN.get();
            case DUSTBIN_CLEAR:
            case DUSTBIN_CLEAR_CONCISE:
                return CommonConfig.CONCISE_DUSTBIN_CLEAR.get();
            case DUSTBIN_DROP:
            case DUSTBIN_DROP_CONCISE:
                return CommonConfig.CONCISE_DUSTBIN_DROP.get();
            case CACHE_CLEAR:
            case CACHE_CLEAR_CONCISE:
                return CommonConfig.CONCISE_CACHE_CLEAR.get();
            case CACHE_DROP:
            case CACHE_DROP_CONCISE:
                return CommonConfig.CONCISE_CACHE_DROP.get();
            case SWEEP:
            case SWEEP_CONCISE:
                return CommonConfig.CONCISE_SWEEP.get();
            case CLEAR_DROP:
            case CLEAR_DROP_CONCISE:
                return CommonConfig.CONCISE_CLEAR_DROP.get();
            default:
                return false;
        }
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(CommandSource source, EnumCommandType type) {
        return source.hasPermission(getCommandPermissionLevel(type)) || hasVirtualPermission(source.getEntity(), type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(PlayerEntity player, EnumCommandType type) {
        return player.hasPermissions(getCommandPermissionLevel(type)) || hasVirtualPermission(player, type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasVirtualPermission(Entity source, EnumCommandType type) {
        // 若为玩家
        if (source instanceof PlayerEntity) {
            return VirtualPermissionManager.getVirtualPermission((PlayerEntity) source).stream()
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
    public static void broadcastMessage(ServerPlayerEntity player, Component message) {
        player.server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", player.getDisplayName(), message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 广播消息
     *
     * @param server  发送者
     * @param message 消息
     */
    public static void broadcastMessage(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", "Server", message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 发送消息至所有玩家
     */
    public static void sendMessageToAll(Component message) {
        for (ServerPlayerEntity player : AotakeSweep.getServerInstance().getPlayerList().getPlayers()) {
            sendMessage(player, message);
        }
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(PlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)), player.getUUID());
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(PlayerEntity player, String message) {
        player.sendMessage(Component.literal(message).toChatComponent(), player.getUUID());
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(PlayerEntity player, String key, Object... args) {
        player.sendMessage(Component.translatable(key, args).setLanguageCode(AotakeUtils.getPlayerLanguage(player)).toChatComponent(), player.getUUID());
    }

    /**
     * 发送翻译消息
     *
     * @param source  指令来源
     * @param success 是否成功
     * @param key     翻译键
     * @param args    参数
     */
    public static void sendTranslatableMessage(CommandSource source, boolean success, String key, Object... args) {
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
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
        for (ServerPlayerEntity player : AotakeSweep.getServerInstance().getPlayerList().getPlayers()) {
            sendActionBarMessage(player, message);
        }
    }

    /**
     * 发送操作栏消息
     */
    public static void sendActionBarMessage(ServerPlayerEntity player, Component message) {
        player.connection.send(new SChatPacket(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)), ChatType.GAME_INFO, player.getUUID()));
    }

    /**
     * 广播数据包至所有玩家
     *
     * @param packet 数据包
     */
    public static void broadcastPacket(IPacket<?> packet) {
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
    public static <MSG> void sendPacketToPlayer(MSG msg, ServerPlayerEntity player) {
        ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    // endregion 消息相关


    // region 玩家语言相关

    public static String getPlayerLanguage(@NonNull PlayerEntity player) {
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

    public static String getValidLanguage(@Nullable PlayerEntity player, @Nullable String language) {
        String result;
        if (StringUtils.isNullOrEmptyEx(language) || "client".equalsIgnoreCase(language)) {
            if (player instanceof ServerPlayerEntity) {
                result = AotakeUtils.getServerPlayerLanguage((ServerPlayerEntity) player);
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

    public static String getServerPlayerLanguage(ServerPlayerEntity player) {
        return player.getLanguage();
    }

    /**
     * 复制玩家语言设置
     *
     * @param originalPlayer 原始玩家
     * @param targetPlayer   目标玩家
     */
    public static void clonePlayerLanguage(ServerPlayerEntity originalPlayer, ServerPlayerEntity targetPlayer) {
        FieldUtils.setPrivateFieldValue(ServerPlayerEntity.class, targetPlayer, FieldUtils.getPlayerLanguageFieldName(originalPlayer), getServerPlayerLanguage(originalPlayer));
    }

    public static String getClientLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected().getCode();
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
                    .collect(Collectors.toList());
        }
        if (SAFE_BLOCKS == null) {
            SAFE_BLOCKS = CommonConfig.SAFE_BLOCKS.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (SAFE_BLOCKS_BELOW_STATE == null) {
            SAFE_BLOCKS_BELOW_STATE = CommonConfig.SAFE_BLOCKS_BELOW.get().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (SAFE_BLOCKS_BELOW == null) {
            SAFE_BLOCKS_BELOW = CommonConfig.SAFE_BLOCKS_BELOW.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (SAFE_BLOCKS_ABOVE_STATE == null) {
            SAFE_BLOCKS_ABOVE_STATE = CommonConfig.SAFE_BLOCKS_ABOVE.get().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (SAFE_BLOCKS_ABOVE == null) {
            SAFE_BLOCKS_ABOVE = CommonConfig.SAFE_BLOCKS_ABOVE.get().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    public static List<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        AotakeSweep.getServerInstance().getAllLevels()
                .forEach(level -> entities.addAll(level.getEntities()
                        .collect(Collectors.toList()))
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
                ).collect(Collectors.toList());

        // 超过阈值的白名单物品
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
                    World level = entity.level;
                    BlockState state = level.getBlockState(entity.blockPosition());
                    BlockState below = level.getBlockState(entity.blockPosition().below());
                    BlockState above = level.getBlockState(entity.blockPosition().above());

                    boolean isUnsafe =
                            !SAFE_BLOCKS.contains(AotakeUtils.getBlockRegistryName(state)) &&
                                    !SAFE_BLOCKS_STATE.contains(state) &&
                                    !SAFE_BLOCKS_BELOW.contains(AotakeUtils.getBlockRegistryName(below)) &&
                                    !SAFE_BLOCKS_BELOW_STATE.contains(below) &&
                                    !SAFE_BLOCKS_ABOVE.contains(AotakeUtils.getBlockRegistryName(above)) &&
                                    !SAFE_BLOCKS_ABOVE_STATE.contains(above);

                    return !isUnsafe;
                })
                .collect(Collectors.groupingBy(entity -> {
                    String dimension = entity.level.dimension().location().toString();
                    int chunkX = entity.blockPosition().getX() / 16;
                    int chunkY = entity.blockPosition().getY() / 16;
                    return dimension + "," + chunkX + "," + chunkY;
                }, Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > CommonConfig.SAFE_BLOCKS_ENTITY_LIMIT.get())
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());

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
            World level = entity.level;
            BlockState state = level.getBlockState(entity.blockPosition());
            BlockState below = level.getBlockState(entity.blockPosition().below());
            BlockState above = level.getBlockState(entity.blockPosition().above());

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

    public static void sweep(@Nullable ServerPlayerEntity player, List<Entity> entities) {
        List<ServerPlayerEntity> players = AotakeSweep.getServerInstance().getPlayerList().getPlayers();
        // 若服务器没有玩家
        if (CollectionUtils.isNullOrEmpty(players) && !CommonConfig.SWEEP_WHEN_NO_PLAYER.get()) return;

        List<Entity> list = getAllEntitiesByFilter(entities);

        SweepResult result = null;
        if (CollectionUtils.isNotNullOrEmpty(list)) {
            // 清空旧的物品
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_CLEAR.name())) {
                WorldTrashData.get().getDropList().clear();
                WorldTrashData.get().getInventoryList().forEach(Inventory::clearContent);
            }
            result = WorldTrashData.get().addDrops(list);
        }

        for (ServerPlayerEntity p : players) {
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
        if (entity instanceof PartEntity) {
            entity = ((PartEntity<?>) entity).getParent();
        }
        if (entity.isMultipartEntity()) {
            PartEntity<?>[] parts = entity.getParts();
            if (CollectionUtils.isNotNullOrEmpty(parts)) {
                for (PartEntity<?> part : parts) {
                    part.remove(keepData);
                }
            }
        }
        entity.remove(keepData);
    }

    /**
     * 移除实体
     */
    public static void removeEntity(ServerWorld level, Entity entity, boolean keepData) {
        if (entity instanceof PartEntity) {
            entity = ((PartEntity<?>) entity).getParent();
        }
        if (entity.isMultipartEntity()) {
            PartEntity<?>[] parts = entity.getParts();
            if (CollectionUtils.isNotNullOrEmpty(parts)) {
                for (PartEntity<?> part : parts) {
                    level.removeEntity(part, keepData);
                }
            }
        }
        level.removeEntity(entity, keepData);
    }

    /**
     * 将物品转为实体
     */
    public static Entity getEntityFromItem(ServerWorld level, ItemStack itemStack) {
        Entity result = null;

        CompoundNBT tag = itemStack.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundNBT aotake = tag.getCompound(AotakeSweep.MODID);
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
    public static boolean executeCommand(@NonNull ServerPlayerEntity player, @NonNull String command) {
        boolean result = false;
        try {
            result = player.getServer().getCommands().performCommand(player.createCommandSourceStack(), command) > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result;
    }

    /**
     * 获取指定维度的世界实例
     */
    public static ServerWorld getWorld(RegistryKey<World> dimension) {
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
    public static void playSound(ServerPlayerEntity player, ResourceLocation sound, float volume, float pitch) {
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(sound);
        if (soundEvent != null) {
            player.playNotifySound(soundEvent, SoundCategory.PLAYERS, volume, pitch);
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
            return new BlockStateParser(new StringReader(block), false).parse(true).getState();
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
    public static String getBlockRegistryName(@NonNull Block block) {
        ResourceLocation location = ForgeRegistries.BLOCKS.getKey(block);
        if (location == null) location = block.getRegistryName();
        return location == null ? "" : location.toString();
    }

    /**
     * 反序列化ItemStack
     */
    public static ItemStack deserializeItemStack(@NonNull String item) {
        ItemStack itemStack;
        try {
            itemStack = ItemStack.of(JsonToNBT.parseTag(item));
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
        if (location == null) location = item.getRegistryName();
        return location == null ? "" : location.toString();
    }

    /**
     * 获取实体类型注册ID
     */
    @NonNull
    public static String getEntityTypeRegistryName(@NonNull Entity entity) {
        EntityType<?> entityType = entity.getType();
        ResourceLocation location = ForgeRegistries.ENTITIES.getKey(entityType);
        if (location == null) location = entityType.getRegistryName();
        return location == null ? AotakeSweep.emptyResource().toString() : location.toString();
    }

    public static String getItemCustomNameJson(@NonNull ItemStack itemStack) {
        String result = "";
        CompoundNBT compoundnbt = itemStack.getTagElement("display");
        if (compoundnbt != null && compoundnbt.contains("Name", 8)) {
            result = compoundnbt.getString("Name");
        }
        return result;
    }

    public static ITextComponent getItemCustomName(@NonNull ItemStack itemStack) {
        ITextComponent result = null;
        String nameJson = getItemCustomNameJson(itemStack);
        if (StringUtils.isNotNullOrEmpty(nameJson)) {
            try {
                result = ITextComponent.Serializer.fromJson(nameJson);
            } catch (Exception e) {
                LOGGER.error("Invalid unsafe item name: {}", nameJson, e);
            }
        }
        return result;
    }

    public static ITextComponent textComponentFromJson(String json) {
        ITextComponent result = null;
        if (StringUtils.isNotNullOrEmpty(json)) {
            try {
                result = ITextComponent.Serializer.fromJson(json);
            } catch (Exception e) {
                LOGGER.error("Invalid unsafe item name: {}", json, e);
            }
        }
        return result;
    }

    public static String getPlayerUUIDString(@NonNull PlayerEntity player) {
        return player.getUUID().toString();
    }

    // endregion 杂项

}
