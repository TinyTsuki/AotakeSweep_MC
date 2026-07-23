package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.internal.platform.EntityRemovalBridge;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.internal.common.AotakeServerRuntime;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("resource")
public class EntitySweeper {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<ResourceKey<Level>, Queue<KeyValue<Entity, Boolean>>> pendingRemovals = new ConcurrentHashMap<>();

    private List<SimpleContainer> inventoryList;
    private ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList;

    private final Set<Entity> entitiesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());

    private void init() {
        WorldTrashData worldTrashData = WorldTrashData.get();
        if (this.inventoryList == null) {
            this.inventoryList = worldTrashData.getInventoryList();
        }
        if (this.dropList == null) {
            this.dropList = worldTrashData.getDropList();
        }
    }

    public void clear() {
        this.inventoryList = null;
        this.dropList = null;
    }

    public SweepResult addDrops(@NonNull List<Entity> entities, SweepResult result) {
        if (result.getTotalBatch() == 0) LOGGER.debug("AddDrops started at {}", System.currentTimeMillis());
        this.init();
        SweepContext context = new SweepContext(CommonConfig.get().base());

        if (result.getTotalBatch() == 0 && CollectionUtils.isNotNullOrEmpty(entities) && entities.size() > context.sweepEntityLimit) {
            List<List<Entity>> lists = CollectionUtils.splitToCollections(entities, context.sweepEntityLimit, context.sweepBatchLimit);
            result.setTotalBatch(lists.size());
            if (lists.size() > 1) {
                for (int i = 1; i < lists.size(); i++) {
                    List<Entity> entityList = lists.get(i);
                    BaniraScheduler.schedule(AotakeServerRuntime.requireServer()
                            , context.sweepEntityInterval * i
                            , () -> AotakeSweep.getEntitySweeper().addDrops(entityList, result)
                    );
                }
                entities = lists.get(0);
            }
        }
        if (result.getTotalBatch() == 0) {
            result.setTotalBatch(1);
        }

        Set<Entity> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity entity : entities) {
            Entity canonical = canonicalEntity(entity);
            if (seenEntities.add(canonical)) {
                result.add(this.processDrop(entity, result, context));
            }
        }

        if (!entitiesToRemove.isEmpty()) {
            for (Entity entity : entitiesToRemove) {
                if (entity.isAlive() && entity.level instanceof ServerLevel) {
                    scheduleRemoveEntity(entity, false);
                }
            }
        }
        entitiesToRemove.clear();

        WorldTrashData.get().setDirty();

        result.incrementBatch();

        if (result.getBatch().get() >= result.getTotalBatch()) {
            LOGGER.debug("AddDrops finished at {}", System.currentTimeMillis());
            ChunkVaultStorage.flushPending(AotakeServerRuntime.requireServer());

            List<ServerPlayer> players = AotakeServerRuntime.requireServer().getPlayerList().getPlayers();
            for (ServerPlayer p : players) {
                String language = AotakeLang.getPlayerLanguage(p);
                Component msg = AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
                        , language
                        , result);
                PlayerSweepData playerData = PlayerSweepData.getData(p);
                if (playerData.isShowSweepResult()) {
                    String openCom = "/" + AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN);
                    msg.clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, openCom))
                            .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                    , AotakeComponent.get().literal(openCom).toVanilla())
                            );
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
                    String voice = AotakeUtils.getWarningVoice(result.isEmpty() ? "fail" : "success");
                    float volume = context.warningVoiceVolume / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        CommandUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        } else {
            LOGGER.debug("AddDrops {}/{} at {}", result.getBatch().get(), result.getTotalBatch(), System.currentTimeMillis());
        }

        return result;
    }

    private SweepResult processDrop(@NonNull Entity original, SweepResult batchResult, SweepContext context) {
        SweepResult result = new SweepResult();
        WorldCoordinate coordinate = new WorldCoordinate(original);
        Entity entity = canonicalEntity(original);

        String typeKey = (entity instanceof ItemEntity)
                ? ItemUtils.getItemRegistryString(((ItemEntity) entity).getItem())
                : EntityUtils.getEntityRegistryString(entity);

        ItemStack itemToRecycle = null;

        // 处理掉落物
        if (entity instanceof ItemEntity) {
            ItemStack item = ((ItemEntity) entity).getItem();
            if (!context.redlist.matches(entity)) {
                itemToRecycle = item.copy();
                result.setItemCount(item.getCount());
            }
            // 延迟移除
            entitiesToRemove.add(entity);
        }
        // 处理其他实体
        else {
            // 回收实体
            if (!context.catchItems.isEmpty()
                    && context.catchMatcher.matches(entity)
            ) {
                String randomItem = CollectionUtils.getRandomElement(context.catchItems);
                itemToRecycle = ItemUtils.deserializeItemStack(randomItem);
                CompoundTag tag = itemToRecycle.getOrCreateTag();
                CompoundTag aotake = new CompoundTag();
                aotake.putBoolean("byPlayer", false);
                if (entity.isPassenger()) {
                    entity.stopRiding();
                }
                CompoundTag entityTag = new CompoundTag();
                entity.save(entityTag);
                AotakeUtils.sanitizeCapturedEntityTag(entityTag);
                aotake.put("entity", entityTag);
                aotake.putString("entityId", EntityUtils.getEntityRegistryString(entity));
                aotake.putString("name", ItemUtils.getItemCustomNameJson(itemToRecycle));
                tag.put(AotakeSweep.MODID, aotake);

                result.setRecycledEntityCount(1);
            }
            result.setEntityCount(1);
            entitiesToRemove.add(entity);
        }

        // 记录清理历史
        WorldTrashData.get().getDropCount().add(new DropStatistics(coordinate
                , typeKey
                , System.currentTimeMillis()
                , result.getItemCount()
                , result.getEntityCount()
        ));

        // 处理回收物品
        if (itemToRecycle != null) {
            if (batchResult != null && batchResult.isChunkOverloadVault()
                    && context.chunkVaultEnabled) {
                ChunkVaultStorage.queueRecycledItem(entity, itemToRecycle, batchResult);
                int recycled = itemToRecycle.getCount();
                result.setRecycledItemCount(Math.max(result.getRecycledItemCount(), recycled));
            } else {
                handleItemRecycling(coordinate, itemToRecycle, result, context);
            }
        }

        return result;
    }

    private void handleItemRecycling(WorldCoordinate coordinate, ItemStack item, SweepResult result,
                                     SweepContext context) {
        if (context.selfCleanModes.contains(EnumSelfCleanMode.SWEEP_DELETE)) {
            switch (context.dustbinMode) {
                case VIRTUAL: {
                    selfCleanVirtualDustbin();
                }
                break;
                case BLOCK: {
                    selfCleanDustbinBlock(context);
                }
                break;
                default: {
                    selfCleanVirtualDustbin();
                    selfCleanDustbinBlock(context);
                }
            }
        }

        ItemStack remaining = item;
        int recycledCount = item.getCount();

        switch (context.dustbinMode) {
            case VIRTUAL: {
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
            case BLOCK: {
                remaining = addItemToDustbinBlock(remaining, context);
            }
            break;
            case VIRTUAL_BLOCK: {
                remaining = addItemToVirtualDustbin(remaining);
                remaining = addItemToDustbinBlock(remaining, context);
            }
            break;
            case BLOCK_VIRTUAL: {
                remaining = addItemToDustbinBlock(remaining, context);
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
        }

        // 剩余部分进行溢出处理
        if (!remaining.isEmpty()) {
            recycledCount = recycledCount - remaining.getCount();
            handleOverflow(coordinate, remaining, result, context);
        }

        result.setRecycledItemCount(recycledCount);
    }

    private void selfCleanVirtualDustbin() {
        if (CollectionUtils.isNullOrEmpty(this.inventoryList)) return;
        SimpleContainer inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    private void selfCleanDustbinBlock(SweepContext context) {
        for (WorldCoordinate dustbinPos : context.blockPositions) {
            Container handler = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (handler != null) {
                for (int i = 0; i < handler.getContainerSize(); i++) {
                    if (!handler.getItem(i).isEmpty()) {
                        handler.removeItemNoUpdate(i);
                        break;
                    }
                }
            }
        }
    }

    private ItemStack addItemToVirtualDustbin(ItemStack item) {
        ItemStack remaining = item;
        for (SimpleContainer inv : this.inventoryList) {
            if (remaining.isEmpty()) break;

            while (!remaining.isEmpty() && inv.canAddItem(remaining)) {
                int before = remaining.getCount();
                remaining = inv.addItem(remaining);
                if (remaining.getCount() >= before) break;
            }
        }
        return remaining;
    }

    private ItemStack addItemToDustbinBlock(ItemStack item, SweepContext context) {
        ItemStack remaining = item;
        for (WorldCoordinate dustbinPos : context.blockPositions) {
            if (remaining.isEmpty()) break;
            remaining = AotakeUtils.addItemToBlock(remaining, dustbinPos);
        }
        return remaining;
    }

    private void handleOverflow(WorldCoordinate coordinate, ItemStack item, SweepResult result,
                                SweepContext context) {
        switch (context.overflowMode) {
            case KEEP: {
                // 多余部分移除
                if (dropList.size() < context.cacheLimit) {
                    this.dropList.add(new KeyValue<>(coordinate, item.copy()));
                }
            }
            break;
            case REPLACE: {
                switch (context.dustbinMode) {
                    case VIRTUAL:
                    case VIRTUAL_BLOCK: {
                        if (CollectionUtils.isNullOrEmpty(this.inventoryList)) break;
                        SimpleContainer inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                        inv.setItem(slot, item.copy());
                    }
                    break;
                    case BLOCK:
                    case BLOCK_VIRTUAL: {
                        WorldCoordinate dustbinPos = CollectionUtils.getRandomElement(context.blockPositions);
                        if (dustbinPos == null) break;
                        Container handler = AotakeUtils.getBlockItemHandler(dustbinPos);
                        if (handler != null) {
                            int slot = AotakeSweep.RANDOM.nextInt(handler.getContainerSize());
                            handler.setItem(slot, item.copy());
                            handler.setChanged();
                        }
                    }
                    break;
                }
            }
            break;
            case REMOVE:
            default:
                // 丢弃
                break;
        }

        long baseCount = result.getItemCount() > 0 ? result.getItemCount() : result.getEntityCount();
        result.setRecycledItemCount(Math.max(result.getRecycledItemCount(), baseCount));
    }

    /**
     * 单批次配置快照，避免每个实体重复创建配置代理和解析垃圾箱坐标。
     */
    private static final class SweepContext {
        private final EntityFilter.Matcher redlist;
        private final EntityFilter.Matcher catchMatcher;
        private final List<String> catchItems;
        private final boolean chunkVaultEnabled;
        private final EnumDustbinMode dustbinMode;
        private final Set<EnumSelfCleanMode> selfCleanModes;
        private final EnumOverflowMode overflowMode;
        private final int cacheLimit;
        private final List<WorldCoordinate> blockPositions;
        private final int sweepEntityLimit;
        private final int sweepEntityInterval;
        private final int sweepBatchLimit;
        private final int warningVoiceVolume;

        private SweepContext(CommonConfig.BaseView base) {
            CommonConfig.SweepView sweep = base.sweep();
            CommonConfig.EntityCatchView entityCatch = base.entityCatch();
            CommonConfig.DustbinView dustbin = base.dustbin();
            CommonConfig.BatchView batch = base.batch();

            EntityFilter filter = AotakeSweep.getEntityFilter();
            this.redlist = filter.compile(sweep.entityRedlist());
            this.catchMatcher = filter.compile(entityCatch.catchEntity());
            this.catchItems = new ArrayList<>(entityCatch.catchItem());
            this.chunkVaultEnabled = base.chunk().chunkVaultEnabled();
            this.dustbinMode = dustbin.dustbinBlockMode();
            this.selfCleanModes = new HashSet<>(dustbin.selfCleanMode());
            this.overflowMode = dustbin.dustbinOverflowMode();
            this.cacheLimit = dustbin.cacheLimit();
            this.blockPositions = new ArrayList<>();
            for (String raw : dustbin.dustbinBlockPositions()) {
                try {
                    WorldCoordinate coordinate = WorldCoordinate.fromString(raw);
                    if (coordinate != null) {
                        this.blockPositions.add(coordinate);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            this.sweepEntityLimit = batch.sweepEntityLimit();
            this.sweepEntityInterval = batch.sweepEntityInterval();
            this.sweepBatchLimit = batch.sweepBatchLimit();
            this.warningVoiceVolume = sweep.sweepWarningVoiceVolume();
        }
    }

    public static void scheduleRemoveEntity(Entity entity, boolean keepData) {
        if (!(entity.level instanceof ServerLevel)) return;
        ResourceKey<Level> dimensionKey = entity.level.dimension();

        Entity canonical = canonicalEntity(entity);
        pendingRemovals
                .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                .add(new KeyValue<>(canonical, keepData));
    }

    public static void flushPendingRemovals(ServerLevel world) {
        Queue<KeyValue<Entity, Boolean>> queue = pendingRemovals.get(world.dimension());
        if (queue == null) return;

        KeyValue<Entity, Boolean> keyValue;
        while ((keyValue = queue.poll()) != null) {
            if (keyValue.key().isAlive()) {
                EntityRemovalBridge.discard(keyValue.key(), keyValue.value());
            }
        }
    }

    /** 原版 Fabric 仅暴露末影龙部件，清理时统一回收到主体实体。 */
    private static Entity canonicalEntity(Entity entity) {
        return entity instanceof EnderDragonPart ? ((EnderDragonPart) entity).parentMob : entity;
    }

}
