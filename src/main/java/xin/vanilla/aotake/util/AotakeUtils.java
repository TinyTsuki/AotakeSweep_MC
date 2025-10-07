package xin.vanilla.aotake.util;

import com.google.gson.reflect.TypeToken;
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
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.network.ModNetworkHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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
            case DUSTBIN_OPEN_OTHER:
                return prefix + " " + CommonConfig.COMMAND_DUSTBIN_OPEN.get();
            case DUSTBIN_OPEN_CONCISE:
            case DUSTBIN_OPEN_OTHER_CONCISE:
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
            case DELAY_SWEEP:
                return prefix + " " + CommonConfig.COMMAND_DELAY_SWEEP.get();
            case DELAY_SWEEP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_DELAY_SWEEP.get() : "";
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
            case DUSTBIN_OPEN_OTHER:
            case DUSTBIN_OPEN_OTHER_CONCISE:
                return ServerConfig.PERMISSION_DUSTBIN_OPEN_OTHER.get();
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
            case DELAY_SWEEP:
            case DELAY_SWEEP_CONCISE:
                return ServerConfig.PERMISSION_DELAY_SWEEP.get();
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
            case DUSTBIN_OPEN_OTHER:
            case DUSTBIN_OPEN_OTHER_CONCISE:
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
            case DELAY_SWEEP:
            case DELAY_SWEEP_CONCISE:
                return CommonConfig.CONCISE_DELAY_SWEEP.get();
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

    /**
     * 获取传送指令
     */
    public static String genTeleportCommand(Coordinate coordinate) {
        if (ModList.get().isLoaded("narcissus_farewell")) {
            return String.format("/%s %s %s %s safe %s"
                    , CompatNarcissus.getTpCommand()
                    , coordinate.getXInt()
                    , coordinate.getYInt()
                    , coordinate.getZInt()
                    , coordinate.getDimensionResourceId()
            );
        } else {
            return String.format("/execute in %s as @s run tp %s %s %s"
                    , coordinate.getDimensionResourceId()
                    , coordinate.getXInt()
                    , coordinate.getYInt()
                    , coordinate.getZInt()
            );
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
        for (ServerPlayerEntity player : AotakeSweep.getServerInstance().key().getPlayerList().getPlayers()) {
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
        for (ServerPlayerEntity player : AotakeSweep.getServerInstance().key().getPlayerList().getPlayers()) {
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
        AotakeSweep.getServerInstance().key().getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
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
        KeyValue<MinecraftServer, Boolean> serverInstance = AotakeSweep.getServerInstance();
        if (serverInstance.val()) {
            serverInstance.key().getAllLevels()
                    .forEach(level -> entities.addAll(level.getEntities()
                            .collect(Collectors.toList()))
                    );
        }
        return entities;
    }

    private static final Map<String, SafeExpressionEvaluator> EXPRESSION_EVALUATOR_CACHE = new HashMap<>();

    private static SafeExpressionEvaluator getSafeExpressionEvaluator(String expression) {
        SafeExpressionEvaluator evaluator = EXPRESSION_EVALUATOR_CACHE.get(expression);
        if (evaluator == null) {
            evaluator = new SafeExpressionEvaluator(expression);
            EXPRESSION_EVALUATOR_CACHE.put(expression, evaluator);
        }
        return evaluator;
    }

    private static boolean isNbtExpressionValid(Entity entity, KeyValue<String, String> kv) {
        if (NBTPathUtils.has(entity.getPersistentData(), kv.getKey())) {
            INBT tag = NBTPathUtils.getTagByPath(entity.getPersistentData(), kv.getKey());
            SafeExpressionEvaluator evaluator = getSafeExpressionEvaluator(kv.getValue());
            if (tag instanceof NumberNBT) {
                evaluator.setVar("value", ((NumberNBT) tag).getAsDouble());
            } else if (tag instanceof StringNBT) {
                evaluator.setVar("value", tag.getAsString());
            } else {
                return false;
            }
            try {
                return evaluator.evaluate() > 0;
            } catch (Exception e) {
                LOGGER.error("Invalid unsafe nbt expression: {}", kv.getValue(), e);
                return false;
            }
        }
        return false;
    }

    public static boolean isItem(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (CollectionUtils.isNullOrEmpty(ServerConfig.ITEM_TYPE_LIST.get())) {
            return entity instanceof ItemEntity;
        } else {
            return entity.getType() == EntityType.ITEM || ServerConfig.ITEM_TYPE_LIST.get().contains(getEntityTypeRegistryName(entity));
        }
    }

    public static boolean isJunkItem(Entity entity) {
        boolean result = false;
        if (isItem(entity)) {
            // 空列表
            if (CollectionUtils.isNullOrEmpty(ServerConfig.ITEM_LIST.get())) {
                result = EnumListType.WHITE.name().equals(ServerConfig.ITEM_LIST_MODE.get());
            }
            // 黑名单模式
            else if (EnumListType.BLACK.name().equals(ServerConfig.ITEM_LIST_MODE.get())) {
                result = ServerConfig.ITEM_LIST.get().contains(getItemRegistryName(((ItemEntity) entity).getItem()));
            }
            // 白名单模式
            else {
                result = !ServerConfig.ITEM_LIST.get().contains(getItemRegistryName(((ItemEntity) entity).getItem()));
            }
        }
        return result;
    }

    public static boolean isJunkEntity(Entity entity, boolean chuck) {
        boolean result = false;
        if (entity != null && !(entity instanceof PlayerEntity) && !entity.hasCustomName()) {
            if (chuck) {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.CHUNK_CHECK_ENTITY_LIST.get())) {
                    result = EnumListType.WHITE.name().equals(ServerConfig.CHUNK_CHECK_ENTITY_LIST_MODE.get());
                }
                // 黑名单模式
                else if (EnumListType.BLACK.name().equals(ServerConfig.CHUNK_CHECK_ENTITY_LIST_MODE.get())) {
                    result = ServerConfig.CHUNK_CHECK_ENTITY_LIST.get().contains(getEntityTypeRegistryName(entity));
                }
                // 白名单模式
                else {
                    result = !ServerConfig.CHUNK_CHECK_ENTITY_LIST.get().contains(getEntityTypeRegistryName(entity));
                }
            } else {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.ENTITY_LIST.get())) {
                    result = EnumListType.WHITE.name().equals(ServerConfig.ENTITY_LIST_MODE.get());
                }
                // 黑名单模式
                else if (EnumListType.BLACK.name().equals(ServerConfig.ENTITY_LIST_MODE.get())) {
                    result = ServerConfig.ENTITY_LIST.get().contains(getEntityTypeRegistryName(entity));
                }
                // 白名单模式
                else {
                    result = !ServerConfig.ENTITY_LIST.get().contains(getEntityTypeRegistryName(entity));
                }
            }
        }
        return result;
    }

    public static List<Entity> getAllEntitiesByFilter(List<Entity> entities, boolean chuck) {
        if (CollectionUtils.isNullOrEmpty(entities)) {
            entities = getAllEntities();
        }
        initSafeBlocks();
        List<Entity> filtered = entities.stream()
                // 物品实体 或 垃圾实体
                .filter(entity -> isItem(entity) || isJunkEntity(entity, chuck))
                // 非驯养实体
                .filter(entity -> !(entity instanceof TameableEntity) || ((TameableEntity) entity).getOwnerUUID() == null)
                .collect(Collectors.toList());

        Map<KeyValue<World, BlockPos>, BlockState> blockStateCache = filtered.stream()
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
                // 物品
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                // 白名单物品
                .filter(item -> !isJunkItem(item))
                .collect(Collectors.groupingBy(item -> getItemRegistryName(item.getItem()), Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > ServerConfig.ITEM_LIST_LIMIT.get())
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());

        // 超过阈值的安全方块实体
        List<Entity> exceededSafeList = filtered.stream()
                .filter(entity -> {
                    World level = entity.level;
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
                .collect(Collectors.toList());

        // 超过阈值的NBT白名单实体
        List<Entity> exceededNbtWhiteBlackList = filtered.stream()
                .filter(entity -> !entity.getPersistentData().isEmpty())
                .filter(entity -> (!ServerConfig.ENTITY_NBT_WHITELIST.isEmpty() &&
                        ServerConfig.ENTITY_NBT_WHITELIST.stream()
                                .anyMatch(keyValue ->
                                        isNbtExpressionValid(entity, keyValue)
                                ))
                        || (!ServerConfig.ENTITY_NBT_BLACKLIST.isEmpty() &&
                        ServerConfig.ENTITY_NBT_BLACKLIST.stream()
                                .noneMatch(keyValue ->
                                        isNbtExpressionValid(entity, keyValue)
                                ))
                )
                .collect(Collectors.groupingBy(Entity::getType, Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > ServerConfig.NBT_WHITE_BLACK_LIST_ENTITY_LIMIT.get())
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());

        // 过滤
        Predicate<Entity> predicate = entity -> {
            boolean isItem = entity instanceof ItemEntity;

            // 掉落时间过滤
            if (isItem && ServerConfig.SWEEP_ITEM_AGE.get() > 0) {
                if (entity.level.isAreaLoaded(entity.blockPosition(), 0)
                        && entity.tickCount < ServerConfig.SWEEP_ITEM_AGE.get()
                        && entity.tickCount > 0
                ) {
                    return false;
                }
            }

            // 物品名单过滤
            if (isItem && !isJunkItem(entity) && !exceededWhiteBlackList.contains(entity)) {
                return false;
            }

            // NBT白名单过滤
            if (!ServerConfig.ENTITY_NBT_WHITELIST.isEmpty()) {
                if (ServerConfig.ENTITY_NBT_WHITELIST.stream()
                        .anyMatch(keyValue ->
                                isNbtExpressionValid(entity, keyValue)
                        )
                        && !exceededNbtWhiteBlackList.contains(entity)
                ) {
                    return false;
                }
            }

            // NBT黑名单过滤
            if (!ServerConfig.ENTITY_NBT_BLACKLIST.isEmpty()) {
                if (ServerConfig.ENTITY_NBT_BLACKLIST.stream()
                        .noneMatch(keyValue ->
                                isNbtExpressionValid(entity, keyValue)
                        )
                        && !exceededNbtWhiteBlackList.contains(entity)
                ) {
                    return false;
                }
            }

            // 安全方块过滤
            World level = entity.level;
            BlockState state = blockStateCache.get(new KeyValue<>(level, entity.blockPosition()));
            BlockState below = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().below()));
            BlockState above = blockStateCache.get(new KeyValue<>(level, entity.blockPosition().above()));

            boolean unsafe =
                    !SAFE_BLOCKS.contains(state == null ? null : AotakeUtils.getBlockRegistryName(state)) &&
                            !SAFE_BLOCKS_STATE.contains(state) &&
                            !SAFE_BLOCKS_BELOW.contains(below == null ? null : AotakeUtils.getBlockRegistryName(below)) &&
                            !SAFE_BLOCKS_BELOW_STATE.contains(below) &&
                            !SAFE_BLOCKS_ABOVE.contains(above == null ? null : AotakeUtils.getBlockRegistryName(above)) &&
                            !SAFE_BLOCKS_ABOVE_STATE.contains(above);

            return unsafe || exceededSafeList.contains(entity);
        };

        return filtered.stream().filter(predicate).collect(Collectors.toList());
    }

    public static void sweep() {
        LOGGER.debug("Sweep started");
        List<Entity> entities = getAllEntities();
        AotakeUtils.sweep(null, entities, false);
        LOGGER.debug("Sweep finished");
    }

    public static void sweep(@Nullable ServerPlayerEntity player, List<Entity> entities, boolean chuck) {
        KeyValue<MinecraftServer, Boolean> serverInstance = AotakeSweep.getServerInstance();
        // 服务器已关闭
        if (!serverInstance.val()) return;

        List<ServerPlayerEntity> players = serverInstance.key().getPlayerList().getPlayers();

        try {
            // 若服务器没有玩家
            if (CollectionUtils.isNullOrEmpty(players) && !CommonConfig.SWEEP_WHEN_NO_PLAYER.get()) {
                LOGGER.debug("No player online, sweep canceled");
                return;
            }

            List<Entity> list = getAllEntitiesByFilter(entities, chuck);

            SweepResult result = null;
            if (CollectionUtils.isNotNullOrEmpty(list)) {
                // 清空旧的物品
                if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_CLEAR.name())) {
                    switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                        case VIRTUAL: {
                            clearVirtualDustbin();
                        }
                        break;
                        case BLOCK: {
                            clearDustbinBlock();
                        }
                        break;
                        default: {
                            clearVirtualDustbin();
                            clearDustbinBlock();
                        }
                    }
                }
                result = AotakeSweep.getEntitySweeper().addDrops(list);
            }

            for (ServerPlayerEntity p : players) {
                String language = AotakeUtils.getPlayerLanguage(p);
                Component msg = getWarningMessage(result == null || result.isEmpty() ? "fail" : "success"
                        , language
                        , result);
                PlayerSweepData playerData = PlayerSweepData.getData(p);
                if (playerData.isShowSweepResult()) {
                    String openCom = "/" + AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN);
                    msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, openCom))
                            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                    , Component.literal(openCom).toTextComponent())
                            );
                    AotakeUtils.sendMessage(p, Component.empty()
                            .append(msg)
                            .append(Component.literal("[x]")
                                    .setColor(EnumMCColor.RED.getColor())
                                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , Component.translatable(EnumI18nType.MESSAGE, "not_show_button")
                                            .toTextComponent(language))
                                    )
                                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                            , "/" + AotakeUtils.getCommandPrefix() + " config showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = getWarningVoice(result == null || result.isEmpty() ? "fail" : "success");
                    float volume = CommonConfig.SWEEP_WARNING_VOICE_VOLUME.get() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
            for (ServerPlayerEntity p : players) {
                String language = AotakeUtils.getPlayerLanguage(p);
                Component msg = getWarningMessage("error", language, null);
                PlayerSweepData playerData = PlayerSweepData.getData(p);
                if (playerData.isShowSweepResult()) {
                    AotakeUtils.sendMessage(p, Component.empty()
                            .append(msg)
                            .append(Component.literal("[x]")
                                    .setColor(EnumMCColor.RED.getColor())
                                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , Component.translatable(EnumI18nType.MESSAGE, "not_show_button")
                                            .toTextComponent(language))
                                    )
                                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                            , "/" + AotakeUtils.getCommandPrefix() + " config showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = getWarningVoice("error");
                    float volume = CommonConfig.SWEEP_WARNING_VOICE_VOLUME.get() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        }
    }

    private static void clearVirtualDustbin() {
        WorldTrashData.get().getDropList().clear();
        List<Inventory> inventories = WorldTrashData.get().getInventoryList();
        if (CollectionUtils.isNotNullOrEmpty(inventories)) inventories.forEach(Inventory::clearContent);
        WorldTrashData.get().setDirty();
    }

    private static void clearDustbinBlock() {
        for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
            Coordinate coordinate = Coordinate.fromSimpleString(pos);
            if (coordinate != null) {
                IItemHandler handler = getBlockItemHandler(coordinate);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        handler.extractItem(i, handler.getSlotLimit(i), false);
                    }
                }
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

    private static final Map<String, String> warns = new HashMap<>();
    private static final Map<String, String> voices = new HashMap<>();

    private static void initWarns() {
        if (warns.isEmpty()) {
            warns.putAll(JsonUtils.GSON.fromJson(CommonConfig.SWEEP_WARNING_CONTENT.get(), new TypeToken<Map<String, String>>() {
            }.getType()));
            warns.putIfAbsent("error", "message.aotake_sweep.cleanup_error");
            warns.putIfAbsent("fail", "message.aotake_sweep.cleanup_started");
            warns.putIfAbsent("success", "message.aotake_sweep.cleanup_started");
        }
        if (voices.isEmpty()) {
            voices.putAll(JsonUtils.GSON.fromJson(CommonConfig.SWEEP_WARNING_VOICE.get(), new TypeToken<Map<String, String>>() {
            }.getType()));
            voices.put("initialized", "initialized");
        }
    }

    public static boolean hasWarning(String key) {
        initWarns();
        return warns.containsKey(key);
    }

    public static Component getWarningMessage(String key, String lang, @Nullable SweepResult result) {
        Component msg = null;
        try {
            initWarns();
            String text = warns.get(key);
            if (StringUtils.isNotNullOrEmpty(text) && text.startsWith("message.aotake_sweep.")) {
                text = I18nUtils.getTranslation(text, lang);
            }
            if (StringUtils.toInt(key) > 0) {
                if (StringUtils.isNullOrEmpty(text)) {
                    text = Component.translatable(EnumI18nType.MESSAGE, "cleanup_will_start", key).toTextComponent(lang).getString();
                } else {
                    text = StringUtils.format(text, key);
                }
            } else if (StringUtils.isNullOrEmpty(text)) {
                text = "";
            }
            if (result == null) result = new SweepResult();
            text = text.replaceAll("\\[itemCount]", String.valueOf(result.getItemCount()))
                    .replaceAll("\\[entityCount]", String.valueOf(result.getEntityCount()))
                    .replaceAll("\\[recycledItemCount]", String.valueOf(result.getRecycledItemCount()))
                    .replaceAll("\\[recycledEntityCount]", String.valueOf(result.getRecycledEntityCount()));

            msg = Component.literal(text);
            msg.appendArg(key);
        } catch (Exception ignored) {
        }
        return msg;
    }

    public static boolean hasWarningVoice(String key) {
        initWarns();
        return voices.containsKey(key);
    }

    public static String getWarningVoice(String key) {
        String id = null;
        try {
            initWarns();
            id = CollectionUtils.getRandomElement(voices.get(key).split(","));
        } catch (Exception ignored) {
        }
        return id;
    }

    // endregion 扫地


    // region nbt文件读写

    public static CompoundNBT readCompressed(InputStream stream) {
        try {
            return CompressedStreamTools.readCompressed(stream);
        } catch (Exception e) {
            LOGGER.error("Failed to read compressed stream", e);
            return new CompoundNBT();
        }
    }

    public static CompoundNBT readCompressed(File file) {
        try {
            return CompressedStreamTools.readCompressed(file);
        } catch (Exception e) {
            LOGGER.error("Failed to read compressed file: {}", file.getAbsolutePath(), e);
            return new CompoundNBT();
        }
    }

    public static boolean writeCompressed(CompoundNBT tag, File file) {
        boolean result = false;
        try {
            CompressedStreamTools.writeCompressed(tag, file);
            result = true;
        } catch (Exception e) {
            LOGGER.error("Failed to write compressed file: {}", file.getAbsolutePath(), e);
        }
        return result;
    }

    public static boolean writeCompressed(CompoundNBT tag, OutputStream stream) {
        boolean result = false;
        try {
            CompressedStreamTools.writeCompressed(tag, stream);
            result = true;
        } catch (Exception e) {
            LOGGER.error("Failed to write compressed stream", e);
        }
        return result;
    }

    // endregion nbt文件读写


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
     * 执行指令
     */
    public static boolean executeCommandNoOutput(@NonNull ServerPlayerEntity player, @NonNull String command) {
        boolean result = false;
        try {
            result = player.getServer().getCommands().performCommand(player.createCommandSourceStack().withSuppressedOutput(), command) > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result;
    }

    /**
     * 获取指定维度的世界实例
     */
    public static ServerWorld getWorld(RegistryKey<World> dimension) {
        return AotakeSweep.getServerInstance().key().getLevel(dimension);
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

    /**
     * 将物品添加到指定的方块容器
     */
    public static ItemStack addItemToBlock(ItemStack stack, Coordinate coordinate) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ServerWorld level = AotakeSweep.getServerInstance().key().getLevel(coordinate.getDimension());
        if (level == null) return stack;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return stack;

        TileEntity te = level.getBlockEntity(pos);
        if (te == null) return stack;

        try {
            LazyOptional<IItemHandler> capOpt = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, coordinate.getDirection());
            if (capOpt.isPresent()) {
                if (capOpt.isPresent()) {
                    IItemHandler handler = capOpt.orElse(new ItemStackHandler());
                    ItemStack remaining = ItemHandlerHelper.insertItem(handler, stack.copy(), false);
                    te.setChanged();
                    return remaining;
                }
            }
        } catch (Throwable ignored) {
        }

        return stack;
    }

    /**
     * 获取指定的方块容器
     */
    public static IItemHandler getBlockItemHandler(Coordinate coordinate) {
        ServerWorld level = AotakeSweep.getServerInstance().key().getLevel(coordinate.getDimension());
        if (level == null) return null;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return null;

        TileEntity te = level.getBlockEntity(pos);
        if (te == null) return null;

        try {
            LazyOptional<IItemHandler> capOpt = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, coordinate.getDirection());
            if (capOpt.isPresent()) {
                if (capOpt.isPresent()) {
                    return capOpt.orElse(new ItemStackHandler());
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    // endregion 杂项

}
