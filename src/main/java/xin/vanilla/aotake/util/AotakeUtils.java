package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.WarningConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.packet.DustbinPageSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;

import javax.annotation.Nullable;
import java.util.*;


@SuppressWarnings({"resource"})
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
            case VIRTUAL_OP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandVirtualOp() : "";
            case DUSTBIN_OPEN, DUSTBIN_OPEN_OTHER -> prefix + " " + CommonConfig.get().command().commandDustbinOpen();
            case DUSTBIN_OPEN_CONCISE, DUSTBIN_OPEN_OTHER_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinOpen() : "";
            case DUSTBIN_CLEAR -> prefix + " " + CommonConfig.get().command().commandDustbinClear();
            case DUSTBIN_CLEAR_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinClear() : "";
            case DUSTBIN_DROP -> prefix + " " + CommonConfig.get().command().commandDustbinDrop();
            case DUSTBIN_DROP_CONCISE ->
                    isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinDrop() : "";
            case CACHE_CLEAR -> prefix + " " + CommonConfig.get().command().commandCacheClear();
            case CACHE_CLEAR_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandCacheClear() : "";
            case CACHE_DROP -> prefix + " " + CommonConfig.get().command().commandCacheDrop();
            case CACHE_DROP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandCacheDrop() : "";
            case SWEEP -> prefix + " " + CommonConfig.get().command().commandSweep();
            case SWEEP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandSweep() : "";
            case CLEAR_DROP -> prefix + " " + CommonConfig.get().command().commandClearDrop();
            case CLEAR_DROP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandClearDrop() : "";
            case DELAY_SWEEP -> prefix + " " + CommonConfig.get().command().commandDelaySweep();
            case DELAY_SWEEP_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandDelaySweep() : "";
            case CHUNK_VAULT -> prefix + " " + CommonConfig.get().command().commandChunkVault();
            case CHUNK_VAULT_CONCISE -> isConciseEnabled(type) ? CommonConfig.get().command().commandChunkVault() : "";
            default -> "";
        };
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        switch (type) {
            case CONFIG:
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return CommonConfig.get().permission().permissionVirtualOp();
            case DUSTBIN_OPEN:
            case DUSTBIN_OPEN_CONCISE:
                return CommonConfig.get().permission().permissionDustbinOpen();
            case DUSTBIN_OPEN_OTHER:
            case DUSTBIN_OPEN_OTHER_CONCISE:
                return CommonConfig.get().permission().permissionDustbinOpenOther();
            case DUSTBIN_CLEAR:
            case DUSTBIN_CLEAR_CONCISE:
                return CommonConfig.get().permission().permissionDustbinClear();
            case DUSTBIN_DROP:
            case DUSTBIN_DROP_CONCISE:
                return CommonConfig.get().permission().permissionDustbinDrop();
            case CACHE_CLEAR:
            case CACHE_CLEAR_CONCISE:
                return CommonConfig.get().permission().permissionCacheClear();
            case CACHE_DROP:
            case CACHE_DROP_CONCISE:
                return CommonConfig.get().permission().permissionCacheDrop();
            case SWEEP:
            case SWEEP_CONCISE:
                return CommonConfig.get().permission().permissionSweep();
            case CLEAR_DROP:
            case CLEAR_DROP_CONCISE:
                return CommonConfig.get().permission().permissionClearDrop();
            case DELAY_SWEEP:
            case DELAY_SWEEP_CONCISE:
                return CommonConfig.get().permission().permissionDelaySweep();
            case CATCH_PLAYER:
                return CommonConfig.get().permission().permissionCatchPlayer();
            case CHUNK_VAULT:
            case CHUNK_VAULT_CONCISE:
                return CommonConfig.get().permission().permissionChunkVault();
            default:
                return 0;
        }
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
        return source.hasPermission(getCommandPermissionLevel(type)) || CommandUtils.hasVirtualPermission(source.getEntity(), type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(Player player, EnumCommandType type) {
        return player.hasPermissions(getCommandPermissionLevel(type)) || CommandUtils.hasVirtualPermission(player, type);
    }

    /**
     * 获取传送指令
     */
    public static String genTeleportCommand(WorldCoordinate coordinate) {
        if (ModList.get().isLoaded("narcissus_farewell")) {
            return String.format("/%s %s %s %s safe %s"
                    , CompatNarcissus.getTpCommand()
                    , coordinate.xInt()
                    , coordinate.yInt()
                    , coordinate.zInt()
                    , coordinate.dimensionId()
            );
        } else {
            return String.format("/execute in %s as @s run tp %s %s %s"
                    , coordinate.dimensionId()
                    , coordinate.xInt()
                    , coordinate.yInt()
                    , coordinate.zInt()
            );
        }
    }

    // endregion 指令相关


    // region 扫地

    private static Set<BlockState> SAFE_BLOCKS_STATE;
    private static Set<String> SAFE_BLOCKS;
    private static Set<BlockState> SAFE_BLOCKS_BELOW_STATE;
    private static Set<String> SAFE_BLOCKS_BELOW;
    private static Set<BlockState> SAFE_BLOCKS_ABOVE_STATE;
    private static Set<String> SAFE_BLOCKS_ABOVE;

    private static void initSafeBlocks() {
        if (SAFE_BLOCKS_STATE == null) {
            SAFE_BLOCKS_STATE = new HashSet<>(CommonConfig.get().base().safe().safeBlocks().stream()
                    .map(BlockUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        if (SAFE_BLOCKS == null) {
            SAFE_BLOCKS = new HashSet<>(CommonConfig.get().base().safe().safeBlocks().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList());
        }
        if (SAFE_BLOCKS_BELOW_STATE == null) {
            SAFE_BLOCKS_BELOW_STATE = new HashSet<>(CommonConfig.get().base().safe().safeBlocksBelow().stream()
                    .map(BlockUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        if (SAFE_BLOCKS_BELOW == null) {
            SAFE_BLOCKS_BELOW = new HashSet<>(CommonConfig.get().base().safe().safeBlocksBelow().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList());
        }
        if (SAFE_BLOCKS_ABOVE_STATE == null) {
            SAFE_BLOCKS_ABOVE_STATE = new HashSet<>(CommonConfig.get().base().safe().safeBlocksAbove().stream()
                    .map(BlockUtils::deserializeBlockState)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        if (SAFE_BLOCKS_ABOVE == null) {
            SAFE_BLOCKS_ABOVE = new HashSet<>(CommonConfig.get().base().safe().safeBlocksAbove().stream()
                    .filter(Objects::nonNull)
                    .map(s -> (String) s)
                    .distinct()
                    .toList());
        }
    }

    /**
     * 配置重载后清理派生集合，避免继续使用旧安全方块规则。
     */
    public static void clearEntityFilterCaches() {
        SAFE_BLOCKS_STATE = null;
        SAFE_BLOCKS = null;
        SAFE_BLOCKS_BELOW_STATE = null;
        SAFE_BLOCKS_BELOW = null;
        SAFE_BLOCKS_ABOVE_STATE = null;
        SAFE_BLOCKS_ABOVE = null;
        AotakeSweep.getEntityFilter().clear();
    }

    public static List<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        MinecraftServer server = BaniraServerUtils.currentServer();
        if (BaniraServerUtils.isRunning() && server != null) {
            server.getAllLevels()
                    .forEach(level -> level.getEntities().getAll().forEach(entities::add)
                    );
        }
        return entities;
    }

    public static boolean isJunkEntity(Entity entity, boolean chuck) {
        CommonConfig.BaseView base = CommonConfig.get().base();
        List<String> rules = chuck ? base.chunk().chunkCheckEntityList() : base.sweep().entityList();
        EnumListType mode = chuck ? base.chunk().chunkCheckEntityListMode() : base.sweep().entityListMode();
        return isJunkEntity(entity, CollectionUtils.isNullOrEmpty(rules), mode,
                AotakeSweep.getEntityFilter().compile(rules));
    }

    private static boolean isJunkEntity(Entity entity, boolean emptyRules, EnumListType mode,
                                        EntityFilter.Matcher matcher) {
        if (entity == null || entity instanceof Player) {
            return false;
        }
        if (emptyRules) {
            return mode == EnumListType.WHITE;
        }
        boolean matched = matcher.matches(entity);
        return mode == EnumListType.BLACK ? matched : !matched;
    }

    public static boolean isSafeEntity(Map<Level, Map<BlockPos, BlockState>> blockStateCache, Entity entity) {
        Level level = entity.level();
        BlockPos position = entity.blockPosition();

        boolean stateFlag = false;
        if (!SAFE_BLOCKS.isEmpty() || !SAFE_BLOCKS_STATE.isEmpty()) {
            BlockState state = cachedBlockState(blockStateCache, level, position);
            stateFlag = SAFE_BLOCKS.contains(BlockUtils.getBlockRegistryString(state))
                    || SAFE_BLOCKS_STATE.contains(state);
        }

        boolean belowFlag = false;
        if (!SAFE_BLOCKS_BELOW.isEmpty() || !SAFE_BLOCKS_BELOW_STATE.isEmpty()) {
            BlockState below = cachedBlockState(blockStateCache, level, position.below());
            belowFlag = SAFE_BLOCKS_BELOW.contains(BlockUtils.getBlockRegistryString(below))
                    || SAFE_BLOCKS_BELOW_STATE.contains(below);
        }

        boolean aboveFlag = false;
        if (!SAFE_BLOCKS_ABOVE.isEmpty() || !SAFE_BLOCKS_ABOVE_STATE.isEmpty()) {
            BlockState above = cachedBlockState(blockStateCache, level, position.above());
            aboveFlag = SAFE_BLOCKS_ABOVE.contains(BlockUtils.getBlockRegistryString(above))
                    || SAFE_BLOCKS_ABOVE_STATE.contains(above);
        }

        return stateFlag || belowFlag || aboveFlag;
    }

    private static BlockState cachedBlockState(Map<Level, Map<BlockPos, BlockState>> cache,
                                               Level level, BlockPos position) {
        return cache.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(position, level::getBlockState);
    }

    public static List<Entity> getAllEntitiesByFilter(@Nullable List<Entity> entities, boolean chuck) {
        LOGGER.debug("Entity filter started at {}", System.currentTimeMillis());
        if (CollectionUtils.isNullOrEmpty(entities)) {
            entities = getAllEntities();
        }
        initSafeBlocks();

        Map<Level, Map<BlockPos, BlockState>> blockStateCache = new IdentityHashMap<>();
        CommonConfig.BaseView base = CommonConfig.get().base();
        List<String> rules = chuck ? base.chunk().chunkCheckEntityList() : base.sweep().entityList();
        EnumListType listMode = chuck ? base.chunk().chunkCheckEntityListMode() : base.sweep().entityListMode();
        boolean emptyRules = CollectionUtils.isNullOrEmpty(rules);
        EntityFilter.Matcher matcher = AotakeSweep.getEntityFilter().compile(rules);

        boolean hasSafeRules = !SAFE_BLOCKS.isEmpty()
                || !SAFE_BLOCKS_STATE.isEmpty()
                || !SAFE_BLOCKS_BELOW.isEmpty()
                || !SAFE_BLOCKS_BELOW_STATE.isEmpty()
                || !SAFE_BLOCKS_ABOVE.isEmpty()
                || !SAFE_BLOCKS_ABOVE_STATE.isEmpty();

        List<EntityScanEntry> scanned = new ArrayList<>(entities.size());
        Map<String, Integer> nonJunkTypeCounts = new HashMap<>();
        Map<ChunkKey, Integer> safeChunkCounts = new HashMap<>();

        LOGGER.debug("Entity exceeded filter started at {}", System.currentTimeMillis());
        for (Entity entity : entities) {
            if (entity instanceof Player) continue;

            boolean safe = hasSafeRules && isSafeEntity(blockStateCache, entity);
            ChunkKey chunkKey = null;
            if (safe) {
                chunkKey = ChunkKey.of(entity);
                safeChunkCounts.merge(chunkKey, 1, Integer::sum);
            }

            boolean junk = isJunkEntity(entity, emptyRules, listMode, matcher);
            String type = null;
            if (!junk) {
                type = EntityUtils.getEntityRegistryString(entity);
                nonJunkTypeCounts.merge(type, 1, Integer::sum);
            }
            scanned.add(new EntityScanEntry(entity, safe, junk, type, chunkKey));
        }

        LOGGER.debug("Entity safe filter started at {}", System.currentTimeMillis());
        int typeLimit = base.sweep().entityListLimit();
        Set<String> exceededTypes = new HashSet<>();
        for (Map.Entry<String, Integer> entry : nonJunkTypeCounts.entrySet()) {
            if (entry.getValue() > typeLimit) {
                exceededTypes.add(entry.getKey());
            }
        }

        int safeLimit = base.safe().safeBlocksEntityLimit();
        Set<ChunkKey> exceededChunks = new HashSet<>();
        for (Map.Entry<ChunkKey, Integer> entry : safeChunkCounts.entrySet()) {
            if (entry.getValue() > safeLimit) {
                exceededChunks.add(entry.getKey());
            }
        }

        LOGGER.debug("Entity junk filter started at {}", System.currentTimeMillis());
        List<Entity> entityList = new ArrayList<>();
        for (EntityScanEntry entry : scanned) {
            boolean exceededType = !entry.junk && exceededTypes.contains(entry.type);
            boolean exceededSafe = entry.safe && exceededChunks.contains(entry.chunkKey);
            if ((!entry.safe && entry.junk) || exceededType || exceededSafe) {
                entityList.add(entry.entity);
            }
        }
        LOGGER.debug("Entity filter finished at {}", System.currentTimeMillis());
        return entityList;
    }

    private static final class EntityScanEntry {
        private final Entity entity;
        private final boolean safe;
        private final boolean junk;
        private final String type;
        private final ChunkKey chunkKey;

        private EntityScanEntry(Entity entity, boolean safe, boolean junk,
                                String type, ChunkKey chunkKey) {
            this.entity = entity;
            this.safe = safe;
            this.junk = junk;
            this.type = type;
            this.chunkKey = chunkKey;
        }
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
        sweep(entities, filtered, false);
    }

    /**
     * @param chunkOverloadVault 为 true 时，回收物品写入区块暂存目录（若配置启用），而非全局垃圾箱。
     */
    public static void sweep(List<Entity> entities, boolean filtered, boolean chunkOverloadVault) {
        MinecraftServer server = BaniraServerUtils.currentServer();
        // 服务器已关闭
        if (!BaniraServerUtils.isRunning() || server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        try {
            // 若服务器没有玩家
            if (CollectionUtils.isNullOrEmpty(players) && !CommonConfig.get().base().sweep().sweepWhenNoPlayer())
                return;

            List<Entity> list = filtered ? entities : getAllEntitiesByFilter(entities, false);

            // if (CollectionUtils.isNotNullOrEmpty(list)) {
            // 清空旧的物品
            if (CommonConfig.get().base().dustbin().selfCleanMode().contains(EnumSelfCleanMode.SWEEP_CLEAR)) {
                switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
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
            SweepResult sweepResult = new SweepResult().setChunkOverloadVault(chunkOverloadVault);
            if (chunkOverloadVault && CommonConfig.get().base().chunk().chunkVaultEnabled()) {
                sweepResult.setChunkVaultTimePrefix(ChunkVaultStorage.vaultTimePrefix());
                sweepResult.setChunkVaultRunId(ChunkVaultStorage.newVaultRunId());
            }
            AotakeSweep.getEntitySweeper().addDrops(list, sweepResult);
            // }

        } catch (Exception e) {
            LOGGER.error(e);
            for (ServerPlayer p : players) {
                String language = AotakeLang.getPlayerLanguage(p);
                Component msg = getWarningMessage("error", language, null);
                PlayerSweepData playerData = PlayerSweepData.getData(p);
                if (playerData.isShowSweepResult()) {
                    MessageUtils.sendNotification(p, AotakeComponent.get().empty()
                                    .append(msg)
                                    .append(AotakeComponent.get().literal("[x]")
                                            .color(EnumMCColor.RED.getColor())
                                            .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                                    , AotakeComponent.get().transAuto("not_show_button")
                                                    .toVanilla(language))
                                            )
                                            .clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                                    , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
                                            )
                                    )
                            , AotakeNotificationTypes.SWEEP_RESULT_INTERACTIVE);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = getWarningVoice("error");
                    float volume = CommonConfig.get().base().sweep().sweepWarningVoiceVolume() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        CommandUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
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
    public static Entity getEntityFromItem(ServerLevel level, ItemStack itemStack) {
        Entity result = null;

        DataComponentMap tag = itemStack.getComponents();
        if (!tag.isEmpty() && tag.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag customData = tag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (customData.contains(AotakeSweep.MODID)) {
                CompoundTag aotake = customData.getCompound(AotakeSweep.MODID);
                if (aotake.contains("entity")) {
                    try {
                        result = EntityType.loadEntityRecursive(aotake.getCompound("entity"), level, e -> e);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load entity from item stack: {}", itemStack, e);
                    }
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
                text = AotakeLang.get().getTranslation(text, lang);
            }
            if (NumberUtils.toInt(key) > 0) {
                if (StringUtils.isNullOrEmpty(text)) {
                    text = AotakeComponent.get().transAuto("cleanup_will_start", key).toVanilla(lang).getString();
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
        long sweepTime = EventHandlerProxy.getNextSweepTime();
        if (activeWarnGroupSweepTime != sweepTime || activeWarnGroup.isEmpty()) {
            activeWarnGroupSweepTime = sweepTime;
            Map<String, List<String>> selected = CollectionUtils.getRandomElement(warnGroups);
            activeWarnGroup = selected != null ? selected : warnGroups.getFirst();
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
            activeVoiceGroup = selected != null ? selected : voiceGroups.getFirst();
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
            MessageUtils.sendNotification(player, AotakeComponent.get().transAuto("dustbin_page_empty"), AotakeNotificationTypes.DUSTBIN);
        } else {
            switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
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
            AotakeSweep.getPlayerDustbinPage().put(PlayerUtils.getPlayerUUIDString(player), page);
            PacketUtils.sendPacketToPlayer(new DustbinPageSyncToClient(page, totalPage), player);
        }
        return result;
    }

    private static int openVirtualDustbin(@NonNull ServerPlayer player, int page) {
        MenuProvider trashContainer = WorldTrashData.getTrashContainer(player, page);
        if (trashContainer == null) return 0;
        int result = player.openMenu(trashContainer).orElse(0);

        if (result > 0) AotakeSweep.getPlayerDustbinPage().put(PlayerUtils.getPlayerUUIDString(player), page);
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

            BlockState state = player.serverLevel().getBlockState(coordinate.toBlockPos());
            InteractionResult res = state.useWithoutItem(player.serverLevel(), player, ray);
            if (res.consumesAction()) {
                result = 1;
            }
        }

        if (result > 0) AotakeSweep.getPlayerDustbinPage().put(PlayerUtils.getPlayerUUIDString(player), page);
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
                    IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            handler.extractItem(i, handler.getSlotLimit(i), false);
                        }
                    }
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromString(CommonConfig.get().base().dustbin().dustbinBlockPositions().get(page - 1));
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
                        Entity entity = AotakeUtils.getEntityFromItem(player.serverLevel(), item);
                        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                        player.serverLevel().addFreshEntity(entity);
                    }
                })
        );
        WorldTrashData.get().setDirty();
    }

    public static void dropDustbinBlock(ServerPlayer player, int page) {
        if (page == 0) {
            for (String pos : CommonConfig.get().base().dustbin().dustbinBlockPositions()) {
                WorldCoordinate coordinate = WorldCoordinate.fromString(pos);
                if (coordinate != null) {
                    IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack stack = handler.extractItem(i, handler.getSlotLimit(i), false);
                            if (!stack.isEmpty()) {
                                Entity entity = AotakeUtils.getEntityFromItem(player.serverLevel(), stack);
                                entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                                player.serverLevel().addFreshEntity(entity);
                            }
                        }
                    }
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromString(CommonConfig.get().base().dustbin().dustbinBlockPositions().get(page - 1));
            if (coordinate != null) {
                IItemHandler handler = AotakeUtils.getBlockItemHandler(coordinate);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.extractItem(i, handler.getSlotLimit(i), false);
                        if (!stack.isEmpty()) {
                            Entity entity = AotakeUtils.getEntityFromItem(player.serverLevel(), stack);
                            entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                            player.serverLevel().addFreshEntity(entity);
                        }
                    }
                }
            }
        }
    }

    public static int getDustbinTotalPage() {
        int result = 0;
        switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
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

    public static boolean hasAotakeTag(ItemStack item) {
        if (item == null) return false;
        DataComponentMap components = item.getComponents();
        return !components.isEmpty()
                && components.has(DataComponents.CUSTOM_DATA)
                && components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(AotakeSweep.MODID);
    }

    public static CompoundTag getAotakeTag(@NonNull ItemStack item) {
        if (!hasAotakeTag(item)) {
            CompoundTag tag = new CompoundTag();
            CompoundTag aotake = new CompoundTag();
            tag.put(AotakeSweep.MODID, aotake);
            item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return aotake;
        }
        DataComponentMap components = item.getComponents();
        CompoundTag tag = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getCompound(AotakeSweep.MODID);
    }

    public static void setAotakeTag(@NonNull ItemStack item, CompoundTag aotakeTag) {
        CompoundTag tag;
        if (!hasAotakeTag(item)) {
            tag = new CompoundTag();
        } else {
            tag = item.getComponents().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        }
        tag.put(AotakeSweep.MODID, aotakeTag);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CompoundTag clearAotakeTag(ItemStack item) {
        if (item == null) return null;
        DataComponentMap components = item.getComponents();
        if (!components.isEmpty() && components.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag tag = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.isEmpty() && tag.contains(AotakeSweep.MODID)) {
                tag.remove(AotakeSweep.MODID);
                item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return tag;
        }
        return null;
    }

    public static void clearItemTag(ItemStack item) {
        if (item == null) return;
        DataComponentMap components = item.getComponents();
        if (!components.isEmpty() && components.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag tag = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.isEmpty()) {
                item.remove(DataComponents.CUSTOM_DATA);
            }
        }
    }

    public static void clearItemTagEx(ItemStack item) {
        if (item == null) return;
        CompoundTag tag = clearAotakeTag(item);
        if (tag != null && tag.isEmpty()) {
            item.remove(DataComponents.CUSTOM_DATA);
        }
    }

    // endregion nbt文件读写


    // region 杂项

    /**
     * 将物品添加到指定的方块容器
     */
    public static ItemStack addItemToBlock(ItemStack stack, WorldCoordinate coordinate) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ServerLevel level = BaniraServerUtils.currentServer().getLevel(coordinate.dimension());
        if (level == null) return stack;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return stack;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return stack;

        try {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, level.getBlockState(pos), te, coordinate.direction());
            if (handler == null && te instanceof Container container) {
                handler = new InvWrapper(container);
            }
            if (handler != null) {
                ItemStack remaining = ItemHandlerHelper.insertItem(handler, stack.copy(), false);
                te.setChanged();
                return remaining;
            }
        } catch (Throwable ignored) {
        }

        return stack;
    }

    /**
     * 获取指定的方块容器
     */
    public static IItemHandler getBlockItemHandler(WorldCoordinate coordinate) {
        ServerLevel level = BaniraServerUtils.currentServer().getLevel(coordinate.dimension());
        if (level == null) return null;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return null;

        LevelChunk chunk = null;
        try {
            chunk = level.getChunkAt(pos);
        } catch (Throwable ignored) {
        }
        if (chunk == null) return null;

        BlockEntity te = chunk.getBlockEntity(pos);
        if (te == null) return null;

        BlockState bs = chunk.getBlockState(pos);
        IItemHandler handler = null;

        try {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, bs, te, coordinate.direction());
            // 别听idea的
            if ((handler == null || (handler instanceof InvWrapper inv && inv.getInv() == null)) && te instanceof Container container) {
                handler = new InvWrapper(container);
            }
        } catch (Throwable ignored) {
        }

        return handler;
    }

    public static <T> List<T> singleList(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    // endregion 杂项

}
