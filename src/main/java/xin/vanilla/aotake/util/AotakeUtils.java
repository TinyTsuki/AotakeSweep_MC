package xin.vanilla.aotake.util;

import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.network.AotakePacket;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"resource", "UnstableApiUsage"})
public class AotakeUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    // region 指令相关

    /**
     * 获取指令前缀
     */
    public static String getCommandPrefix() {
        String commandPrefix = ServerConfig.get().commandPrefix();
        if (StringUtils.isNullOrEmptyEx(commandPrefix) || !commandPrefix.matches("^(\\w ?)+$")) {
            ServerConfig.get().commandPrefix(AotakeSweep.DEFAULT_COMMAND_PREFIX);
        }
        return ServerConfig.get().commandPrefix().trim();
    }

    /**
     * 获取完整的指令
     */
    public static String getCommand(EnumCommandType type) {
        String prefix = AotakeUtils.getCommandPrefix();
        return switch (type) {
            case HELP -> prefix + " help";
            case LANGUAGE -> prefix + " " + ServerConfig.get().commandConfig().commandLanguage();
            case LANGUAGE_CONCISE -> isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandLanguage() : "";
            case VIRTUAL_OP -> prefix + " " + ServerConfig.get().commandConfig().commandVirtualOp();
            case VIRTUAL_OP_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandVirtualOp() : "";
            case DUSTBIN_OPEN, DUSTBIN_OPEN_OTHER ->
                    prefix + " " + ServerConfig.get().commandConfig().commandDustbinOpen();
            case DUSTBIN_OPEN_CONCISE, DUSTBIN_OPEN_OTHER_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandDustbinOpen() : "";
            case DUSTBIN_CLEAR -> prefix + " " + ServerConfig.get().commandConfig().commandDustbinClear();
            case DUSTBIN_CLEAR_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandDustbinClear() : "";
            case DUSTBIN_DROP -> prefix + " " + ServerConfig.get().commandConfig().commandDustbinDrop();
            case DUSTBIN_DROP_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandDustbinDrop() : "";
            case CACHE_CLEAR -> prefix + " " + ServerConfig.get().commandConfig().commandCacheClear();
            case CACHE_CLEAR_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandCacheClear() : "";
            case CACHE_DROP -> prefix + " " + ServerConfig.get().commandConfig().commandCacheDrop();
            case CACHE_DROP_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandCacheDrop() : "";
            case SWEEP -> prefix + " " + ServerConfig.get().commandConfig().commandSweep();
            case SWEEP_CONCISE -> isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandSweep() : "";
            case CLEAR_DROP -> prefix + " " + ServerConfig.get().commandConfig().commandClearDrop();
            case CLEAR_DROP_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandClearDrop() : "";
            case DELAY_SWEEP -> prefix + " " + ServerConfig.get().commandConfig().commandDelaySweep();
            case DELAY_SWEEP_CONCISE ->
                    isConciseEnabled(type) ? ServerConfig.get().commandConfig().commandDelaySweep() : "";
            default -> "";
        };
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        return switch (type) {
            case VIRTUAL_OP, VIRTUAL_OP_CONCISE -> ServerConfig.get().permissionConfig().permissionVirtualOp();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE -> ServerConfig.get().permissionConfig().permissionDustbinOpen();
            case DUSTBIN_OPEN_OTHER, DUSTBIN_OPEN_OTHER_CONCISE ->
                    ServerConfig.get().permissionConfig().permissionDustbinOpenOther();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> ServerConfig.get().permissionConfig().permissionDustbinClear();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> ServerConfig.get().permissionConfig().permissionDustbinDrop();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> ServerConfig.get().permissionConfig().permissionCacheClear();
            case CACHE_DROP, CACHE_DROP_CONCISE -> ServerConfig.get().permissionConfig().permissionCacheDrop();
            case SWEEP, SWEEP_CONCISE -> ServerConfig.get().permissionConfig().permissionSweep();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> ServerConfig.get().permissionConfig().permissionClearDrop();
            case DELAY_SWEEP, DELAY_SWEEP_CONCISE -> ServerConfig.get().permissionConfig().permissionDelaySweep();
            default -> 0;
        };
    }

    /**
     * 判断指令是否启用简短模式
     */
    public static boolean isConciseEnabled(EnumCommandType type) {
        return switch (type) {
            case LANGUAGE, LANGUAGE_CONCISE -> ServerConfig.get().conciseConfig().conciseLanguage();
            case VIRTUAL_OP, VIRTUAL_OP_CONCISE -> ServerConfig.get().conciseConfig().conciseVirtualOp();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE, DUSTBIN_OPEN_OTHER, DUSTBIN_OPEN_OTHER_CONCISE ->
                    ServerConfig.get().conciseConfig().conciseDustbinOpen();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> ServerConfig.get().conciseConfig().conciseDustbinClear();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> ServerConfig.get().conciseConfig().conciseDustbinDrop();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> ServerConfig.get().conciseConfig().conciseCacheClear();
            case CACHE_DROP, CACHE_DROP_CONCISE -> ServerConfig.get().conciseConfig().conciseCacheDrop();
            case SWEEP, SWEEP_CONCISE -> ServerConfig.get().conciseConfig().conciseSweep();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> ServerConfig.get().conciseConfig().conciseClearDrop();
            case DELAY_SWEEP, DELAY_SWEEP_CONCISE -> ServerConfig.get().conciseConfig().conciseDelaySweep();
            default -> false;
        };
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasPermission(Player player, int level) {
        return player.hasPermissions(level);
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

    /**
     * 获取传送指令
     */
    public static String genTeleportCommand(WorldCoordinate coordinate) {
        // if (ModList.get().isLoaded("narcissus_farewell")) {
        //     return String.format("/%s %s %s %s safe %s"
        //             , CompatNarcissus.getTpCommand()
        //             , coordinate.getXInt()
        //             , coordinate.getYInt()
        //             , coordinate.getZInt()
        //             , coordinate.getDimensionResourceId()
        //     );
        // } else {
        return String.format("/execute in %s as @s run tp %s %s %s"
                , coordinate.getDimensionResourceId()
                , coordinate.getXInt()
                , coordinate.getYInt()
                , coordinate.getZInt()
        );
        // }
    }

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayer player, @NonNull String command, int permission, boolean suppressedOutput) {
        boolean result = false;
        try {
            MinecraftServer server = player.getServer();
            CommandSourceStack commandSourceStack = player.createCommandSourceStack();
            if (permission > 0) {
                commandSourceStack = commandSourceStack.withPermission(permission);
            }
            if (suppressedOutput) {
                commandSourceStack = commandSourceStack.withSuppressedOutput();
            }
            result = server.getCommands().performPrefixedCommand(commandSourceStack, command) > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result;
    }

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayer player, @NonNull String command) {
        return executeCommand(player, command, 0, false);
    }

    /**
     * 执行指令
     */
    public static boolean executeCommandNoOutput(@NonNull ServerPlayer player, @NonNull String command) {
        return executeCommandNoOutput(player, command, 0);
    }

    /**
     * 执行指令
     */
    public static boolean executeCommandNoOutput(@NonNull ServerPlayer player, @NonNull String command, int permission) {
        return executeCommand(player, command, permission, true);
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
        for (ServerPlayer player : AotakeSweep.serverInstance().key().getPlayerList().getPlayers()) {
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
            source.sendSuccess(Component.translatable(key, args).setLanguageCode(ServerConfig.get().defaultLanguage()).toChatComponent(), false);
        } else {
            source.sendFailure(Component.translatable(key, args).setLanguageCode(ServerConfig.get().defaultLanguage()).toChatComponent());
        }
    }

    /**
     * 发送操作栏消息至所有玩家
     */
    public static void sendActionBarMessageToAll(Component message) {
        for (ServerPlayer player : AotakeSweep.serverInstance().key().getPlayerList().getPlayers()) {
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
        AotakeSweep.serverInstance().key().getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
    }

    /**
     * 发送数据包至服务器
     */
    public static void sendPacketToServer(AotakePacket packet) {
        sendPacketToServer(packet.id(), packet.toBytes(null));
    }

    /**
     * 发送数据包至服务器
     */
    public static void sendPacketToServer(ResourceLocation id) {
        sendPacketToServer(id, PacketByteBufs.create());
    }

    /**
     * 发送数据包至服务器
     */
    public static void sendPacketToServer(ResourceLocation id, FriendlyByteBuf buf) {
        ClientPlayNetworking.send(id, buf);
    }

    /**
     * 发送数据包至玩家
     */
    public static void sendPacketToPlayer(ServerPlayer player, AotakePacket packet) {
        sendPacketToPlayer(player, packet.id(), packet.toBytes(null));
    }

    /**
     * 发送数据包至玩家
     */
    public static void sendPacketToPlayer(ResourceLocation id, ServerPlayer player) {
        sendPacketToPlayer(player, id, PacketByteBufs.create());
    }

    /**
     * 发送数据包至玩家
     */
    public static void sendPacketToPlayer(AotakePacket packet, ServerPlayer player) {
        sendPacketToPlayer(player, packet.id(), packet.toBytes(null));
    }

    /**
     * 发送数据包至玩家
     */
    public static void sendPacketToPlayer(ServerPlayer player, ResourceLocation id) {
        sendPacketToPlayer(player, id, PacketByteBufs.create());
    }

    /**
     * 发送数据包至玩家
     */
    public static void sendPacketToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        ServerPlayNetworking.send(player, id, buf);
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
            return ServerConfig.get().defaultLanguage();
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
            result = ServerConfig.get().defaultLanguage();
        } else {
            result = language;
        }
        return result;
    }

    public static String getServerPlayerLanguage(ServerPlayer player) {
        return ServerPlayerLanguageManager.get(player);
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
            SAFE_BLOCKS_STATE = ServerConfig.get().safeConfig().safeBlocks().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS == null) {
            SAFE_BLOCKS = ServerConfig.get().safeConfig().safeBlocks().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW_STATE == null) {
            SAFE_BLOCKS_BELOW_STATE = ServerConfig.get().safeConfig().safeBlocksBelow().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW == null) {
            SAFE_BLOCKS_BELOW = ServerConfig.get().safeConfig().safeBlocksBelow().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE_STATE == null) {
            SAFE_BLOCKS_ABOVE_STATE = ServerConfig.get().safeConfig().safeBlocksAbove().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE == null) {
            SAFE_BLOCKS_ABOVE = ServerConfig.get().safeConfig().safeBlocksAbove().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
    }

    public static List<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        KeyValue<MinecraftServer, Boolean> serverInstance = AotakeSweep.serverInstance();
        if (serverInstance.val()) {
            serverInstance.key().getAllLevels()
                    .forEach(level -> level.getAllEntities().forEach(entities::add)
                    );
        }
        return entities;
    }

    public static boolean isJunkEntity(Entity entity, boolean chuck) {
        boolean result = false;
        if (entity != null && !(entity instanceof Player)) {
            if (chuck) {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.get().chunkCheckConfig().chunkCheckEntityList())) {
                    result = EnumListType.WHITE == ServerConfig.get().chunkCheckConfig().chunkCheckEntityListMode();
                }
                // 黑名单模式
                else if (EnumListType.BLACK == ServerConfig.get().chunkCheckConfig().chunkCheckEntityListMode()) {
                    result = AotakeSweep.entityFilter().validEntity(ServerConfig.get().chunkCheckConfig().chunkCheckEntityList(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.entityFilter().validEntity(ServerConfig.get().chunkCheckConfig().chunkCheckEntityList(), entity);
                }
            } else {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.get().sweepConfig().entityList())) {
                    result = EnumListType.WHITE == ServerConfig.get().sweepConfig().entityListMode();
                }
                // 黑名单模式
                else if (EnumListType.BLACK == ServerConfig.get().sweepConfig().entityListMode()) {
                    result = AotakeSweep.entityFilter().validEntity(ServerConfig.get().sweepConfig().entityList(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.entityFilter().validEntity(ServerConfig.get().sweepConfig().entityList(), entity);
                }
            }
        }
        return result;
    }

    public static boolean isSafeEntity(Map<KeyValue<Level, BlockPos>, BlockState> blockStateCache, Entity entity) {
        Level level = entity.getLevel();

        boolean stateFlag = false;
        if (!SAFE_BLOCKS.isEmpty() || !SAFE_BLOCKS_STATE.isEmpty()) {
            BlockState state = blockStateCache.computeIfAbsent(new KeyValue<>(level, entity.blockPosition())
                    , pair -> pair.getKey().getBlockState(pair.getValue()));
            stateFlag = SAFE_BLOCKS.contains(AotakeUtils.getBlockRegistryName(state))
                    || SAFE_BLOCKS_STATE.contains(state);
        }

        boolean belowFlag = false;
        if (!SAFE_BLOCKS_BELOW.isEmpty() || !SAFE_BLOCKS_BELOW_STATE.isEmpty()) {
            BlockState below = blockStateCache.computeIfAbsent(new KeyValue<>(level, entity.blockPosition().below())
                    , pair -> pair.getKey().getBlockState(pair.getValue()));
            belowFlag = SAFE_BLOCKS_BELOW.contains(AotakeUtils.getBlockRegistryName(below))
                    || SAFE_BLOCKS_BELOW_STATE.contains(below);
        }

        boolean aboveFlag = false;
        if (!SAFE_BLOCKS_ABOVE.isEmpty() || !SAFE_BLOCKS_ABOVE_STATE.isEmpty()) {
            BlockState above = blockStateCache.computeIfAbsent(new KeyValue<>(level, entity.blockPosition().above())
                    , pair -> pair.getKey().getBlockState(pair.getValue()));
            aboveFlag = SAFE_BLOCKS_ABOVE.contains(AotakeUtils.getBlockRegistryName(above))
                    || SAFE_BLOCKS_ABOVE_STATE.contains(above);
        }

        return stateFlag || belowFlag || aboveFlag;
    }

    public static List<Entity> getAllEntitiesByFilter(@Nullable List<Entity> entities, boolean chuck) {
        LOGGER.debug("Entity filter started at {}", System.currentTimeMillis());
        if (CollectionUtils.isNullOrEmpty(entities)) {
            entities = getAllEntities();
        }
        initSafeBlocks();

        Map<KeyValue<Level, BlockPos>, BlockState> blockStateCache = new HashMap<>();

        List<Entity> filtered = entities.stream().filter(entity -> !(entity instanceof Player)).toList();

        LOGGER.debug("Entity exceeded filter started at {}", System.currentTimeMillis());
        // 超限的非垃圾实体
        List<Entity> exceededEntityList = entities.stream()
                // 非垃圾实体
                .filter(entity -> !isJunkEntity(entity, chuck))
                .collect(Collectors.groupingBy(AotakeUtils::getEntityTypeRegistryName, Collectors.toList()))
                .entrySet().stream()
                // 超限
                .filter(entry -> entry.getValue().size() > ServerConfig.get().sweepConfig().entityListLimit())
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        LOGGER.debug("Entity safe filter started at {}", System.currentTimeMillis());

        // 超限的安全实体
        List<Entity> exceededBlockList = filtered.stream()
                // 安全实体
                .filter(entity -> isSafeEntity(blockStateCache, entity))
                .collect(Collectors.groupingBy(entity -> {
                    String dimension = entity.getLevel().dimension().location().toString();
                    int chunkX = entity.blockPosition().getX() / 16;
                    int chunkZ = entity.blockPosition().getZ() / 16;
                    return dimension + "," + chunkX + "," + chunkZ;
                }, Collectors.toList()))
                .entrySet().stream()
                // 超限
                .filter(entry -> entry.getValue().size() > ServerConfig.get().safeConfig().safeBlocksEntityLimit())
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        LOGGER.debug("Entity junk filter started at {}", System.currentTimeMillis());

        // 过滤
        Predicate<Entity> predicate = entity -> {
            boolean unsafe = !isSafeEntity(blockStateCache, entity);

            // 垃圾
            return (unsafe && isJunkEntity(entity, chuck))
                    // 超限的非垃圾
                    || exceededEntityList.contains(entity)
                    // 超限的安全实体
                    || exceededBlockList.contains(entity);
        };

        List<Entity> entityList = filtered.stream().filter(predicate).collect(Collectors.toList());
        LOGGER.debug("Entity filter finished at {}", System.currentTimeMillis());
        return entityList;
    }

    public static void sweep() {
        LOGGER.debug("Sweep started at {}", System.currentTimeMillis());
        List<Entity> entities = getAllEntities();
        AotakeUtils.sweep(entities, false);
        LOGGER.debug("Sweep finished at {}", System.currentTimeMillis());
    }

    /**
     * 执行清理
     *
     * @param entities 实体列表
     * @param filtered 实体列表是否已过滤
     */
    public static void sweep(List<Entity> entities, boolean filtered) {
        KeyValue<MinecraftServer, Boolean> serverInstance = AotakeSweep.serverInstance();
        // 服务器已关闭
        if (!serverInstance.val()) return;

        List<ServerPlayer> players = serverInstance.key().getPlayerList().getPlayers();

        try {
            // 若服务器没有玩家
            if (CollectionUtils.isNullOrEmpty(players) && !ServerConfig.get().sweepConfig().sweepWhenNoPlayer()) {
                LOGGER.debug("No player online, sweep canceled");
                return;
            }

            List<Entity> list = filtered ? entities : getAllEntitiesByFilter(entities, false);

            // if (CollectionUtils.isNotNullOrEmpty(list)) {
            // 清空旧的物品
            if (ServerConfig.get().dustbinConfig().selfCleanMode().contains(EnumSelfCleanMode.SWEEP_CLEAR.name())) {
                switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
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
            AotakeSweep.entitySweeper().addDrops(list, new SweepResult());
            // }

        } catch (Exception e) {
            LOGGER.error(e);
            for (ServerPlayer p : players) {
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
                                            , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = getWarningVoice("error");
                    float volume = ServerConfig.get().sweepConfig().sweepWarningVoiceVolume() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        }
    }

    private static void clearVirtualDustbin() {
        WorldTrashData.get().getDropList().clear();
        List<SimpleContainer> inventories = WorldTrashData.get().getInventoryList();
        if (CollectionUtils.isNotNullOrEmpty(inventories)) inventories.forEach(SimpleContainer::clearContent);
        WorldTrashData.get().setDirty();
    }

    private static void clearDustbinBlock() {
        for (String pos : ServerConfig.get().dustbinConfig().dustbinBlockPositions()) {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
            if (coordinate != null) {
                AotakeUtils.clearStorage(AotakeUtils.getBlockItemHandler(coordinate));
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
                    CompoundTag entityTag = aotake.getCompound("entity");
                    sanitizeCapturedEntityTag(entityTag);
                    result = EntityType.loadEntityRecursive(entityTag, level, e -> e);
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

    public static CompoundTag sanitizeCapturedEntityTag(CompoundTag entityTag) {
        if (entityTag == null) return new CompoundTag();
        entityTag.remove("Passengers");
        entityTag.remove("Vehicle");
        entityTag.remove("RootVehicle");
        entityTag.remove("UUID");
        entityTag.remove("UUIDMost");
        entityTag.remove("UUIDLeast");
        return entityTag;
    }

    private static final Map<String, String> warns = new HashMap<>();
    private static final Map<String, String> voices = new HashMap<>();

    private static void initWarns() {
        if (warns.isEmpty()) {
            warns.putAll(JsonUtils.GSON.fromJson(ServerConfig.get().sweepConfig().sweepWarningContent(), new TypeToken<Map<String, String>>() {
            }.getType()));
            warns.putIfAbsent("error", "message.aotake_sweep.cleanup_error");
            warns.putIfAbsent("fail", "message.aotake_sweep.cleanup_started");
            warns.putIfAbsent("success", "message.aotake_sweep.cleanup_started");
        }
        if (voices.isEmpty()) {
            voices.putAll(JsonUtils.GSON.fromJson(ServerConfig.get().sweepConfig().sweepWarningVoice(), new TypeToken<Map<String, String>>() {
            }.getType()));
            voices.put("initialized", "initialized");
        }
    }

    public static void clearWarns() {
        warns.clear();
        voices.clear();
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


    // region 垃圾箱相关

    public static int dustbin(@NonNull ServerPlayer player, int page) {
        int result = 0;
        int vPage = ServerConfig.get().dustbinConfig().dustbinPageLimit();
        int bPage = ServerConfig.get().dustbinConfig().dustbinBlockPositions().size();
        int totalPage = getDustbinTotalPage();
        if (totalPage <= 0) {
            AotakeUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "dustbin_page_empty"));
        } else {
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
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
        }

        if (result > 0) AotakeSweep.playerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    private static int openVirtualDustbin(@NonNull ServerPlayer player, int page) {
        MenuProvider trashContainer = WorldTrashData.getTrashContainer(player, page);
        if (trashContainer == null) return 0;
        int result = player.openMenu(trashContainer).orElse(0);

        if (result > 0) AotakeSweep.playerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    private static int openDustbinBlock(@NonNull ServerPlayer player, int page) {
        int result = 0;
        List<? extends String> positions = ServerConfig.get().dustbinConfig().dustbinBlockPositions();
        if (CollectionUtils.isNotNullOrEmpty(positions) && positions.size() >= page) {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(positions.get(page - 1));

            Direction direction = coordinate.getDirection();
            if (direction == null) direction = Direction.UP;
            // 命中点：方块中心或面上
            Vec3 center = coordinate.toVec3().add(0.5, 0.5, 0.5);
            Vec3 hitVec = center.add(direction.getStepX() * 0.500001, direction.getStepY() * 0.500001, direction.getStepZ() * 0.500001);

            BlockHitResult ray = new BlockHitResult(hitVec, direction, coordinate.toBlockPos(), false);

            BlockState state = player.getLevel().getBlockState(coordinate.toBlockPos());
            InteractionResult res = state.use(player.getLevel(), player, InteractionHand.MAIN_HAND, ray);
            if (res.consumesAction()) {
                result = 1;
            }
        }

        if (result > 0) AotakeSweep.playerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
        return result;
    }

    public static void clearVirtualDustbin(int page) {
        List<SimpleContainer> inventories = WorldTrashData.get().getInventoryList();
        if (page == 0) {
            inventories.forEach(SimpleContainer::clearContent);
        } else {
            SimpleContainer inventory = CollectionUtils.getOrDefault(inventories, page - 1, null);
            if (inventory != null) inventory.clearContent();
        }
        WorldTrashData.get().setDirty();
    }

    public static void clearDustbinBlock(int page) {
        if (page == 0) {
            for (String pos : ServerConfig.get().dustbinConfig().dustbinBlockPositions()) {
                WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
                if (coordinate != null) {
                    AotakeUtils.clearStorage(AotakeUtils.getBlockItemHandler(coordinate));
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(ServerConfig.get().dustbinConfig().dustbinBlockPositions().get(page - 1));
            if (coordinate != null) {
                AotakeUtils.clearStorage(AotakeUtils.getBlockItemHandler(coordinate));
            }
        }
    }

    public static void dropVirtualDustbin(ServerPlayer player, int page) {
        List<SimpleContainer> inventoryList = new ArrayList<>();
        List<SimpleContainer> inventories = WorldTrashData.get().getInventoryList();
        if (page == 0) {
            if (CollectionUtils.isNotNullOrEmpty(inventories)) inventoryList.addAll(inventories);
        } else {
            SimpleContainer inventory = CollectionUtils.getOrDefault(inventories, page - 1, null);
            if (inventory != null) inventoryList.add(inventory);
        }
        inventoryList.forEach(inventory -> inventory.removeAllItems()
                .forEach(item -> {
                    if (!item.isEmpty()) {
                        Entity entity = AotakeUtils.getEntityFromItem(player.getLevel(), item);
                        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                        player.getLevel().addFreshEntity(entity);
                    }
                })
        );
        WorldTrashData.get().setDirty();
    }

    public static void dropDustbinBlock(ServerPlayer player, int page) {
        Consumer<WorldCoordinate> processCoord = coordinate -> {
            if (coordinate == null) return;
            Storage<ItemVariant> storage = AotakeUtils.getBlockItemHandler(coordinate);
            if (storage == null) return;

            try {
                for (StorageView<ItemVariant> view : storage) {
                    if (view == null || view.isResourceBlank()) continue;
                    ItemVariant variant = view.getResource();
                    long amount = view.getAmount();
                    if (amount <= 0) continue;

                    try (Transaction tx = Transaction.openOuter()) {
                        long extracted = storage.extract(variant, amount, tx);
                        tx.commit();
                        if (extracted > 0) {
                            ItemStack stack = variant.toStack((int) extracted);
                            if (!stack.isEmpty()) {
                                Entity entity = AotakeUtils.getEntityFromItem(player.getLevel(), stack);
                                entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                                player.getLevel().addFreshEntity(entity);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        };

        List<String> positions = ServerConfig.get().dustbinConfig().dustbinBlockPositions();

        if (page == 0) {
            for (String pos : positions) {
                WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
                processCoord.accept(coordinate);
            }
        } else {
            if (page - 1 >= 0 && page - 1 < positions.size()) {
                WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(positions.get(page - 1));
                processCoord.accept(coordinate);
            }
        }
    }

    public static int getDustbinTotalPage() {
        int result = 0;
        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
            case VIRTUAL: {
                result = ServerConfig.get().dustbinConfig().dustbinPageLimit();
            }
            break;
            case BLOCK: {
                result = ServerConfig.get().dustbinConfig().dustbinBlockPositions().size();
            }
            break;
            case VIRTUAL_BLOCK: {
                result = ServerConfig.get().dustbinConfig().dustbinPageLimit() + ServerConfig.get().dustbinConfig().dustbinBlockPositions().size();
            }
            break;
            case BLOCK_VIRTUAL: {
                result = ServerConfig.get().dustbinConfig().dustbinBlockPositions().size() + ServerConfig.get().dustbinConfig().dustbinPageLimit();
            }
            break;
        }
        return result;
    }

    // endregion 垃圾箱相关


    // region nbt文件读写

    public static CompoundTag readCompressed(InputStream stream) {
        try {
            return NbtIo.readCompressed(stream);
        } catch (Exception e) {
            LOGGER.error("Failed to read compressed stream", e);
            return new CompoundTag();
        }
    }

    public static CompoundTag readCompressed(File file) {
        try {
            return NbtIo.readCompressed(file);
        } catch (Exception e) {
            LOGGER.error("Failed to read compressed file: {}", file.getAbsolutePath(), e);
            return new CompoundTag();
        }
    }

    public static boolean writeCompressed(CompoundTag tag, File file) {
        boolean result = false;
        try {
            NbtIo.writeCompressed(tag, file);
            result = true;
        } catch (Exception e) {
            LOGGER.error("Failed to write compressed file: {}", file.getAbsolutePath(), e);
        }
        return result;
    }

    public static boolean writeCompressed(CompoundTag tag, OutputStream stream) {
        boolean result = false;
        try {
            NbtIo.writeCompressed(tag, stream);
            result = true;
        } catch (Exception e) {
            LOGGER.error("Failed to write compressed stream", e);
        }
        return result;
    }

    // endregion nbt文件读写


    // region 杂项

    /**
     * 获取指定维度的世界实例
     */
    public static ServerLevel getWorld(ResourceKey<Level> dimension) {
        return AotakeSweep.serverInstance().key().getLevel(dimension);
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
            return BlockStateParser.parseForBlock(AotakeSweep.serverInstance().key().getAllLevels().iterator().next().holderLookup(Registries.BLOCK)
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
        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        return location == null ? "" : location.toString();
    }

    /**
     * 获取实体类型注册ID
     */
    @NonNull
    public static String getEntityTypeRegistryName(@NonNull Entity entity) {
        if (entity instanceof ItemEntity) {
            return getItemRegistryName(((ItemEntity) entity).getItem());
        }
        return getEntityTypeRegistryName(entity.getType());
    }

    /**
     * 获取实体类型注册ID
     */
    @NonNull
    public static String getEntityTypeRegistryName(@NonNull EntityType<?> entityType) {
        ResourceLocation location = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
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

    public static String getPlayerName(@NonNull Player player) {
        return player.getName().getString();
    }

    public static ServerPlayer getPlayerByUUID(String uuid) {
        return AotakeSweep.serverInstance().key().getPlayerList().getPlayer(UUID.fromString(uuid));
    }

    /**
     * 将物品添加到指定的容器
     */
    public static ItemStack addItemToStorage(ItemStack stack, Storage<ItemVariant> storage) {
        if (stack == null || stack.isEmpty() || storage == null || !storage.supportsInsertion()) {
            return stack;
        }

        try {
            ItemVariant variant = ItemVariant.of(stack);
            long toInsert = stack.getCount();

            try (Transaction tx = Transaction.openOuter()) {
                long inserted = storage.insert(variant, toInsert, tx);
                tx.commit();

                int remainingCount = (int) (toInsert - inserted);

                if (remainingCount <= 0) {
                    return ItemStack.EMPTY;
                } else {
                    ItemStack remaining = stack.copy();
                    remaining.setCount(remainingCount);
                    return remaining;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to add item to storage", t);
        }
        return stack;
    }

    /**
     * 将物品添加到指定的方块容器
     */
    public static ItemStack addItemToBlock(ItemStack stack, WorldCoordinate coordinate) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        ServerLevel level = AotakeSweep.serverInstance().key().getLevel(coordinate.getDimension());
        if (level == null) return stack;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return stack;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return stack;

        try {
            Direction side = coordinate.getDirection();
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);

            ItemStack remaining = addItemToStorage(stack, storage);
            if (remaining.getCount() != stack.getCount()) {
                te.setChanged();
            }
            return remaining;
        } catch (Throwable t) {
            LOGGER.warn("Failed to insert item into block at {}", coordinate, t);
        }

        return stack;
    }

    /**
     * 获取指定的方块容器
     */
    @Nullable
    public static Storage<ItemVariant> getBlockItemHandler(WorldCoordinate coordinate) {
        ServerLevel level = AotakeSweep.serverInstance().key().getLevel(coordinate.getDimension());
        if (level == null) return null;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return null;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return null;

        try {
            Direction side = coordinate.getDirection();
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
            if (storage != null && storage.supportsInsertion()) {
                return storage;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void clearStorage(Storage<ItemVariant> storage) {
        if (storage == null) return;

        try (Transaction tx = Transaction.openOuter()) {
            for (StorageView<ItemVariant> view : storage) {
                if (!view.isResourceBlank()) {
                    long amount = view.getAmount();
                    if (amount > 0) {
                        storage.extract(view.getResource(), amount, tx);
                    }
                }
            }
            tx.commit();
        } catch (Throwable ignored) {
        }
    }

    public static String getDimensionRegistryName(Level world) {
        return world.dimension().location().toString();
    }

    // endregion 杂项

}
