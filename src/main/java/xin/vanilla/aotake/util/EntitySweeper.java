package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.items.IItemHandler;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.*;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EntitySweeper {
    private static final Map<ResourceKey<Level>, Queue<KeyValue<Entity, Boolean>>> pendingRemovals = new ConcurrentHashMap<>();

    private List<SimpleContainer> inventoryList;
    private ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> dropList;
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

    public SweepResult addDrops(@NonNull List<Entity> entities) {
        this.init();

        SweepResult result = new SweepResult();
        Set<Integer> seenEntities = new HashSet<>();

        for (Entity entity : entities) {
            int id = System.identityHashCode(entity);
            if (seenEntities.add(id)) {
                result.add(this.processDrop(entity));
            }
        }

        for (Entity entity : entitiesToRemove) {
            if (entity.isAlive() && entity.level() instanceof ServerLevel) {
                scheduleRemoveEntity(entity, false);
            }
        }
        entitiesToRemove.clear();

        WorldTrashData.get().setDirty();

        return result;
    }

    private SweepResult processDrop(@NonNull Entity original) {
        SweepResult result = new SweepResult();
        Coordinate coordinate = new Coordinate(original);
        Entity entity = (original instanceof PartEntity) ? ((PartEntity<?>) original).getParent() : original;

        String typeKey = (entity instanceof ItemEntity)
                ? AotakeUtils.getItemRegistryName(((ItemEntity) entity).getItem())
                : AotakeUtils.getEntityTypeRegistryName(entity);

        ItemStack itemToRecycle = null;

        // 处理掉落物
        if (entity instanceof ItemEntity) {
            ItemStack item = ((ItemEntity) entity).getItem();
            if (!ServerConfig.ITEM_REDLIST.get().contains(typeKey)) {
                itemToRecycle = item.copy();
                result.setItemCount(item.getCount());
            }
            // 延迟移除
            entitiesToRemove.add(entity);
        }
        // 处理其他实体
        else {
            // 回收实体
            if (!ServerConfig.CATCH_ITEM.get().isEmpty()
                    && ServerConfig.CATCH_ENTITY.get().contains(typeKey)
            ) {
                String randomItem = CollectionUtils.getRandomElement(ServerConfig.CATCH_ITEM.get());
                Item it = AotakeUtils.deserializeItem(randomItem);
                if (it != null) {
                    itemToRecycle = new ItemStack(it);
                    CompoundTag customData = new CompoundTag();
                    CompoundTag aotake = new CompoundTag();
                    aotake.putBoolean("byPlayer", false);
                    aotake.put("entity", entity.serializeNBT(entity.registryAccess()));
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

    private void handleItemRecycling(Coordinate coordinate, ItemStack item, SweepResult result) {
        // 自清洁模式
        if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_DELETE.name())) {
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
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

        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
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
        for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
            Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
            IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (handler != null) {
                IntStream.range(0, handler.getSlots())
                        .filter(i -> !handler.getStackInSlot(i).isEmpty())
                        .findAny()
                        .ifPresent(i -> handler.extractItem(i, handler.getSlotLimit(i), false));
            }
        }
    }

    private ItemStack addItemToVirtualDustbin(ItemStack item) {
        ItemStack remaining = item;
        for (SimpleContainer inv : this.inventoryList) {
            if (remaining.isEmpty()) break;

            if (inv.canAddItem(remaining)) {
                List<ItemStack> remainingList = new ArrayList<>();
                for (ItemStack itemStack : splitItemStack(remaining, inv.getMaxStackSize())) {
                    ItemStack leftover = inv.addItem(itemStack);
                    remainingList.add(leftover);
                }
                remaining = mergeItemStack(remainingList);
            }
        }
        return remaining;
    }

    private ItemStack addItemToDustbinBlock(ItemStack item) {
        ItemStack remaining = item;
        for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
            Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
            remaining = AotakeUtils.addItemToBlock(remaining, dustbinPos);
        }
        return remaining;
    }

    private void handleOverflow(Coordinate coordinate, ItemStack item, SweepResult result) {
        EnumOverflowMode mode = EnumOverflowMode.valueOf(ServerConfig.DUSTBIN_OVERFLOW_MODE.get());

        switch (mode) {
            case KEEP: {
                // 多余部分移除
                if (dropList.size() < CommonConfig.CACHE_LIMIT.get()) {
                    this.dropList.add(new KeyValue<>(coordinate, item.copy()));
                }
            }
            break;
            case REPLACE: {
                switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                    case VIRTUAL:
                    case VIRTUAL_BLOCK: {
                        SimpleContainer inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                        inv.setItem(slot, item.copy());
                    }
                    break;
                    case BLOCK:
                    case BLOCK_VIRTUAL: {
                        String pos = CollectionUtils.getRandomElement(ServerConfig.DUSTBIN_BLOCK_POSITIONS.get());
                        Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
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

    public static void scheduleRemoveEntity(Entity entity, boolean keepData) {
        if (!(entity.level() instanceof ServerLevel)) return;
        ResourceKey<Level> dimensionKey = entity.level().dimension();

        if (entity instanceof PartEntity) {
            entity = ((PartEntity<?>) entity).getParent();
        }
        if (entity.isMultipartEntity()) {
            PartEntity<?>[] parts = entity.getParts();
            if (CollectionUtils.isNotNullOrEmpty(parts)) {
                for (PartEntity<?> part : parts) {
                    pendingRemovals
                            .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                            .add(new KeyValue<>(part, keepData));
                }
            }
        } else {
            pendingRemovals
                    .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                    .add(new KeyValue<>(entity, keepData));
        }
    }

    public static void flushPendingRemovals(ServerLevel world) {
        Queue<KeyValue<Entity, Boolean>> queue = pendingRemovals.get(world.dimension());
        if (queue == null) return;

        KeyValue<Entity, Boolean> keyValue;
        while ((keyValue = queue.poll()) != null) {
            if (keyValue.getKey().isAlive()) {
                keyValue.getKey().remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    /**
     * 按 count 拆分 ItemStack
     *
     * @param stack 原始物品栈
     * @param count 每个子栈最大数量
     */
    private static List<ItemStack> splitItemStack(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }

        final int total = stack.getCount();
        final int parts = (total + count - 1) / count;

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

        ItemStack base = stacks.getFirst().copy();
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
