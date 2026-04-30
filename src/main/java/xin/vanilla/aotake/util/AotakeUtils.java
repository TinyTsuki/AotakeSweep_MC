package xin.vanilla.aotake.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
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
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.WarningConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.DustbinPageSyncToClient;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;
import xin.vanilla.banira.internal.config.CustomConfig;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings({"resource", "UnstableApiUsage"})
public class AotakeUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    // region 指令相关

    /**
     * 获取指令前缀
     */
    public static String getCommandPrefix() {
        String commandPrefix = CommonConfig.get().command().commandPrefix();
        if (StringUtils.isNullOrEmptyEx(commandPrefix) || !commandPrefix.matches("^(\\w ?)+$")) {
            CommonConfig.get().command().commandPrefix(AotakeSweep.DEFAULT_COMMAND_PREFIX);
            CommonConfig.save();
        }
        return CommonConfig.get().command().commandPrefix().trim();
    }

    /**
     * 获取完整的指令
     */
    public static String getCommand(EnumCommandType type) {
        String prefix = AotakeUtils.getCommandPrefix();
        return switch (type) {
            case HELP -> prefix + " help";
            case LANGUAGE -> prefix + " " + CommonConfig.get().command().commandLanguage();
            case LANGUAGE_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandLanguage() : "";
            case VIRTUAL_OP -> prefix + " " + CommonConfig.get().command().commandVirtualOp();
            case VIRTUAL_OP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandVirtualOp() : "";
            case DUSTBIN_OPEN, DUSTBIN_OPEN_OTHER ->
                    prefix + " " + CommonConfig.get().command().commandDustbinOpen();
            case DUSTBIN_OPEN_CONCISE, DUSTBIN_OPEN_OTHER_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinOpen() : "";
            case DUSTBIN_CLEAR -> prefix + " " + CommonConfig.get().command().commandDustbinClear();
            case DUSTBIN_CLEAR_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinClear() : "";
            case DUSTBIN_DROP -> prefix + " " + CommonConfig.get().command().commandDustbinDrop();
            case DUSTBIN_DROP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinDrop() : "";
            case CACHE_CLEAR -> prefix + " " + CommonConfig.get().command().commandCacheClear();
            case CACHE_CLEAR_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandCacheClear() : "";
            case CACHE_DROP -> prefix + " " + CommonConfig.get().command().commandCacheDrop();
            case CACHE_DROP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandCacheDrop() : "";
            case SWEEP -> prefix + " " + CommonConfig.get().command().commandSweep();
            case SWEEP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandSweep() : "";
            case CLEAR_DROP -> prefix + " " + CommonConfig.get().command().commandClearDrop();
            case CLEAR_DROP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandClearDrop() : "";
            case DELAY_SWEEP -> prefix + " " + CommonConfig.get().command().commandDelaySweep();
            case DELAY_SWEEP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDelaySweep() : "";
            case CHUNK_VAULT -> prefix + " " + CommonConfig.get().command().commandChunkVault();
            case CHUNK_VAULT_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandChunkVault() : "";
            default -> "";
        };
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        return switch (type) {
            case CONFIG, VIRTUAL_OP, VIRTUAL_OP_CONCISE -> CommonConfig.get().permission().permissionVirtualOp();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE -> CommonConfig.get().permission().permissionDustbinOpen();
            case DUSTBIN_OPEN_OTHER, DUSTBIN_OPEN_OTHER_CONCISE ->
                    CommonConfig.get().permission().permissionDustbinOpenOther();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> CommonConfig.get().permission().permissionDustbinClear();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> CommonConfig.get().permission().permissionDustbinDrop();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> CommonConfig.get().permission().permissionCacheClear();
            case CACHE_DROP, CACHE_DROP_CONCISE -> CommonConfig.get().permission().permissionCacheDrop();
            case SWEEP, SWEEP_CONCISE -> CommonConfig.get().permission().permissionSweep();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> CommonConfig.get().permission().permissionClearDrop();
            case DELAY_SWEEP, DELAY_SWEEP_CONCISE -> CommonConfig.get().permission().permissionDelaySweep();
            case CHUNK_VAULT, CHUNK_VAULT_CONCISE -> CommonConfig.get().permission().permissionChunkVault();
            case CATCH_PLAYER -> CommonConfig.get().permission().permissionCatchPlayer();
            default -> 0;
        };
    }

    /**
     * 判断指令是否启用简短模式
     */
    public static boolean isConciseEnabled(EnumCommandType type) {
        return switch (type) {
            case LANGUAGE, LANGUAGE_CONCISE -> CommonConfig.get().concise().conciseLanguage();
            case VIRTUAL_OP, VIRTUAL_OP_CONCISE -> CommonConfig.get().concise().conciseVirtualOp();
            case DUSTBIN_OPEN, DUSTBIN_OPEN_CONCISE, DUSTBIN_OPEN_OTHER, DUSTBIN_OPEN_OTHER_CONCISE ->
                    CommonConfig.get().concise().conciseDustbinOpen();
            case DUSTBIN_CLEAR, DUSTBIN_CLEAR_CONCISE -> CommonConfig.get().concise().conciseDustbinClear();
            case DUSTBIN_DROP, DUSTBIN_DROP_CONCISE -> CommonConfig.get().concise().conciseDustbinDrop();
            case CACHE_CLEAR, CACHE_CLEAR_CONCISE -> CommonConfig.get().concise().conciseCacheClear();
            case CACHE_DROP, CACHE_DROP_CONCISE -> CommonConfig.get().concise().conciseCacheDrop();
            case SWEEP, SWEEP_CONCISE -> CommonConfig.get().concise().conciseSweep();
            case CLEAR_DROP, CLEAR_DROP_CONCISE -> CommonConfig.get().concise().conciseClearDrop();
            case DELAY_SWEEP, DELAY_SWEEP_CONCISE -> CommonConfig.get().concise().conciseDelaySweep();
            case CHUNK_VAULT, CHUNK_VAULT_CONCISE -> CommonConfig.get().concise().conciseChunkVault();
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
        if (source instanceof Player player) {
            return VirtualPermissionManager.getVirtualPermission(player, EnumCommandType.class).contains(type);
        }
        return false;
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
                , coordinate.dimensionId()
                , coordinate.xInt()
                , coordinate.yInt()
                , coordinate.zInt()
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

    public static void refreshPermission(@NonNull ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            server = AotakeSweep.serverInstance().key();
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
        String lang = CommonConfig.get().base().common().defaultLanguage();
        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable("chat.type.announcement", net.minecraft.network.chat.Component.literal("Server"), message.toVanilla(lang)), false);
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(Player player, Component message) {
        player.sendSystemMessage(message.toVanilla(AotakeUtils.getPlayerLanguage(player)));
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(Player player, String key, Object... args) {
        String lang = AotakeUtils.getPlayerLanguage(player);
        player.sendSystemMessage(AotakeComponent.get().transLang(lang, EnumI18nType.FORMAT, key, args).toVanilla(lang));
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
            String lang = CommonConfig.get().base().common().defaultLanguage();
            source.sendSuccess(AotakeComponent.get().transLang(lang, EnumI18nType.FORMAT, key, args).toVanilla(lang), false);
        } else {
            source.sendFailure(AotakeComponent.get().transLang(CommonConfig.get().base().common().defaultLanguage(), EnumI18nType.FORMAT, key, args).toVanilla(CommonConfig.get().base().common().defaultLanguage()));
        }
    }

    /**
     * 发送操作栏消息
     */
    public static void sendActionBarMessage(ServerPlayer player, Component message) {
        player.displayClientMessage(message.toVanilla(AotakeUtils.getPlayerLanguage(player)), true);
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
     * 发送 Aotake 已注册网络包至服务器（客户端调用）。
     */
    @Environment(EnvType.CLIENT)
    public static void sendPacketToServer(AotakeNetworkPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!PlayerUtils.isRemoteServerModInstalled(mc.player, AotakeSweep.MODID)) {
            return;
        }
        ResourceLocation channel = NetworkInit.HANDLER.channel();
        if (!ClientPlayNetworking.canSend(channel)) {
            return;
        }
        ClientPlayNetworking.send(channel, NetworkInit.HANDLER.encode(packet));
    }

    /**
     * 发送 Aotake 已注册网络包至玩家（服务端调用）。
     */
    public static void sendPacketToPlayer(AotakeNetworkPacket packet, ServerPlayer player) {
        ResourceLocation channel = NetworkInit.HANDLER.channel();
        if (!ServerPlayNetworking.canSend(player, channel)) {
            return;
        }
        if (!PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
            return;
        }
        ServerPlayNetworking.send(player, channel, NetworkInit.HANDLER.encode(packet));
    }

    public static void sendPacketToPlayer(ServerPlayer player, AotakeNetworkPacket packet) {
        sendPacketToPlayer(packet, player);
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
            return CommonConfig.get().base().common().defaultLanguage();
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
            result = CommonConfig.get().base().common().defaultLanguage();
        } else {
            result = language;
        }
        return result;
    }

    public static String getServerPlayerLanguage(ServerPlayer player) {
        return PlayerLanguageManager.get(player);
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
            SAFE_BLOCKS_STATE = CommonConfig.get().base().safe().safeBlocks().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS == null) {
            SAFE_BLOCKS = CommonConfig.get().base().safe().safeBlocks().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW_STATE == null) {
            SAFE_BLOCKS_BELOW_STATE = CommonConfig.get().base().safe().safeBlocksBelow().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_BELOW == null) {
            SAFE_BLOCKS_BELOW = CommonConfig.get().base().safe().safeBlocksBelow().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE_STATE == null) {
            SAFE_BLOCKS_ABOVE_STATE = CommonConfig.get().base().safe().safeBlocksAbove().stream()
                    .map(AotakeUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (SAFE_BLOCKS_ABOVE == null) {
            SAFE_BLOCKS_ABOVE = CommonConfig.get().base().safe().safeBlocksAbove().stream()
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
                if (CollectionUtils.isNullOrEmpty(CommonConfig.get().base().chunk().chunkCheckEntityList())) {
                    result = EnumListType.WHITE == CommonConfig.get().base().chunk().chunkCheckEntityListMode();
                }
                // 黑名单模式
                else if (EnumListType.BLACK == CommonConfig.get().base().chunk().chunkCheckEntityListMode()) {
                    result = AotakeSweep.entityFilter().validEntity(CommonConfig.get().base().chunk().chunkCheckEntityList(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.entityFilter().validEntity(CommonConfig.get().base().chunk().chunkCheckEntityList(), entity);
                }
            } else {
                // 空列表
                if (CollectionUtils.isNullOrEmpty(CommonConfig.get().base().sweep().entityList())) {
                    result = EnumListType.WHITE == CommonConfig.get().base().sweep().entityListMode();
                }
                // 黑名单模式
                else if (EnumListType.BLACK == CommonConfig.get().base().sweep().entityListMode()) {
                    result = AotakeSweep.entityFilter().validEntity(CommonConfig.get().base().sweep().entityList(), entity);
                }
                // 白名单模式
                else {
                    result = !AotakeSweep.entityFilter().validEntity(CommonConfig.get().base().sweep().entityList(), entity);
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
                    , pair -> pair.key().getBlockState(pair.val()));
            stateFlag = SAFE_BLOCKS.contains(AotakeUtils.getBlockRegistryName(state))
                    || SAFE_BLOCKS_STATE.contains(state);
        }

        boolean belowFlag = false;
        if (!SAFE_BLOCKS_BELOW.isEmpty() || !SAFE_BLOCKS_BELOW_STATE.isEmpty()) {
            BlockState below = blockStateCache.computeIfAbsent(new KeyValue<>(level, entity.blockPosition().below())
                    , pair -> pair.key().getBlockState(pair.val()));
            belowFlag = SAFE_BLOCKS_BELOW.contains(AotakeUtils.getBlockRegistryName(below))
                    || SAFE_BLOCKS_BELOW_STATE.contains(below);
        }

        boolean aboveFlag = false;
        if (!SAFE_BLOCKS_ABOVE.isEmpty() || !SAFE_BLOCKS_ABOVE_STATE.isEmpty()) {
            BlockState above = blockStateCache.computeIfAbsent(new KeyValue<>(level, entity.blockPosition().above())
                    , pair -> pair.key().getBlockState(pair.val()));
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
            if (entity instanceof Player) continue;
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
        int typeLimit = CommonConfig.get().base().sweep().entityListLimit();
        Set<String> exceededTypes = new HashSet<>();
        for (Map.Entry<String, Integer> entry : nonJunkTypeCounts.entrySet()) {
            if (entry.getValue() > typeLimit) {
                exceededTypes.add(entry.getKey());
            }
        }

        int safeLimit = CommonConfig.get().base().safe().safeBlocksEntityLimit();
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
        KeyValue<MinecraftServer, Boolean> serverInstance = AotakeSweep.serverInstance();
        // 服务器已关闭
        if (!serverInstance.val()) return;

        List<ServerPlayer> players = serverInstance.key().getPlayerList().getPlayers();

        try {
            // 若服务器没有玩家
            if (CollectionUtils.isNullOrEmpty(players) && !CommonConfig.get().base().sweep().sweepWhenNoPlayer()) {
                LOGGER.debug("No player online, sweep canceled");
                return;
            }

            List<Entity> list = filtered ? entities : getAllEntitiesByFilter(entities, false);

            // if (CollectionUtils.isNotNullOrEmpty(list)) {
            // 清空旧的物品
            if (CommonConfig.get().base().dustbin().selfCleanMode().contains(EnumSelfCleanMode.SWEEP_CLEAR)) {
                switch (EnumDustbinMode.valueOfOrDefault(CommonConfig.get().base().dustbin().dustbinBlockMode())) {
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
                    AotakeUtils.sendMessage(p, AotakeComponent.get().empty()
                            .append(msg)
                            .append(AotakeComponent.get().literal("[x]")
                                    .color(EnumMCColor.RED.getColor())
                                    .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , AotakeComponent.get().trans(EnumI18nType.WORD, "not_show_button").toVanilla(language))
                                    )
                                    .clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                            , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = getWarningVoice("error");
                    float volume = CommonConfig.get().base().sweep().sweepWarningVoiceVolume() / 100f;
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
        for (String pos : CommonConfig.get().base().dustbin().dustbinBlockPositions()) {
            WorldCoordinate coordinate = WorldCoordinate.fromString(pos);
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
                text = Translator.of(AotakeSweep.MODID).getTranslation(text, lang);
            }
            if (NumberUtils.toInt(key) > 0) {
                if (StringUtils.isNullOrEmpty(text)) {
                    text = AotakeComponent.get().trans(EnumI18nType.FORMAT, "cleanup_will_start", key).getString(lang);
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

            msg = AotakeComponent.get().literal(text);
            msg.appendArg(key);
        } catch (Exception ignored) {
        }
        return msg;
    }

    private static Map<String, List<String>> getActiveWarnGroup() {
        if (warnGroups.isEmpty()) {
            return Collections.emptyMap();
        }
        long sweepTime = ServerEventHandler.getNextSweepTime();
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
        long sweepTime = ServerEventHandler.getNextSweepTime();
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

    public static int dustbin(@NonNull ServerPlayer player, int page) {
        int result = 0;
        int vPage = CommonConfig.get().base().dustbin().dustbinPageLimit();
        int bPage = CommonConfig.get().base().dustbin().dustbinBlockPositions().size();
        int totalPage = getDustbinTotalPage();
        if (totalPage <= 0) {
            AotakeUtils.sendMessage(player, AotakeComponent.get().trans(EnumI18nType.WORD, "dustbin_page_empty"));
        } else {
            switch (EnumDustbinMode.valueOfOrDefault(CommonConfig.get().base().dustbin().dustbinBlockMode())) {
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
            AotakeSweep.playerDustbinPage().put(AotakeUtils.getPlayerUUIDString(player), page);
            AotakeUtils.sendPacketToPlayer(new DustbinPageSyncToClient(page, totalPage), player);
        }
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
        List<? extends String> positions = CommonConfig.get().base().dustbin().dustbinBlockPositions();
        if (CollectionUtils.isNotNullOrEmpty(positions) && positions.size() >= page) {
            WorldCoordinate coordinate = WorldCoordinate.fromString(positions.get(page - 1));

            Direction direction = coordinate.direction();
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
            for (String pos : CommonConfig.get().base().dustbin().dustbinBlockPositions()) {
                WorldCoordinate coordinate = WorldCoordinate.fromString(pos);
                if (coordinate != null) {
                    AotakeUtils.clearStorage(AotakeUtils.getBlockItemHandler(coordinate));
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromString(CommonConfig.get().base().dustbin().dustbinBlockPositions().get(page - 1));
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

        List<String> positions = CommonConfig.get().base().dustbin().dustbinBlockPositions();

        if (page == 0) {
            for (String pos : positions) {
                WorldCoordinate coordinate = WorldCoordinate.fromString(pos);
                processCoord.accept(coordinate);
            }
        } else {
            if (page - 1 >= 0 && page - 1 < positions.size()) {
                WorldCoordinate coordinate = WorldCoordinate.fromString(positions.get(page - 1));
                processCoord.accept(coordinate);
            }
        }
    }

    public static int getDustbinTotalPage() {
        int result = 0;
        switch (EnumDustbinMode.valueOfOrDefault(CommonConfig.get().base().dustbin().dustbinBlockMode())) {
            case VIRTUAL: {
                result = CommonConfig.get().base().dustbin().dustbinPageLimit();
            }
            break;
            case BLOCK: {
                result = CommonConfig.get().base().dustbin().dustbinBlockPositions().size();
            }
            break;
            case VIRTUAL_BLOCK: {
                result = CommonConfig.get().base().dustbin().dustbinPageLimit() + CommonConfig.get().base().dustbin().dustbinBlockPositions().size();
            }
            break;
            case BLOCK_VIRTUAL: {
                result = CommonConfig.get().base().dustbin().dustbinBlockPositions().size() + CommonConfig.get().base().dustbin().dustbinPageLimit();
            }
            break;
        }
        return result;
    }

    // endregion 垃圾箱相关


    // region nbt文件读写

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

    public static boolean hasAotakeTag(ItemStack item) {
        if (item == null) return false;
        CompoundTag tag = item.getTag();
        return tag != null && tag.contains(AotakeSweep.MODID);
    }

    public static CompoundTag getAotakeTag(@NonNull ItemStack item) {
        CompoundTag tag = item.getTag();
        if (tag == null) {
            tag = new CompoundTag();
            item.setTag(tag);
        }
        if (!tag.contains(AotakeSweep.MODID)) {
            tag.put(AotakeSweep.MODID, new CompoundTag());
        }
        return tag.getCompound(AotakeSweep.MODID);
    }

    public static void setAotakeTag(@NonNull ItemStack item, CompoundTag aotakeTag) {
        CompoundTag tag = item.getTag();
        if (tag == null) {
            tag = new CompoundTag();
            item.setTag(tag);
        }
        tag.put(AotakeSweep.MODID, aotakeTag);
    }

    public static CompoundTag clearAotakeTag(ItemStack item) {
        if (item == null) return null;
        CompoundTag tag = item.getTag();
        if (tag != null) {
            tag.remove(AotakeSweep.MODID);
        }
        return tag;
    }

    public static void clearItemTag(ItemStack item) {
        if (item == null) return;
        CompoundTag tag = item.getTag();
        if (tag != null && tag.isEmpty()) {
            item.setTag(null);
        }
    }

    public static void clearItemTagEx(ItemStack item) {
        if (item == null) return;
        CompoundTag tag = clearAotakeTag(item);
        if (tag != null && tag.isEmpty()) {
            item.setTag(null);
        }
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
     * 反序列化方块状态
     */
    public static BlockState deserializeBlockState(String block) {
        try {
            return BlockStateParser.parseForBlock(Registry.BLOCK, new StringReader(block), false).blockState();
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
        ResourceLocation location = Registry.ITEM.getKey(item);
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
        ResourceLocation location = Registry.ENTITY_TYPE.getKey(entityType);
        return location == null ? Identifier.id().empty().toString() : location.toString();
    }

    public static String getItemCustomNameJson(@NonNull ItemStack itemStack) {
        String result = "";
        CompoundTag CompoundTag = itemStack.getTagElement("display");
        if (CompoundTag != null && CompoundTag.contains("Name", 8)) {
            result = CompoundTag.getString("Name");
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
     * 获取指定的方块容器
     */
    @Nullable
    public static Storage<ItemVariant> getBlockItemHandler(WorldCoordinate coordinate) {
        ServerLevel level = AotakeSweep.serverInstance().key().getLevel(coordinate.dimension());
        if (level == null) return null;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return null;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return null;

        try {
            Direction side = coordinate.direction();
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

    public static <T> List<T> singleList(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    // endregion 杂项

}
