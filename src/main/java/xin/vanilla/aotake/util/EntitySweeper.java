package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.entity.PartEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.*;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public class EntitySweeper {
    private static final Map<RegistryKey<World>, Queue<KeyValue<Entity, Boolean>>> pendingRemovals = new ConcurrentHashMap<>();

    private List<Inventory> inventoryList;
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
            if (entity.isAlive() && entity.level instanceof ServerWorld) {
                scheduleRemoveEntity(entity, false);
            }
        }
        entitiesToRemove.clear();

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
                itemToRecycle = new ItemStack(AotakeUtils.deserializeItem(randomItem));
                CompoundNBT tag = itemToRecycle.getOrCreateTag();
                CompoundNBT aotake = new CompoundNBT();
                aotake.putBoolean("byPlayer", false);
                aotake.put("entity", entity.serializeNBT());
                tag.put(AotakeSweep.MODID, aotake);

                result.setRecycledEntityCount(1);
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
            Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
            IntStream.range(0, inv.getContainerSize())
                    .filter(i -> !inv.getItem(i).isEmpty())
                    .findAny()
                    .ifPresent(i -> inv.setItem(i, ItemStack.EMPTY));
        }

        ItemStack remaining = item;
        int recycledCount = 0;

        for (Inventory inv : this.inventoryList) {
            if (remaining.isEmpty()) break;

            if (inv.canAddItem(remaining)) {
                ItemStack leftover = inv.addItem(remaining);
                int inserted = remaining.getCount() - leftover.getCount();
                recycledCount += inserted;
                remaining = leftover;
            }
        }
        // 剩余部分进行溢出处理
        if (!remaining.isEmpty()) {
            handleOverflow(coordinate, remaining, result);
        }

        result.setRecycledItemCount(recycledCount);
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
                Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                inv.setItem(slot, item.copy());
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
        if (!(entity.level instanceof ServerWorld)) return;
        RegistryKey<World> dimensionKey = entity.level.dimension();

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

    public static void flushPendingRemovals(ServerWorld world) {
        Queue<KeyValue<Entity, Boolean>> queue = pendingRemovals.get(world.dimension());
        if (queue == null) return;

        KeyValue<Entity, Boolean> keyValue;
        while ((keyValue = queue.poll()) != null) {
            if (keyValue.getKey().isAlive()) {
                world.removeEntity(keyValue.getKey(), keyValue.getValue());
            }
        }
    }
}
