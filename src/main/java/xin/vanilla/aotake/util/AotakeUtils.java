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
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
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
import xin.vanilla.aotake.config.WarningConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.mixin.ServerPlayerAccessor;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.network.packet.DustbinPageSyncToClient;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


@SuppressWarnings({"resource"})
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
            case CONFIG:
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
            case CATCH_PLAYER:
                return ServerConfig.PERMISSION_CATCH_PLAYER.get();
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
    public static String genTeleportCommand(WorldCoordinate coordinate) {
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

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayerEntity player, @NonNull String command, int permission, boolean suppressedOutput) {
        boolean result = false;
        try {
            MinecraftServer server = player.getServer();
            CommandSource commandSourceStack = player.createCommandSourceStack();
            if (permission > 0) {
                commandSourceStack = commandSourceStack.withPermission(permission);
            }
            if (suppressedOutput) {
                commandSourceStack = commandSourceStack.withSuppressedOutput();
            }
            result = server.getCommands().performCommand(commandSourceStack, command) > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result;
    }

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayerEntity player, @NonNull String command) {
        return executeCommand(player, command, 0, false);
    }

    /**
     * 执行指令
     */
    public static boolean executeCommandNoOutput(@NonNull ServerPlayerEntity player, @NonNull String command) {
        return executeCommandNoOutput(player, command, 0);
    }

    /**
     * 执行指令
     */
    public static boolean executeCommandNoOutput(@NonNull ServerPlayerEntity player, @NonNull String command, int permission) {
        return executeCommand(player, command, permission, true);
    }

    public static void refreshPermission(@NonNull ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            server = AotakeSweep.getServerInstance().getKey();
        }
        server.getPlayerList().sendPlayerPermissionLevel(player);
    }

    // endregion 指令相关


    // region 消息相关

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
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(PlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)), player.getUUID());
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
        return PlayerLanguageManager.get(player);
    }

    /**
     * 复制玩家语言设置
     *
     * @param originalPlayer 原始玩家
     * @param targetPlayer   目标玩家
     */
    public static void clonePlayerLanguage(ServerPlayerEntity originalPlayer, ServerPlayerEntity targetPlayer) {
        ((ServerPlayerAccessor) targetPlayer).language(((ServerPlayerAccessor) originalPlayer).language());
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

    public static boolean isJunkEntity(Entity entity, boolean chuck) {
        boolean result = false;
        if (entity != null && !(entity instanceof PlayerEntity)) {
            if (chuck) {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.CHUNK_CHECK_ENTITY_LIST.get())) {
                    result = EnumListType.WHITE.name().equals(ServerConfig.CHUNK_CHECK_ENTITY_LIST_MODE.get());
                }
                // 黑名单模式
                else if (EnumListType.BLACK.name().equals(ServerConfig.CHUNK_CHECK_ENTITY_LIST_MODE.get())) {
                    result = AotakeSweep.getEntityFilter().validEntity(ServerConfig.CHUNK_CHECK_ENTITY_LIST.get(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.getEntityFilter().validEntity(ServerConfig.CHUNK_CHECK_ENTITY_LIST.get(), entity);
                }
            } else {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(ServerConfig.ENTITY_LIST.get())) {
                    result = EnumListType.WHITE.name().equals(ServerConfig.ENTITY_LIST_MODE.get());
                }
                // 黑名单模式
                else if (EnumListType.BLACK.name().equals(ServerConfig.ENTITY_LIST_MODE.get())) {
                    result = AotakeSweep.getEntityFilter().validEntity(ServerConfig.ENTITY_LIST.get(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.getEntityFilter().validEntity(ServerConfig.ENTITY_LIST.get(), entity);
                }
            }
        }
        return result;
    }

    public static boolean isSafeEntity(Map<KeyValue<World, BlockPos>, BlockState> blockStateCache, Entity entity) {
        World level = entity.level;

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

        Map<KeyValue<World, BlockPos>, BlockState> blockStateCache = new HashMap<>();

        boolean hasSafeRules = !SAFE_BLOCKS.isEmpty()
                || !SAFE_BLOCKS_STATE.isEmpty()
                || !SAFE_BLOCKS_BELOW.isEmpty()
                || !SAFE_BLOCKS_BELOW_STATE.isEmpty()
                || !SAFE_BLOCKS_ABOVE.isEmpty()
                || !SAFE_BLOCKS_ABOVE_STATE.isEmpty();

        List<Entity> filtered = new ArrayList<>(entities.size());
        Map<String, Integer> nonJunkTypeCounts = new HashMap<>();
        Map<ChunkKey, Integer> safeChunkCounts = new HashMap<>();
        IdentityHashMap<Entity, Boolean> junkCache = new IdentityHashMap<>();
        IdentityHashMap<Entity, Boolean> safeCache = new IdentityHashMap<>();
        IdentityHashMap<Entity, String> typeCache = new IdentityHashMap<>();
        IdentityHashMap<Entity, ChunkKey> chunkKeyCache = new IdentityHashMap<>();

        LOGGER.debug("Entity exceeded filter started at {}", System.currentTimeMillis());
        for (Entity entity : entities) {
            if (entity instanceof PlayerEntity) continue;
            filtered.add(entity);

            boolean safe = hasSafeRules && isSafeEntity(blockStateCache, entity);
            safeCache.put(entity, safe);
            if (safe) {
                ChunkKey key = ChunkKey.of(entity);
                chunkKeyCache.put(entity, key);
                safeChunkCounts.merge(key, 1, Integer::sum);
            }

            boolean junk = isJunkEntity(entity, chuck);
            junkCache.put(entity, junk);
            if (!junk) {
                String type = getEntityTypeRegistryName(entity);
                typeCache.put(entity, type);
                nonJunkTypeCounts.merge(type, 1, Integer::sum);
            }
        }

        LOGGER.debug("Entity safe filter started at {}", System.currentTimeMillis());
        int typeLimit = ServerConfig.ENTITY_LIST_LIMIT.get();
        Set<String> exceededTypes = new HashSet<>();
        for (Map.Entry<String, Integer> entry : nonJunkTypeCounts.entrySet()) {
            if (entry.getValue() > typeLimit) {
                exceededTypes.add(entry.getKey());
            }
        }

        int safeLimit = CommonConfig.SAFE_BLOCKS_ENTITY_LIMIT.get();
        Set<ChunkKey> exceededChunks = new HashSet<>();
        for (Map.Entry<ChunkKey, Integer> entry : safeChunkCounts.entrySet()) {
            if (entry.getValue() > safeLimit) {
                exceededChunks.add(entry.getKey());
            }
        }

        LOGGER.debug("Entity junk filter started at {}", System.currentTimeMillis());
        List<Entity> entityList = new ArrayList<>();
        for (Entity entity : filtered) {
            boolean safe = safeCache.getOrDefault(entity, false);
            boolean junk = junkCache.getOrDefault(entity, false);
            boolean exceededType = false;
            if (!junk && !exceededTypes.isEmpty()) {
                String type = typeCache.get(entity);
                if (type == null) {
                    type = getEntityTypeRegistryName(entity);
                    typeCache.put(entity, type);
                }
                exceededType = exceededTypes.contains(type);
            }
            boolean exceededSafe = false;
            if (safe && !exceededChunks.isEmpty()) {
                ChunkKey key = chunkKeyCache.get(entity);
                if (key == null) {
                    key = ChunkKey.of(entity);
                    chunkKeyCache.put(entity, key);
                }
                exceededSafe = exceededChunks.contains(key);
            }
            if ((!safe && junk) || exceededType || exceededSafe) {
                entityList.add(entity);
            }
        }
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

            List<Entity> list = filtered ? entities : getAllEntitiesByFilter(entities, false);

            // if (CollectionUtils.isNotNullOrEmpty(list)) {
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
            AotakeSweep.getEntitySweeper().addDrops(list, new SweepResult());
            // }

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
                                            , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
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
            WorldCoordinate coordinate = WorldCoordinate.fromSimpleString(pos);
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

    public static CompoundNBT sanitizeCapturedEntityTag(CompoundNBT entityTag) {
        if (entityTag == null) return new CompoundNBT();
        entityTag.remove("Passengers");
        entityTag.remove("Vehicle");
        entityTag.remove("RootVehicle");
        entityTag.remove("UUID");
        entityTag.remove("UUIDMost");
        entityTag.remove("UUIDLeast");
        return entityTag;
    }

    private static final List<Map<String, List<String>>> warnGroups = new ArrayList<>();
    private static final List<Map<String, List<String>>> voiceGroups = new ArrayList<>();
    private static Map<String, List<String>> activeWarnGroup = new HashMap<>();
    private static long activeWarnGroupSweepTime = Long.MIN_VALUE;
    private static Map<String, List<String>> activeVoiceGroup = new HashMap<>();
    private static long activeVoiceGroupSweepTime = Long.MIN_VALUE;


    public static void clearWarns() {
        warnGroups.clear();
        voiceGroups.clear();
        activeWarnGroup = new HashMap<>();
        activeWarnGroupSweepTime = Long.MIN_VALUE;
        activeVoiceGroup = new HashMap<>();
        activeVoiceGroupSweepTime = Long.MIN_VALUE;
    }

    private static void initWarns() {
        if (!warnGroups.isEmpty() && !voiceGroups.isEmpty()) {
            return;
        }
        WarningConfig.WarningGroupData data = WarningConfig.loadWarningGroups();
        warnGroups.clear();
        voiceGroups.clear();
        if (CollectionUtils.isNotNullOrEmpty(data.contentGroups())) {
            warnGroups.addAll(data.contentGroups());
        }
        if (CollectionUtils.isNotNullOrEmpty(data.voiceGroups())) {
            voiceGroups.addAll(data.voiceGroups());
        }
    }

    public static boolean hasWarning(String key) {
        initWarns();
        Map<String, List<String>> group = getActiveWarnGroup();
        return CollectionUtils.isNotNullOrEmpty(group.get(key));
    }

    public static Component getWarningMessage(String key, String lang, @Nullable SweepResult result) {
        Component msg = null;
        try {
            initWarns();
            Map<String, List<String>> group = getActiveWarnGroup();
            String text = CollectionUtils.getRandomElement(group.get(key));
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

    private static Map<String, List<String>> getActiveWarnGroup() {
        if (warnGroups.isEmpty()) {
            return Collections.emptyMap();
        }
        long sweepTime = EventHandlerProxy.getNextSweepTime();
        if (activeWarnGroupSweepTime != sweepTime || activeWarnGroup.isEmpty()) {
            activeWarnGroupSweepTime = sweepTime;
            Map<String, List<String>> selected = CollectionUtils.getRandomElement(warnGroups);
            activeWarnGroup = selected != null ? selected : warnGroups.get(0);
        }
        return activeWarnGroup;
    }

    private static Map<String, List<String>> getActiveVoiceGroup() {
        if (voiceGroups.isEmpty()) {
            return Collections.emptyMap();
        }
        long sweepTime = EventHandlerProxy.getNextSweepTime();
        if (activeVoiceGroupSweepTime != sweepTime || activeVoiceGroup.isEmpty()) {
            activeVoiceGroupSweepTime = sweepTime;
            Map<String, List<String>> selected = CollectionUtils.getRandomElement(voiceGroups);
            activeVoiceGroup = selected != null ? selected : voiceGroups.get(0);
        }
        return activeVoiceGroup;
    }

    public static boolean hasWarningVoice(String key) {
        initWarns();
        Map<String, List<String>> group = getActiveVoiceGroup();
        return CollectionUtils.isNotNullOrEmpty(group.get(key));
    }

    public static String getWarningVoice(String key) {
        String id = null;
        try {
            initWarns();
            Map<String, List<String>> group = getActiveVoiceGroup();
            id = CollectionUtils.getRandomElement(group.get(key));
        } catch (Exception ignored) {
        }
        return id;
    }

    // endregion 扫地


    // region 垃圾箱相关

    public static int dustbin(@NonNull ServerPlayerEntity player, int page) {
        int result = 0;
        int vPage = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
        int bPage = ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size();
        int totalPage = getDustbinTotalPage();
        if (totalPage <= 0) {
            AotakeUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "dustbin_page_empty"));
        } else {
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
        }

        if (result > 0) {
            AotakeSweep.getPlayerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
            AotakeUtils.sendPacketToPlayer(new DustbinPageSyncToClient(page, totalPage), player);
        }
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

    public static void clearVirtualDustbin(int page) {
        List<Inventory> inventories = WorldTrashData.get().getInventoryList();
        if (page == 0) {
            inventories.forEach(Inventory::clearContent);
        } else {
            Inventory inventory = CollectionUtils.getOrDefault(inventories, page - 1, null);
            if (inventory != null) inventory.clearContent();
        }
        WorldTrashData.get().setDirty();
    }

    public static void clearDustbinBlock(int page) {
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

    public static void dropVirtualDustbin(ServerPlayerEntity player, int page) {
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

    public static void dropDustbinBlock(ServerPlayerEntity player, int page) {
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

    // endregion 垃圾箱相关


    // region nbt文件读写

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

    public static boolean hasAotakeTag(ItemStack item) {
        if (item == null) return false;
        CompoundNBT tag = item.getTag();
        return tag != null && tag.contains(AotakeSweep.MODID);
    }

    public static CompoundNBT getAotakeTag(@NonNull ItemStack item) {
        CompoundNBT tag = item.getTag();
        if (tag == null) {
            tag = new CompoundNBT();
            item.setTag(tag);
        }
        if (!tag.contains(AotakeSweep.MODID)) {
            tag.put(AotakeSweep.MODID, new CompoundNBT());
        }
        return tag.getCompound(AotakeSweep.MODID);
    }

    public static void setAotakeTag(@NonNull ItemStack item, CompoundNBT aotakeTag) {
        CompoundNBT tag = item.getTag();
        if (tag == null) {
            tag = new CompoundNBT();
            item.setTag(tag);
        }
        tag.put(AotakeSweep.MODID, aotakeTag);
    }

    public static CompoundNBT clearAotakeTag(ItemStack item) {
        if (item == null) return null;
        CompoundNBT tag = item.getTag();
        if (tag != null) {
            tag.remove(AotakeSweep.MODID);
        }
        return tag;
    }

    public static void clearItemTag(ItemStack item) {
        if (item == null) return;
        CompoundNBT tag = item.getTag();
        if (tag != null && tag.isEmpty()) {
            item.setTag(null);
        }
    }

    public static void clearItemTagEx(ItemStack item) {
        if (item == null) return;
        CompoundNBT tag = clearAotakeTag(item);
        if (tag != null && tag.isEmpty()) {
            item.setTag(null);
        }
    }

    // endregion nbt文件读写


    // region 杂项

    /**
     * 获取指定维度的世界实例
     */
    public static ServerWorld getWorld(RegistryKey<World> dimension) {
        return AotakeSweep.getServerInstance().key().getLevel(dimension);
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
        if (entity instanceof ItemEntity) {
            return getItemRegistryName(((ItemEntity) entity).getItem());
        }
        EntityType<?> entityType = entity.getType();
        ResourceLocation location = ForgeRegistries.ENTITIES.getKey(entityType);
        if (location == null) location = entityType.getRegistryName();
        return location == null ? AotakeSweep.emptyIdentifier().toString() : location.toString();
    }

    public static String getItemCustomNameJson(@NonNull ItemStack itemStack) {
        String result = "";
        CompoundNBT compoundnbt = itemStack.getTagElement("display");
        if (compoundnbt != null && compoundnbt.contains("Name", 8)) {
            result = compoundnbt.getString("Name");
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

    public static ServerPlayerEntity getPlayerByUUID(String uuid) {
        return AotakeSweep.getServerInstance().key().getPlayerList().getPlayer(UUID.fromString(uuid));
    }

    /**
     * 将物品添加到指定的方块容器
     */
    public static ItemStack addItemToBlock(ItemStack stack, WorldCoordinate coordinate) {
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
    public static IItemHandler getBlockItemHandler(WorldCoordinate coordinate) {
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

    public static String getDimensionRegistryName(World world) {
        return world.dimension().location().toString();
    }

    public static <T> List<T> singleList(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    // endregion 杂项

}
