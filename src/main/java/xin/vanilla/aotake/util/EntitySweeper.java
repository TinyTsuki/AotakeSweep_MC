package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.*;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@SuppressWarnings("UnstableApiUsage")
public class EntitySweeper {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<ResourceKey<Level>, Queue<KeyValue<Entity, Boolean>>> pendingRemovals = new ConcurrentHashMap<>();

    private List<SimpleContainer> inventoryList;
    private ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList;
    private Queue<DropStatistics> dropCount;

    private final Set<Entity> entitiesToRemove = ConcurrentHashMap.newKeySet();

    private void init() {
        WorldTrashData worldTrashData = WorldTrashData.get();
        if (this.inventoryList == null) {
            this.inventoryList = worldTrashData.getInventoryList();
        }
        if (this.dropList == null) {
            this.dropList = worldTrashData.getDropList();
        }
        if (this.dropCount == null) {
            this.dropCount = worldTrashData.getDropCount();
        }
    }

    public void clear() {
        this.inventoryList = null;
        this.dropList = null;
        this.dropCount = null;
    }

    public SweepResult addDrops(@NonNull List<Entity> entities, SweepResult result) {
        if (result.getTotalBatch() == 0) LOGGER.debug("AddDrops started at {}", System.currentTimeMillis());
        this.init();

        if (result.getTotalBatch() == 0 && CollectionUtils.isNotNullOrEmpty(entities) && entities.size() > ServerConfig.get().batchConfig().sweepEntityLimit()) {
            List<List<Entity>> lists = CollectionUtils.splitToCollections(entities, ServerConfig.get().batchConfig().sweepEntityLimit(), ServerConfig.get().batchConfig().sweepBatchLimit());
            result.setTotalBatch(lists.size());
            if (lists.size() > 1) {
                for (int i = 1; i < lists.size(); i++) {
                    List<Entity> entityList = lists.get(i);
                    AotakeScheduler.schedule(AotakeSweep.serverInstance().key()
                            , ServerConfig.get().batchConfig().sweepEntityInterval() * i
                            , () -> AotakeSweep.entitySweeper().addDrops(entityList, result)
                    );
                }
                entities = lists.get(0);
            }
        }
        if (result.getTotalBatch() == 0) {
            result.setTotalBatch(1);
        }

        Set<Integer> seenEntities = new HashSet<>();
        for (Entity entity : entities) {
            int id = System.identityHashCode(entity);
            if (seenEntities.add(id)) {
                result.add(this.processDrop(entity));
            }
        }

        for (Entity entity : entitiesToRemove) {
            if (entity.level() instanceof ServerLevel) {
                scheduleRemoveEntity(entity, false);
            }
        }
        entitiesToRemove.clear();

        WorldTrashData.get().setDirty();

        result.incrementBatch();

        if (result.getBatch().get() >= result.getTotalBatch()) {
            LOGGER.debug("AddDrops finished at {}", System.currentTimeMillis());

            List<ServerPlayer> players = AotakeSweep.serverInstance().key().getPlayerList().getPlayers();
            for (ServerPlayer p : players) {
                String language = AotakeUtils.getPlayerLanguage(p);
                Component msg = AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
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
                                            , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = AotakeUtils.getWarningVoice(result.isEmpty() ? "fail" : "success");
                    float volume = ServerConfig.get().sweepConfig().sweepWarningVoiceVolume() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        } else {
            LOGGER.debug("AddDrops {}/{} at {}", result.getBatch().get(), result.getTotalBatch(), System.currentTimeMillis());
        }

        return result;
    }

    private SweepResult processDrop(@NonNull Entity original) {
        SweepResult result = new SweepResult();
        WorldCoordinate coordinate = new WorldCoordinate(original);
        Entity entity = (original instanceof EnderDragonPart part) ? part.parentMob : original;

        String typeKey = (entity instanceof ItemEntity)
                ? AotakeUtils.getItemRegistryName(((ItemEntity) entity).getItem())
                : AotakeUtils.getEntityTypeRegistryName(entity);

        ItemStack itemToRecycle = null;

        // 处理掉落物
        if (entity instanceof ItemEntity) {
            ItemStack item = ((ItemEntity) entity).getItem();
            if (!AotakeSweep.entityFilter().validEntity(ServerConfig.get().sweepConfig().entityRedlist(), entity)) {
                itemToRecycle = item.copy();
                result.setItemCount(item.getCount());
            }
            // 延迟移除
            entitiesToRemove.add(entity);
        }
        // 处理其他实体
        else {
            // 回收实体
            if (!ServerConfig.get().catchConfig().catchItem().isEmpty()
                    && AotakeSweep.entityFilter().validEntity(ServerConfig.get().catchConfig().catchEntity(), entity)
            ) {
                String randomItem = CollectionUtils.getRandomElement(ServerConfig.get().catchConfig().catchItem());
                Item it = AotakeUtils.deserializeItem(randomItem);
                if (it != null) {
                    itemToRecycle = new ItemStack(it);
                    CompoundTag customData = new CompoundTag();
                    CompoundTag aotake = new CompoundTag();
                    aotake.putBoolean("byPlayer", false);
                    CompoundTag entityTag = new CompoundTag();
                    if (entity.isPassenger()) {
                        entity.stopRiding();
                    }
                    entity.save(entityTag);
                    AotakeUtils.sanitizeCapturedEntityTag(entityTag);
                    aotake.put("entity", entityTag);
                    customData.put(AotakeSweep.MODID, aotake);
                    itemToRecycle.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

                    result.setRecycledEntityCount(1);
                }
            }
            result.setEntityCount(1);
            entitiesToRemove.add(entity);
        }

        // 记录清理历史
        this.dropCount.add(new DropStatistics(coordinate
                , typeKey
                , System.currentTimeMillis()
                , result.getItemCount()
                , result.getEntityCount()
        ));

        // 处理回收物品
        if (itemToRecycle != null) {
            handleItemRecycling(coordinate, itemToRecycle, result);
        }

        return result;
    }

    private void handleItemRecycling(WorldCoordinate coordinate, ItemStack item, SweepResult result) {
        // 自清洁模式
        if (ServerConfig.get().dustbinConfig().selfCleanMode().contains(EnumSelfCleanMode.SWEEP_DELETE.name())) {
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
                case VIRTUAL: {
                    selfCleanVirtualDustbin();
                }
                break;
                case BLOCK: {
                    selfCleanDustbinBlock();
                }
                break;
                default: {
                    selfCleanVirtualDustbin();
                    selfCleanDustbinBlock();
                }
            }
        }

        ItemStack remaining = item;
        int recycledCount = item.getCount();

        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
            case VIRTUAL: {
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
            case BLOCK: {
                remaining = addItemToDustbinBlock(remaining);
            }
            break;
            case VIRTUAL_BLOCK: {
                remaining = addItemToVirtualDustbin(remaining);
                remaining = addItemToDustbinBlock(remaining);
            }
            break;
            case BLOCK_VIRTUAL: {
                remaining = addItemToDustbinBlock(remaining);
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
        }

        // 剩余部分进行溢出处理
        if (!remaining.isEmpty()) {
            recycledCount = recycledCount - remaining.getCount();
            handleOverflow(coordinate, remaining, result);
        }

        result.setRecycledItemCount(recycledCount);
    }

    private void selfCleanVirtualDustbin() {
        SimpleContainer inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
        IntStream.range(0, inv.getContainerSize())
                .filter(i -> !inv.getItem(i).isEmpty())
                .findAny()
                .ifPresent(i -> inv.setItem(i, ItemStack.EMPTY));
    }

    private void selfCleanDustbinBlock() {
        for (String pos : ServerConfig.get().dustbinConfig().dustbinBlockPositions()) {
            WorldCoordinate dustbinPos = WorldCoordinate.fromSimpleString(pos);
            Storage<ItemVariant> storage = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (storage != null) {
                try {
                    for (StorageView<ItemVariant> view : storage) {
                        if (view == null || view.isResourceBlank()) continue;
                        long amount = view.getAmount();
                        if (amount <= 0) continue;
                        try (Transaction tx = Transaction.openOuter()) {
                            storage.extract(view.getResource(), amount, tx);
                            tx.commit();
                        } catch (Throwable ignored) {
                        }
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private ItemStack addItemToVirtualDustbin(ItemStack item) {
        ItemStack remaining = item;
        for (SimpleContainer inv : this.inventoryList) {
            if (remaining.isEmpty()) break;

            if (inv.canAddItem(remaining)) {
                List<ItemStack> remainingList = new ArrayList<>();
                List<ItemStack> itemStackList = splitItemStack(remaining, inv.getMaxStackSize());
                int splits = itemStackList.size();
                for (ItemStack itemStack : itemStackList) {
                    ItemStack leftover = inv.addItem(itemStack);
                    remainingList.add(leftover);
                }
                if (splits > 0) remaining = mergeItemStack(remainingList);
            }
        }
        return remaining;
    }

    private ItemStack addItemToDustbinBlock(ItemStack item) {
        ItemStack remaining = item;
        for (String pos : ServerConfig.get().dustbinConfig().dustbinBlockPositions()) {
            WorldCoordinate dustbinPos = WorldCoordinate.fromSimpleString(pos);
            Storage<ItemVariant> storage = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (storage != null) {
                int invMax = 64;
                try {
                    long minCap = Long.MAX_VALUE;
                    if (storage instanceof SlottedStorage<?> slottedRaw) {
                        @SuppressWarnings("unchecked")
                        SlottedStorage<ItemVariant> slotted = (SlottedStorage<ItemVariant>) slottedRaw;
                        int slotCount = slotted.getSlotCount();
                        for (int i = 0; i < slotCount; i++) {
                            try {
                                SingleSlotStorage<ItemVariant> slot = slotted.getSlot(i);
                                long cap = slot.getCapacity();
                                if (cap > 0 && cap < minCap) minCap = cap;
                            } catch (Throwable t) {
                                LOGGER.debug("Failed to get slot capacity", t);
                            }
                        }
                    } else {
                        for (StorageView<ItemVariant> view : storage) {
                            try {
                                long cap = view.getCapacity();
                                if (cap > 0 && cap < minCap) minCap = cap;
                            } catch (Throwable t) {
                                LOGGER.debug("Failed to get view capacity", t);
                            }
                        }
                    }
                    if (minCap != Long.MAX_VALUE) {
                        invMax = (int) Math.min(minCap, Integer.MAX_VALUE);
                    }
                    List<ItemStack> remainingList = new ArrayList<>();
                    List<ItemStack> itemStackList = splitItemStack(remaining, invMax);
                    int splits = itemStackList.size();
                    for (ItemStack itemStack : itemStackList) {
                        ItemStack leftover = AotakeUtils.addItemToStorage(itemStack, storage);
                        remainingList.add(leftover);
                    }
                    if (splits > 0) remaining = mergeItemStack(remainingList);
                } catch (Throwable t) {
                    LOGGER.debug("Failed to add item to dustbin block at {}", dustbinPos, t);
                }
            }
        }
        return remaining;
    }

    private void handleOverflow(WorldCoordinate coordinate, ItemStack item, SweepResult result) {
        EnumOverflowMode mode = EnumOverflowMode.valueOf(ServerConfig.get().dustbinConfig().dustbinOverflowMode());

        switch (mode) {
            case KEEP: {
                // 多余部分移除
                if (dropList.size() < ServerConfig.get().dustbinConfig().cacheLimit()) {
                    this.dropList.add(new KeyValue<>(coordinate, item.copy()));
                }
            }
            break;
            case REPLACE: {
                switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.get().dustbinConfig().dustbinMode())) {
                    case VIRTUAL:
                    case VIRTUAL_BLOCK: {
                        SimpleContainer inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                        inv.setItem(slot, item.copy());
                    }
                    break;
                    case BLOCK:
                    case BLOCK_VIRTUAL: {
                        String pos = CollectionUtils.getRandomElement(ServerConfig.get().dustbinConfig().dustbinBlockPositions());
                        WorldCoordinate dustbinPos = WorldCoordinate.fromSimpleString(pos);
                        Storage<ItemVariant> storage = AotakeUtils.getBlockItemHandler(dustbinPos);

                        if (storage != null) {
                            try (Transaction transaction = Transaction.openOuter()) {
                                List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
                                for (StorageView<ItemVariant> view : storage) {
                                    if (view instanceof SingleSlotStorage<ItemVariant> slotStorage) {
                                        slots.add(slotStorage);
                                    }
                                }
                                if (!slots.isEmpty()) {
                                    int slotIndex = AotakeSweep.RANDOM.nextInt(slots.size());
                                    SingleSlotStorage<ItemVariant> targetSlot = slots.get(slotIndex);
                                    if (!targetSlot.isResourceBlank()) {
                                        targetSlot.extract(targetSlot.getResource(), targetSlot.getAmount(), transaction);
                                    }
                                    ItemVariant newVariant = ItemVariant.of(item);
                                    long amount = item.getCount();
                                    targetSlot.insert(newVariant, amount, transaction);
                                }
                                transaction.commit();
                            }
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

    public static void scheduleRemoveEntity(Entity entity, boolean keepData) {
        if (!(entity.level() instanceof ServerLevel)) return;
        ResourceKey<Level> dimensionKey = entity.level().dimension();
        if (entity instanceof EnderDragonPart part) {
            entity = part.parentMob;
        }
        pendingRemovals
                .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                .add(new KeyValue<>(entity, keepData));
    }

    public static void flushPendingRemovals(ServerLevel world) {
        Queue<KeyValue<Entity, Boolean>> queue = pendingRemovals.get(world.dimension());
        if (queue == null) return;

        KeyValue<Entity, Boolean> keyValue;
        while ((keyValue = queue.poll()) != null) {
            Entity entity = keyValue.getKey();
            if (entity.isAlive()) {
                try {
                    entity.discard();
                    LOGGER.debug("Removed entity {} at {}", entity, entity.position());
                } catch (Throwable t) {
                    LOGGER.debug("Failed to remove entity {}", entity, t);
                }
            }
        }
    }

    /**
     * 按 invMax 拆分 ItemStack
     *
     * @param stack  原始物品栈
     * @param invMax 每个子栈最大数量
     */
    private static List<ItemStack> splitItemStack(ItemStack stack, int invMax) {
        if (stack == null || stack.isEmpty() || invMax <= 0) {
            return new ArrayList<>();
        }
        int count = Math.min(invMax, stack.getMaxStackSize());

        int total = stack.getCount();
        int parts = (int) Math.ceil((double) total / count);

        return IntStream.range(0, parts)
                .mapToObj(i -> {
                    int splitSize = Math.min(count, total - i * count);
                    ItemStack s = stack.copy();
                    s.setCount(splitSize);
                    return s;
                })
                .collect(Collectors.toList());
    }

    /**
     * 合并多个 ItemStack
     *
     * @param stacks 要合并的物品栈(必须是相同的物品)
     */
    private static ItemStack mergeItemStack(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack base = stacks.get(0).copy();
        if (base.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int totalCount = stacks.stream()
                // .filter(s -> s != null && !s.isEmpty() && ItemStack.isSameItemSameTags(base, s))
                .mapToInt(ItemStack::getCount)
                .sum();

        base.setCount(totalCount);
        return base;
    }

}
