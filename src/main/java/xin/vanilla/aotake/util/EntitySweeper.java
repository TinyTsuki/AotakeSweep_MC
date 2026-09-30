package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CommonConfigView;
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
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.rule.CleanupRuleGate;
import xin.vanilla.aotake.rule.RuleGeneration;
import xin.vanilla.aotake.rule.RuleManager;
import xin.vanilla.aotake.sweep.SweepBackend;
import xin.vanilla.aotake.sweep.SweepBatchCommitter;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.*;

import java.util.*;

@SuppressWarnings("resource")
public class EntitySweeper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final RateLimitedErrorLogger ENTITY_ERRORS = new RateLimitedErrorLogger(60_000L);

    private static final RemovalLedger<RegistryKey<World>, Entity> removals = new RemovalLedger<>();

    private List<Inventory> inventoryList;
    private ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList;

    private final Set<SweepJob> jobs = Collections.newSetFromMap(new IdentityHashMap<>());

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
        for (SweepJob job : new ArrayList<>(jobs)) job.cancel();
        ChunkVaultStorage.flushPending();
        removals.clear();
        this.inventoryList = null;
        this.dropList = null;
    }

    public SweepResult addDrops(@NonNull List<Entity> entities, SweepResult result) {
        return addDrops(entities, result, AotakeRuleRuntime.acquire(RuleManager.RuleUse.RECYCLE));
    }

    public SweepResult addDrops(@NonNull List<Entity> entities, SweepResult result, RuleGeneration<Entity> generation) {
        CleanupRuleGate<Entity> gate = new CleanupRuleGate<>(generation, () -> AotakeRuleRuntime.isUsable(generation.id()));
        gate.requireUsable();
        SweepContext context = new SweepContext(CommonConfig.get().base());
        List<Entity> eligible = eligible(entities);
        gate.prepare(eligible, entity -> entity instanceof ItemEntity, !context.catchItems.isEmpty());
        List<List<Entity>> batches = eligible.isEmpty() ? Collections.singletonList(new ArrayList<>())
                : CollectionUtils.splitToCollections(eligible, context.sweepEntityLimit, context.sweepBatchLimit);
        this.init();
        result.setTotalBatch(batches.size());
        SweepJob job = new SweepJob(gate, batches, result, context);
        jobs.add(job);
        job.run();
        return result;
    }

    private static List<Entity> eligible(List<Entity> entities) {
        List<Entity> eligible = new ArrayList<>();
        Set<Entity> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity original : entities) {
            Entity canonical = (original instanceof PartEntity) ? ((PartEntity<?>) original).getParent() : original;
            if (seenEntities.add(canonical) && AotakeUtils.prepareSweepCandidate(canonical)) eligible.add(canonical);
        }
        return eligible;
    }

    private final class SweepJob implements Runnable {
        private final List<List<Entity>> batches;
        private final SweepResult result;
        private final SweepContext context;
        private final RecyclingBatch committer;
        private int index;
        private boolean canceled;

        private SweepJob(CleanupRuleGate<Entity> gate,
                         List<List<Entity>> batches, SweepResult result, SweepContext context) {
            this.batches = new ArrayList<>();
            for (List<Entity> batch : batches) this.batches.add(new ArrayList<>(batch));
            this.result = result; this.context = context;
            this.committer = new RecyclingBatch(gate, result, context);
        }

        void cancel() { canceled = true; batches.clear(); jobs.remove(this); }

        @Override public void run() {
            if (canceled) return;
            try {
                List<Entity> batch = batches.get(index);
                committer.execute(batch, new SweepBackend.Progress(batch.size()));
                batches.get(index).clear();
                finishBatch(result, context);
                if (++index >= batches.size()) cancel();
                else BaniraScheduler.schedule(BaniraServer.require(MinecraftServer.class), context.sweepEntityInterval, this);
            } catch (RuntimeException failure) {
                cancel();
                AotakeUtils.reportSweepFailure(failure);
            }
        }
    }

    /** One recycling transaction state shared by every batch of a cleanup task. */
    public final class RecyclingBatch {
        private final Map<Entity, Boolean> decisions = new IdentityHashMap<>();
        private final SweepBatchCommitter<Entity> executor;
        private RecyclingBatch(CleanupRuleGate<Entity> gate, SweepResult result, SweepContext context) {
            executor = new SweepBatchCommitter<>(new SweepBatchCommitter.Operations<Entity>() {
                public void requireUsable() { gate.requireUsable(); }
                public void prepare(List<Entity> entities) {
                    decisions.clear();
                    for (CleanupRuleGate.Decision<Entity> decision : gate.prepare(EntitySweeper.eligible(entities),
                            entity -> entity instanceof ItemEntity, !context.catchItems.isEmpty()))
                        decisions.put(decision.subject(), decision.recycle());
                }
                public void begin() {
                    init();
                    if (!context.selfCleanModes.contains(EnumSelfCleanMode.SWEEP_CLEAR)) return;
                    if (context.dustbinMode != EnumDustbinMode.BLOCK) AotakeUtils.clearVirtualDustbin();
                    if (context.dustbinMode != EnumDustbinMode.VIRTUAL) {
                        for (WorldCoordinate position : context.blockPositions) {
                            IItemHandler handler = AotakeUtils.getBlockItemHandler(position);
                            if (handler != null) for (int slot = 0; slot < handler.getSlots(); slot++) handler.extractItem(slot, handler.getSlotLimit(slot), false);
                        }
                    }
                }
                public boolean eligible(Entity entity) { return decisions.containsKey(entity) && AotakeUtils.prepareSweepCandidate(entity); }
                public void commit(Entity entity, SweepBackend.Progress progress) {
                    boolean recycle = decisions.get(entity);
                    result.add(processDrop(entity, result, context, recycle, progress));
                }
                public void flush() { try { flushPendingStorage(); } finally { decisions.clear(); } }
            });
        }
        public void execute(List<Entity> entities, SweepBackend.Progress progress) { executor.execute(entities, progress); }
    }

    public RecyclingBatch beginRecycling(SweepResult result, RuleGeneration<Entity> generation, CommonConfigView.BaseView settings) {
        return new RecyclingBatch(new CleanupRuleGate<>(generation, () -> AotakeRuleRuntime.isUsable(generation.id())),
                result, new SweepContext(settings));
    }

    public static void flushPendingStorage() {
        try { ChunkVaultStorage.flushPending(); }
        finally { WorldTrashData.get().setDirty(); }
    }

    private void finishBatch(SweepResult result, SweepContext context) {
        result.incrementBatch();

        if (result.getBatch().get() >= result.getTotalBatch()) {
            ChunkVaultStorage.flushPending();

            List<ServerPlayerEntity> players = BaniraServer.require(MinecraftServer.class).getPlayerList().getPlayers();
            for (ServerPlayerEntity p : players) {
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

    }

    private SweepResult processDrop(@NonNull Entity original, SweepResult batchResult, SweepContext context, boolean recycle, SweepBackend.Progress progress) {
        SweepResult result = new SweepResult();
        WorldCoordinate coordinate = new WorldCoordinate(original);
        Entity entity = (original instanceof PartEntity) ? ((PartEntity<?>) original).getParent() : original;

        String typeKey = (entity instanceof ItemEntity)
                ? ItemUtils.getItemRegistryString(((ItemEntity) entity).getItem())
                : EntityUtils.getEntityRegistryString(entity);

        ItemStack itemToRecycle = null;

        // 处理掉落物
        if (entity instanceof ItemEntity) {
            ItemStack item = ((ItemEntity) entity).getItem();
            result.setItemCount(item.getCount());
            if (recycle) {
                itemToRecycle = item.copy();
            }
        }
        // 处理其他实体
        else {
            // 回收实体
            if (recycle) {
                String randomItem = CollectionUtils.getRandomElement(context.catchItems);
                itemToRecycle = ItemUtils.deserializeItemStack(randomItem);
                CompoundNBT tag = itemToRecycle.getOrCreateTag();
                CompoundNBT aotake = new CompoundNBT();
                aotake.putBoolean("byPlayer", false);
                if (entity.isPassenger()) {
                    entity.stopRiding();
                }
                CompoundNBT entityTag = new CompoundNBT();
                entity.save(entityTag);
                AotakeUtils.sanitizeCapturedEntityTag(entityTag);
                aotake.put("entity", entityTag);
                aotake.putString("entityId", EntityUtils.getEntityRegistryString(entity));
                aotake.putString("name", ItemUtils.getItemCustomNameJson(itemToRecycle));
                tag.put(AotakeSweep.MODID, aotake);

                result.setRecycledEntityCount(1);
            }
            result.setEntityCount(1);
        }

        // Own the entity before transferring items: nested callbacks and retries cannot recycle it again.
        if (!tryScheduleRemoveEntity(entity, false)) {
            progress.skipped(1);
            return new SweepResult();
        }
        progress.processed(1, recycle ? 1 : 0, 1, true);

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
        Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    private void selfCleanDustbinBlock(SweepContext context) {
        for (WorldCoordinate dustbinPos : context.blockPositions) {
            IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!handler.getStackInSlot(i).isEmpty()) {
                        handler.extractItem(i, handler.getSlotLimit(i), false);
                        break;
                    }
                }
            }
        }
    }

    private ItemStack addItemToVirtualDustbin(ItemStack item) {
        ItemStack remaining = item;
        for (Inventory inv : this.inventoryList) {
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
                        Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                        inv.setItem(slot, item.copy());
                    }
                    break;
                    case BLOCK:
                    case BLOCK_VIRTUAL: {
                        WorldCoordinate dustbinPos = CollectionUtils.getRandomElement(context.blockPositions);
                        if (dustbinPos == null) break;
                        IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
                        if (handler != null) {
                            int slot = AotakeSweep.RANDOM.nextInt(handler.getSlots());
                            handler.extractItem(slot, handler.getSlotLimit(slot), false);
                            handler.insertItem(slot, item.copy(), false);
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

        private SweepContext(CommonConfigView.BaseView base) {
            CommonConfigView.BaseView.SweepView sweep = base.sweep();
            CommonConfigView.BaseView.EntityCatchView entityCatch = base.entityCatch();
            CommonConfigView.BaseView.DustbinView dustbin = base.dustbin();
            CommonConfigView.BaseView.BatchView batch = base.batch();

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
        tryScheduleRemoveEntity(entity, keepData);
    }

    public static boolean tryScheduleRemoveEntity(Entity entity, boolean keepData) {
        Entity canonical = entity instanceof PartEntity ? ((PartEntity<?>) entity).getParent() : entity;
        if (canonical == null || !canonical.isAlive() || !(canonical.level instanceof ServerWorld)) return false;
        RegistryKey<World> dimensionKey = canonical.level.dimension();

        return removals.schedule(dimensionKey, canonical, keepData);
    }

    public static boolean isPendingRemoval(Entity entity) { return removals.contains(entity); }

    public static void flushPendingRemovals(ServerWorld world) {
        removals.flush(world.dimension(), (entity, keepData) -> {
            if (!entity.isAlive()) return true;
            try {
                EntityRemovalBridge.discard(world, entity, keepData);
                return !entity.isAlive();
            } catch (RuntimeException e) {
                ENTITY_ERRORS.log(LOGGER, "Discard sweep entity " + entity.getStringUUID(), e);
                return false;
            }
        });
    }

}
