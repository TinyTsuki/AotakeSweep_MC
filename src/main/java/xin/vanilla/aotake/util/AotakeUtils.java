package xin.vanilla.aotake.util;

import xin.vanilla.aotake.api.sweep.SweepOperation;
import xin.vanilla.aotake.api.sweep.SweepTrigger;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.internal.server.AotakeSweepRuntime;
import xin.vanilla.aotake.internal.fabric.sweep.FabricSweepFilter;
import xin.vanilla.aotake.internal.fabric.sweep.FabricSweepTarget;
import xin.vanilla.aotake.rule.RuleGeneration;
import xin.vanilla.aotake.rule.RuleManager;
import xin.vanilla.aotake.rule.RuleExecutionException;
import xin.vanilla.aotake.sweep.SweepConfiguration;
import xin.vanilla.banira.api.BaniraServer;
import lombok.NonNull;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.WarningConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.common.AotakeServerRuntime;
import xin.vanilla.aotake.network.packet.DustbinPageSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;

import javax.annotation.Nullable;
import java.util.*;


@SuppressWarnings({"resource"})
public class AotakeUtils {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final RateLimitedErrorLogger SWEEP_ERRORS = new RateLimitedErrorLogger(60_000L);

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
        switch (type) {
            case HELP:
                return prefix + " help";
            case LANGUAGE:
                return prefix + " " + CommonConfig.get().command().commandLanguage();
            case LANGUAGE_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandLanguage() : "";
            case VIRTUAL_OP:
                return prefix + " " + CommonConfig.get().command().commandVirtualOp();
            case VIRTUAL_OP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandVirtualOp() : "";
            case DUSTBIN_OPEN:
            case DUSTBIN_OPEN_OTHER:
                return prefix + " " + CommonConfig.get().command().commandDustbinOpen();
            case DUSTBIN_OPEN_CONCISE:
            case DUSTBIN_OPEN_OTHER_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinOpen() : "";
            case DUSTBIN_CLEAR:
                return prefix + " " + CommonConfig.get().command().commandDustbinClear();
            case DUSTBIN_CLEAR_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinClear() : "";
            case DUSTBIN_DROP:
                return prefix + " " + CommonConfig.get().command().commandDustbinDrop();
            case DUSTBIN_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandDustbinDrop() : "";
            case CACHE_CLEAR:
                return prefix + " " + CommonConfig.get().command().commandCacheClear();
            case CACHE_CLEAR_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandCacheClear() : "";
            case CACHE_DROP:
                return prefix + " " + CommonConfig.get().command().commandCacheDrop();
            case CACHE_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandCacheDrop() : "";
            case SWEEP:
                return prefix + " " + CommonConfig.get().command().commandSweep();
            case SWEEP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandSweep() : "";
            case CLEAR_DROP:
                return prefix + " " + CommonConfig.get().command().commandClearDrop();
            case CLEAR_DROP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandClearDrop() : "";
            case DELAY_SWEEP:
                return prefix + " " + CommonConfig.get().command().commandDelaySweep();
            case DELAY_SWEEP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandDelaySweep() : "";
            case CHUNK_VAULT:
                return prefix + " " + CommonConfig.get().command().commandChunkVault();
            case CHUNK_VAULT_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.get().command().commandChunkVault() : "";
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
        switch (type) {
            case LANGUAGE:
            case LANGUAGE_CONCISE:
                return CommonConfig.get().concise().conciseLanguage();
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return CommonConfig.get().concise().conciseVirtualOp();
            case DUSTBIN_OPEN:
            case DUSTBIN_OPEN_CONCISE:
            case DUSTBIN_OPEN_OTHER:
            case DUSTBIN_OPEN_OTHER_CONCISE:
                return CommonConfig.get().concise().conciseDustbinOpen();
            case DUSTBIN_CLEAR:
            case DUSTBIN_CLEAR_CONCISE:
                return CommonConfig.get().concise().conciseDustbinClear();
            case DUSTBIN_DROP:
            case DUSTBIN_DROP_CONCISE:
                return CommonConfig.get().concise().conciseDustbinDrop();
            case CACHE_CLEAR:
            case CACHE_CLEAR_CONCISE:
                return CommonConfig.get().concise().conciseCacheClear();
            case CACHE_DROP:
            case CACHE_DROP_CONCISE:
                return CommonConfig.get().concise().conciseCacheDrop();
            case SWEEP:
            case SWEEP_CONCISE:
                return CommonConfig.get().concise().conciseSweep();
            case CLEAR_DROP:
            case CLEAR_DROP_CONCISE:
                return CommonConfig.get().concise().conciseClearDrop();
            case DELAY_SWEEP:
            case DELAY_SWEEP_CONCISE:
                return CommonConfig.get().concise().conciseDelaySweep();
            case CHUNK_VAULT:
            case CHUNK_VAULT_CONCISE:
                return CommonConfig.get().concise().conciseChunkVault();
            default:
                return false;
        }
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
        if (FabricLoader.getInstance().isModLoaded("narcissus_farewell")) {
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


    public static List<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
        if (BaniraServer.isRunning() && server != null) {
            server.getAllLevels()
                    .forEach(level -> level.getAllEntities().forEach(entity -> {
                        if (prepareSweepCandidate(entity)) {
                            entities.add(entity);
                        }
                    }));
        }
        return entities;
    }

    /**
     * 扫描只剔除失效实体，不安排移除或写入垃圾箱。
     */
    public static boolean prepareSweepCandidate(Entity entity) {
        if (entity == null || entity instanceof Player || !entity.isAlive()
                || !(entity.level() instanceof ServerLevel)) {
            return false;
        }
        return !EntitySweeper.isPendingRemoval(entity)
                && (!(entity instanceof ItemEntity) || !((ItemEntity) entity).getItem().isEmpty());
    }

    public static boolean isJunkEntity(Entity entity, boolean chuck) {
        RuleGeneration<Entity> generation = AotakeRuleRuntime.acquire(chuck ? RuleManager.RuleUse.CHUNK : RuleManager.RuleUse.SWEEP);
        return prepareSweepCandidate(entity) && generation.shouldClean(chuck, entity);
    }


    public static List<Entity> getAllEntitiesByFilter(@Nullable List<Entity> entities, boolean chuck) {
        return getAllEntitiesByFilter(entities, chuck,
                AotakeRuleRuntime.acquire(chuck ? RuleManager.RuleUse.CHUNK : RuleManager.RuleUse.SWEEP));
    }

    public static List<Entity> getAllEntitiesByFilter(@Nullable List<Entity> entities, boolean chuck, RuleGeneration<Entity> generation) {
        generation.requireUsable();
        if (!AotakeRuleRuntime.isUsable(generation.id()))
            throw new RuleExecutionException(generation.id(), "Stale entity scan");
        xin.vanilla.banira.common.config.ConfigHolder holder = xin.vanilla.banira.api.BaniraConfigs.holder(CommonConfig.class);
        CommonConfig.BaseCategory settings = SweepConfiguration.base(holder.snapshotForEdit(holder.valuePaths()));
        return new FabricSweepFilter(settings, generation, chuck)
                .select(entities == null ? EntityUtils.getAllEntities() : entities);
    }

    public static void sweep() {
        submitSweep(null, false, false, null, SweepTrigger.SCHEDULED);
    }

    public static void sweep(List<Entity> entities, boolean filtered) {
        sweep(entities, filtered, false);
    }

    public static void sweep(List<Entity> entities, boolean filtered, boolean chunkOverloadVault) {
        sweep(entities, filtered, chunkOverloadVault, null);
    }

    public static void sweep(List<Entity> entities, boolean filtered, boolean chunkOverloadVault, RuleGeneration<Entity> expected) {
        submitSweep(entities, filtered, chunkOverloadVault, expected, SweepTrigger.COMMAND);
    }

    private static void submitSweep(List<Entity> entities, boolean filtered, boolean chunkOverloadVault, RuleGeneration<Entity> expected,
                                    SweepTrigger trigger) {
        if (!BaniraServer.isRunning()) return;
        try {
            AotakeSweepRuntime.submit(trigger, SweepOperation.RECYCLE,
                    FabricSweepTarget.all(), false, entities, filtered,
                    new SweepResult().setChunkOverloadVault(chunkOverloadVault), AotakeSweep.getEntitySweeper(), expected);
        } catch (RuntimeException failure) {
            reportSweepFailure(failure);
        }
    }

    public static void reportSweepFailure(Exception e) {
        SWEEP_ERRORS.log(LOGGER, "Entity sweep", e);
        MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
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

    public static void clearVirtualDustbin() {
        WorldTrashData.get().getDropList().clear();
        List<SimpleContainer> inventories = WorldTrashData.get().getInventoryList();
        if (CollectionUtils.isNotNullOrEmpty(inventories)) inventories.forEach(SimpleContainer::clearContent);
        WorldTrashData.get().setDirty();
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
            InteractionResult res = state.use(player.serverLevel(), player, InteractionHand.MAIN_HAND, ray);
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
                    Container handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        handler.clearContent();
                    }
                }
            }
        } else {
            WorldCoordinate coordinate = WorldCoordinate.fromString(CommonConfig.get().base().dustbin().dustbinBlockPositions().get(page - 1));
            if (coordinate != null) {
                Container handler = AotakeUtils.getBlockItemHandler(coordinate);
                if (handler != null) {
                    handler.clearContent();
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
                    Container handler = AotakeUtils.getBlockItemHandler(coordinate);
                    if (handler != null) {
                        for (int i = 0; i < handler.getContainerSize(); i++) {
                            ItemStack stack = handler.removeItemNoUpdate(i);
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
                Container handler = AotakeUtils.getBlockItemHandler(coordinate);
                if (handler != null) {
                    for (int i = 0; i < handler.getContainerSize(); i++) {
                        ItemStack stack = handler.removeItemNoUpdate(i);
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
     * 将物品添加到指定的方块容器
     */
    public static ItemStack addItemToBlock(ItemStack stack, WorldCoordinate coordinate) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ServerLevel level = AotakeServerRuntime.requireServer().getLevel(coordinate.dimension());
        if (level == null) return stack;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return stack;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return stack;

        if (!(te instanceof Container)) return stack;
        ItemStack remaining = insertIntoContainer((Container) te, stack.copy());
        te.setChanged();
        return remaining;
    }

    /**
     * 获取指定的方块容器
     */
    public static Container getBlockItemHandler(WorldCoordinate coordinate) {
        ServerLevel level = AotakeServerRuntime.requireServer().getLevel(coordinate.dimension());
        if (level == null) return null;

        BlockPos pos = coordinate.toBlockPos();
        if (!level.isLoaded(pos)) return null;

        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return null;

        return te instanceof Container ? (Container) te : null;
    }

    /**
     * Fabric 1.16 没有 Forge item capability，使用原版容器语义完成合并与插入。
     */
    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack inserted = stack.copy();
            inserted.setCount(moved);
            container.setItem(i, inserted);
            stack.shrink(moved);
        }
        container.setChanged();
        return stack;
    }

    public static <T> List<T> singleList(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    // endregion 杂项

}
